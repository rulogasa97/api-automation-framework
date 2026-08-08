package com.reservations.generator.domain;

/**
 * Thrown when reservation creation fails at or after the upstream request
 * was dispatched (network I/O, timeout, or a malformed/unexpected
 * response). Bytes may already have reached the upstream system, so this
 * always carries a {@link OrphanRisk#POSSIBLE_ORPHAN_RESERVATION} risk.
 */
public class PostDispatchReservationException extends ReservationCreationException {

    public PostDispatchReservationException(String message, Throwable cause) {
        super(message, cause, OrphanRisk.POSSIBLE_ORPHAN_RESERVATION);
    }
}
