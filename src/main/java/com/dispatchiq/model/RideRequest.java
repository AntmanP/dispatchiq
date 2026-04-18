package com.dispatchiq.model;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A ride request submitted by a rider.
 * Tracks its own lifecycle from QUEUED → MATCHED (or REJECTED/FAILED).
 */
public class RideRequest {

    private final String requestId;
    private final String riderId;
    private final Location pickup;
    private final Location dropoff;
    private final Instant submittedAt;

    private final AtomicReference<RequestStatus> status;
    private volatile String assignedDriverId;
    private volatile Instant matchedAt;

    public RideRequest(String riderId, Location pickup, Location dropoff) {
        this.requestId = UUID.randomUUID().toString().substring(0, 8);
        this.riderId = riderId;
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.submittedAt = Instant.now();
        this.status = new AtomicReference<>(RequestStatus.QUEUED);
    }

    public void markMatched(String driverId) {
        this.assignedDriverId = driverId;
        this.matchedAt = Instant.now();
        this.status.set(RequestStatus.MATCHED);
    }

    public void markRejected() {
        this.status.set(RequestStatus.REJECTED);
    }

    public void markFailed() {
        this.status.set(RequestStatus.FAILED);
    }

    /**
     * How long this request has been waiting in the queue (its SLA clock).
     * Batch matcher uses this to prioritize older requests.
     */
    public long waitTimeMillis() {
        return Instant.now().toEpochMilli() - submittedAt.toEpochMilli();
    }

    public String getRequestId() { return requestId; }
    public String getRiderId() { return riderId; }
    public Location getPickup() { return pickup; }
    public Location getDropoff() { return dropoff; }
    public Instant getSubmittedAt() { return submittedAt; }
    public RequestStatus getStatus() { return status.get(); }
    public String getAssignedDriverId() { return assignedDriverId; }
    public Instant getMatchedAt() { return matchedAt; }
}
