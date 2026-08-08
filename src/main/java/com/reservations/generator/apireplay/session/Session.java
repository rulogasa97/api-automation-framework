package com.reservations.generator.apireplay.session;

import io.restassured.response.Response;

import java.util.Map;
import java.util.Objects;

/**
 * Concrete sandbox session: cookies, CSRF token, and extra headers required
 * to replay the captured browser flow.
 *
 * <p>Implements the domain-owned {@link com.reservations.generator.domain.model.Session}
 * marker interface (see that type's Javadoc for the hexagonal-purity
 * rationale of why the marker lives in {@code domain.model} while this
 * concrete implementation lives here in {@code apireplay.session}).
 *
 * @apiNote PLACEHOLDER: the CSRF header name below is invented. Replace with
 * the real header/cookie name once a DevTools capture of an authorized
 * United sandbox session is available.
 */
public final class Session implements com.reservations.generator.domain.model.Session {

    // PLACEHOLDER: fictional CSRF header name.
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    private volatile Map<String, String> cookies;
    private volatile Map<String, String> headers;
    private volatile String csrfToken;

    public Session(Map<String, String> cookies, Map<String, String> headers, String csrfToken) {
        this.cookies = copyOf(cookies);
        this.headers = copyOf(headers);
        this.csrfToken = csrfToken;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    /**
     * Rotates this session's state from an upstream response after a replay
     * call.
     *
     * <p>Cookie rotation is a full replacement, never a merge: whatever
     * cookies the response carries become this session's entire cookie set
     * going forward, so no stale cookie from the previous state can leak
     * through once the upstream system has stopped sending it. The CSRF
     * token is only replaced when the response actually carries a new one;
     * otherwise the previous token is kept, since not every response
     * rotates it.
     */
    public synchronized void absorb(Response response) {
        Objects.requireNonNull(response, "response");

        this.cookies = copyOf(response.getCookies());

        String rotatedCsrf = response.getHeaders() != null
                ? response.getHeaders().getValue(CSRF_HEADER_NAME)
                : null;
        if (rotatedCsrf != null && !rotatedCsrf.isBlank()) {
            this.csrfToken = rotatedCsrf;
        }
    }

    private static Map<String, String> copyOf(Map<String, String> source) {
        return source == null ? Map.of() : Map.copyOf(source);
    }
}
