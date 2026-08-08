package com.reservations.generator.web;

import com.reservations.generator.config.WebUiProperties;
import com.reservations.generator.domain.flow.FlowRegistry;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.web.view.FormView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Renders the reservation page and its passenger-row fragment for the
 * default flow (see {@link WebUiProperties}), via real Thymeleaf view
 * resolution (see {@code templates/reservation-page.html},
 * {@code templates/fragments/passenger-row.html}) — the branded rendering
 * this class's earlier {@code @ResponseBody} skeleton was always meant to be
 * replaced by (see the Phase 3 apply-progress "Deviations" note).
 *
 * <p>Both routes are rendering-only: neither declares a
 * {@code @PathVariable} and both return {@code String} (a Thymeleaf view
 * name), satisfying {@code arch.CreateOnlyBoundaryTest}'s R3 (GET is allowed
 * only for rendering, never for a lookup-by-identifier).
 */
@Controller
public class ReservationPageController {

    private final FlowRegistry flowRegistry;
    private final WebUiProperties webUiProperties;

    public ReservationPageController(FlowRegistry flowRegistry, WebUiProperties webUiProperties) {
        this.flowRegistry = flowRegistry;
        this.webUiProperties = webUiProperties;
    }

    @GetMapping("/")
    public String showForm(Model model) {
        model.addAttribute("rows", List.of(defaultFormView()));
        model.addAttribute("result", null);
        model.addAttribute("failureRows", List.of());
        model.addAttribute("view", null);
        return "reservation-page";
    }

    @GetMapping("/ui/passenger-row")
    public String passengerRow(@RequestParam(defaultValue = "0") int index, Model model) {
        model.addAttribute("fields", defaultFormView().fields());
        model.addAttribute("rowIndex", index);
        return "fragments/passenger-row";
    }

    private FormView defaultFormView() {
        return FormView.blank(flowRegistry.describePassengerFields(requireDefaultFlow()));
    }

    private FlowDefinition requireDefaultFlow() {
        return flowRegistry.require(webUiProperties.getDefaultFlowId(), webUiProperties.getDefaultSchemaVersion());
    }
}
