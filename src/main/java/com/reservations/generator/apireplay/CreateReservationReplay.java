package com.reservations.generator.apireplay;

import com.reservations.generator.apireplay.session.Session;
import com.reservations.generator.domain.PostDispatchReservationException;
import com.reservations.generator.domain.PreDispatchReservationException;
import com.reservations.generator.domain.ReservationCreator;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.Pnr;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;
import java.util.Objects;

/**
 * Outbound adapter: "captured request reproduction" of flow #1 against the
 * configured sandbox base URL ({@code reservations.sandbox.base-url}).
 *
 * @apiNote PLACEHOLDER: since no authorized United sandbox capture exists,
 * the request path/shape used here (flow id {@code ual-create-v1}) is
 * entirely invented for illustration and is stubbed with WireMock in tests.
 * It must be replaced with the real captured request once DevTools HAR data
 * is available from an authorized United sandbox session. This adapter must
 * never target any real {@code *.united.com} host; the base URL is always
 * externally configured and points at WireMock/localhost in tests and dev.
 *
 * <p>Failure classification: any failure while building the request (before
 * dispatch) is reported as {@link PreDispatchReservationException} (no
 * orphan-reservation risk, since nothing was sent). Any failure from the
 * point the HTTP call is actually dispatched onward — network I/O, timeout,
 * or a malformed response (via {@link PnrExtractor} throwing
 * {@code DriftDetectedException}) — is a
 * {@link PostDispatchReservationException} (possible orphan reservation,
 * since bytes may already have reached the upstream system).
 *
 * @apiNote PLACEHOLDER: this slice does not yet distinguish
 * connection-establishment failures (e.g. connection refused, no bytes
 * sent) from failures after bytes were actually written to the socket; both
 * are conservatively classified as post-dispatch here. Precise
 * classification down to the socket/HTTP-error-code level is a slice-3
 * concern (see {@code api/ErrorMapper}, not yet built).
 */
public final class CreateReservationReplay implements ReservationCreator {

    // PLACEHOLDER: fictional flow id/endpoint path.
    private static final String FLOW_ID = "ual-create-v1";
    private static final String CREATE_RESERVATION_PATH = "/ual-create-v1/reservations";

    private final String sandboxBaseUrl;
    private final PnrExtractor pnrExtractor;

    public CreateReservationReplay(String sandboxBaseUrl, PnrExtractor pnrExtractor) {
        this.sandboxBaseUrl = Objects.requireNonNull(sandboxBaseUrl, "sandboxBaseUrl");
        this.pnrExtractor = Objects.requireNonNull(pnrExtractor, "pnrExtractor");
    }

    @Override
    public Pnr create(com.reservations.generator.domain.model.Session session, FlowDefinition flow, Passenger passenger) {
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(passenger, "passenger");
        Session replaySession = asReplaySession(session);

        RequestSpecification spec = buildRequestSpec(replaySession, flow, passenger);
        Response response = dispatch(spec);

        replaySession.absorb(response);
        return pnrExtractor.extract(response.asString());
    }

    private static Session asReplaySession(com.reservations.generator.domain.model.Session session) {
        if (!(session instanceof Session replaySession)) {
            throw new PreDispatchReservationException(
                    "Expected a session created by StubSessionProvider, got: "
                            + (session == null ? "null" : session.getClass().getName()),
                    null);
        }
        return replaySession;
    }

    private RequestSpecification buildRequestSpec(Session session, FlowDefinition flow, Passenger passenger) {
        try {
            RequestSpecification spec = ReplaySpecFactory.forSession(session);
            return spec.baseUri(sandboxBaseUrl)
                    .contentType("application/json")
                    .body(toRequestBody(flow, passenger));
        } catch (RuntimeException e) {
            // Nothing was sent yet: building the request spec/body failed
            // strictly before dispatch, so there is no orphan-reservation risk.
            throw new PreDispatchReservationException(
                    "Failed to build the replay request for flow '" + flow.flowId() + "'", e);
        }
    }

    private static Map<String, Object> toRequestBody(FlowDefinition flow, Passenger passenger) {
        // PLACEHOLDER: invented request shape; replace with the real
        // captured payload once DevTools HAR data is available.
        return Map.of(
                "flowId", flow.flowId(),
                "schemaVersion", flow.schemaVersion(),
                "passengerName", passenger.getName());
    }

    private Response dispatch(RequestSpecification spec) {
        try {
            // From here on, bytes may have left the client: any failure from
            // this point carries a possible-orphan-reservation risk.
            return ReplaySpecFactory.forDispatch(spec).post(CREATE_RESERVATION_PATH);
        } catch (Exception e) {
            // Catches Exception, not just RuntimeException: RestAssured's
            // dispatch path runs through Groovy internals, which do not
            // enforce checked exceptions, so a real I/O failure (e.g.
            // java.net.SocketException on a connection reset) can propagate
            // out of this call as a checked exception despite this method's
            // own signature declaring none. Narrowing this catch to
            // RuntimeException would let such a checked failure escape
            // unwrapped, bypass PostDispatchReservationException entirely,
            // and surface as an unhandled 500 instead of a graceful
            // per-passenger failure.
            throw new PostDispatchReservationException(
                    "Create-reservation request dispatch failed for flow '" + FLOW_ID + "'", e);
        }
    }
}
