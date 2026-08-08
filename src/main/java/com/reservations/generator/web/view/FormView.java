package com.reservations.generator.web.view;

import com.reservations.generator.domain.model.PassengerFieldDescriptor;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Renderable view of a whole passenger row/form: one {@link FieldView} per
 * {@link PassengerFieldDescriptor}, in schema order. This is the single
 * place a driving adapter turns "what the flow's schema declares" into
 * "what gets rendered" — never a hand-maintained field list, satisfying the
 * spec's zero-hardcoded-field-name requirement.
 */
public record FormView(List<FieldView> fields) {

    public FormView {
        Objects.requireNonNull(fields, "fields must not be null");
        fields = List.copyOf(fields);
    }

    public static FormView blank(List<PassengerFieldDescriptor> descriptors) {
        return new FormView(descriptors.stream().map(FieldView::blank).collect(Collectors.toList()));
    }
}
