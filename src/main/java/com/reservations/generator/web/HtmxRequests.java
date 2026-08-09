package com.reservations.generator.web;

/**
 * Shared constant for the htmx-specific request header the {@code web}
 * driving adapter uses to decide between rendering a bare fragment (an htmx
 * swap) and a full page (no-JS fallback) — see {@link ReservationFormController}
 * and {@link WebExceptionHandler}, both of which key off it. Extracted here
 * so it is declared exactly once instead of independently in each class.
 */
final class HtmxRequests {

    static final String HX_REQUEST_HEADER = "HX-Request";

    private HtmxRequests() {
    }
}
