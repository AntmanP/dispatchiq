package com.dispatchiq.service;

import com.dispatchiq.model.Location;
import com.dispatchiq.model.RideRequest;
import com.dispatchiq.queue.RideRequestQueue;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RideService {

    private final RideRequestQueue queue;
    private final Map<String, RideRequest> requestStore = new ConcurrentHashMap<>();

    public RideService(RideRequestQueue queue) {
        this.queue = queue;
    }

    /**
     * Submit a new ride request.
     * Returns the request — caller checks request.getStatus() to know if it was
     * accepted (QUEUED) or rejected due to backpressure (REJECTED).
     */
    public RideRequest submitRequest(String riderId, Location pickup, Location dropoff) {
        RideRequest request = new RideRequest(riderId, pickup, dropoff);
        requestStore.put(request.getRequestId(), request);
        queue.enqueue(request); // sets status to REJECTED internally if queue full
        return request;
    }

    public RideRequest getRequest(String requestId) {
        return requestStore.get(requestId);
    }

    public Map<String, RideRequest> getAllRequests() {
        return requestStore;
    }

    public long countByStatus(com.dispatchiq.model.RequestStatus status) {
        return requestStore.values().stream()
            .filter(r -> r.getStatus() == status)
            .count();
    }
}
