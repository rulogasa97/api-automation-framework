package com.reservations.generator.api;

import com.reservations.generator.api.dto.ErrorResponse;
import com.reservations.generator.domain.ErrorCode;
import com.reservations.generator.domain.OrphanRisk;
import com.reservations.generator.domain.ReservationFailureClassifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.stream.Collectors;

/**
 * Maps failures raised anywhere while handling {@code POST /reservations} to
 * an HTTP status and an {@link ErrorResponse}, via {@link #map(Throwable, String)}.
 *
 * <p>Exception-to-{@link ErrorCode} classification itself lives in
 * {@link ReservationFailureClassifier} (a framework-free domain concept, so
 * other driving adapters can reuse it without depending on {@code api}).
 * This mapper keeps only what is genuinely {@code api}-layer: recognizing
 * the framework-specific {@link MethodArgumentNotValidException}, turning an
 * {@link ErrorCode} into its {@link HttpStatus}, and assembling the final
 * {@link ErrorResponse} body (message, {@link OrphanRisk}).
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
 *     200 response instead, via
 *     {@link ReservationFailureClassifier#classifyPerPassenger(RuntimeException)},
 *     so that one passenger's failure never suppresses the results for the
 *     others.</li>
 * </ul>
 *
 * <p>Mapping rules for whole-batch failures (see
 * {@link ReservationFailureClassifier} for the classification rules, and the
 * design doc for the full rationale):
 * <ul>
 *     <li>Bean Validation failures on the request envelope, and an
 *     unrecognized (flowId, schemaVersion) pair, are request-shape problems:
 *     {@link ErrorCode#VALIDATION_FAILED}, 400, {@link OrphanRisk#NONE}.</li>
 *     <li>A passenger payload field outside its flow's strict schema:
 *     {@link ErrorCode#UNKNOWN_FIELD}, 400, {@link OrphanRisk#NONE}.</li>
 *     <li>A failure to acquire the upstream session (nothing was
 *     dispatched): {@link ErrorCode#SESSION_UNAVAILABLE}, 503,
 *     {@link OrphanRisk#NONE}.</li>
 *     <li>The upstream response not matching its expected shape:
 *     {@link ErrorCode#DRIFT_DETECTED}, 502,
 *     {@link OrphanRisk#POSSIBLE_ORPHAN_RESERVATION}.</li>
 *     <li>Any other post-dispatch failure (e.g. dispatch failure/timeout):
 *     {@link ErrorCode#UPSTREAM_TIMEOUT}, 504,
 *     {@link OrphanRisk#POSSIBLE_ORPHAN_RESERVATION}.</li>
 *     <li>Anything else unexpected: {@link ErrorCode#INTERNAL_ERROR}, 500,
 *     {@link OrphanRisk#NONE}. This case is a safety net, not a mapped
 *     requirement.</li>
 * </ul>
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
        ErrorCode errorCode = ReservationFailureClassifier.classify(ex);
        return new MappedError(statusFor(errorCode),
                new ErrorResponse(errorCode, flowId, ReservationFailureClassifier.orphanRiskFor(ex), null, safeMessage(ex)));
    }

    /**
     * The HTTP status for a whole-batch failure, one-to-one with its
     * {@link ErrorCode} (see {@link ReservationFailureClassifier} for the
     * exception-to-code classification rules this depends on).
     */
    private static HttpStatus statusFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_FAILED, UNKNOWN_FIELD -> HttpStatus.BAD_REQUEST;
            case SESSION_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DRIFT_DETECTED -> HttpStatus.BAD_GATEWAY;
            case UPSTREAM_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
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
