package com.dispatchiq.controller;

import com.dispatchiq.model.Location;
import com.dispatchiq.model.RequestStatus;
import com.dispatchiq.model.RideRequest;
import com.dispatchiq.service.RideService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    /**
     * Submit a ride request.
     * Returns 202 Accepted if queued, 503 Service Unavailable if backpressure triggered.
     *
     * 503 with Retry-After header is the correct HTTP idiom for backpressure —
     * it tells the client "we're overwhelmed, try again in N seconds."
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submitRide(@RequestBody RideRequestDTO dto) {
        Location pickup = new Location(dto.pickupLat(), dto.pickupLng());
        Location dropoff = new Location(dto.dropoffLat(), dto.dropoffLng());

        RideRequest request = rideService.submitRequest(dto.riderId(), pickup, dropoff);

        if (request.getStatus() == RequestStatus.REJECTED) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "2")
                .body(Map.of(
                    "requestId", request.getRequestId(),
                    "status", "REJECTED",
                    "reason", "System at capacity. Please retry in 2 seconds."
                ));
        }

        return ResponseEntity.accepted().body(Map.of(
            "requestId", request.getRequestId(),
            "status", "QUEUED",
            "message", "Your request is queued. Poll /api/rides/{id} for status."
        ));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<Map<String, Object>> getRideStatus(@PathVariable String requestId) {
        RideRequest request = rideService.getRequest(requestId);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }

        var body = new java.util.HashMap<String, Object>();
        body.put("requestId", request.getRequestId());
        body.put("riderId", request.getRiderId());
        body.put("status", request.getStatus());
        body.put("waitTimeMs", request.waitTimeMillis());

        if (request.getAssignedDriverId() != null) {
            body.put("assignedDriverId", request.getAssignedDriverId());
            body.put("matchedAt", request.getMatchedAt());
        }

        return ResponseEntity.ok(body);
    }

    public record RideRequestDTO(
        String riderId,
        double pickupLat, double pickupLng,
        double dropoffLat, double dropoffLng
    ) {}
}
