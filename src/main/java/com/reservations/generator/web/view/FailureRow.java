package com.reservations.generator.web.view;

import com.reservations.generator.domain.model.ReservationFailure;

import java.util.Objects;

/**
 * Pairs a {@link ReservationFailure}'s passenger index with its classified
 * {@link ResultView}, so {@code fragments/result.html} can render each
 * failed row's own differentiated banner without re-deriving the
 * classification in the template. Purely structural (no branching logic):
 * {@link ResultView#forFailure} carries the actual classification rules and
 * already has its own dedicated tests.
 */
public record FailureRow(int passengerIndex, ResultView view) {

    public FailureRow {
        Objects.requireNonNull(view, "view must not be null");
    }

    public static FailureRow from(ReservationFailure failure) {
        return new FailureRow(failure.getPassengerIndex(), ResultView.forFailure(failure));
    }
}
