package com.reservations.generator.apireplay;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.reservations.generator.apireplay.session.Session;
import com.reservations.generator.domain.DriftDetectedException;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.Pnr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Component test for {@link CreateReservationReplay} against a local
 * WireMock server stubbing the invented placeholder "ual-create-v1" flow
 * (see {@link CreateReservationReplay}'s Javadoc {@code @apiNote}). Never
 * targets any real upstream host.
 */
class CreateReservationReplayTest {

    private static final FlowDefinition FLOW = new FlowDefinition("ual-create-v1", "1", Passenger.class);
    private static final String CREATE_RESERVATION_PATH = "/ual-create-v1/reservations";

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
    void happyPathReturnsThePnrFromTheStubbedResponse() {
        wireMockServer.stubFor(post(urlEqualTo(CREATE_RESERVATION_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"CONFIRMED","reservation":{"pnrCode":"ABC123"}}""")));

        CreateReservationReplay replay = new CreateReservationReplay(wireMockServer.baseUrl(), new PnrExtractor());
        Session session = new Session(Map.of(), Map.of(), null);

        Pnr pnr = replay.create(session, FLOW, new Passenger("Ada Lovelace"));

        assertThat(pnr).isEqualTo(new Pnr("ABC123"));
        wireMockServer.verify(postRequestedFor(urlEqualTo(CREATE_RESERVATION_PATH)));
    }

    @Test
    void malformedResponseTriggersDriftDetectedException() {
        wireMockServer.stubFor(post(urlEqualTo(CREATE_RESERVATION_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"unexpectedShape":true}""")));

        CreateReservationReplay replay = new CreateReservationReplay(wireMockServer.baseUrl(), new PnrExtractor());
        Session session = new Session(Map.of(), Map.of(), null);

        assertThatThrownBy(() -> replay.create(session, FLOW, new Passenger("Ada Lovelace")))
                .isInstanceOf(DriftDetectedException.class);
    }

    @Test
    void successfulCallAbsorbsRotatedSessionStateFromTheResponse() {
        wireMockServer.stubFor(post(urlEqualTo(CREATE_RESERVATION_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader(Session.CSRF_HEADER_NAME, "rotated-csrf")
                        .withHeader("Set-Cookie", "session-id=rotated-value")
                        .withBody("""
                                {"status":"CONFIRMED","reservation":{"pnrCode":"ABC123"}}""")));

        CreateReservationReplay replay = new CreateReservationReplay(wireMockServer.baseUrl(), new PnrExtractor());
        Session session = new Session(Map.of(), Map.of(), "initial-csrf");

        replay.create(session, FLOW, new Passenger("Ada Lovelace"));

        assertThat(session.getCsrfToken()).isEqualTo("rotated-csrf");
        assertThat(session.getCookies()).containsEntry("session-id", "rotated-value");
    }

    @Test
    void requestCarriesTheSessionsCsrfTokenHeader() {
        wireMockServer.stubFor(post(urlEqualTo(CREATE_RESERVATION_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"CONFIRMED","reservation":{"pnrCode":"ABC123"}}""")));

        CreateReservationReplay replay = new CreateReservationReplay(wireMockServer.baseUrl(), new PnrExtractor());
        Session session = new Session(Map.of(), Map.of(), "outgoing-csrf");

        replay.create(session, FLOW, new Passenger("Ada Lovelace"));

        wireMockServer.verify(postRequestedFor(urlEqualTo(CREATE_RESERVATION_PATH))
                .withHeader(Session.CSRF_HEADER_NAME, equalTo("outgoing-csrf")));
    }
}
