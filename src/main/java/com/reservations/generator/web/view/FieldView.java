package com.reservations.generator.web.view;

import com.reservations.generator.domain.model.FieldKind;
import com.reservations.generator.domain.model.PassengerFieldDescriptor;

import java.util.List;
import java.util.Objects;

/**
 * Renderable view of a single passenger field, derived one-to-one from a
 * {@link PassengerFieldDescriptor}. Kept separate from the domain descriptor
 * so rendering-only concerns (a bound value) never leak into {@code domain}.
 *
 * <p>Deliberately carries no per-field error message. The spec's error
 * feedback is rendered as one shared alert/banner per failure (see {@code
 * web.view.ResultView}, {@code fragments/error.html}) rather than an inline
 * message next to the specific field that caused it: neither a whole-batch
 * failure (e.g. {@code UNKNOWN_FIELD}, caught before any {@code
 * ReservationResult} exists) nor a per-passenger {@code ReservationFailure}
 * carries a field name to attribute the message to, only a passenger index
 * and a message string (see {@code domain.model.ReservationFailure}, {@code
 * api.dto.ErrorResponse}). Wiring true per-field precision would need a
 * larger redesign (propagating a field name through the strict-parsing
 * failure path and threading it back into the specific row's rendering) that
 * is out of scope here — this is an explicit, documented v1 scope decision,
 * not an oversight. An earlier {@code errorMessage()}/{@code hasError()} pair
 * on this type was dead code (nothing ever constructed a {@link FieldView}
 * with an error set) and has been removed accordingly.
 */
public record FieldView(String name, FieldKind kind, boolean required, List<String> allowedValues,
                         String value) {

    public FieldView {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(allowedValues, "allowedValues must not be null");
        allowedValues = List.copyOf(allowedValues);
        value = value == null ? "" : value;
    }

    /**
     * A field view with no bound value yet — the state shown when a form is
     * first rendered.
     */
    public static FieldView blank(PassengerFieldDescriptor descriptor) {
        return new FieldView(descriptor.name(), descriptor.kind(), descriptor.required(),
                descriptor.allowedValues(), "");
    }
}
