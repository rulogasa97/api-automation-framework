package com.reservations.generator.domain;

import com.reservations.generator.domain.flow.InvalidPassengerPayloadException;
import com.reservations.generator.domain.flow.UnknownFlowException;
import com.reservations.generator.domain.flow.UnknownPassengerFieldException;

/**
 * Classifies a reservation-creation failure into a stable, wire-facing
 * {@link ErrorCode}, extracted from {@code api.ErrorMapper}'s former
 * if-chain so the rule set is a domain concept rather than duplicated by
 * every driving adapter (see the design doc's rationale for moving the
 * failure taxonomy inward).
 *
 * <p>This classifier handles the two distinct failure-reporting paths
 * described on {@code api.ErrorMapper}:
 * <ul>
 *     <li>{@link #classify(Throwable)}: whole-batch failures, i.e. an
 *     exception that propagates all the way out before any response could
 *     be produced.</li>
 *     <li>{@link #classifyPerPassenger(RuntimeException)}: a failure caught
 *     per-passenger by {@code CreateReservationsUseCase}.</li>
 * </ul>
 *
 * <p>Deliberately does not classify
 * {@code org.springframework.web.bind.MethodArgumentNotValidException}
 * (Bean Validation failures on the request envelope): that is a framework
 * type, so recognizing it stays an {@code api}-layer concern to keep this
 * class framework-free.
 */
public final class ReservationFailureClassifier {

    private ReservationFailureClassifier() {
    }

    /**
     * Classifies a whole-batch failure — see the class-level Javadoc above.
     *
     * @param ex the failure to classify.
     */
    public static ErrorCode classify(Throwable ex) {
        if (ex instanceof UnknownPassengerFieldException) {
            return ErrorCode.UNKNOWN_FIELD;
        }
        if (ex instanceof DriftDetectedException) {
            return ErrorCode.DRIFT_DETECTED;
        }
        if (ex instanceof PostDispatchReservationException) {
            return ErrorCode.UPSTREAM_TIMEOUT;
        }
        if (ex instanceof PreDispatchReservationException) {
            return ErrorCode.SESSION_UNAVAILABLE;
        }
        if (isUnknownFlowOrInvalidPayload(ex)) {
            return ErrorCode.VALIDATION_FAILED;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    /**
     * Classifies a per-passenger failure's causing exception — see the
     * class-level Javadoc above.
     *
     * @param cause the exception that caused the per-passenger failure; may
     *              be {@code null} for a synthetic failure with no real
     *              underlying exception, in which case this falls back to
     *              {@link ErrorCode#INTERNAL_ERROR}.
     */
    public static ErrorCode classifyPerPassenger(RuntimeException cause) {
        if (cause instanceof DriftDetectedException) {
            return ErrorCode.DRIFT_DETECTED;
        }
        if (cause instanceof PostDispatchReservationException) {
            return ErrorCode.UPSTREAM_TIMEOUT;
        }
        if (cause instanceof PreDispatchReservationException) {
            return ErrorCode.SESSION_UNAVAILABLE;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private static boolean isUnknownFlowOrInvalidPayload(Throwable ex) {
        return ex instanceof UnknownFlowException || ex instanceof InvalidPassengerPayloadException;
    }
}
