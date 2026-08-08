package com.reservations.generator.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Wire-level request body for {@code POST /reservations}.
 *
 * <p>{@code flowId} and {@code schemaVersion} identify the
 * {@link com.reservations.generator.domain.model.FlowDefinition} to use; both
 * are required before any upstream call is attempted. {@code passengers} is
 * kept as raw, opaque JSON objects on purpose: the wire-level envelope does
 * not know (and must not assume) any particular passenger shape. Per-flow,
 * strict schema validation (including unknown-field rejection) is delegated
 * to {@link com.reservations.generator.domain.flow.FlowRegistry} once the
 * flow has been resolved, rather than duplicated here.
 */
public record CreateReservationsRequest(
        @NotBlank(message = "flowId must not be blank") String flowId,
        @NotBlank(message = "schemaVersion must not be blank") String schemaVersion,
        @NotEmpty(message = "passengers must not be empty")
        @Valid
        List<@NotNull(message = "a passenger entry must not be null") Map<String, Object>> passengers) {
}
