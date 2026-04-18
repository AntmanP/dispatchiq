package com.dispatchiq.model;

/**
 * Represents a single assignment decision made by the BatchMatcher.
 * One MatchResult = one (request, driver) pair the algorithm decided to assign.
 */
public record MatchResult(
    RideRequest request,
    Driver driver,
    double distanceKm  // cost the algorithm used to make this decision
) {
    @Override
    public String toString() {
        return String.format("Match[rider=%s → driver=%s, dist=%.2f]",
            request.getRiderId(), driver.getName(), distanceKm);
    }
}
