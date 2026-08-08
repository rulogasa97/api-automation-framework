package com.reservations.generator.domain;

import java.util.Objects;

/**
 * Base type for reservation-creation failures that carry an
 * {@link OrphanRisk} classification, distinguishing pre-dispatch failures
 * (no risk) from post-dispatch failures (possible orphan reservation).
 *
 * <p>Concrete subclasses are {@link PreDispatchReservationException} and
 * {@link PostDispatchReservationException}. This slice only establishes the
 * signal; mapping it to HTTP status codes/error bodies is a later-slice
 * {@code api}-layer concern.
 */
public abstract class ReservationCreationException extends RuntimeException {

    private final OrphanRisk orphanRisk;

    protected ReservationCreationException(String message, Throwable cause, OrphanRisk orphanRisk) {
        super(message, cause);
        this.orphanRisk = Objects.requireNonNull(orphanRisk, "orphanRisk");
    }

    public OrphanRisk getOrphanRisk() {
        return orphanRisk;
    }
}
