package com.reservations.generator.apireplay;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservations.generator.domain.DriftDetectedException;
import com.reservations.generator.domain.model.Pnr;

import java.io.IOException;

/**
 * Strictly parses a (placeholder) "ual-create-v1" create-reservation
 * response and extracts its PNR code.
 *
 * @apiNote PLACEHOLDER: the response shape parsed here
 * ({@code {"status": "...", "reservation": {"pnrCode": "..."}}}) is entirely
 * invented, since no authorized United sandbox capture exists. It must be
 * replaced with the real captured response shape once DevTools HAR data is
 * available from an authorized United sandbox session.
 *
 * <p>Extraction is strict, never lenient/best-effort: any unexpected or
 * malformed shape (parse failure, missing field, unexpected status, blank
 * PNR) throws {@link DriftDetectedException} rather than attempting a
 * partial extraction.
 */
public final class PnrExtractor {

    // PLACEHOLDER: fictional expected status value.
    private static final String CONFIRMED_STATUS = "CONFIRMED";

    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public Pnr extract(String responseBody) {
        PlaceholderCreateReservationResponse parsed = parse(responseBody);
        validate(parsed, responseBody);
        return new Pnr(parsed.reservation().pnrCode());
    }

    private PlaceholderCreateReservationResponse parse(String responseBody) {
        try {
            return STRICT_MAPPER.readValue(responseBody, PlaceholderCreateReservationResponse.class);
        } catch (IOException | RuntimeException e) {
            throw new DriftDetectedException(
                    "Create-reservation response did not match the expected placeholder shape", e);
        }
    }

    private void validate(PlaceholderCreateReservationResponse parsed, String responseBody) {
        if (parsed.status() == null || parsed.reservation() == null) {
            throw new DriftDetectedException(
                    "Create-reservation response is missing required fields: " + responseBody);
        }
        if (!CONFIRMED_STATUS.equals(parsed.status())) {
            throw new DriftDetectedException(
                    "Create-reservation response reported unexpected status '" + parsed.status() + "'");
        }
        String pnrCode = parsed.reservation().pnrCode();
        if (pnrCode == null || pnrCode.isBlank()) {
            throw new DriftDetectedException("Create-reservation response has a blank/missing PNR code");
        }
    }

    // PLACEHOLDER: invented wire shape; replace with the real captured
    // response DTO once authorized sandbox data is available.
    private record PlaceholderCreateReservationResponse(
            @JsonProperty("status") String status,
            @JsonProperty("reservation") ReservationSection reservation) {
    }

    private record ReservationSection(@JsonProperty("pnrCode") String pnrCode) {
    }
}
