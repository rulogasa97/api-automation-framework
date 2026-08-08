package com.reservations.generator.api;

import com.reservations.generator.api.dto.ErrorResponse;
import com.reservations.generator.domain.DriftDetectedException;
import com.reservations.generator.domain.OrphanRisk;
import com.reservations.generator.domain.PostDispatchReservationException;
import com.reservations.generator.domain.PreDispatchReservationException;
import com.reservations.generator.domain.flow.UnknownPassengerFieldException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.stream.Collectors;

/**
 * Maps failures raised anywhere while handling {@code POST /reservations} to
 * an HTTP status and an {@link ErrorResponse}, via {@link #map(Throwable, String)}.
 *
 * <p>This mapper handles two distinct failure-reporting paths that share the
 * same {@link ErrorCode}/{@link OrphanRisk} taxonomy but surface differently
 * on the wire:
 * <ul>
 *     <li><b>Whole-batch failures</b>: an exception that propagates all the
 *     way out of {@code POST /reservations} before any
 *     {@link com.reservations.generator.api.dto.ReservationResponse} could be
 *     produced. These are reported as an HTTP error status + an
 *     {@link ErrorResponse} body, via {@link #map(Throwable, String)}
 *     below.</li>
 *     <li><b>Per-passenger failures</b>: a failure caught per-passenger by
 *     {@code CreateReservationsUseCase} and folded into that passenger's
 *     {@code ReservationFailure}. These never reach this mapper's
 *     {@code map(...)} methods or produce an HTTP error status; they are
 *     reported as a {@code ReservationResponse.Failure} entry inside a normal
 *     200 response instead, via {@link #classifyPerPassengerFailure(RuntimeException)}
 *     below, so that one passenger's failure never suppresses the results for
 *     the others.</li>
 * </ul>
 *
 * <p>Mapping rules for whole-batch failures (see the design doc for the full
 * rationale):
 * <ul>
 *     <li>Bean Validation failures on the request envelope, and an
 *     unrecognized (flowId, schemaVersion) pair, are request-shape problems:
 *     {@link ErrorCode#VALIDATION_FAILED}, 400, {@link OrphanRisk#NONE}.</li>
 *     <li>A passenger payload field outside its flow's strict schema:
 *     {@link ErrorCode#UNKNOWN_FIELD}, 400, {@link OrphanRisk#NONE}.</li>
 *     <li>{@link PreDispatchReservationException}: nothing was dispatched to
 *     the upstream system. In this codebase, the only place a
 *     {@code PreDispatchReservationException} can propagate all the way to
 *     this mapper (rather than being caught per-passenger by
 *     {@code CreateReservationsUseCase} and folded into that passenger's
 *     {@code ReservationFailure}) is a failure to acquire the upstream
 *     session, since that call sits outside the use case's per-passenger
 *     try/catch. It is therefore mapped as
 *     {@link ErrorCode#SESSION_UNAVAILABLE}, 503, {@link OrphanRisk#NONE}.</li>
 *     <li>{@link DriftDetectedException} (checked before its supertype,
 *     since it is a subclass): the upstream response didn't match its
 *     expected shape: {@link ErrorCode#DRIFT_DETECTED}, 502,
 *     {@link OrphanRisk#POSSIBLE_ORPHAN_RESERVATION}.</li>
 *     <li>Any other {@link PostDispatchReservationException} (e.g. dispatch
 *     failure/timeout): {@link ErrorCode#UPSTREAM_TIMEOUT}, 504,
 *     {@link OrphanRisk#POSSIBLE_ORPHAN_RESERVATION}.</li>
 *     <li>Anything else unexpected: {@link ErrorCode#INTERNAL_ERROR}, 500,
 *     {@link OrphanRisk#NONE}. This case is a safety net, not a mapped
 *     requirement.</li>
 * </ul>
 *
 * <p>{@link #classifyPerPassengerFailure(RuntimeException)} reuses the same
 * {@link ErrorCode#DRIFT_DETECTED}/{@link ErrorCode#UPSTREAM_TIMEOUT}/
 * {@link ErrorCode#SESSION_UNAVAILABLE} rules above for a per-passenger
 * failure's causing exception, without HTTP status or a whole-batch
 * {@link ErrorResponse}: the resulting {@link OrphanRisk}
 * ("{@code sideEffect}") always comes from the already-classified
 * {@code ReservationFailure} instead, never from this method's
 * {@link ErrorCode#INTERNAL_ERROR} fallback (which, unlike the whole-batch
 * fallback above, does not imply {@link OrphanRisk#NONE}).</p>
 */
