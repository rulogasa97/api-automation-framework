package com.reservations.generator.domain;

import com.reservations.generator.domain.flow.InvalidPassengerPayloadException;
import com.reservations.generator.domain.flow.UnknownFlowException;
import com.reservations.generator.domain.flow.UnknownPassengerFieldException;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReservationFailureClassifier}: exercises the
 * exception-to-{@link ErrorCode} classification rules extracted from
 * {@code api.ErrorMapper}'s former if-chain, in isolation from HTTP status
 * mapping and {@code ErrorResponse} assembly (both of which stay in
 * {@code api.ErrorMapper}).
 */
class ReservationFailureClassifierTest {

    private static final FlowDefinition FLOW = new FlowDefinition("ual-create-v1", "1", Passenger.class);

    @Test
    void classifyMapsUnknownPassengerFieldToUnknownField() {
        UnknownPassengerFieldException ex = new UnknownPassengerFieldException(FLOW, "extraField", null);

        assertThat(ReservationFailureClassifier.classify(ex)).isEqualTo(ErrorCode.UNKNOWN_FIELD);
    }

    @Test
    void classifyMapsDriftDetectedToDriftDetected() {
        DriftDetectedException ex = new DriftDetectedException("response shape drifted");

        assertThat(ReservationFailureClassifier.classify(ex)).isEqualTo(ErrorCode.DRIFT_DETECTED);
    }

    @Test
    void classifyMapsPostDispatchFailureToUpstreamTimeout() {
        PostDispatchReservationException ex = new PostDispatchReservationException("dispatch timed out", null);

        assertThat(ReservationFailureClassifier.classify(ex)).isEqualTo(ErrorCode.UPSTREAM_TIMEOUT);
    }

    @Test
    void classifyMapsPreDispatchFailureToSessionUnavailable() {
        PreDispatchReservationException ex = new PreDispatchReservationException("session acquisition failed", null);

        assertThat(ReservationFailureClassifier.classify(ex)).isEqualTo(ErrorCode.SESSION_UNAVAILABLE);
    }

    @Test
    void classifyMapsUnknownFlowToValidationFailed() {
        UnknownFlowException ex = new UnknownFlowException("bogus-flow", "1");

        assertThat(ReservationFailureClassifier.classify(ex)).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void classifyMapsInvalidPassengerPayloadToValidationFailed() {
        InvalidPassengerPayloadException ex = new InvalidPassengerPayloadException(FLOW, new RuntimeException("bad json"));

        assertThat(ReservationFailureClassifier.classify(ex)).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void classifyFallsBackToInternalErrorForUnrecognizedFailures() {
        RuntimeException ex = new IllegalStateException("totally unexpected");

        assertThat(ReservationFailureClassifier.classify(ex)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void classifyPerPassengerMapsDriftDetectedToDriftDetected() {
        DriftDetectedException ex = new DriftDetectedException("response shape drifted");

        assertThat(ReservationFailureClassifier.classifyPerPassenger(ex)).isEqualTo(ErrorCode.DRIFT_DETECTED);
    }

    @Test
    void classifyPerPassengerMapsPostDispatchFailureToUpstreamTimeout() {
        PostDispatchReservationException ex = new PostDispatchReservationException("dispatch timed out", null);

        assertThat(ReservationFailureClassifier.classifyPerPassenger(ex)).isEqualTo(ErrorCode.UPSTREAM_TIMEOUT);
    }

    @Test
    void classifyPerPassengerMapsPreDispatchFailureToSessionUnavailable() {
        PreDispatchReservationException ex = new PreDispatchReservationException("session acquisition failed", null);

        assertThat(ReservationFailureClassifier.classifyPerPassenger(ex)).isEqualTo(ErrorCode.SESSION_UNAVAILABLE);
    }

    @Test
    void classifyPerPassengerFallsBackToInternalErrorForUnrecognizedOrNullCause() {
        assertThat(ReservationFailureClassifier.classifyPerPassenger(new IllegalStateException("totally unexpected")))
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ReservationFailureClassifier.classifyPerPassenger(null)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
