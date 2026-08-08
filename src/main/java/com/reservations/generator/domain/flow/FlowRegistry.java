package com.reservations.generator.domain.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.reservations.generator.domain.model.FieldKind;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.PassengerFieldDescriptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private static final Set<Class<?>> NUMBER_TYPES = Set.of(
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            BigDecimal.class, BigInteger.class);

    private static final Set<Class<?>> DATE_TYPES = Set.of(
            LocalDate.class, LocalDateTime.class, OffsetDateTime.class, Date.class);

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

    /**
     * Derives the renderable field metadata for a flow's passenger schema by
     * introspecting it with the same {@code STRICT_MAPPER} used to parse
     * passenger payloads, so a rendered field can never drift from an
     * accepted field: both come from the same source.
     */
    public List<PassengerFieldDescriptor> describePassengerFields(FlowDefinition flow) {
        JavaType schemaType = STRICT_MAPPER.constructType(flow.passengerSchema());
        BeanDescription beanDescription = STRICT_MAPPER.getDeserializationConfig().introspect(schemaType);

        List<PassengerFieldDescriptor> descriptors = new ArrayList<>();
        for (BeanPropertyDefinition property : beanDescription.findProperties()) {
            descriptors.add(describeField(property));
        }
        return descriptors;
    }

    private PassengerFieldDescriptor describeField(BeanPropertyDefinition property) {
        Class<?> rawType = property.getRawPrimaryType();
        FieldKind kind = kindFor(rawType);
        List<String> allowedValues = kind == FieldKind.ENUM ? enumValueNames(rawType) : List.of();
        return new PassengerFieldDescriptor(property.getName(), kind, property.isRequired(), allowedValues);
    }

    private static FieldKind kindFor(Class<?> rawType) {
        if (rawType == String.class) {
            return FieldKind.TEXT;
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return FieldKind.BOOLEAN;
        }
        if (rawType.isEnum()) {
            return FieldKind.ENUM;
        }
        if (NUMBER_TYPES.contains(rawType)) {
            return FieldKind.NUMBER;
        }
        if (DATE_TYPES.contains(rawType)) {
            return FieldKind.DATE;
        }
        return FieldKind.UNSUPPORTED;
    }

    private static List<String> enumValueNames(Class<?> enumType) {
        List<String> names = new ArrayList<>();
        for (Object constant : enumType.getEnumConstants()) {
            names.add(((Enum<?>) constant).name());
        }
        return names;
    }

    /**
     * Strictly parses every raw passenger map in {@code rawPassengers}
     * against the flow's schema, in submission order. This is the single
     * binding path a driving adapter should use — it owns the
     * map-&gt;JSON-&gt;typed-instance relay so no adapter reimplements
     * strict parsing independently.
     */
    public List<Passenger> parsePassengers(FlowDefinition flow, List<Map<String, Object>> rawPassengers) {
        List<Passenger> passengers = new ArrayList<>(rawPassengers.size());
        for (Map<String, Object> raw : rawPassengers) {
            passengers.add(parsePassengerFromRawMap(flow, raw));
        }
        return passengers;
    }

    private Passenger parsePassengerFromRawMap(FlowDefinition flow, Map<String, Object> raw) {
        String rawJson = toRawJson(flow, raw);
        Object parsed = parsePassenger(flow, rawJson);
        if (!(parsed instanceof Passenger passenger)) {
            // Cannot happen with the currently registered flow(s), whose
            // passenger schema is always Passenger.class, but fail loudly
            // rather than silently miscasting if that ever changes.
            throw new IllegalStateException(
                    "Flow '" + flow.flowId() + "' registered an unsupported passenger schema type: "
                            + (parsed == null ? "null" : parsed.getClass().getName()));
        }
        return passenger;
    }

    private String toRawJson(FlowDefinition flow, Map<String, Object> raw) {
        try {
            return STRICT_MAPPER.writeValueAsString(raw);
        } catch (JsonProcessingException e) {
            // The raw value came from the JSON body itself, so re-serializing
            // it back to JSON cannot realistically fail; treated as an
            // invalid-payload case for consistency rather than a 500.
            throw new InvalidPassengerPayloadException(flow, e);
        }
    }
}
