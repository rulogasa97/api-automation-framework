package com.reservations.generator.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Renderable description of a single field on a flow's passenger schema,
 * derived by {@link com.reservations.generator.domain.flow.FlowRegistry}
 * from the same strict Jackson introspection used to parse passenger
 * payloads. This is the single source of truth a driving adapter (e.g. the
 * web UI) uses to render an input for the field — it is never hand-declared
 * separately, so a rendered field and an accepted field can never drift
 * apart.
 *
 * @param name          the wire-level property name (matches the JSON field
 *                       strict parsing accepts/rejects)
 * @param kind           the input-widget category for this field
 * @param required      whether the schema requires this field to be present
 * @param allowedValues the fixed set of accepted values (only non-empty for
 *                      {@link FieldKind#ENUM}); empty for every other kind
 */
public record PassengerFieldDescriptor(String name, FieldKind kind, boolean required, List<String> allowedValues) {

    public PassengerFieldDescriptor {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(allowedValues, "allowedValues must not be null");
        allowedValues = List.copyOf(allowedValues);
    }
}
