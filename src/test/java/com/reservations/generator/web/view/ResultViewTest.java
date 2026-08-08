package com.reservations.generator.web.view;

import com.reservations.generator.domain.ErrorCode;
import com.reservations.generator.domain.OrphanRisk;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResultView#from}: proves the spec's
 * "Error-Code-Specific Rendering" requirement purely from
 * {@link ErrorCode}/{@link OrphanRisk} inputs, with no Spring context.
 */
class ResultViewTest {

    @Test
    void validationFailedIsInlineAndAllowsRetry() {
        ResultView view = ResultView.from(ErrorCode.VALIDATION_FAILED, OrphanRisk.NONE, "flowId: must not be blank");

        assertThat(view.treatment()).isEqualTo(ResultView.Treatment.INLINE);
        assertThat(view.retryAllowed()).isTrue();
        assertThat(view.message()).isEqualTo("flowId: must not be blank");
    }

    @Test
    void unknownFieldIsInlineAndAllowsRetry() {
        ResultView view = ResultView.from(ErrorCode.UNKNOWN_FIELD, OrphanRisk.NONE, "unexpected field: frequentFlyerNumber");

        assertThat(view.treatment()).isEqualTo(ResultView.Treatment.INLINE);
        assertThat(view.retryAllowed()).isTrue();
    }

    @Test
    void upstreamTimeoutWithNoOrphanRiskIsBannerAndAllowsRetry() {
        ResultView view = ResultView.from(ErrorCode.UPSTREAM_TIMEOUT, OrphanRisk.NONE, "upstream call did not complete");

        assertThat(view.treatment()).isEqualTo(ResultView.Treatment.BANNER);
        assertThat(view.retryAllowed()).isTrue();
    }

    @Test
    void driftDetectedWithPossibleOrphanReservationIsBannerAndForbidsRetry() {
        ResultView view = ResultView.from(ErrorCode.DRIFT_DETECTED, OrphanRisk.POSSIBLE_ORPHAN_RESERVATION,
                "response shape drifted");

        assertThat(view.treatment()).isEqualTo(ResultView.Treatment.BANNER);
        assertThat(view.retryAllowed()).isFalse();
    }

    @Test
    void sessionUnavailableIsBanner() {
        ResultView view = ResultView.from(ErrorCode.SESSION_UNAVAILABLE, OrphanRisk.NONE, "sandbox session unavailable");

        assertThat(view.treatment()).isEqualTo(ResultView.Treatment.BANNER);
    }

    @Test
    void internalErrorIsBanner() {
        ResultView view = ResultView.from(ErrorCode.INTERNAL_ERROR, OrphanRisk.NONE, "unexpected failure");

        assertThat(view.treatment()).isEqualTo(ResultView.Treatment.BANNER);
    }
}
