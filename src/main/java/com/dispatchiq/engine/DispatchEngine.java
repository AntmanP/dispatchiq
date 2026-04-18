package com.dispatchiq.engine;

import com.dispatchiq.metrics.DispatchMetrics;
import com.dispatchiq.model.Driver;
import com.dispatchiq.model.MatchResult;
import com.dispatchiq.model.RideRequest;
import com.dispatchiq.queue.RideRequestQueue;
import com.dispatchiq.service.DriverRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * The heart of DispatchIQ.
 *
 * Runs a fixed pool of WORKER_COUNT worker threads. Each worker fires on a
 * fixed schedule (every BATCH_INTERVAL_MS milliseconds), drains a batch from
 * the queue, runs the BatchMatcher, and dispatches the results.
 *
 * WHY SCHEDULED WORKERS INSTEAD OF EVENT-DRIVEN?
 * Event-driven (match immediately when request arrives) is faster per-request
 * but loses the batching benefit. By waiting BATCH_INTERVAL_MS, we accumulate
 * multiple requests and can make smarter global assignments.
 * This is the fundamental latency/quality tradeoff in batch matching systems.
 *
 * WORKER_COUNT = number of "servers" in our limited resource scenario.
 * Even with 500 concurrent clients, only WORKER_COUNT threads do actual work.
 */
@Component
public class DispatchEngine {

    private static final Logger log = Logger.getLogger(DispatchEngine.class.getName());

    private static final int WORKER_COUNT = 5;
    private static final long BATCH_INTERVAL_MS = 500; // run a batch cycle every 500ms
    private static final int BATCH_SIZE = 20;           // max requests per batch per worker

    private final RideRequestQueue requestQueue;
    private final DriverRegistry driverRegistry;
    private final BatchMatcher batchMatcher;
    private final DispatchMetrics metrics;

    private final ScheduledExecutorService workerPool;
    private final AtomicLong totalMatched = new AtomicLong(0);
    private final AtomicLong totalBatchesProcessed = new AtomicLong(0);

    public DispatchEngine(RideRequestQueue requestQueue,
                          DriverRegistry driverRegistry,
                          DispatchMetrics metrics) {
        this.requestQueue = requestQueue;
        this.driverRegistry = driverRegistry;
        this.batchMatcher = new BatchMatcher();
        this.metrics = metrics;
        this.workerPool = Executors.newScheduledThreadPool(WORKER_COUNT, r -> {
            Thread t = new Thread(r);
            t.setName("dispatch-worker-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void start() {
        log.info(String.format("DispatchEngine starting with %d workers, batch interval %dms, batch size %d",
            WORKER_COUNT, BATCH_INTERVAL_MS, BATCH_SIZE));

        for (int i = 0; i < WORKER_COUNT; i++) {
            workerPool.scheduleAtFixedRate(
                this::runBatchCycle,
                i * 100L,        // stagger worker starts by 100ms each to reduce contention
                BATCH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * One batch processing cycle:
     * 1. Drain up to BATCH_SIZE requests from the queue
     * 2. Get currently available drivers
     * 3. Run batch matching algorithm
     * 4. Dispatch matched pairs and update metrics
     */
    private void runBatchCycle() {
        List<RideRequest> batch = requestQueue.drainBatch(BATCH_SIZE);
        if (batch.isEmpty()) return;

        List<Driver> availableDrivers = driverRegistry.getAvailableDrivers();
        List<MatchResult> matches = batchMatcher.match(batch, availableDrivers);

        // Dispatch each match
        for (MatchResult match : matches) {
            match.request().markMatched(match.driver().getId());
            driverRegistry.scheduleTripCompletion(match.driver(), match.distanceKm());
            totalMatched.incrementAndGet();
            metrics.recordMatch(match);
            log.fine(String.format("[%s] %s", Thread.currentThread().getName(), match));
        }

        // Any requests in the batch that didn't get a driver — put back or mark failed
        int unmatched = batch.size() - matches.size();
        if (unmatched > 0) {
            batch.stream()
                .filter(r -> r.getAssignedDriverId() == null)
                .forEach(r -> {
                    // Try to re-enqueue; if queue is full, mark as failed
                    boolean requeued = requestQueue.enqueue(r);
                    if (!requeued) r.markFailed();
                });
        }

        totalBatchesProcessed.incrementAndGet();
        metrics.recordBatch(batch.size(), matches.size());
    }

    @PreDestroy
    public void stop() {
        workerPool.shutdown();
    }

    public long getTotalMatched() { return totalMatched.get(); }
    public long getTotalBatchesProcessed() { return totalBatchesProcessed.get(); }
    public int getWorkerCount() { return WORKER_COUNT; }
    public int getBatchSize() { return BATCH_SIZE; }
}
