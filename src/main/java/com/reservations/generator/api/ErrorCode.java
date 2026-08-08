package com.reservations.generator.api;

/**
 * Stable machine-readable error codes surfaced in {@link com.reservations.generator.api.dto.ErrorResponse}.
 *
 * <p>See {@link ErrorMapper} for the exception-to-code mapping rules.
 */
public enum ErrorCode {

    /** Request-level Bean Validation failure, or an unrecognized flow reference. */
    VALIDATION_FAILED,

    /** A passenger payload contained a field outside its flow's strict schema. */
    UNKNOWN_FIELD,

    /** The upstream session could not be acquired; nothing was dispatched. */
    SESSION_UNAVAILABLE,

    /** The upstream call was dispatched but failed to complete (network/timeout). */
    UPSTREAM_TIMEOUT,

    /** The upstream response did not match the expected strict shape. */
    DRIFT_DETECTED,

    /** Fallback for any failure not covered by a more specific code above. */
    INTERNAL_ERROR
}
