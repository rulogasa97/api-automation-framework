package com.reservations.generator.domain.model;

import com.reservations.generator.domain.OrphanRisk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationResultTest {

    @Test
    void aggregatesSuccessesAndFailuresWithoutOmission() {
        List<Pnr> pnrs = List.of(new Pnr("ABC123"), new Pnr("DEF456"));
        List<ReservationFailure> failures =
                List.of(new ReservationFailure(2, "upstream timeout", OrphanRisk.POSSIBLE_ORPHAN_RESERVATION, null));

        ReservationResult result = new ReservationResult(pnrs, failures);

        assertThat(result.getPnrs()).containsExactly(new Pnr("ABC123"), new Pnr("DEF456"));
        assertThat(result.getFailures())
                .containsExactly(new ReservationFailure(2, "upstream timeout", OrphanRisk.POSSIBLE_ORPHAN_RESERVATION, null));
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.totalOutcomes()).isEqualTo(3);
    }

    @Test
    void emptyResultHasNoOutcomesAndNoFailures() {
        ReservationResult result = ReservationResult.empty();

        assertThat(result.getPnrs()).isEmpty();
        assertThat(result.getFailures()).isEmpty();
        assertThat(result.hasFailures()).isFalse();
        assertThat(result.totalOutcomes()).isZero();
    }

    @Test
    void partialFailureDoesNotOmitSuccessfulOutcomes() {
        // Passenger 0 succeeds, passenger 1 fails, passenger 2 succeeds:
        // both successes must still be present alongside the failure.
        List<Pnr> pnrs = List.of(new Pnr("SUC001"), new Pnr("SUC002"));
        List<ReservationFailure> failures =
                List.of(new ReservationFailure(1, "validation failed", OrphanRisk.NONE, null));

        ReservationResult result = new ReservationResult(pnrs, failures);

        assertThat(result.totalOutcomes()).isEqualTo(3);
        assertThat(result.getPnrs()).hasSize(2);
        assertThat(result.getFailures()).hasSize(1);
    }

    @Test
    void resultListsAreImmutable() {
        ReservationResult result = new ReservationResult(List.of(new Pnr("XYZ789")), List.of());

        assertThat(result.getPnrs()).isUnmodifiable();
        assertThat(result.getFailures()).isUnmodifiable();
    }
}
