package com.reservations.generator.apireplay.session;

import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionTest {

    @Test
    void absorbRotatesCookiesAndCsrfFromResponse() {
        Session session = new Session(Map.of("old-cookie", "old-value"), Map.of(), "old-csrf");

        Response response = mock(Response.class);
        when(response.getCookies()).thenReturn(Map.of("new-cookie", "new-value"));
        when(response.getHeaders()).thenReturn(new Headers(new Header(Session.CSRF_HEADER_NAME, "new-csrf")));

        session.absorb(response);

        assertThat(session.getCookies()).containsExactlyEntriesOf(Map.of("new-cookie", "new-value"));
        assertThat(session.getCsrfToken()).isEqualTo("new-csrf");
    }

    @Test
    void absorbDoesNotLeakPreviousCookiesNotPresentInTheNewResponse() {
        Session session = new Session(Map.of("stale-cookie", "stale-value"), Map.of(), "old-csrf");

        Response response = mock(Response.class);
        when(response.getCookies()).thenReturn(Map.of("fresh-cookie", "fresh-value"));
        when(response.getHeaders()).thenReturn(new Headers());

        session.absorb(response);

        assertThat(session.getCookies()).doesNotContainKey("stale-cookie");
        assertThat(session.getCookies()).containsExactlyEntriesOf(Map.of("fresh-cookie", "fresh-value"));
    }

    @Test
    void absorbKeepsPreviousCsrfTokenWhenResponseDoesNotCarryANewOne() {
        Session session = new Session(Map.of(), Map.of(), "existing-csrf");

        Response response = mock(Response.class);
        when(response.getCookies()).thenReturn(Map.of());
        when(response.getHeaders()).thenReturn(new Headers());

        session.absorb(response);

        assertThat(session.getCsrfToken()).isEqualTo("existing-csrf");
    }
}
