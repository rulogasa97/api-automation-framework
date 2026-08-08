package com.reservations.generator.domain.flow;

import com.reservations.generator.domain.model.FlowDefinition;

/**
 * Thrown when a passenger payload cannot be parsed against its flow's
 * strict schema for a reason other than an unknown field (malformed JSON,
 * type mismatch, missing required field, etc.).
 */
public final class InvalidPassengerPayloadException extends RuntimeException {

    private final String flowId;
    private final String schemaVersion;

    public InvalidPassengerPayloadException(FlowDefinition flow, Throwable cause) {
        super("Invalid passenger payload for flowId='" + flow.flowId()
                + "', schemaVersion='" + flow.schemaVersion() + "': " + cause.getMessage(), cause);
        this.flowId = flow.flowId();
        this.schemaVersion = flow.schemaVersion();
    }

    public String getFlowId() {
        return flowId;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }
}
