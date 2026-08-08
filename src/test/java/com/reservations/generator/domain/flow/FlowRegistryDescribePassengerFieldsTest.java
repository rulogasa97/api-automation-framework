package com.reservations.generator.domain.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservations.generator.domain.model.FieldKind;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.PassengerFieldDescriptor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FlowRegistryDescribePassengerFieldsTest {

    private static final String FLOW_ID = "ual-create-v1";
    private static final String SCHEMA_VERSION = "1";

    private final FlowRegistry registry = new FlowRegistry();

    @Test
    void describesTheSingleRequiredTextFieldOnTheRegisteredPassengerSchema() {
        FlowDefinition flow = new FlowDefinition(FLOW_ID, SCHEMA_VERSION, Passenger.class);
        registry.register(flow);

        List<PassengerFieldDescriptor> descriptors = registry.describePassengerFields(flow);

        assertThat(descriptors).hasSize(1);
        PassengerFieldDescriptor nameField = descriptors.get(0);
        assertThat(nameField.name()).isEqualTo("name");
        assertThat(nameField.kind()).isEqualTo(FieldKind.TEXT);
        assertThat(nameField.required()).isTrue();
        assertThat(nameField.allowedValues()).isEmpty();
    }

    @Test
    void derivesKindRequiredAndAllowedValuesForEachFieldOnARicherSchema() {
        FlowDefinition flow = new FlowDefinition("richer-flow", "1", RicherPassenger.class);
        registry.register(flow);

        List<PassengerFieldDescriptor> descriptors = registry.describePassengerFields(flow);

        assertThat(descriptors).hasSize(6);

        assertThat(findByName(descriptors, "name"))
                .contains(new PassengerFieldDescriptor("name", FieldKind.TEXT, true, List.of()));
        assertThat(findByName(descriptors, "age"))
                .contains(new PassengerFieldDescriptor("age", FieldKind.NUMBER, false, List.of()));
        assertThat(findByName(descriptors, "birthDate"))
                .contains(new PassengerFieldDescriptor("birthDate", FieldKind.DATE, true, List.of()));
        assertThat(findByName(descriptors, "frequentFlyer"))
                .contains(new PassengerFieldDescriptor("frequentFlyer", FieldKind.BOOLEAN, false, List.of()));
        assertThat(findByName(descriptors, "seatClass"))
                .contains(new PassengerFieldDescriptor("seatClass", FieldKind.ENUM, true,
                        List.of("ECONOMY", "BUSINESS", "FIRST")));

        PassengerFieldDescriptor unsupported = findByName(descriptors, "loyaltyProfile").orElseThrow();
        assertThat(unsupported.kind()).isEqualTo(FieldKind.UNSUPPORTED);
        assertThat(unsupported.allowedValues()).isEmpty();
    }

    private static Optional<PassengerFieldDescriptor> findByName(List<PassengerFieldDescriptor> descriptors,
                                                                    String name) {
        return descriptors.stream().filter(d -> d.name().equals(name)).findFirst();
    }

    enum SeatClass { ECONOMY, BUSINESS, FIRST }

    /** Test-only schema exercising every {@link FieldKind}, including the UNSUPPORTED fallback. */
    static final class RicherPassenger {
        private final String name;
        private final Integer age;
        private final LocalDate birthDate;
        private final Boolean frequentFlyer;
        private final SeatClass seatClass;
        private final Object loyaltyProfile;

        @JsonCreator
        RicherPassenger(@JsonProperty(value = "name", required = true) String name,
                         @JsonProperty("age") Integer age,
                         @JsonProperty(value = "birthDate", required = true) LocalDate birthDate,
                         @JsonProperty("frequentFlyer") Boolean frequentFlyer,
                         @JsonProperty(value = "seatClass", required = true) SeatClass seatClass,
                         @JsonProperty("loyaltyProfile") Object loyaltyProfile) {
            this.name = name;
            this.age = age;
            this.birthDate = birthDate;
            this.frequentFlyer = frequentFlyer;
            this.seatClass = seatClass;
            this.loyaltyProfile = loyaltyProfile;
        }
    }
}
