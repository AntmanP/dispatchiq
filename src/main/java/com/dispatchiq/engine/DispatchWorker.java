package com.dispatchiq.engine;

import com.dispatchiq.model.Driver;
import com.dispatchiq.model.Match;
import com.dispatchiq.model.RideRequest;
import com.dispatchiq.service.DriverRegistry;
import com.dispatchiq.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * One dispatch worker. Runs a batch cycle every `intervalMs` milliseconds:
 *   1. Drain up to batchSize requests from the queue
 *   2. Get currently available drivers
 *   3. Run BatchMatcher to find optimal assignment
 *   4. For each match: schedule the driver to complete their trip after a random duration
 */
public class DispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(DispatchWorker.class);

    private final int workerId;
    private final DispatchQueue queue;
    private final DriverRegistry driverRegistry;
    private final MetricsService metrics;
    private final int batchSize;
    private final int minTripMs;
    private final int maxTripMs;
    private final Random random = new Random();

    // Each worker gets its own single-threaded scheduler for trip completion callbacks
    private final ScheduledExecutorService tripScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "trip-scheduler-" + workerId);
                t.setDaemon(true);
                return t;
            });

    public DispatchWorker(int workerId, DispatchQueue queue, DriverRegistry driverRegistry,
                          MetricsService metrics, int batchSize, int minTripMs, int maxTripMs) {
        this.workerId       = workerId;
        this.queue          = queue;
        this.driverRegistry = driverRegistry;
        this.metrics        = metrics;
        this.batchSize      = batchSize;
        this.minTripMs      = minTripMs;
        this.maxTripMs      = maxTripMs;
    }

    /**
     * One batch cycle. Called by the DispatchEngine on a fixed schedule.
     */
    public void runCycle() {
        metrics.updateQueueDepth(queue.size());

        List<RideRequest> batch = queue.drainBatch(batchSize);
        if (batch.isEmpty()) return;

        List<Driver> available = driverRegistry.getAvailableDrivers();
        if (available.isEmpty()) {
            // No drivers right now — put requests back? No — we leave them drained.
            // In production you'd re-enqueue; for demo simplicity we log and count them.
            log.warn("[Worker-{}] Drained {} requests but no drivers available — requests dropped",
                    workerId, batch.size());
            return;
        }

        List<Match> matches = BatchMatcher.match(batch, available);
        metrics.recordBatch();

        for (Match match : matches) {
            long waitMs = match.getWaitTimeMs();
            metrics.recordMatched(waitMs);

            log.info("[Worker-{}] MATCHED request={} → driver={} | dist={:.2f}km | wait={}ms",
                    workerId, match.getRequestId(), match.getDriverId(),
                    match.getDistanceKm(), waitMs);

            // Schedule driver to become available again after a random trip duration
            int tripDuration = minTripMs + random.nextInt(maxTripMs - minTripMs);
            Driver driver = driverRegistry.getById(match.getDriverId());
            tripScheduler.schedule(() -> {
                driver.completeTrip();
                log.info("[Worker-{}] Driver {} completed trip — now AVAILABLE", workerId, driver.getDriverId());
            }, tripDuration, TimeUnit.MILLISECONDS);
        }

        // Unmatched requests (no driver was available for them) stay lost for this cycle
        int unmatched = batch.size() - matches.size();
        if (unmatched > 0) {
            log.info("[Worker-{}] {} requests in batch could not be matched (no available drivers)",
                    workerId, unmatched);
        }
    }

    public void shutdown() {
        tripScheduler.shutdown();
    }
}
