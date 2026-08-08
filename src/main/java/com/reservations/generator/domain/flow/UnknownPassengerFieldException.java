package com.reservations.generator.domain.flow;

import com.reservations.generator.domain.model.FlowDefinition;

/**
 * Thrown when a passenger payload contains a field that is not part of the
 * strict schema registered for its flow. Unknown fields are always rejected,
 * never silently dropped.
 */
public final class UnknownPassengerFieldException extends RuntimeException {

    private final String flowId;
    private final String schemaVersion;
    private final String fieldName;

    public UnknownPassengerFieldException(FlowDefinition flow, String fieldName, Throwable cause) {
        super("Unknown passenger field '" + fieldName + "' for flowId='" + flow.flowId()
                + "', schemaVersion='" + flow.schemaVersion() + "'", cause);
        this.flowId = flow.flowId();
        this.schemaVersion = flow.schemaVersion();
        this.fieldName = fieldName;
    }

    public String getFlowId() {
        return flowId;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getFieldName() {
        return fieldName;
    }
}
