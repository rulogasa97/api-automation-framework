package com.reservations.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.reservations.generator.api.dto.ReservationResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that two concurrent {@code POST /reservations} callers never leak
 * PNR results or session/CSRF material into one another, through the real
 * running application (no mocked ports), against a single local WireMock
 * server stubbed by request-body content so each caller is routed to its own
 * distinct fixture.
 *
 * <p>Each caller submits a two-passenger batch. Since {@code CreateReservationsUseCase}
 * processes a batch's passengers sequentially within one acquired
 * {@code Session} (see its Javadoc), the second passenger's outgoing request
 * carries whatever CSRF token the first passenger's response rotated in
 * ({@link com.reservations.generator.apireplay.session.Session#absorb}).
 * Asserting that each caller's second outgoing request carries only its own
 * caller's rotated token — never the other concurrently-running caller's —
 * is a direct, observable proof that no shared mutable session state exists
 * across concurrent requests.
 *
 * <p>Investigation note (task 30): before writing this test, {@code StubSessionProvider}
 * was inspected to confirm it returns a fresh {@link com.reservations.generator.apireplay.session.Session}
 * instance on every {@code acquire()} call rather than a shared/cached one.
 * It does (a plain {@code new Session(...)} per call, no caching field) —
 * this is the expected slice 1/2 design and no regression was found there.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationsGeneratorConcurrentCallersEndToEndTest {

    private static final String CREATE_RESERVATION_PATH = "/ual-create-v1/reservations";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    void concurrentCallersNeverMixPnrsOrLeakRotatedSessionMaterial() throws Exception {
        stubPassenger("Caller1-P1", "PNR-C1-P1", "csrf-caller1-rotated");
        stubPassenger("Caller1-P2", "PNR-C1-P2", "csrf-caller1-final");
        stubPassenger("Caller2-P1", "PNR-C2-P1", "csrf-caller2-rotated");
        stubPassenger("Caller2-P2", "PNR-C2-P2", "csrf-caller2-final");

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> caller1 = executor.submit(
                    () -> fireAfterLatch(startLatch, "Caller1-P1", "Caller1-P2"));
            Future<ResponseEntity<String>> caller2 = executor.submit(
                    () -> fireAfterLatch(startLatch, "Caller2-P1", "Caller2-P2"));

            startLatch.countDown();

            ReservationResponse response1 = parse(caller1.get(30, TimeUnit.SECONDS));
            ReservationResponse response2 = parse(caller2.get(30, TimeUnit.SECONDS));

            assertThat(response1.pnrs()).containsExactly("PNR-C1-P1", "PNR-C1-P2");
            assertThat(response2.pnrs()).containsExactly("PNR-C2-P1", "PNR-C2-P2");
        } finally {
            executor.shutdownNow();
        }

        // No cookie/CSRF bleed: caller 1's second outgoing call must carry
        // only the CSRF token caller 1's own first response rotated in, and
        // caller 2's second outgoing call must carry only its own.
        wireMockServer.verify(postRequestedFor(urlEqualTo(CREATE_RESERVATION_PATH))
                .withRequestBody(matchingJsonPath("$.passengerName", equalTo("Caller1-P2")))
                .withHeader("X-CSRF-Token", equalTo("csrf-caller1-rotated")));
        wireMockServer.verify(postRequestedFor(urlEqualTo(CREATE_RESERVATION_PATH))
                .withRequestBody(matchingJsonPath("$.passengerName", equalTo("Caller2-P2")))
                .withHeader("X-CSRF-Token", equalTo("csrf-caller2-rotated")));
    }

    private ResponseEntity<String> fireAfterLatch(CountDownLatch startLatch, String firstPassenger, String secondPassenger)
            throws InterruptedException {
        startLatch.await();
        String requestBody = """
                {"flowId":"ual-create-v1","schemaVersion":"1","passengers":[{"name":"%s"},{"name":"%s"}]}
                """.formatted(firstPassenger, secondPassenger);
        return restTemplate.postForEntity("/reservations", jsonEntity(requestBody), String.class);
    }

    private static void stubPassenger(String passengerName, String pnrCode, String rotatedCsrfToken) {
        wireMockServer.stubFor(post(urlEqualTo(CREATE_RESERVATION_PATH))
                .withRequestBody(matchingJsonPath("$.passengerName", equalTo(passengerName)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-CSRF-Token", rotatedCsrfToken)
                        .withBody("""
                                {"status":"CONFIRMED","reservation":{"pnrCode":"%s"}}""".formatted(pnrCode))));
    }

    private static ReservationResponse parse(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return OBJECT_MAPPER.readValue(response.getBody(), ReservationResponse.class);
    }

    private static HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
