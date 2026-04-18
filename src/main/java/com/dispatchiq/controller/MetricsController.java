package com.dispatchiq.controller;

import com.dispatchiq.engine.DispatchEngine;
import com.dispatchiq.metrics.DispatchMetrics;
import com.dispatchiq.model.DriverStatus;
import com.dispatchiq.model.RequestStatus;
import com.dispatchiq.queue.RideRequestQueue;
import com.dispatchiq.service.DriverRegistry;
import com.dispatchiq.service.RideService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Live metrics endpoint — this is what you show in the demo video.
 * Hit GET /api/metrics and watch the numbers change in real time.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final RideRequestQueue queue;
    private final DispatchEngine engine;
    private final DriverRegistry driverRegistry;
    private final RideService rideService;
    private final DispatchMetrics metrics;

    public MetricsController(RideRequestQueue queue, DispatchEngine engine,
                              DriverRegistry driverRegistry, RideService rideService,
                              DispatchMetrics metrics) {
        this.queue = queue;
        this.engine = engine;
        this.driverRegistry = driverRegistry;
        this.rideService = rideService;
        this.metrics = metrics;
    }

    @GetMapping
    public Map<String, Object> getMetrics() {
        return Map.of(
            "queue", Map.of(
                "currentDepth", queue.currentDepth(),
                "capacity", queue.capacity(),
                "fillPercentage", String.format("%.1f%%", queue.fillPercentage()),
                "totalEnqueued", queue.getTotalEnqueued(),
                "totalRejected", queue.getTotalRejected()
            ),
            "engine", Map.of(
                "workerCount", engine.getWorkerCount(),
                "batchSize", engine.getBatchSize(),
                "totalBatchesProcessed", engine.getTotalBatchesProcessed(),
                "totalMatched", engine.getTotalMatched()
            ),
            "drivers", Map.of(
                "available", driverRegistry.countByStatus(DriverStatus.AVAILABLE),
                "onTrip", driverRegistry.countByStatus(DriverStatus.ON_TRIP),
                "total", driverRegistry.getAllDrivers().size()
            ),
            "requests", Map.of(
                "matched", rideService.countByStatus(RequestStatus.MATCHED),
                "queued", rideService.countByStatus(RequestStatus.QUEUED),
                "rejected", rideService.countByStatus(RequestStatus.REJECTED),
                "failed", rideService.countByStatus(RequestStatus.FAILED)
            ),
            "matching", Map.of(
                "averageMatchDistanceKm", String.format("%.4f", metrics.getAverageMatchDistance()),
                "batchEfficiencyPct", String.format("%.1f%%", metrics.getBatchEfficiency())
            )
        );
    }
}
