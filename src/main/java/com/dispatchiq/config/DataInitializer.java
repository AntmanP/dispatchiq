package com.dispatchiq.config;

import com.dispatchiq.model.Location;
import com.dispatchiq.service.DriverRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Seeds the system with 50 drivers at random locations on startup.
 * This means the demo is ready to go immediately — just fire ride requests.
 *
 * Coordinates are loosely around a 0-1 grid (think of it as a city grid).
 */
@Component
public class DataInitializer {

    private final DriverRegistry driverRegistry;
    private final Random random = new Random(42); // seed for reproducibility

    public DataInitializer(DriverRegistry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    @PostConstruct
    public void seedDrivers() {
        String[] firstNames = {"Alex", "Jordan", "Morgan", "Taylor", "Casey",
                               "Riley", "Sam", "Chris", "Dana", "Jamie"};
        String[] lastNames = {"Smith", "Johnson", "Williams", "Brown", "Jones",
                              "Garcia", "Miller", "Davis", "Wilson", "Moore"};

        for (int i = 0; i < 50; i++) {
            String name = firstNames[i % firstNames.length] + " " + lastNames[i % lastNames.length];
            Location location = new Location(
                random.nextDouble(),   // lat between 0.0 and 1.0
                random.nextDouble()    // lng between 0.0 and 1.0
            );
            driverRegistry.registerDriver(name, location);
        }

        System.out.println("[DispatchIQ] Seeded 50 drivers. System ready.");
    }
}
