package com.reservations.generator.web.view;

import com.reservations.generator.domain.DriftDetectedException;
import com.reservations.generator.domain.ErrorCode;
import com.reservations.generator.domain.OrphanRisk;
import com.reservations.generator.domain.model.ReservationFailure;
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

    /**
     * The three banner-level codes the spec's "Error-Code-Specific
     * Rendering" requirement calls out (UPSTREAM_TIMEOUT, DRIFT_DETECTED,
     * POSSIBLE_ORPHAN_RESERVATION) must each be visually distinguishable.
     * {@code errorCode()}/{@code cssVariant()} are the mechanism the error
     * fragment template hooks a distinct CSS class onto — proven here as a
     * pure Java computation, no template/Spring involved.
     */
    @Test
    void errorCodeIsExposedForTemplateStyling() {
        ResultView view = ResultView.from(ErrorCode.UPSTREAM_TIMEOUT, OrphanRisk.NONE, "upstream call did not complete");

        assertThat(view.errorCode()).isEqualTo(ErrorCode.UPSTREAM_TIMEOUT);
    }

    @Test
    void cssVariantIsAKebabCaseVersionOfTheErrorCode() {
        ResultView timeout = ResultView.from(ErrorCode.UPSTREAM_TIMEOUT, OrphanRisk.NONE, "msg");
        ResultView drift = ResultView.from(ErrorCode.DRIFT_DETECTED, OrphanRisk.POSSIBLE_ORPHAN_RESERVATION, "msg");

        assertThat(timeout.cssVariant()).isEqualTo("upstream-timeout");
        assertThat(drift.cssVariant()).isEqualTo("drift-detected");
        assertThat(timeout.cssVariant()).isNotEqualTo(drift.cssVariant());
    }

    /**
     * A per-passenger {@link ReservationFailure} (e.g. one row's create
     * dispatch drifted while other rows in the same batch succeeded) must
     * get the exact same differentiated banner treatment as a whole-batch
     * failure — the spec's "Error-Code-Specific Rendering" requirement does
     * not carve out an exception for partial-batch results.
     */
    @Test
    void forFailureClassifiesAPerPassengerFailureTheSameWayAsAWholeBatchOne() {
        DriftDetectedException cause = new DriftDetectedException("response shape drifted");
        ReservationFailure failure = new ReservationFailure(
                1, "response shape drifted", OrphanRisk.POSSIBLE_ORPHAN_RESERVATION, cause);

        ResultView view = ResultView.forFailure(failure);

        assertThat(view.treatment()).isEqualTo(ResultView.Treatment.BANNER);
        assertThat(view.errorCode()).isEqualTo(ErrorCode.DRIFT_DETECTED);
        assertThat(view.retryAllowed()).isFalse();
        assertThat(view.message()).isEqualTo("response shape drifted");
    }

    @Test
    void forFailureWithNoOrphanRiskAllowsRetry() {
        ReservationFailure failure = new ReservationFailure(0, "upstream timed out", OrphanRisk.NONE, null);

        ResultView view = ResultView.forFailure(failure);

        assertThat(view.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(view.retryAllowed()).isTrue();
    }
}
