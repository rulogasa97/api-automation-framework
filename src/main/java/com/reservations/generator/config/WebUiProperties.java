package com.reservations.generator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code reservations.web.*}: the default flow the web UI resolves at
 * startup via {@link com.reservations.generator.domain.flow.FlowRegistry#require},
 * per design D6. Deliberately config-driven rather than "pick the only
 * registered flow": once a second flow is registered, silently guessing
 * would be ambiguous.
 */
@ConfigurationProperties(prefix = "reservations.web")
public class WebUiProperties {

    private String defaultFlowId = "ual-create-v1";
    private String defaultSchemaVersion = "1";

    public String getDefaultFlowId() {
        return defaultFlowId;
    }

    public void setDefaultFlowId(String defaultFlowId) {
        this.defaultFlowId = defaultFlowId;
    }

    public String getDefaultSchemaVersion() {
        return defaultSchemaVersion;
    }

    public void setDefaultSchemaVersion(String defaultSchemaVersion) {
        this.defaultSchemaVersion = defaultSchemaVersion;
    }
}
