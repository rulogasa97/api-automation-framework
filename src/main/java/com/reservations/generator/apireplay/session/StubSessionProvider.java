package com.reservations.generator.apireplay.session;

import com.reservations.generator.domain.SessionProvider;
import com.reservations.generator.domain.model.FlowDefinition;

import java.util.Map;
import java.util.Objects;

/**
 * v1 {@link SessionProvider}: builds a {@link Session} from statically
 * configured sandbox session material (cookie + token), read from the
 * {@code reservations.apireplay.session.*} configuration keys (see
 * {@code application.yml}). No secrets are hardcoded here; empty values
 * simply produce a session with no cookie/token, which is convenient for
 * local runs against a WireMock stub that does not check them.
 *
 * @apiNote PLACEHOLDER: a static, config-supplied session is a stand-in for
 * real session acquisition (e.g. driving an authenticated login flow
 * against the sandbox). Replace with a real login-flow-backed
 * {@code SessionProvider} once a DevTools capture of that flow is available
 * from an authorized United sandbox.
 */
public final class StubSessionProvider implements SessionProvider {

    // PLACEHOLDER: fictional cookie name.
    private static final String SESSION_COOKIE_NAME = "sandbox-session";

    private final String cookie;
    private final String token;

    public StubSessionProvider(String cookie, String token) {
        this.cookie = Objects.requireNonNullElse(cookie, "");
        this.token = Objects.requireNonNullElse(token, "");
    }

    @Override
    public Session acquire(FlowDefinition flow) {
        Objects.requireNonNull(flow, "flow");
        Map<String, String> cookies = cookie.isBlank() ? Map.of() : Map.of(SESSION_COOKIE_NAME, cookie);
        String csrfToken = token.isBlank() ? null : token;
        return new Session(cookies, Map.of(), csrfToken);
    }
}
