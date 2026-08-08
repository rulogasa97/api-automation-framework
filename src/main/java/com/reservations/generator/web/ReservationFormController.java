package com.reservations.generator.web;

import com.reservations.generator.config.WebUiProperties;
import com.reservations.generator.domain.CreateReservationsUseCase;
import com.reservations.generator.domain.flow.FlowRegistry;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.ReservationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

/**
 * Submits one batch of passengers through the same in-process
 * {@link CreateReservationsUseCase} that backs {@code POST /reservations}
 * (design D2) — no second reservation-creation code path.
 *
 * <p>Renders a result fragment only when the request came from htmx
 * ({@code HX-Request} header present); otherwise wraps the same fragment in
 * a minimal full-page shell, satisfying the spec's no-JS fallback
 * requirement with a single route/contract. This is a skeleton response
 * body (unbranded); real Thymeleaf rendering lands with the templates in a
 * later slice.
 */
@Controller
public class ReservationFormController {

    static final String HX_REQUEST_HEADER = "HX-Request";

    private final FlowRegistry flowRegistry;
    private final CreateReservationsUseCase createReservationsUseCase;
    private final WebUiProperties webUiProperties;

    public ReservationFormController(FlowRegistry flowRegistry,
                                      CreateReservationsUseCase createReservationsUseCase,
                                      WebUiProperties webUiProperties) {
        this.flowRegistry = flowRegistry;
        this.createReservationsUseCase = createReservationsUseCase;
        this.webUiProperties = webUiProperties;
    }

    @PostMapping("/ui/reservations")
    public ResponseEntity<String> submit(@RequestBody SubmitPassengersRequest request,
                                          @RequestHeader(value = HX_REQUEST_HEADER, required = false) String hxRequest) {
        FlowDefinition flow = flowRegistry.require(
                webUiProperties.getDefaultFlowId(), webUiProperties.getDefaultSchemaVersion());
        List<Passenger> passengers = flowRegistry.parsePassengers(flow, request.passengers());
        ReservationResult result = createReservationsUseCase.execute(flow, passengers);

        boolean fragmentOnly = hxRequest != null;
        return ResponseEntity.ok(render(result, fragmentOnly));
    }

    private static String render(ReservationResult result, boolean fragmentOnly) {
        StringBuilder body = new StringBuilder();
        if (!fragmentOnly) {
            body.append("<html><body>");
        }
        body.append("<section data-fragment=\"result\">");
        result.getPnrs().forEach(pnr -> body.append("<span data-pnr=\"").append(pnr.getCode()).append("\"></span>"));
        result.getFailures().forEach(failure -> body.append("<span data-failure-index=\"")
                .append(failure.getPassengerIndex()).append("\"></span>"));
        body.append("</section>");
        if (!fragmentOnly) {
            body.append("</body></html>");
        }
        return body.toString();
    }

    /**
     * Raw passenger rows submitted by the form, in the same shape
     * {@link FlowRegistry#parsePassengers} accepts. No {@code flowId}/
     * {@code schemaVersion} here: the web UI never lets a caller pick a flow
     * (design D6), unlike {@code api.dto.CreateReservationsRequest}.
     */
    public record SubmitPassengersRequest(List<Map<String, Object>> passengers) {
    }
}
