package com.reservations.generator.web.view;

import com.reservations.generator.domain.model.FieldKind;
import com.reservations.generator.domain.model.PassengerFieldDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FieldView#blank}: proves a {@link FieldView} is
 * derived one-to-one from a {@link PassengerFieldDescriptor} with no bound
 * value/error yet, and {@link FieldView#hasError()} reflects the presence of
 * an inline message.
 */
class FieldViewTest {

    @Test
    void blankCopiesNameKindAndRequiredFromTheDescriptor() {
        PassengerFieldDescriptor descriptor = new PassengerFieldDescriptor("name", FieldKind.TEXT, true, List.of());

        FieldView view = FieldView.blank(descriptor);

        assertThat(view.name()).isEqualTo("name");
        assertThat(view.kind()).isEqualTo(FieldKind.TEXT);
        assertThat(view.required()).isTrue();
        assertThat(view.value()).isEmpty();
        assertThat(view.hasError()).isFalse();
    }

    @Test
    void blankCopiesAllowedValuesForAnEnumDescriptor() {
        PassengerFieldDescriptor descriptor = new PassengerFieldDescriptor(
                "cabinClass", FieldKind.ENUM, false, List.of("ECONOMY", "BUSINESS"));

        FieldView view = FieldView.blank(descriptor);

        assertThat(view.allowedValues()).containsExactly("ECONOMY", "BUSINESS");
        assertThat(view.required()).isFalse();
    }

    @Test
    void hasErrorIsTrueWhenAnErrorMessageIsPresent() {
        FieldView view = new FieldView("name", FieldKind.TEXT, true, List.of(), "Ada", "must not be blank");

        assertThat(view.hasError()).isTrue();
    }
}
