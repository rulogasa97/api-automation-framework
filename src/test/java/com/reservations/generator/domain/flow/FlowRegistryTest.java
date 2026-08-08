package com.reservations.generator.domain.flow;

import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowRegistryTest {

    private static final String FLOW_ID = "ual-create-v1";
    private static final String SCHEMA_VERSION = "1";

    private FlowRegistry registry;
    private FlowDefinition flow;

    @BeforeEach
    void setUp() {
        registry = new FlowRegistry();
        flow = new FlowDefinition(FLOW_ID, SCHEMA_VERSION, Passenger.class);
        registry.register(flow);
    }

    @Test
    void findsRegisteredFlowByIdAndSchemaVersion() {
        Optional<FlowDefinition> found = registry.find(FLOW_ID, SCHEMA_VERSION);

        assertThat(found).contains(flow);
    }

    @Test
    void requireThrowsForUnknownFlow() {
        assertThatThrownBy(() -> registry.require("unknown-flow", "9"))
                .isInstanceOf(UnknownFlowException.class);
    }

    @Test
    void parsesValidPassengerPayload() {
        Object parsed = registry.parsePassenger(flow, "{\"name\":\"Ada Lovelace\"}");

        assertThat(parsed).isInstanceOf(Passenger.class);
        assertThat(((Passenger) parsed).getName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void rejectsUnknownFieldInsteadOfSilentlyDroppingIt() {
        assertThatThrownBy(() -> registry.parsePassenger(flow, "{\"name\":\"Ada\",\"favoriteColor\":\"blue\"}"))
                .isInstanceOf(UnknownPassengerFieldException.class)
                .satisfies(e -> {
                    UnknownPassengerFieldException ex = (UnknownPassengerFieldException) e;
                    assertThat(ex.getFieldName()).isEqualTo("favoriteColor");
                    assertThat(ex.getFlowId()).isEqualTo(FLOW_ID);
                });
    }

    @Test
    void rejectsMalformedPayload() {
        assertThatThrownBy(() -> registry.parsePassenger(flow, "not-json"))
                .isInstanceOf(InvalidPassengerPayloadException.class);
    }

    @Test
    void rejectsPayloadMissingRequiredField() {
        assertThatThrownBy(() -> registry.parsePassenger(flow, "{}"))
                .isInstanceOf(InvalidPassengerPayloadException.class);
    }
}
