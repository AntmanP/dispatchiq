package com.dispatchiq.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Service
public class MetricsService {

    private final LongAdder totalSubmitted  = new LongAdder();
    private final LongAdder totalMatched    = new LongAdder();
    private final LongAdder totalRejected   = new LongAdder();  // backpressure rejections
    private final LongAdder totalBatches    = new LongAdder();
    private final LongAdder totalWaitTimeMs = new LongAdder();
    private final AtomicLong peakQueueDepth = new AtomicLong(0);

    public void recordSubmitted()           { totalSubmitted.increment(); }
    public void recordMatched(long waitMs)  { totalMatched.increment(); totalWaitTimeMs.add(waitMs); }
    public void recordRejected()            { totalRejected.increment(); }
    public void recordBatch()               { totalBatches.increment(); }
    public void updateQueueDepth(int depth) {
        peakQueueDepth.updateAndGet(current -> Math.max(current, depth));
    }

    public long getTotalSubmitted()  { return totalSubmitted.sum(); }
    public long getTotalMatched()    { return totalMatched.sum(); }
    public long getTotalRejected()   { return totalRejected.sum(); }
    public long getTotalBatches()    { return totalBatches.sum(); }
    public long getPeakQueueDepth()  { return peakQueueDepth.get(); }

    public double getAvgWaitTimeMs() {
        long matched = totalMatched.sum();
        return matched == 0 ? 0 : (double) totalWaitTimeMs.sum() / matched;
    }

    public double getMatchRate() {
        long submitted = totalSubmitted.sum();
        return submitted == 0 ? 0 : (double) totalMatched.sum() / submitted * 100;
    }
}
