package com.reservations.generator.web.view;

import com.reservations.generator.domain.model.FieldKind;
import com.reservations.generator.domain.model.PassengerFieldDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FormView#blank}: proves the form's field list is
 * derived one-to-one, in order, from the flow's
 * {@link PassengerFieldDescriptor} list — the mechanism behind the spec's
 * "zero hardcoded field name" schema-driven rendering requirement.
 */
class FormViewTest {

    @Test
    void blankProducesOneFieldViewPerDescriptorInOrder() {
        List<PassengerFieldDescriptor> descriptors = List.of(
                new PassengerFieldDescriptor("name", FieldKind.TEXT, true, List.of()),
                new PassengerFieldDescriptor("age", FieldKind.NUMBER, false, List.of()));

        FormView form = FormView.blank(descriptors);

        assertThat(form.fields()).hasSize(2);
        assertThat(form.fields().get(0).name()).isEqualTo("name");
        assertThat(form.fields().get(1).name()).isEqualTo("age");
    }

    @Test
    void blankOnAnEmptySchemaProducesNoFields() {
        FormView form = FormView.blank(List.of());

        assertThat(form.fields()).isEmpty();
    }
}
