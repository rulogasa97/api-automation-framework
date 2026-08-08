package com.reservations.generator.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservations.generator.api.dto.CreateReservationsRequest;
import com.reservations.generator.api.dto.ReservationResponse;
import com.reservations.generator.domain.CreateReservationsUseCase;
import com.reservations.generator.domain.flow.FlowRegistry;
import com.reservations.generator.domain.flow.InvalidPassengerPayloadException;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.ReservationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP edge for reservation creation.
 *
 * <p>This is deliberately a create-only surface: there is no lookup, cancel,
 * or list-my-reservations route anywhere in {@code api}, matching the
 * project's synchronous, fire-and-record batch-creation scope (see
 * {@code arch.CreateOnlyBoundaryTest}, which enforces this at the test
 * level).
 *
 * <p>Validation happens in two stages: Bean Validation ({@code @Valid})
 * enforces the request envelope's required fields, then, once the flow is
 * resolved, {@link FlowRegistry#parsePassenger} strictly re-validates each
 * passenger payload against that flow's schema (rejecting unknown fields
 * rather than silently dropping them). Any failure at either stage is
 * translated into an {@link com.reservations.generator.api.dto.ErrorResponse}
 * by {@link GlobalExceptionHandler} via {@link ErrorMapper}, never partially
 * handled here.
 */
@RestController
@RequestMapping("/reservations")
public class ReservationController {

    /**
     * Name of the {@link HttpServletRequest} attribute used to hand the
     * requested flowId to {@link GlobalExceptionHandler} for exceptions
     * raised after Bean Validation has already succeeded (i.e. everything
     * except {@link org.springframework.web.bind.MethodArgumentNotValidException},
     * which instead reads the flowId directly off its (partially bound)
     * target object).
     */
    static final String FLOW_ID_REQUEST_ATTRIBUTE = ReservationController.class.getName() + ".flowId";

    private final FlowRegistry flowRegistry;
    private final CreateReservationsUseCase createReservationsUseCase;
    private final ObjectMapper objectMapper;

    public ReservationController(FlowRegistry flowRegistry,
                                  CreateReservationsUseCase createReservationsUseCase,
                                  ObjectMapper objectMapper) {
        this.flowRegistry = flowRegistry;
        this.createReservationsUseCase = createReservationsUseCase;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationsRequest request,
                                                        HttpServletRequest httpRequest) {
        httpRequest.setAttribute(FLOW_ID_REQUEST_ATTRIBUTE, request.flowId());

        FlowDefinition flow = flowRegistry.require(request.flowId(), request.schemaVersion());
        List<Passenger> passengers = parsePassengers(flow, request.passengers());
        ReservationResult result = createReservationsUseCase.execute(flow, passengers);

        return ResponseEntity.ok(ReservationResponse.from(result));
    }

    private List<Passenger> parsePassengers(FlowDefinition flow, List<Map<String, Object>> rawPassengers) {
        List<Passenger> passengers = new ArrayList<>(rawPassengers.size());
        for (Map<String, Object> raw : rawPassengers) {
            passengers.add(parsePassenger(flow, raw));
        }
        return passengers;
    }

    private Passenger parsePassenger(FlowDefinition flow, Map<String, Object> raw) {
        String rawJson = toRawJson(flow, raw);
        Object parsed = flowRegistry.parsePassenger(flow, rawJson);
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
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException e) {
            // The raw value came from the JSON body itself, so re-serializing
            // it back to JSON cannot realistically fail; treated as an
            // invalid-payload case for consistency rather than a 500.
            throw new InvalidPassengerPayloadException(flow, e);
        }
    }
}
