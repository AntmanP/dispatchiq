package com.dispatchiq.controller;

import com.dispatchiq.model.Driver;
import com.dispatchiq.model.Location;
import com.dispatchiq.service.DriverRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverRegistry driverRegistry;

    public DriverController(DriverRegistry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> registerDriver(@RequestBody DriverDTO dto) {
        Driver driver = driverRegistry.registerDriver(dto.name(), new Location(dto.lat(), dto.lng()));
        return ResponseEntity.ok(Map.of(
            "driverId", driver.getId(),
            "name", driver.getName(),
            "status", driver.getStatus().name()
        ));
    }

    @GetMapping
    public ResponseEntity<?> listDrivers() {
        var drivers = driverRegistry.getAllDrivers().stream()
            .map(d -> Map.of(
                "id", d.getId(),
                "name", d.getName(),
                "status", d.getStatus().name(),
                "location", Map.of("lat", d.getLocation().lat(), "lng", d.getLocation().lng()),
                "totalTrips", d.getTotalTrips()
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(drivers);
    }

    public record DriverDTO(String name, double lat, double lng) {}
}
