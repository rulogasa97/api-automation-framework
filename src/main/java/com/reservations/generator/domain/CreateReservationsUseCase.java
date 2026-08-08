package com.reservations.generator.domain;

import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.Pnr;
import com.reservations.generator.domain.model.ReservationFailure;
import com.reservations.generator.domain.model.ReservationResult;
import com.reservations.generator.domain.model.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Application service that creates a batch of reservations for a single
 * flow.
 *
 * <p>Concurrency model: intra-request creates run strictly sequentially
 * (one passenger after another, within one acquired session). Concurrency
 * is instead bounded ACROSS callers/requests by a shared {@link Semaphore}
 * permit acquired for the whole batch, so at most {@code maxConcurrentCreates}
 * requests are creating reservations against the sandbox at any given time.
 *
 * <p>Failure handling: one passenger's failure never causes the results for
 * other passengers to be silently omitted. Every passenger ends up
 * represented in either {@link ReservationResult#getPnrs()} or
 * {@link ReservationResult#getFailures()}.
 */
public final class CreateReservationsUseCase {

    private final SessionProvider sessionProvider;
    private final ReservationCreator reservationCreator;
    private final Semaphore concurrencyGate;

    public CreateReservationsUseCase(SessionProvider sessionProvider,
                                      ReservationCreator reservationCreator,
                                      int maxConcurrentCreates) {
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider");
        this.reservationCreator = Objects.requireNonNull(reservationCreator, "reservationCreator");
        if (maxConcurrentCreates <= 0) {
            throw new IllegalArgumentException("maxConcurrentCreates must be positive");
        }
        this.concurrencyGate = new Semaphore(maxConcurrentCreates);
    }

    public ReservationResult execute(FlowDefinition flow, List<Passenger> passengers) {
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(passengers, "passengers");

        acquirePermit();
        try {
            Session session = sessionProvider.acquire(flow);
            return createSequentially(session, flow, passengers);
        } finally {
            concurrencyGate.release();
        }
    }

    private void acquirePermit() {
        try {
            concurrencyGate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConcurrencyAcquisitionException(e);
        }
    }

    private ReservationResult createSequentially(Session session, FlowDefinition flow, List<Passenger> passengers) {
        List<Pnr> pnrs = new ArrayList<>();
        List<ReservationFailure> failures = new ArrayList<>();

        for (int index = 0; index < passengers.size(); index++) {
            Passenger passenger = passengers.get(index);
            try {
                Pnr pnr = reservationCreator.create(session, flow, passenger);
                pnrs.add(pnr);
            } catch (RuntimeException e) {
                failures.add(new ReservationFailure(index, describe(e), classifyOrphanRisk(e), e));
            }
        }

        return new ReservationResult(pnrs, failures);
    }

    private static String describe(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /**
     * Classifies the {@link OrphanRisk} for a per-passenger failure.
     *
     * <p>If the caught exception is a {@link ReservationCreationException}
     * (or subtype), its own already-classified {@link OrphanRisk} is reused
     * directly, since the exception hierarchy already encodes this signal.
     * Otherwise (an unexpected {@code RuntimeException} outside the known
     * hierarchy), this conservatively defaults to
     * {@link OrphanRisk#POSSIBLE_ORPHAN_RESERVATION}: a create attempt was
     * already made for that passenger, so an unknown failure afterward must
     * not be assumed safe.
     */
    private static OrphanRisk classifyOrphanRisk(RuntimeException e) {
        if (e instanceof ReservationCreationException rce) {
            return rce.getOrphanRisk();
        }
        return OrphanRisk.POSSIBLE_ORPHAN_RESERVATION;
    }
}
