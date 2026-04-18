package com.dispatchiq.engine;

import com.dispatchiq.model.RideRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Bounded queue that sits between riders submitting requests and dispatch workers.
 *
 * BACKPRESSURE: When the queue is at capacity, offer() returns false immediately
 * instead of blocking forever. The caller then rejects the request with a 503 —
 * this is how the system stays alive under load instead of running out of memory.
 */
@Component
public class DispatchQueue {

    private final LinkedBlockingQueue<RideRequest> queue;

    public DispatchQueue(@Value("${dispatch.queue.capacity}") int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    /**
     * Non-blocking enqueue. Returns false if queue is full (backpressure signal).
     */
    public boolean enqueue(RideRequest request) {
        return queue.offer(request);
    }

    /**
     * Drain up to batchSize requests in one shot for the batch matcher.
     * Non-blocking — if fewer than batchSize are available, we just get what's there.
     */
    public List<RideRequest> drainBatch(int batchSize) {
        List<RideRequest> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        return batch;
    }

    public int size()    { return queue.size(); }
    public boolean isEmpty() { return queue.isEmpty(); }
}
