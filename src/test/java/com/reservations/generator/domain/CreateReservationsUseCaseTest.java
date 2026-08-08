package com.reservations.generator.domain;

import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.Pnr;
import com.reservations.generator.domain.model.ReservationFailure;
import com.reservations.generator.domain.model.ReservationResult;
import com.reservations.generator.domain.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateReservationsUseCaseTest {

    private static final FlowDefinition FLOW =
            new FlowDefinition("ual-create-v1", "1", Passenger.class);

    private SessionProvider sessionProvider;
    private ReservationCreator reservationCreator;
    private Session session;

    @BeforeEach
    void setUp() {
        sessionProvider = mock(SessionProvider.class);
        reservationCreator = mock(ReservationCreator.class);
        session = mock(Session.class);
        when(sessionProvider.acquire(any())).thenReturn(session);
    }

    @Test
    void allPassengersSucceedSequentiallyInOrder() {
        Passenger p1 = new Passenger("Ada Lovelace");
        Passenger p2 = new Passenger("Grace Hopper");
        when(reservationCreator.create(session, FLOW, p1)).thenReturn(new Pnr("PNR001"));
        when(reservationCreator.create(session, FLOW, p2)).thenReturn(new Pnr("PNR002"));

        CreateReservationsUseCase useCase = new CreateReservationsUseCase(sessionProvider, reservationCreator, 8);

        ReservationResult result = useCase.execute(FLOW, List.of(p1, p2));

        assertThat(result.getPnrs()).containsExactly(new Pnr("PNR001"), new Pnr("PNR002"));
        assertThat(result.getFailures()).isEmpty();

        var order = inOrder(reservationCreator);
        order.verify(reservationCreator).create(session, FLOW, p1);
        order.verify(reservationCreator).create(session, FLOW, p2);
    }

    @Test
    void onePassengerFailureDoesNotOmitOtherSuccesses() {
        Passenger p1 = new Passenger("Ada Lovelace");
        Passenger p2 = new Passenger("Broken Passenger");
        Passenger p3 = new Passenger("Grace Hopper");

        when(reservationCreator.create(session, FLOW, p1)).thenReturn(new Pnr("PNR001"));
        when(reservationCreator.create(session, FLOW, p2))
                .thenThrow(new RuntimeException("upstream rejected passenger"));
        when(reservationCreator.create(session, FLOW, p3)).thenReturn(new Pnr("PNR003"));

        CreateReservationsUseCase useCase = new CreateReservationsUseCase(sessionProvider, reservationCreator, 8);

        ReservationResult result = useCase.execute(FLOW, List.of(p1, p2, p3));

        assertThat(result.getPnrs()).containsExactly(new Pnr("PNR001"), new Pnr("PNR003"));
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getPassengerIndex()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getMessage()).isEqualTo("upstream rejected passenger");
        assertThat(result.getFailures().get(0).getOrphanRisk())
                .as("an unexpected RuntimeException outside the known hierarchy must default to POSSIBLE_ORPHAN_RESERVATION")
                .isEqualTo(OrphanRisk.POSSIBLE_ORPHAN_RESERVATION);
        assertThat(result.totalOutcomes()).isEqualTo(3);

        verify(reservationCreator, times(3)).create(any(), any(), any());
    }

    @Test
    void perPassengerReservationCreationExceptionReusesItsOwnOrphanRiskAndIsExposedAsTheCause() {
        Passenger p1 = new Passenger("Ada Lovelace");
        PreDispatchReservationException thrown =
                new PreDispatchReservationException("failed to build request", null);

        when(reservationCreator.create(session, FLOW, p1)).thenThrow(thrown);

        CreateReservationsUseCase useCase = new CreateReservationsUseCase(sessionProvider, reservationCreator, 8);

        ReservationResult result = useCase.execute(FLOW, List.of(p1));

        assertThat(result.getFailures()).hasSize(1);
        ReservationFailure failure = result.getFailures().get(0);
        assertThat(failure.getOrphanRisk())
                .as("a known ReservationCreationException must keep its own classified OrphanRisk, not the fail-safe default")
                .isEqualTo(OrphanRisk.NONE);
        assertThat(failure.getCause()).isSameAs(thrown);
    }

    @Test
    void rejectsNonPositiveConcurrencyBound() {
        assertThatThrownBy(() -> new CreateReservationsUseCase(sessionProvider, reservationCreator, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundsConcurrencyAcrossConcurrentCallers() throws Exception {
        AtomicInteger concurrentInvocations = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrency = new AtomicInteger(0);

        when(reservationCreator.create(any(), any(), any())).thenAnswer(invocation -> {
            int current = concurrentInvocations.incrementAndGet();
            maxObservedConcurrency.updateAndGet(max -> Math.max(max, current));
            Thread.sleep(150);
            concurrentInvocations.decrementAndGet();
            return new Pnr("PNR-" + Thread.currentThread().getId());
        });

        // Only one permit: two concurrent callers must never run their
        // creation phase at the same time.
        CreateReservationsUseCase useCase = new CreateReservationsUseCase(sessionProvider, reservationCreator, 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Passenger passenger = new Passenger("Solo Passenger");
            Future<ReservationResult> f1 = executor.submit(() -> useCase.execute(FLOW, List.of(passenger)));
            Future<ReservationResult> f2 = executor.submit(() -> useCase.execute(FLOW, List.of(passenger)));

            ReservationResult r1 = f1.get(5, TimeUnit.SECONDS);
            ReservationResult r2 = f2.get(5, TimeUnit.SECONDS);

            assertThat(r1.getPnrs()).hasSize(1);
            assertThat(r2.getPnrs()).hasSize(1);
            assertThat(maxObservedConcurrency.get())
                    .as("concurrency must be bounded to the configured permit count")
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void interruptingThreadWaitingForPermitThrowsConcurrencyAcquisitionException() throws Exception {
        CreateReservationsUseCase useCase = new CreateReservationsUseCase(sessionProvider, reservationCreator, 1);
        Passenger passenger = new Passenger("Solo Passenger");

        CountDownLatch permitHeld = new CountDownLatch(1);
        CountDownLatch releasePermit = new CountDownLatch(1);
        when(reservationCreator.create(any(), any(), any())).thenAnswer(invocation -> {
            permitHeld.countDown();
            releasePermit.await();
            return new Pnr("PNR-HOLDER");
        });

        ExecutorService executor = Executors.newFixedThreadPool(1);
        try {
            // First caller holds the single permit for the whole duration of the test.
            Future<ReservationResult> holder = executor.submit(() -> useCase.execute(FLOW, List.of(passenger)));
            assertThat(permitHeld.await(5, TimeUnit.SECONDS)).isTrue();

            AtomicReference<Throwable> capturedException = new AtomicReference<>();
            Thread waiter = new Thread(() -> {
                try {
                    useCase.execute(FLOW, List.of(passenger));
                } catch (Throwable t) {
                    capturedException.set(t);
                }
            });
            waiter.start();
            waitUntilBlocked(waiter);

            waiter.interrupt();
            waiter.join(5000);

            assertThat(capturedException.get())
                    .as("interrupted permit wait must surface as ConcurrencyAcquisitionException")
                    .isInstanceOf(ConcurrencyAcquisitionException.class)
                    .hasCauseInstanceOf(InterruptedException.class);

            releasePermit.countDown();
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void waitUntilBlocked(Thread thread) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Thread never reached a blocked state waiting for the permit: " + thread.getState());
    }
}
