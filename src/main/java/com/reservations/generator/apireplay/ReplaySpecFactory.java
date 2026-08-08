package com.reservations.generator.apireplay;

import com.reservations.generator.apireplay.session.Session;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.specification.RequestSender;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import java.util.Objects;

/**
 * Builds a brand-new {@link RequestSpecification} per call, seeded from the
 * given {@link Session}'s current cookies/headers/CSRF token, and provides
 * {@link #forDispatch(RequestSpecification)} to make a fully-configured spec
 * ready for an actual HTTP call.
 *
 * <p>Deliberately never touches the static/mutable configuration surface of
 * {@code io.restassured.RestAssured} (e.g. {@code RestAssured.baseURI}, the
 * no-arg {@code given()}, the one-argument {@code given(RequestSpecification)}
 * — which, verified against the library sources, seeds its result from those
 * same shared static fields), all of which hold process-wide shared state.
 * Each returned spec is a fresh, fully independent instance (a new
 * {@link RequestSpecBuilder} is used per call and never reused/cached across
 * calls), so concurrent callers replaying different sessions can never leak
 * state into one another. This is enforced by the ArchUnit rule banning
 * static access to {@code io.restassured.RestAssured}'s shared state in
 * {@code src/main} (with a narrow, explicit carve-out for the stateless
 * {@code given(RequestSpecification, ResponseSpecification)} overload used
 * below) and is directly exercised by
 * {@code ReplaySpecFactoryTest#twoCallsProduceDistinctIndependentSpecInstances}.
 *
 * <p>Implementation note: calling an HTTP method directly on a bare
 * {@code new RequestSpecBuilder().build()} spec fails with an internal
 * {@code NullPointerException} ("assertionClosure on null object"), since it
 * was never wired to a response specification. Of RestAssured's public entry
 * points that perform that wiring, only the two-argument
 * {@link RestAssured#given(RequestSpecification, ResponseSpecification)}
 * overload is truly stateless (verified against the library sources: unlike
 * the no-arg/one-arg {@code given(...)} overloads, it reads neither
 * {@code RestAssured.baseURI} nor any other shared static field). It returns
 * a {@link RequestSender}, not a {@link RequestSpecification}, so callers
 * must finish configuring the spec (base URI, content type, body, ...) via
 * {@link #forSession(Session)}'s result <em>first</em>, then pass the
 * fully-configured spec to {@link #forDispatch(RequestSpecification)}
 * immediately before issuing the actual request.
 */
public final class ReplaySpecFactory {

    private ReplaySpecFactory() {
    }

    public static RequestSpecification forSession(Session session) {
        Objects.requireNonNull(session, "session");

        RequestSpecBuilder builder = new RequestSpecBuilder();
        if (!session.getCookies().isEmpty()) {
            builder.addCookies(session.getCookies());
        }
        if (!session.getHeaders().isEmpty()) {
            builder.addHeaders(session.getHeaders());
        }
        String csrfToken = session.getCsrfToken();
        if (csrfToken != null && !csrfToken.isBlank()) {
            builder.addHeader(Session.CSRF_HEADER_NAME, csrfToken);
        }

        return builder.build();
    }

    /**
     * Wraps a fully-configured {@link RequestSpecification} (base URI,
     * content type, body, ... already set) into a {@link RequestSender}
     * ready to dispatch an HTTP call, without ever reading/writing shared
     * static {@code RestAssured} state.
     */
    public static RequestSender forDispatch(RequestSpecification configuredSpec) {
        Objects.requireNonNull(configuredSpec, "configuredSpec");

        ResponseSpecification emptyResponseSpec = new ResponseSpecBuilder().build();
        return RestAssured.given(configuredSpec, emptyResponseSpec);
    }
}
