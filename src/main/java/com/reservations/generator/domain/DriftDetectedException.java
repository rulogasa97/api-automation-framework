package com.reservations.generator.domain;

/**
 * Thrown when an upstream response does not match the strict shape expected
 * for its flow (unexpected/missing field, unexpected status, malformed
 * body). Extraction is never lenient or best-effort: any shape drift is a
 * hard failure rather than a silently accepted partial result.
 *
 * <p>This can only be detected once a response has actually been received
 * from the upstream system, so it is always a
 * {@link PostDispatchReservationException} (possible orphan reservation).
 */
public final class DriftDetectedException extends PostDispatchReservationException {

    public DriftDetectedException(String message) {
        super(message, null);
    }

    public DriftDetectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
