package com.reservations.generator.domain.model;

import com.reservations.generator.domain.OrphanRisk;
import com.reservations.generator.domain.ReservationCreationException;

import java.util.Objects;

/**
 * Records that reservation creation failed for a single passenger within a
 * batch, keeping the failing passenger's original index so callers can
 * correlate it back to the request without losing information.
 *
 * <p>Besides the passenger index and a human-readable message, this type
 * carries the {@link OrphanRisk} classification for the failure. That
 * classification is itself a domain concept (see {@link OrphanRisk}) and is
 * computed by {@code CreateReservationsUseCase} at the point the per-passenger
 * failure is caught:
 * <ul>
 *     <li>if the caught exception is a {@link ReservationCreationException}
 *     (or subtype), its own already-classified {@link OrphanRisk} is reused
 *     directly;</li>
 *     <li>otherwise (an unexpected {@code RuntimeException} outside the known
 *     hierarchy), it conservatively defaults to
 *     {@link OrphanRisk#POSSIBLE_ORPHAN_RESERVATION}, since a create attempt
 *     was already made for that passenger and an unknown failure afterward
 *     must not be assumed safe.</li>
 * </ul>
 *
 * <p>The causing exception itself is also kept (when available) purely so
 * the {@code api} layer can derive a stable, fine-grained
 * {@code ErrorCode} for this failure (see {@code api/ErrorMapper}) the same
 * way it does for whole-batch failures, without duplicating that
 * classification logic here. Exposing the raw exception is not an API-layer
 * concept leaking into domain/: {@link ReservationCreationException} and its
 * subtypes already live in domain/.
 *
 * <p>The full HTTP-facing error taxonomy (errorCode, HTTP status) remains an
 * api-layer concern; this domain-level type only carries what the domain
 * itself knows or can classify without any HTTP awareness.
 */
public final class ReservationFailure {

    private final int passengerIndex;
    private final String message;
    private final OrphanRisk orphanRisk;
    private final RuntimeException cause;

    public ReservationFailure(int passengerIndex, String message, OrphanRisk orphanRisk, RuntimeException cause) {
        if (passengerIndex < 0) {
            throw new IllegalArgumentException("passengerIndex must not be negative");
        }
        this.passengerIndex = passengerIndex;
        this.message = Objects.requireNonNull(message, "message");
        this.orphanRisk = Objects.requireNonNull(orphanRisk, "orphanRisk");
        this.cause = cause;
    }

    public int getPassengerIndex() {
        return passengerIndex;
    }

    public String getMessage() {
        return message;
    }

    public OrphanRisk getOrphanRisk() {
        return orphanRisk;
    }

    /**
     * The exception that caused this failure, when known; {@code null} for
     * synthetic failures (e.g. constructed directly in tests) that have no
     * real underlying exception. Intended for API-layer errorCode
     * classification only — never re-derive {@link #getOrphanRisk()} from
     * this instead of trusting the already-classified field above.
     */
    public RuntimeException getCause() {
        return cause;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReservationFailure that)) {
            return false;
        }
        return passengerIndex == that.passengerIndex
                && message.equals(that.message)
                && orphanRisk == that.orphanRisk;
    }

    @Override
    public int hashCode() {
        return Objects.hash(passengerIndex, message, orphanRisk);
    }

    @Override
    public String toString() {
        return "ReservationFailure{passengerIndex=" + passengerIndex
                + ", message='" + message + '\''
                + ", orphanRisk=" + orphanRisk
                + '}';
    }
}
