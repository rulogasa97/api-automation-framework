package com.reservations.generator.api.dto;

import com.reservations.generator.domain.ErrorCode;
import com.reservations.generator.domain.OrphanRisk;
import com.reservations.generator.domain.ReservationFailureClassifier;
import com.reservations.generator.domain.model.Pnr;
import com.reservations.generator.domain.model.ReservationFailure;
import com.reservations.generator.domain.model.ReservationResult;

import java.util.List;

/**
 * Wire-level response body for a successfully completed (i.e. not rejected
 * before dispatch) {@code POST /reservations} call.
 *
 * <p>Mirrors {@link ReservationResult}: every passenger from the request
 * ends up represented either in {@link #pnrs()} or in {@link #failures()}.
 * A single passenger's failure never causes the results for the other,
 * successfully processed passengers to be silently omitted.
 *
 * <p>{@link Failure} mirrors {@link ErrorResponse}'s shape (errorCode +
 * sideEffect + passengerIndex + message) so the two failure-reporting paths
 * — whole-batch errors ({@link ErrorResponse}) and per-passenger failures
 * ({@link Failure}) — are consistent for API consumers. See
 * {@code api.ErrorMapper}'s class-level Javadoc for how the two paths
 * relate.
 */
public record ReservationResponse(List<String> pnrs, List<Failure> failures) {

    public record Failure(ErrorCode errorCode, OrphanRisk sideEffect, int passengerIndex, String message) {
    }

    public static ReservationResponse from(ReservationResult result) {
        List<String> pnrCodes = result.getPnrs().stream().map(Pnr::getCode).toList();
        List<Failure> failures = result.getFailures().stream()
                .map(ReservationResponse::toFailure)
                .toList();
        return new ReservationResponse(pnrCodes, failures);
    }

    private static Failure toFailure(ReservationFailure failure) {
        ErrorCode errorCode = ReservationFailureClassifier.classifyPerPassenger(failure.getCause());
        return new Failure(errorCode, failure.getOrphanRisk(), failure.getPassengerIndex(), failure.getMessage());
    }
}
