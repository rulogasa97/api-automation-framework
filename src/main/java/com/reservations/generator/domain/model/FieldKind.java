package com.reservations.generator.domain.model;

/**
 * Rendering/input-widget category for a {@link PassengerFieldDescriptor},
 * derived from the Java type backing a passenger schema field.
 *
 * <p>{@link #UNSUPPORTED} is the deliberate fallback for any Java type this
 * enum does not (yet) recognize, so an unexpected field type degrades to a
 * plain text input rather than breaking form rendering.
 */
public enum FieldKind {
    TEXT,
    NUMBER,
    DATE,
    BOOLEAN,
    ENUM,
    UNSUPPORTED
}
