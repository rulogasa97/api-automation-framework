package com.reservations.generator.api.dto;

import com.reservations.generator.api.ErrorCode;
import com.reservations.generator.domain.OrphanRisk;

/**
 * Wire-level error body returned for any request that fails before a
 * {@link ReservationResponse} can be produced.
 *
 * <p>{@code passengerIndex} is nullable: whole-batch errors (e.g. session
 * acquisition failure, request validation) have no single passenger to
 * attribute the failure to. Per-passenger failures are never reported this
 * way; they are reported as {@link ReservationResponse.Failure} entries
 * inside a normal (200) {@link ReservationResponse}, since one passenger's
 * failure must never suppress the results for the others.
 */
public record ErrorResponse(
        ErrorCode errorCode,
        String flowId,
        OrphanRisk sideEffect,
        Integer passengerIndex,
        String message) {
}
