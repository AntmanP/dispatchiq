package com.dispatchiq.queue;

import com.dispatchiq.model.RideRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The central ingest queue for all incoming ride requests.
 *
 * KEY DESIGN DECISIONS:
 * - ArrayBlockingQueue with a fixed capacity = bounded queue.
 *   If we used an unbounded queue, the system would appear to accept everything
 *   but silently build up memory pressure until it crashes.
 *   A bounded queue forces explicit backpressure: when full, we reject with a
 *   clear signal so the client knows to retry.
 *
 * - We use offer() not put():
 *   put() would block the HTTP thread waiting for space.
 *   offer() returns immediately — we send a 503 to the client fast and move on.
 */
@Component
public class RideRequestQueue {

    private static final int CAPACITY = 500;

    private final BlockingQueue<RideRequest> queue = new ArrayBlockingQueue<>(CAPACITY);
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);

    public boolean enqueue(RideRequest request) {
        boolean accepted = queue.offer(request);
        if (accepted) {
            totalEnqueued.incrementAndGet();
        } else {
            totalRejected.incrementAndGet();
            request.markRejected();
        }
        return accepted;
    }

    /**
     * Drain up to maxBatchSize requests for one batch processing cycle.
     * Non-blocking — returns however many are available right now.
     */
    public List<RideRequest> drainBatch(int maxBatchSize) {
        List<RideRequest> batch = new ArrayList<>(maxBatchSize);
        queue.drainTo(batch, maxBatchSize);
        return batch;
    }

    public int currentDepth() { return queue.size(); }
    public int capacity() { return CAPACITY; }
    public long getTotalEnqueued() { return totalEnqueued.get(); }
    public long getTotalRejected() { return totalRejected.get(); }
    public double fillPercentage() { return (double) queue.size() / CAPACITY * 100.0; }
}
