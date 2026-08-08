package com.reservations.generator.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Aggregate outcome of a batch reservation-creation request.
 *
 * <p>Every passenger in the originating request must be accounted for by
 * exactly one entry in either {@link #getPnrs()} or {@link #getFailures()}.
 * A single passenger's failure must never cause the results for other,
 * successfully processed passengers to be silently dropped.
 */
public final class ReservationResult {

    private final List<Pnr> pnrs;
    private final List<ReservationFailure> failures;

    public ReservationResult(List<Pnr> pnrs, List<ReservationFailure> failures) {
        this.pnrs = List.copyOf(Objects.requireNonNull(pnrs, "pnrs"));
        this.failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }

    public static ReservationResult empty() {
        return new ReservationResult(List.of(), List.of());
    }

    public List<Pnr> getPnrs() {
        return pnrs;
    }

    public List<ReservationFailure> getFailures() {
        return failures;
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    /**
     * Total number of accounted-for outcomes (successes + failures). Callers
     * can compare this against the original passenger count to detect any
     * silent omission.
     */
    public int totalOutcomes() {
        return pnrs.size() + failures.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReservationResult that)) {
            return false;
        }
        return pnrs.equals(that.pnrs) && failures.equals(that.failures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pnrs, failures);
    }

    @Override
    public String toString() {
        return "ReservationResult{pnrs=" + pnrs + ", failures=" + failures + "}";
    }
}
