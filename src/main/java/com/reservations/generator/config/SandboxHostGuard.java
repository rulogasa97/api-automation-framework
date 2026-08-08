package com.reservations.generator.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Fails application startup if {@code reservations.sandbox.base-url} looks
 * like it targets a real {@code united.com} host.
 *
 * <p>This is a hard infrastructure-level guard, not just a code-review
 * convention: this project must never dispatch any request to a real United
 * production system (see {@code CreateReservationReplay}'s Javadoc and
 * REQ-8). A misconfigured environment variable pointing at a real host is
 * exactly the kind of mistake this guard exists to catch before a single
 * HTTP call can be made.
 */
@Component
public class SandboxHostGuard {

    // Deliberately references the real production domain as the comparison
    // target for this safety guard: this is the one legitimate place in the
    // codebase where "united.com" names the host we must reject, not a host
    // we target.
    private static final String FORBIDDEN_PRODUCTION_HOST_SUFFIX = "united.com";

    private final SandboxProperties sandboxProperties;

    public SandboxHostGuard(SandboxProperties sandboxProperties) {
        this.sandboxProperties = sandboxProperties;
    }

    @PostConstruct
    void validateSandboxBaseUrlIsNeverProduction() {
        String baseUrl = sandboxProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("reservations.sandbox.base-url must be configured");
        }

        String host = extractHost(baseUrl);
        if (isForbiddenProductionHost(host)) {
            throw new IllegalStateException(
                    "reservations.sandbox.base-url must never target a real united.com host; got: " + baseUrl);
        }
    }

    private static String extractHost(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("reservations.sandbox.base-url is not a valid URL: " + baseUrl, e);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("reservations.sandbox.base-url must include a host: " + baseUrl);
        }
        return host;
    }

    private static boolean isForbiddenProductionHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals(FORBIDDEN_PRODUCTION_HOST_SUFFIX)
                || normalizedHost.endsWith("." + FORBIDDEN_PRODUCTION_HOST_SUFFIX);
    }
}
