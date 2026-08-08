package com.reservations.generator.web;

import com.reservations.generator.config.WebUiProperties;
import com.reservations.generator.domain.CreateReservationsUseCase;
import com.reservations.generator.domain.flow.FlowRegistry;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.ReservationResult;
import com.reservations.generator.web.view.FailureRow;
import com.reservations.generator.web.view.FormView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.Map;

/**
 * Submits one batch of passengers through the same in-process
 * {@link CreateReservationsUseCase} that backs {@code POST /reservations}
 * (design D2) — no second reservation-creation code path.
 *
 * <p>Binds the real HTML form's {@code passengers[N].fieldName} naming
 * convention (see {@code fragments/passenger-row.html},
 * {@link PassengerFormBinding}) — this replaces the JSON {@code
 * SubmitPassengersRequest} body Phase 3's skeleton used, per that phase's
 * documented deviation ("Phase 4 will likely need to adapt or replace this
 * binding once the real form's field-naming scheme is fixed").
 *
 * <p>Renders just the result/error fragment when the request came from htmx
 * ({@code HX-Request} header present, swapped into {@code #ual-result}
 * without a reload); otherwise re-renders the full page (no-JS fallback,
 * same route/contract, per the spec's "Non-Blocking Submission" requirement
 * and the design's no-JS fallback note) with a freshly blank form. See this
 * class's own scope note below on why the no-JS fallback does not attempt
 * to reconstruct per-row submitted values.
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
    public ModelAndView submit(HttpServletRequest request,
                                @RequestHeader(value = HX_REQUEST_HEADER, required = false) String hxRequest) {
        FlowDefinition flow = requireDefaultFlow();
        List<Map<String, Object>> rawRows = PassengerFormBinding.parseRows(request.getParameterMap());
        List<Passenger> passengers = flowRegistry.parsePassengers(flow, rawRows);
        ReservationResult result = createReservationsUseCase.execute(flow, passengers);

        List<FailureRow> failureRows = result.getFailures().stream().map(FailureRow::from).toList();

        boolean fragmentOnly = hxRequest != null;
        if (fragmentOnly) {
            ModelAndView mav = new ModelAndView("fragments/result :: resultSection");
            mav.addObject("result", result);
            mav.addObject("failureRows", failureRows);
            return mav;
        }

        // No-JS fallback: same route/contract, full page instead of a bare
        // fragment. Deliberately renders a freshly BLANK form here rather
        // than reconstructing each submitted row's raw values: unlike the
        // htmx path (where the form DOM is never touched, so entered values
        // survive automatically — see WebControllersIntegrationTest), a real
        // full-page reload has no client-side state left to rely on, and
        // server-side reconstruction of every row's per-field values is
        // deliberately out of scope for this slice (see apply-progress
        // Deviations). The differentiated result/error content itself is
        // still fully rendered either way.
        ModelAndView mav = new ModelAndView("reservation-page");
        mav.addObject("rows", List.of(FormView.blank(flowRegistry.describePassengerFields(flow))));
        mav.addObject("result", result);
        mav.addObject("failureRows", failureRows);
        mav.addObject("view", null);
        return mav;
    }

    private FlowDefinition requireDefaultFlow() {
        return flowRegistry.require(webUiProperties.getDefaultFlowId(), webUiProperties.getDefaultSchemaVersion());
    }
}
