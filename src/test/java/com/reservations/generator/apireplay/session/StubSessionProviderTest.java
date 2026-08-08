package com.reservations.generator.apireplay.session;

import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubSessionProviderTest {

    private static final FlowDefinition FLOW = new FlowDefinition("ual-create-v1", "1", Passenger.class);

    @Test
    void returnsASessionPopulatedFromConfiguredCookieAndToken() {
        StubSessionProvider provider = new StubSessionProvider("configured-cookie-value", "configured-token-value");

        Session session = provider.acquire(FLOW);

        assertThat(session.getCookies()).containsValue("configured-cookie-value");
        assertThat(session.getCsrfToken()).isEqualTo("configured-token-value");
    }

    @Test
    void returnsAnEmptySessionWhenConfigIsBlank() {
        StubSessionProvider provider = new StubSessionProvider("", "");

        Session session = provider.acquire(FLOW);

        assertThat(session.getCookies()).isEmpty();
        assertThat(session.getCsrfToken()).isNull();
    }
}
