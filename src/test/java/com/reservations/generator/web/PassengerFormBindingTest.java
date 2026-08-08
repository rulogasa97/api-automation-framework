package com.reservations.generator.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PassengerFormBinding#parseRows}: proves the
 * {@code passengers[N].fieldName} form-encoded naming convention (used by
 * {@code fragments/passenger-row.html}) is parsed back into the same
 * {@code List<Map<String,Object>>} shape {@link
 * com.reservations.generator.domain.flow.FlowRegistry#parsePassengers}
 * already accepts, with no Spring context involved.
 */
class PassengerFormBindingTest {

    @Test
    void oneRowWithOneFieldProducesOneMapEntry() {
        Map<String, String[]> parameters = Map.of("passengers[0].name", new String[] {"Ada Lovelace"});

        List<Map<String, Object>> rows = PassengerFormBinding.parseRows(parameters);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("name", "Ada Lovelace");
    }

    @Test
    void multipleRowsAreOrderedByIndexRegardlessOfParameterIterationOrder() {
        Map<String, String[]> parameters = Map.of(
                "passengers[1].name", new String[] {"Grace Hopper"},
                "passengers[0].name", new String[] {"Ada Lovelace"});

        List<Map<String, Object>> rows = PassengerFormBinding.parseRows(parameters);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("name", "Ada Lovelace");
        assertThat(rows.get(1)).containsEntry("name", "Grace Hopper");
    }

    @Test
    void nonPassengerParametersAreIgnored() {
        Map<String, String[]> parameters = Map.of(
                "passengers[0].name", new String[] {"Ada Lovelace"},
                "unrelatedParam", new String[] {"noise"});

        List<Map<String, Object>> rows = PassengerFormBinding.parseRows(parameters);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsOnlyKeys("name");
    }

    @Test
    void emptyParameterMapProducesNoRows() {
        List<Map<String, Object>> rows = PassengerFormBinding.parseRows(Map.of());

        assertThat(rows).isEmpty();
    }
}
