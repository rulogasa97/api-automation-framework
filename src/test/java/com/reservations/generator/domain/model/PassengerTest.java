package com.reservations.generator.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PassengerTest {

    @Test
    void createsPassengerWithValidName() {
        Passenger passenger = new Passenger("Ada Lovelace");

        assertThat(passenger.getName()).isEqualTo("Ada Lovelace");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejectsBlankOrNullName(String invalidName) {
        assertThatThrownBy(() -> new Passenger(invalidName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void equalsAndHashCodeAreBasedOnName() {
        Passenger a = new Passenger("Grace Hopper");
        Passenger b = new Passenger("Grace Hopper");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
