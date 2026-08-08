package com.reservations.generator.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Passenger input model for flow schema v1.
 *
 * <p>This model is intentionally minimal: it carries only the fields the
 * first supported flow requires. The passenger contract is expected to grow
 * additively as new flows/schema versions are introduced — new fields should
 * be added to new versioned classes (e.g. a future {@code PassengerV2}) or to
 * this class in a backward-compatible, additive way, never by silently
 * accepting unknown fields. Unknown-field rejection is enforced by
 * {@link com.reservations.generator.domain.flow.FlowRegistry} using strict
 * Jackson binding, not by this class itself.
 */
public final class Passenger {

    private final String name;

    @JsonCreator
    public Passenger(@JsonProperty("name") String name) {
        this.name = requireNonBlank(name);
    }

    private static String requireNonBlank(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Passenger name must not be blank");
        }
        return name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Passenger passenger)) {
            return false;
        }
        return name.equals(passenger.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Passenger{name='" + name + "'}";
    }
}
