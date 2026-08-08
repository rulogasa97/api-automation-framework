package com.reservations.generator.domain.flow;

/**
 * Thrown when a (flowId, schemaVersion) pair has no registered
 * {@link com.reservations.generator.domain.model.FlowDefinition}.
 */
public final class UnknownFlowException extends RuntimeException {

    public UnknownFlowException(String flowId, String schemaVersion) {
        super("No flow registered for flowId='" + flowId + "', schemaVersion='" + schemaVersion + "'");
    }
}
