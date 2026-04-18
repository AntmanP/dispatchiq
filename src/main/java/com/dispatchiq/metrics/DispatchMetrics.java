package com.dispatchiq.metrics;

import com.dispatchiq.model.MatchResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight in-memory metrics store.
 * Uses LongAdder for high-concurrency counters (better than AtomicLong under contention).
 * Exposed via the /api/metrics endpoint so the video demo can show live numbers.
 */
@Component
public class DispatchMetrics {

    private final LongAdder totalMatchedCount = new LongAdder();
    private final LongAdder totalBatchCount = new LongAdder();
    private final LongAdder totalRequestsInBatches = new LongAdder();
    private final LongAdder totalUnmatchedInBatches = new LongAdder();
    private final LongAdder totalMatchDistanceSum = new LongAdder(); // stored as int * 1000 for precision

    private volatile long lastBatchTimestamp = System.currentTimeMillis();

    public void recordMatch(MatchResult match) {
        totalMatchedCount.increment();
        totalMatchDistanceSum.add((long)(match.distanceKm() * 1000));
    }

    public void recordBatch(int batchSize, int matchedCount) {
        totalBatchCount.increment();
        totalRequestsInBatches.add(batchSize);
        totalUnmatchedInBatches.add(batchSize - matchedCount);
        lastBatchTimestamp = System.currentTimeMillis();
    }

    public long getTotalMatched() { return totalMatchedCount.sum(); }
    public long getTotalBatches() { return totalBatchCount.sum(); }

    public double getAverageMatchDistance() {
        long matched = totalMatchedCount.sum();
        return matched == 0 ? 0 : (totalMatchDistanceSum.sum() / 1000.0) / matched;
    }

    public double getBatchEfficiency() {
        long requests = totalRequestsInBatches.sum();
        long unmatched = totalUnmatchedInBatches.sum();
        return requests == 0 ? 0 : (double)(requests - unmatched) / requests * 100.0;
    }

    public long getLastBatchTimestamp() { return lastBatchTimestamp; }
}
