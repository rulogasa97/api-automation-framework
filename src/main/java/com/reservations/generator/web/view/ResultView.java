package com.reservations.generator.web.view;

import com.reservations.generator.domain.ErrorCode;
import com.reservations.generator.domain.OrphanRisk;

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
 */
public record ResultView(Treatment treatment, boolean retryAllowed, String message) {

    public enum Treatment {
        INLINE,
        BANNER
    }

    public ResultView {
        Objects.requireNonNull(treatment, "treatment must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static ResultView from(ErrorCode errorCode, OrphanRisk sideEffect, String message) {
        Treatment treatment = switch (errorCode) {
            case VALIDATION_FAILED, UNKNOWN_FIELD -> Treatment.INLINE;
            case SESSION_UNAVAILABLE, UPSTREAM_TIMEOUT, DRIFT_DETECTED, INTERNAL_ERROR -> Treatment.BANNER;
        };
        boolean retryAllowed = sideEffect != OrphanRisk.POSSIBLE_ORPHAN_RESERVATION;
        return new ResultView(treatment, retryAllowed, message);
    }
}
