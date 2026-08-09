package com.reservations.generator;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full 3-passenger submission through the real running application (real
 * embedded Tomcat, real {@code web} controllers, real Thymeleaf rendering,
 * real {@code CreateReservationsUseCase} — mirrors {@link
 * ReservationsGeneratorApplicationEndToEndTest}'s harness, but through
 * {@code POST /ui/reservations} instead of {@code POST /reservations}),
 * with only the final upstream hop stubbed by a local WireMock server.
 *
 * <p>Proves the spec's "Multi-Passenger Submission in One Request" scenario
 * end to end: one submission containing 3 passengers, one outcome per
 * passenger in submission order — through the real
 * {@code passengers[N].fieldName} form-encoded binding (see {@link
 * com.reservations.generator.web.PassengerFormBinding}), not a
 * hand-constructed use-case call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationsGeneratorWebEndToEndTest {

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
    void threePassengerSubmissionViaTheWebUiReturnsOneOutcomePerPassengerInSubmissionOrder() {
        stubUpstreamPnr("Ada Lovelace", "WEB-ADA");
        stubUpstreamPnr("Grace Hopper", "WEB-GRACE");
        stubUpstreamPnr("Katherine Johnson", "WEB-KATHERINE");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("passengers[0].name", "Ada Lovelace");
        form.add("passengers[1].name", "Grace Hopper");
        form.add("passengers[2].name", "Katherine Johnson");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("HX-Request", "true");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/ui/reservations", new HttpEntity<>(form, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pnrCodesInRenderedOrder(response.getBody()))
                .as("PNR cards must render in submission order, not just be present somewhere in the body")
                .containsExactly("WEB-ADA", "WEB-GRACE", "WEB-KATHERINE");
        assertThat(response.getBody()).doesNotContain("<html");
    }

    /**
     * Extracts each rendered PNR card's code, in the order
     * {@code fragments/result.html}'s {@code th:each="pnr : ${result.pnrs}"}
     * emitted them — i.e. the actual on-page order, not merely which codes
     * are present anywhere in the body (see the {@code data-copy-pnr}
     * attribute {@code fragments/result.html} renders on each card).
     */
    private static List<String> pnrCodesInRenderedOrder(String html) {
        Matcher matcher = Pattern.compile("data-copy-pnr=\"([^\"]+)\"").matcher(html);
        List<String> codesInOrder = new ArrayList<>();
        while (matcher.find()) {
            codesInOrder.add(matcher.group(1));
        }
        return codesInOrder;
    }

    private static void stubUpstreamPnr(String passengerName, String pnrCode) {
        wireMockServer.stubFor(post(urlEqualTo(CREATE_RESERVATION_PATH))
                .withRequestBody(matchingJsonPath("$.passengerName", equalTo(passengerName)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"CONFIRMED","reservation":{"pnrCode":"%s"}}""".formatted(pnrCode))));
    }
}
