package com.reservations.generator.apireplay;

import com.reservations.generator.domain.DriftDetectedException;
import com.reservations.generator.domain.OrphanRisk;
import com.reservations.generator.domain.model.Pnr;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PnrExtractor} against the invented placeholder
 * response shape (see {@link PnrExtractor}'s Javadoc {@code @apiNote}).
 */
class PnrExtractorTest {

    private final PnrExtractor extractor = new PnrExtractor();

    @Test
    void extractsPnrFromWellFormedPlaceholderResponse() {
        String responseBody = """
                {"status":"CONFIRMED","reservation":{"pnrCode":"ABC123"}}""";

        Pnr pnr = extractor.extract(responseBody);

        assertThat(pnr).isEqualTo(new Pnr("ABC123"));
    }

    @Test
    void throwsDriftDetectedExceptionOnMalformedJson() {
        assertThatThrownBy(() -> extractor.extract("not-json"))
                .isInstanceOf(DriftDetectedException.class)
                .satisfies(e -> assertThat(((DriftDetectedException) e).getOrphanRisk())
                        .isEqualTo(OrphanRisk.POSSIBLE_ORPHAN_RESERVATION));
    }

    @Test
    void throwsDriftDetectedExceptionOnUnknownField() {
        String responseBody = """
                {"status":"CONFIRMED","reservation":{"pnrCode":"ABC123"},"unexpectedField":true}""";

        assertThatThrownBy(() -> extractor.extract(responseBody))
                .isInstanceOf(DriftDetectedException.class);
    }

    @Test
    void throwsDriftDetectedExceptionOnMissingReservationSection() {
        String responseBody = """
                {"status":"CONFIRMED"}""";

        assertThatThrownBy(() -> extractor.extract(responseBody))
                .isInstanceOf(DriftDetectedException.class);
    }

    @Test
    void throwsDriftDetectedExceptionOnUnexpectedStatus() {
        String responseBody = """
                {"status":"PENDING","reservation":{"pnrCode":"ABC123"}}""";

        assertThatThrownBy(() -> extractor.extract(responseBody))
                .isInstanceOf(DriftDetectedException.class);
    }

    @Test
    void throwsDriftDetectedExceptionOnBlankPnrCode() {
        String responseBody = """
                {"status":"CONFIRMED","reservation":{"pnrCode":""}}""";

        assertThatThrownBy(() -> extractor.extract(responseBody))
                .isInstanceOf(DriftDetectedException.class);
    }
}
