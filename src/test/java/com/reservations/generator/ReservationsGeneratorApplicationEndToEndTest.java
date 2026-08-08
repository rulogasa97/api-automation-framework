package com.reservations.generator;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full synchronous create-a-reservation path, end to end: a real HTTP
 * request against the actual running application, through the real
 * {@code ReservationController}, {@code CreateReservationsUseCase},
 * {@code StubSessionProvider}, and {@code CreateReservationReplay}, with
 * only the final upstream hop stubbed by a local WireMock server (never a
 * real {@code united.com} host).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationsGeneratorApplicationEndToEndTest {

    private static final String CREATE_RESERVATION_PATH = "/ual-create-v1/reservations";

    private static WireMockServer wireMockServer;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void sandboxBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("reservations.sandbox.base-url", () -> wireMockServer.baseUrl());
    }

    @Test
    void createReservationsEndToEndReturnsThePnrFromTheStubbedSandbox() {
        wireMockServer.stubFor(post(urlEqualTo(CREATE_RESERVATION_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"CONFIRMED","reservation":{"pnrCode":"E2E123"}}""")));

        String requestBody = """
                {"flowId":"ual-create-v1","schemaVersion":"1","passengers":[{"name":"Ada Lovelace"}]}
                """;

        ResponseEntity<String> response = restTemplate.postForEntity("/reservations", jsonEntity(requestBody), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("E2E123");
        assertThat(response.getBody()).contains("\"failures\":[]");
    }

    /**
     * Same per-passenger partial-failure shape already covered at the
     * MockMvc level by {@code ReservationControllerTest}, but proven here
     * through the real running application: a real HTTP call reaches the
     * real {@code CreateReservationReplay}/{@code PnrExtractor} pair, and one
     * passenger's malformed upstream response is what actually triggers
     * {@code DriftDetectedException}, not a mocked {@code ReservationCreator}.
     */
    @Test
    void perPassengerMalformedUpstreamResponseSurfacesPartialSuccessThroughTheRealStack() {
        wireMockServer.stubFor(post(urlEqualTo(CREATE_RESERVATION_PATH))
                .withRequestBody(matchingJsonPath("$.passengerName", equalTo("Ada Lovelace")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"CONFIRMED","reservation":{"pnrCode":"E2E-ADA"}}""")));
        wireMockServer.stubFor(post(urlEqualTo(CREATE_RESERVATION_PATH))
                .withRequestBody(matchingJsonPath("$.passengerName", equalTo("Grace Hopper")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"unexpectedShape":true}""")));

        String requestBody = """
                {"flowId":"ual-create-v1","schemaVersion":"1","passengers":[{"name":"Ada Lovelace"},{"name":"Grace Hopper"}]}
                """;

        ResponseEntity<String> response = restTemplate.postForEntity("/reservations", jsonEntity(requestBody), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("E2E-ADA");
        assertThat(response.getBody()).contains("\"passengerIndex\":1");
        assertThat(response.getBody()).contains("\"errorCode\":\"DRIFT_DETECTED\"");
        assertThat(response.getBody()).contains("\"sideEffect\":\"POSSIBLE_ORPHAN_RESERVATION\"");
    }

    /**
     * Models the "no PNR produced for the only passenger in the batch" case:
     * session/flow resolution both succeed, but the create-dispatch call
     * itself fails (a real connection reset, not a malformed response body,
     * so this exercises {@code PostDispatchReservationException} rather than
     * {@code DriftDetectedException}).
     *
     * <p>Note on naming: in this codebase's current wiring, a create-dispatch
     * failure is always caught per-passenger by {@code CreateReservationsUseCase}
     * (see its Javadoc) and folded into a {@code ReservationResponse.Failure}
     * inside a 200 response — it never propagates out as a whole-batch HTTP
     * error status. A whole-batch HTTP error status (see {@code ErrorMapper})
     * is only reachable today via session-acquisition failure or request
     * validation, neither of which {@code StubSessionProvider} can currently
     * produce. This test therefore verifies the actual observable behavior
     * (200, zero PNRs, one {@code UPSTREAM_TIMEOUT} failure) rather than an
     * HTTP error response, since that is what a single-passenger dispatch
     * failure genuinely produces through the real stack today.
     */
    @Test
    void createDispatchFailureForTheOnlyPassengerYieldsZeroPnrsAndOneUpstreamTimeoutFailure() {
        wireMockServer.stubFor(post(urlEqualTo(CREATE_RESERVATION_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        String requestBody = """
                {"flowId":"ual-create-v1","schemaVersion":"1","passengers":[{"name":"Ada Lovelace"}]}
                """;

        ResponseEntity<String> response = restTemplate.postForEntity("/reservations", jsonEntity(requestBody), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"pnrs\":[]");
        assertThat(response.getBody()).contains("\"errorCode\":\"UPSTREAM_TIMEOUT\"");
        assertThat(response.getBody()).contains("\"sideEffect\":\"POSSIBLE_ORPHAN_RESERVATION\"");
    }

    private static org.springframework.http.HttpEntity<String> jsonEntity(String body) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}
