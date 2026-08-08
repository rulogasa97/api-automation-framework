package com.reservations.generator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code reservations.sandbox.*}. Validated at startup by
 * {@link SandboxHostGuard} to guarantee {@link #baseUrl} can never point at
 * a real {@code united.com} host (REQ-8: sandbox-only, never production).
 */
@ConfigurationProperties(prefix = "reservations.sandbox")
public class SandboxProperties {

    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
