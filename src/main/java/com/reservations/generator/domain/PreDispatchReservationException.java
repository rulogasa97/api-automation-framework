package com.reservations.generator.domain;

/**
 * Thrown when reservation creation fails strictly before the upstream
 * request was dispatched (session acquisition, request building). No bytes
 * reached the upstream system, so there is no orphan-reservation risk.
 */
public final class PreDispatchReservationException extends ReservationCreationException {

    public PreDispatchReservationException(String message, Throwable cause) {
        super(message, cause, OrphanRisk.NONE);
    }
}
