package com.reservations.generator.domain.flow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.reservations.generator.domain.model.FlowDefinition;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of supported {@link FlowDefinition}s, keyed by
 * (flowId, schemaVersion), and the strict passenger-payload parser for each.
 *
 * <p>Parsing always uses {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}
 * so unknown fields are rejected rather than silently dropped.
 */
public final class FlowRegistry {

    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private final Map<String, FlowDefinition> definitions = new ConcurrentHashMap<>();

    public void register(FlowDefinition definition) {
        definitions.put(definition.key(), definition);
    }

    public Optional<FlowDefinition> find(String flowId, String schemaVersion) {
        return Optional.ofNullable(definitions.get(FlowDefinition.key(flowId, schemaVersion)));
    }

    public FlowDefinition require(String flowId, String schemaVersion) {
        return find(flowId, schemaVersion)
                .orElseThrow(() -> new UnknownFlowException(flowId, schemaVersion));
    }

    /**
     * Parses raw passenger JSON against the strict schema registered for the
     * given flow. Any field not present in the schema causes a rejection;
     * it is never silently dropped.
     */
    public Object parsePassenger(FlowDefinition flow, String rawJson) {
        try {
            return STRICT_MAPPER.readValue(rawJson, flow.passengerSchema());
        } catch (UnrecognizedPropertyException e) {
            throw new UnknownPassengerFieldException(flow, e.getPropertyName(), e);
        } catch (IOException e) {
            throw new InvalidPassengerPayloadException(flow, e);
        }
    }
}
