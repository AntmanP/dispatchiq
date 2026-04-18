package com.dispatchiq.model;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a driver in the system.
 * Uses atomic fields so multiple batch workers can read/update state safely
 * without locking the whole object.
 */
public class Driver {

    private final String id;
    private final String name;
    private final Location location;

    // Atomic so concurrent batch workers don't race on status updates
    private final AtomicReference<DriverStatus> status;
    private final AtomicInteger totalTrips;
    private volatile Instant tripEndTime;   // when current trip ends (for natural queue drain)

    public Driver(String name, Location location) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.location = location;
        this.status = new AtomicReference<>(DriverStatus.AVAILABLE);
        this.totalTrips = new AtomicInteger(0);
    }

    /**
     * Attempt to claim this driver atomically.
     * Returns true only if this thread successfully changed status from AVAILABLE → ON_TRIP.
     * Prevents two batch workers from double-assigning the same driver.
     */
    public boolean tryAssign() {
        return status.compareAndSet(DriverStatus.AVAILABLE, DriverStatus.ON_TRIP);
    }

    public void completeTrip() {
        totalTrips.incrementAndGet();
        status.set(DriverStatus.AVAILABLE);
        tripEndTime = null;
    }

    public void setTripEndTime(Instant time) {
        this.tripEndTime = time;
    }

    public Instant getTripEndTime() { return tripEndTime; }
    public String getId() { return id; }
    public String getName() { return name; }
    public Location getLocation() { return location; }
    public DriverStatus getStatus() { return status.get(); }
    public int getTotalTrips() { return totalTrips.get(); }
}
