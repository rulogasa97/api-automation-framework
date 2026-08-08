package com.reservations.generator.api;

import com.reservations.generator.api.dto.CreateReservationsRequest;
import com.reservations.generator.api.dto.ErrorResponse;
import com.reservations.generator.domain.DriftDetectedException;
import com.reservations.generator.domain.OrphanRisk;
import com.reservations.generator.domain.PostDispatchReservationException;
import com.reservations.generator.domain.PreDispatchReservationException;
import com.reservations.generator.domain.flow.InvalidPassengerPayloadException;
import com.reservations.generator.domain.flow.UnknownFlowException;
import com.reservations.generator.domain.flow.UnknownPassengerFieldException;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ErrorMapper}: exercises every error code directly
 * against the mapper class, without going through HTTP.
 */
class ErrorMapperTest {

    private static final FlowDefinition FLOW = new FlowDefinition("ual-create-v1", "1", Passenger.class);

    private final ErrorMapper errorMapper = new ErrorMapper();

    @Test
    void validationFailureMapsTo400WithNoSideEffect() throws NoSuchMethodException {
        MethodArgumentNotValidException ex = validationException();

        ErrorMapper.MappedError mapped = errorMapper.map(ex, null);

        assertThat(mapped.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapped.body().errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(mapped.body().sideEffect()).isEqualTo(OrphanRisk.NONE);
        assertThat(mapped.body().passengerIndex()).isNull();
    }

    @Test
    void unknownFlowMapsToValidationFailed400WithNoSideEffect() {
        UnknownFlowException ex = new UnknownFlowException("bogus-flow", "1");

        ErrorMapper.MappedError mapped = errorMapper.map(ex, "bogus-flow");

        assertThat(mapped.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapped.body().errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(mapped.body().sideEffect()).isEqualTo(OrphanRisk.NONE);
        assertThat(mapped.body().flowId()).isEqualTo("bogus-flow");
    }

    @Test
    void invalidPassengerPayloadMapsToValidationFailed400() {
        InvalidPassengerPayloadException ex = new InvalidPassengerPayloadException(FLOW, new RuntimeException("bad json"));

        ErrorMapper.MappedError mapped = errorMapper.map(ex, FLOW.flowId());

        assertThat(mapped.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapped.body().errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(mapped.body().sideEffect()).isEqualTo(OrphanRisk.NONE);
    }

    @Test
    void unknownPassengerFieldMapsTo400WithNoSideEffect() {
        UnknownPassengerFieldException ex = new UnknownPassengerFieldException(FLOW, "extraField", null);

        ErrorMapper.MappedError mapped = errorMapper.map(ex, FLOW.flowId());

        assertThat(mapped.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapped.body().errorCode()).isEqualTo(ErrorCode.UNKNOWN_FIELD);
        assertThat(mapped.body().sideEffect()).isEqualTo(OrphanRisk.NONE);
    }

    @Test
    void preDispatchFailureMapsToSessionUnavailable503WithNoSideEffect() {
        PreDispatchReservationException ex = new PreDispatchReservationException("session acquisition failed", null);

        ErrorMapper.MappedError mapped = errorMapper.map(ex, FLOW.flowId());

        assertThat(mapped.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(mapped.body().errorCode()).isEqualTo(ErrorCode.SESSION_UNAVAILABLE);
        assertThat(mapped.body().sideEffect()).isEqualTo(OrphanRisk.NONE);
    }

    @Test
    void postDispatchFailureMapsToUpstreamTimeout504WithPossibleOrphanReservation() {
        PostDispatchReservationException ex = new PostDispatchReservationException("dispatch timed out", null);

        ErrorMapper.MappedError mapped = errorMapper.map(ex, FLOW.flowId());

        assertThat(mapped.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(mapped.body().errorCode()).isEqualTo(ErrorCode.UPSTREAM_TIMEOUT);
        assertThat(mapped.body().sideEffect()).isEqualTo(OrphanRisk.POSSIBLE_ORPHAN_RESERVATION);
    }

    @Test
    void driftDetectedMapsToDriftDetected502WithPossibleOrphanReservation() {
        DriftDetectedException ex = new DriftDetectedException("response shape drifted");

        ErrorMapper.MappedError mapped = errorMapper.map(ex, FLOW.flowId());

        assertThat(mapped.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(mapped.body().errorCode()).isEqualTo(ErrorCode.DRIFT_DETECTED);
        assertThat(mapped.body().sideEffect()).isEqualTo(OrphanRisk.POSSIBLE_ORPHAN_RESERVATION);
    }

    @Test
    void unexpectedFailureFallsBackToInternalError500() {
        RuntimeException ex = new IllegalStateException("totally unexpected");

        ErrorMapper.MappedError mapped = errorMapper.map(ex, FLOW.flowId());

        assertThat(mapped.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(mapped.body().errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(mapped.body().sideEffect()).isEqualTo(OrphanRisk.NONE);
    }

    private static MethodArgumentNotValidException validationException() throws NoSuchMethodException {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        CreateReservationsRequest invalidRequest = new CreateReservationsRequest("", "", List.<Map<String, Object>>of());
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(invalidRequest, "createReservationsRequest");
        validator.validate(invalidRequest).forEach(violation ->
                bindingResult.addError(new org.springframework.validation.FieldError(
                        "createReservationsRequest",
                        violation.getPropertyPath().toString(),
                        violation.getMessage())));

        Method createMethod = ReservationController.class.getMethod(
                "create", CreateReservationsRequest.class, jakarta.servlet.http.HttpServletRequest.class);
        MethodParameter requestParameter = new MethodParameter(createMethod, 0);
        return new MethodArgumentNotValidException(requestParameter, bindingResult);
    }
}
