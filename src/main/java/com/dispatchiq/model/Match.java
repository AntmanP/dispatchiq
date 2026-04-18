package com.dispatchiq.model;

import java.time.Instant;

public class Match {

    private final String requestId;
    private final String riderId;
    private final String driverId;
    private final String driverName;
    private final double distanceKm;
    private final long waitTimeMs;
    private final Instant matchedAt;

    public Match(RideRequest request, Driver driver) {
        this.requestId   = request.getRequestId();
        this.riderId     = request.getRiderId();
        this.driverId    = driver.getDriverId();
        this.driverName  = driver.getName();
        this.distanceKm  = GeoUtils.distanceKm(
                request.getPickupLat(), request.getPickupLng(),
                driver.getLatitude(),   driver.getLongitude()
        );
        this.matchedAt   = Instant.now();
        this.waitTimeMs  = request.waitTimeMs();
    }

    public String getRequestId()  { return requestId; }
    public String getRiderId()    { return riderId; }
    public String getDriverId()   { return driverId; }
    public String getDriverName() { return driverName; }
    public double getDistanceKm() { return distanceKm; }
    public long getWaitTimeMs()   { return waitTimeMs; }
    public Instant getMatchedAt() { return matchedAt; }

    // ── tiny utility ──────────────────────────────────────────────────────────

    public static class GeoUtils {
        /**
         * Euclidean approximation — good enough for demo-scale distances.
         * For production you'd use the Haversine formula.
         */
        public static double distanceKm(double lat1, double lng1,
                                        double lat2, double lng2) {
            double dLat = (lat2 - lat1) * 111.0;          // ~111 km per degree lat
            double dLng = (lng2 - lng1) * 111.0 * Math.cos(Math.toRadians(lat1));
            return Math.sqrt(dLat * dLat + dLng * dLng);
        }
    }
}
