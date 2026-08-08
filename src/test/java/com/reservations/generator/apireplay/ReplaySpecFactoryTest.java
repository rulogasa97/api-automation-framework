package com.reservations.generator.apireplay;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.reservations.generator.apireplay.session.Session;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves REQ-7 at the apireplay layer: every call to
 * {@link ReplaySpecFactory#forSession(Session)} returns a brand-new,
 * independent {@link RequestSpecification} instance carrying that session's
 * own state, never a shared/static one.
 */
class ReplaySpecFactoryTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void stopWireMock() {
        wireMockServer.stop();
    }

    @Test
    void twoCallsProduceDistinctIndependentSpecInstances() {
        Session session = new Session(Map.of("session-id", "abc"), Map.of(), "csrf-1");

        RequestSpecification first = ReplaySpecFactory.forSession(session);
        RequestSpecification second = ReplaySpecFactory.forSession(session);

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void carriesTheOwningSessionsCookieAndCsrfTokenOntoTheWire() {
        wireMockServer.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200)));

        Session session = new Session(Map.of("session-id", "abc-123"), Map.of(), "csrf-xyz");

        RequestSpecification spec = ReplaySpecFactory.forSession(session).baseUri(wireMockServer.baseUrl());
        ReplaySpecFactory.forDispatch(spec).get("/ping");

        wireMockServer.verify(getRequestedFor(urlEqualTo("/ping"))
                .withCookie("session-id", equalTo("abc-123"))
                .withHeader(Session.CSRF_HEADER_NAME, equalTo("csrf-xyz")));
    }

    @Test
    void independentSpecsDoNotLeakOneSessionsCookiesIntoAnothers() {
        wireMockServer.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200)));

        Session sessionA = new Session(Map.of("session-id", "session-a"), Map.of(), "csrf-a");
        Session sessionB = new Session(Map.of("session-id", "session-b"), Map.of(), "csrf-b");

        RequestSpecification specA = ReplaySpecFactory.forSession(sessionA).baseUri(wireMockServer.baseUrl());
        RequestSpecification specB = ReplaySpecFactory.forSession(sessionB).baseUri(wireMockServer.baseUrl());
        ReplaySpecFactory.forDispatch(specA).get("/ping");
        ReplaySpecFactory.forDispatch(specB).get("/ping");

        wireMockServer.verify(getRequestedFor(urlEqualTo("/ping")).withCookie("session-id", equalTo("session-a")));
        wireMockServer.verify(getRequestedFor(urlEqualTo("/ping")).withCookie("session-id", equalTo("session-b")));
    }
}
