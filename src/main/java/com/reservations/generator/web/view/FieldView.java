package com.reservations.generator.web.view;

import com.reservations.generator.domain.model.FieldKind;
import com.reservations.generator.domain.model.PassengerFieldDescriptor;

import java.util.List;
import java.util.Objects;

/**
 * Renderable view of a single passenger field, derived one-to-one from a
 * {@link PassengerFieldDescriptor}. Kept separate from the domain descriptor
 * so rendering-only concerns (a bound value, an inline validation message)
 * never leak into {@code domain}.
 */
public record FieldView(String name, FieldKind kind, boolean required, List<String> allowedValues,
                         String value, String errorMessage) {

    public FieldView {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(allowedValues, "allowedValues must not be null");
        allowedValues = List.copyOf(allowedValues);
        value = value == null ? "" : value;
    }

    /**
     * A field view with no bound value and no error yet — the state shown
     * when a form is first rendered.
     */
    public static FieldView blank(PassengerFieldDescriptor descriptor) {
        return new FieldView(descriptor.name(), descriptor.kind(), descriptor.required(),
                descriptor.allowedValues(), "", null);
    }

    public boolean hasError() {
        return errorMessage != null;
    }
}
