package com.reservations.generator.domain.flow;

import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowRegistryParsePassengersTest {

    private static final String FLOW_ID = "ual-create-v1";
    private static final String SCHEMA_VERSION = "1";

    private final FlowRegistry registry = new FlowRegistry();
    private FlowDefinition flow;

    @BeforeEach
    void setUp() {
        flow = new FlowDefinition(FLOW_ID, SCHEMA_VERSION, Passenger.class);
        registry.register(flow);
    }

    @Test
    void parsesEachRawPassengerMapInSubmissionOrder() {
        Map<String, Object> first = rawPassenger("Ada Lovelace");
        Map<String, Object> second = rawPassenger("Grace Hopper");

        List<Passenger> passengers = registry.parsePassengers(flow, List.of(first, second));

        assertThat(passengers).hasSize(2);
        assertThat(passengers.get(0).getName()).isEqualTo("Ada Lovelace");
        assertThat(passengers.get(1).getName()).isEqualTo("Grace Hopper");
    }

    @Test
    void rejectsAnyRawPassengerCarryingAFieldOutsideTheStrictSchema() {
        Map<String, Object> valid = rawPassenger("Ada Lovelace");
        Map<String, Object> invalid = new LinkedHashMap<>(rawPassenger("Grace Hopper"));
        invalid.put("favoriteColor", "blue");

        assertThatThrownBy(() -> registry.parsePassengers(flow, List.of(valid, invalid)))
                .isInstanceOf(UnknownPassengerFieldException.class)
                .satisfies(e -> {
                    UnknownPassengerFieldException ex = (UnknownPassengerFieldException) e;
                    assertThat(ex.getFieldName()).isEqualTo("favoriteColor");
                });
    }

    @Test
    void rejectsARawPassengerMissingARequiredField() {
        Map<String, Object> missingName = Map.of();

        assertThatThrownBy(() -> registry.parsePassengers(flow, List.of(missingName)))
                .isInstanceOf(InvalidPassengerPayloadException.class);
    }

    private static Map<String, Object> rawPassenger(String name) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", name);
        return raw;
    }
}
