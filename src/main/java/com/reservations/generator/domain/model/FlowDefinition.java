package com.reservations.generator.domain.model;

import java.util.Objects;

/**
 * Identifies a supported reservation-creation flow and the strict schema
 * used to validate/parse its passenger payloads.
 *
 * <p>A flow is uniquely keyed by ({@code flowId}, {@code schemaVersion}).
 * Introducing a new passenger field means introducing a new schema version
 * (and, typically, a new passenger schema class) rather than loosening the
 * existing strict schema.
 */
public final class FlowDefinition {

    private final String flowId;
    private final String schemaVersion;
    private final Class<?> passengerSchema;

    public FlowDefinition(String flowId, String schemaVersion, Class<?> passengerSchema) {
        this.flowId = requireNonBlank(flowId, "flowId");
        this.schemaVersion = requireNonBlank(schemaVersion, "schemaVersion");
        this.passengerSchema = Objects.requireNonNull(passengerSchema, "passengerSchema");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public String flowId() {
        return flowId;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public Class<?> passengerSchema() {
        return passengerSchema;
    }

    public String key() {
        return key(flowId, schemaVersion);
    }

    public static String key(String flowId, String schemaVersion) {
        return flowId + "@" + schemaVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FlowDefinition that)) {
            return false;
        }
        return flowId.equals(that.flowId) && schemaVersion.equals(that.schemaVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowId, schemaVersion);
    }

    @Override
    public String toString() {
        return "FlowDefinition{flowId='" + flowId + "', schemaVersion='" + schemaVersion + "'}";
    }
}
