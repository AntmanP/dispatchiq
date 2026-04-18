package com.dispatchiq.model;

/**
 * Represents a geographic coordinate.
 * We use Euclidean distance for simplicity in demo — in production this would be
 * replaced with Haversine formula or a routing API call.
 */
public record Location(double lat, double lng) {

    public double distanceTo(Location other) {
        double dLat = this.lat - other.lat;
        double dLng = this.lng - other.lng;
        return Math.sqrt(dLat * dLat + dLng * dLng);
    }

    @Override
    public String toString() {
        return String.format("(%.4f, %.4f)", lat, lng);
    }
}
