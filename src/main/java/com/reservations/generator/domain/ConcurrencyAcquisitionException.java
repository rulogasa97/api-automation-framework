package com.reservations.generator.domain;

/**
 * Thrown when the calling thread is interrupted while waiting for a
 * concurrency permit before creating reservations.
 */
public final class ConcurrencyAcquisitionException extends RuntimeException {

    public ConcurrencyAcquisitionException(InterruptedException cause) {
        super("Interrupted while waiting for a reservation-creation permit", cause);
    }
}
