package com.reservations.generator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * <p>As of slice 3, the HTTP edge ({@code api/}), the outbound sandbox
 * adapter ({@code apireplay/}), and their wiring ({@code config/}) are all
 * present and enabled via component scanning from this package's root.
 */
@SpringBootApplication
public class ReservationsGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationsGeneratorApplication.class, args);
    }
}
