package com.dispatchiq.engine;

import com.dispatchiq.model.Driver;
import com.dispatchiq.model.MatchResult;
import com.dispatchiq.model.RideRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * THE CORE ALGORITHM — Adaptive Batch Matching
 *
 * PROBLEM:
 * Given a batch of N ride requests and M available drivers (where M may be < N),
 * find the best assignment that minimizes TOTAL distance across all matches.
 *
 * WHY BATCH INSTEAD OF ONE-AT-A-TIME (GREEDY)?
 * Greedy one-at-a-time: assign the closest driver to request #1, then closest
 * remaining driver to request #2, etc. Fast, but often suboptimal — the first
 * request might "steal" a driver that was perfect for request #2, forcing a
 * much farther driver to serve #2 instead.
 *
 * Batch matching: look at ALL requests and ALL drivers together, find the
 * global minimum-cost assignment. Slightly slower per cycle, but produces
 * meaningfully better matches at scale.
 *
 * ALGORITHM: Greedy Cost Minimization (approximation of Hungarian algorithm)
 * 1. Generate all (request, driver) pairs with their distances
 * 2. Sort all pairs by distance ascending — cheapest assignments first
 * 3. Walk the sorted list: if both request and driver are still unassigned, assign them
 * 4. This gives us a near-optimal assignment in O(R*D log(R*D)) time
 *
 * SLA AWARENESS:
 * Requests that have been waiting longer get a distance penalty reduction —
 * meaning the system will accept a slightly suboptimal distance match to avoid
 * a rider waiting forever. This is the "SLA" part of "SLA-aware batch matching."
 */
public class BatchMatcher {

    private static final double SLA_PENALTY_REDUCTION_PER_SECOND = 0.001;
    private static final long SLA_THRESHOLD_MS = 5000; // start penalizing after 5s wait

    public List<MatchResult> match(List<RideRequest> requests, List<Driver> availableDrivers) {
        List<MatchResult> results = new ArrayList<>();

        if (requests.isEmpty() || availableDrivers.isEmpty()) {
            return results;
        }

        // Step 1: Generate all possible (request, driver) pairs with adjusted cost
        List<CandidatePair> candidates = new ArrayList<>();
        for (RideRequest request : requests) {
            for (Driver driver : availableDrivers) {
                double rawDistance = request.getPickup().distanceTo(driver.getLocation());
                double adjustedCost = applySlaPenalty(rawDistance, request.waitTimeMillis());
                candidates.add(new CandidatePair(request, driver, rawDistance, adjustedCost));
            }
        }

        // Step 2: Sort by adjusted cost — best matches first
        candidates.sort(Comparator.comparingDouble(CandidatePair::adjustedCost));

        // Step 3: Greedy assignment — assign each (request, driver) pair if both still free
        List<RideRequest> assignedRequests = new ArrayList<>();
        List<Driver> assignedDrivers = new ArrayList<>();

        for (CandidatePair pair : candidates) {
            if (assignedRequests.contains(pair.request) || assignedDrivers.contains(pair.driver)) {
                continue; // one of them already matched in this batch
            }

            // Atomically claim the driver — if another worker grabbed them first, skip
            if (pair.driver.tryAssign()) {
                assignedRequests.add(pair.request);
                assignedDrivers.add(pair.driver);
                results.add(new MatchResult(pair.request, pair.driver, pair.rawDistance));
            }
        }

        return results;
    }

    /**
     * SLA penalty reduction: requests waiting longer than SLA_THRESHOLD_MS
     * get their effective cost reduced, making them more likely to be matched
     * even if the driver is slightly farther away.
     *
     * This prevents long-waiting requests from being perpetually skipped
     * in favor of newer requests with closer drivers.
     */
    private double applySlaPenalty(double rawDistance, long waitTimeMillis) {
        if (waitTimeMillis <= SLA_THRESHOLD_MS) {
            return rawDistance;
        }
        long excessWaitSeconds = (waitTimeMillis - SLA_THRESHOLD_MS) / 1000;
        double reduction = excessWaitSeconds * SLA_PENALTY_REDUCTION_PER_SECOND;
        return Math.max(0, rawDistance - reduction);
    }

    private record CandidatePair(
        RideRequest request,
        Driver driver,
        double rawDistance,
        double adjustedCost
    ) {}
}
