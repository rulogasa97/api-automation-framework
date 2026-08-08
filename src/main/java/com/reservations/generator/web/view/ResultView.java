package com.reservations.generator.web.view;

import com.reservations.generator.domain.ErrorCode;
import com.reservations.generator.domain.OrphanRisk;
import com.reservations.generator.domain.ReservationFailureClassifier;
import com.reservations.generator.domain.model.ReservationFailure;

import java.util.Locale;
import java.util.Objects;

/**
 * Renders how a whole-batch or per-passenger failure should be presented in
 * the web UI, derived purely from {@link ErrorCode} and {@link OrphanRisk}
 * per the spec's "Error-Code-Specific Rendering" requirement:
 * {@code VALIDATION_FAILED}/{@code UNKNOWN_FIELD} are field-level
 * ({@link Treatment#INLINE}); every other code is a visually distinct,
 * non-field-level {@link Treatment#BANNER}. Any failure carrying
 * {@link OrphanRisk#POSSIBLE_ORPHAN_RESERVATION} must never offer a
 * one-click retry affordance ({@link #retryAllowed()} is {@code false}).
 *
 * <p>{@link #errorCode()}/{@link #cssVariant()} additionally let the error
 * fragment template give {@code UPSTREAM_TIMEOUT}, {@code DRIFT_DETECTED}
 * and {@code POSSIBLE_ORPHAN_RESERVATION} each their own distinct visual
 * treatment, not just a shared generic banner — computed here in plain Java
 * rather than as template string logic, so it stays unit-testable.
 */
public record ResultView(Treatment treatment, boolean retryAllowed, String message, ErrorCode errorCode) {

    public enum Treatment {
        INLINE,
        BANNER
    }

    public ResultView {
        Objects.requireNonNull(treatment, "treatment must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public static ResultView from(ErrorCode errorCode, OrphanRisk sideEffect, String message) {
        Treatment treatment = switch (errorCode) {
            case VALIDATION_FAILED, UNKNOWN_FIELD -> Treatment.INLINE;
            case SESSION_UNAVAILABLE, UPSTREAM_TIMEOUT, DRIFT_DETECTED, INTERNAL_ERROR -> Treatment.BANNER;
        };
        boolean retryAllowed = sideEffect != OrphanRisk.POSSIBLE_ORPHAN_RESERVATION;
        return new ResultView(treatment, retryAllowed, message, errorCode);
    }

    /**
     * Classifies a single per-passenger {@link ReservationFailure} (a batch
     * can partially succeed — see {@code ReservationResult}) with the exact
     * same rules as a whole-batch failure ({@link #from}), so
     * {@code fragments/result.html} can render each failed row with the same
     * differentiated banner/no-retry treatment as
     * {@code fragments/error.html} uses for a whole-batch failure. Reuses
     * {@link ReservationFailureClassifier#classifyPerPassenger}, never a
     * separate/duplicated mapping.
     */
    public static ResultView forFailure(ReservationFailure failure) {
        ErrorCode errorCode = ReservationFailureClassifier.classifyPerPassenger(failure.getCause());
        return from(errorCode, failure.getOrphanRisk(), failure.getMessage());
    }

    /**
     * Kebab-case rendering of {@link #errorCode()} (e.g. {@code
     * UPSTREAM_TIMEOUT} -&gt; {@code "upstream-timeout"}), used by the error
     * fragment template as a CSS class suffix (e.g. {@code
     * ual-banner--upstream-timeout}) so each banner-level code gets its own
     * distinct styling hook.
     */
    public String cssVariant() {
        return errorCode.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
