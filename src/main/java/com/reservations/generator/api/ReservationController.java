package com.reservations.generator.api;

import com.reservations.generator.api.dto.CreateReservationsRequest;
import com.reservations.generator.api.dto.ReservationResponse;
import com.reservations.generator.domain.CreateReservationsUseCase;
import com.reservations.generator.domain.flow.FlowRegistry;
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

import java.util.List;

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
 * resolved, {@link FlowRegistry#parsePassengers} strictly re-validates each
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

    public ReservationController(FlowRegistry flowRegistry,
                                  CreateReservationsUseCase createReservationsUseCase) {
        this.flowRegistry = flowRegistry;
        this.createReservationsUseCase = createReservationsUseCase;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationsRequest request,
                                                        HttpServletRequest httpRequest) {
        httpRequest.setAttribute(FLOW_ID_REQUEST_ATTRIBUTE, request.flowId());

        FlowDefinition flow = flowRegistry.require(request.flowId(), request.schemaVersion());
        List<Passenger> passengers = flowRegistry.parsePassengers(flow, request.passengers());
        ReservationResult result = createReservationsUseCase.execute(flow, passengers);

        return ResponseEntity.ok(ReservationResponse.from(result));
    }
}