@Component
public final class ErrorMapper {

    public record MappedError(HttpStatus status, ErrorResponse body) {
    }

    /**
     * Maps a failure to its HTTP status and error body.
     *
     * @param ex     the failure to map.
     * @param flowId the requested flowId, when known; {@code null} if the
     *               failure happened before the flowId could be determined
     *               (e.g. it failed its own Bean Validation).
     */
    public MappedError map(Throwable ex, String flowId) {
        if (ex instanceof MethodArgumentNotValidException manv) {
            return validationFailed(manv, flowId);
        }
        if (ex instanceof UnknownPassengerFieldException upfe) {
            return new MappedError(HttpStatus.BAD_REQUEST,
                    new ErrorResponse(ErrorCode.UNKNOWN_FIELD, flowId, OrphanRisk.NONE, null, upfe.getMessage()));
        }
        if (ex instanceof DriftDetectedException dde) {
            return new MappedError(HttpStatus.BAD_GATEWAY,
                    new ErrorResponse(ErrorCode.DRIFT_DETECTED, flowId, dde.getOrphanRisk(), null, dde.getMessage()));
        }
        if (ex instanceof PostDispatchReservationException pdre) {
            return new MappedError(HttpStatus.GATEWAY_TIMEOUT,
                    new ErrorResponse(ErrorCode.UPSTREAM_TIMEOUT, flowId, pdre.getOrphanRisk(), null, pdre.getMessage()));
        }
        if (ex instanceof PreDispatchReservationException prde) {
            return new MappedError(HttpStatus.SERVICE_UNAVAILABLE,
                    new ErrorResponse(ErrorCode.SESSION_UNAVAILABLE, flowId, prde.getOrphanRisk(), null, prde.getMessage()));
        }
        if (isUnknownFlowOrInvalidPayload(ex)) {
            return new MappedError(HttpStatus.BAD_REQUEST,
                    new ErrorResponse(ErrorCode.VALIDATION_FAILED, flowId, OrphanRisk.NONE, null, ex.getMessage()));
        }
        return new MappedError(HttpStatus.INTERNAL_SERVER_ERROR,
                new ErrorResponse(ErrorCode.INTERNAL_ERROR, flowId, OrphanRisk.NONE, null, safeMessage(ex)));
    }

    /**
     * Maps a per-passenger failure's causing exception to its
     * {@link ErrorCode}. See the class-level Javadoc above for how this
     * differs from {@link #map(Throwable, String)}.
     *
     * @param cause the exception that caused the per-passenger failure, as
     *              carried by {@code ReservationFailure.getCause()}; may be
     *              {@code null} for a synthetic failure with no real
     *              underlying exception, in which case this falls back to
     *              {@link ErrorCode#INTERNAL_ERROR}.
     */
    public static ErrorCode classifyPerPassengerFailure(RuntimeException cause) {
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
        return ex instanceof com.reservations.generator.domain.flow.UnknownFlowException
                || ex instanceof com.reservations.generator.domain.flow.InvalidPassengerPayloadException;
    }

    private static MappedError validationFailed(MethodArgumentNotValidException manv, String flowId) {
        String message = manv.getBindingResult().getFieldErrors().stream()
                .map(ErrorMapper::describeFieldError)
                .collect(Collectors.joining("; "));
        return new MappedError(HttpStatus.BAD_REQUEST,
                new ErrorResponse(ErrorCode.VALIDATION_FAILED, flowId, OrphanRisk.NONE, null,
                        message.isBlank() ? "Request validation failed" : message));
    }

    private static String describeFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private static String safeMessage(Throwable ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    /**
     * Convenience overload for callers that have no flowId context available
     * (e.g. the failure happened before the request could be parsed at all).
     */
    public MappedError map(Throwable ex) {
        return map(ex, null);
    }
}
