package com.dispatchiq.service;

import com.dispatchiq.model.Driver;
import com.dispatchiq.model.DriverStatus;
import com.dispatchiq.model.Location;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Manages the driver fleet.
 *
 * Also runs a background "trip completion" thread that simulates drivers
 * finishing their trips and returning to AVAILABLE status.
 * This is what makes the queue drain naturally in the demo.
 */
@Service
public class DriverRegistry {

    private final Map<String, Driver> drivers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService tripCompletionScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trip-completion-thread");
            t.setDaemon(true);
            return t;
        });

    public DriverRegistry() {
        // Check every second if any driver has finished their trip
        tripCompletionScheduler.scheduleAtFixedRate(this::processCompletedTrips, 1, 1, TimeUnit.SECONDS);
    }

    public Driver registerDriver(String name, Location location) {
        Driver driver = new Driver(name, location);
        drivers.put(driver.getId(), driver);
        return driver;
    }

    public List<Driver> getAvailableDrivers() {
        return drivers.values().stream()
            .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
            .collect(Collectors.toList());
    }

    public Collection<Driver> getAllDrivers() {
        return drivers.values();
    }

    /**
     * After a match, schedule the driver to complete their trip after a delay.
     * Delay simulates actual trip duration — shorter trips free up drivers faster.
     * This is what drains the queue naturally: drivers finish and come back available.
     */
    public void scheduleTripCompletion(Driver driver, double distanceKm) {
        // Simulate trip time: ~30 seconds base + distance factor
        long tripDurationSeconds = (long) (30 + distanceKm * 100);
        Instant tripEnd = Instant.now().plusSeconds(tripDurationSeconds);
        driver.setTripEndTime(tripEnd);
    }

    private void processCompletedTrips() {
        Instant now = Instant.now();
        drivers.values().stream()
            .filter(d -> d.getStatus() == DriverStatus.ON_TRIP)
            .filter(d -> d.getTripEndTime() != null && now.isAfter(d.getTripEndTime()))
            .forEach(Driver::completeTrip);
    }

    public long countByStatus(DriverStatus status) {
        return drivers.values().stream().filter(d -> d.getStatus() == status).count();
    }
}
