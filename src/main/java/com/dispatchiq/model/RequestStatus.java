package com.dispatchiq.model;

public enum RequestStatus {
    QUEUED,       // in the ingest queue, waiting to be batched
    MATCHING,     // picked up by a batch worker, being matched
    MATCHED,      // successfully assigned a driver
    REJECTED,     // queue full — backpressure kicked in
    FAILED        // no drivers available after retry attempts
}
