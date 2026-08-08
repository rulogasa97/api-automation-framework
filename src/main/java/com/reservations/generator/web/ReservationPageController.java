package com.reservations.generator.web;

import com.reservations.generator.config.WebUiProperties;
import com.reservations.generator.domain.flow.FlowRegistry;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.web.view.FieldView;
import com.reservations.generator.web.view.FormView;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Renders the reservation page and its passenger-row fragment for the
 * default flow (see {@link WebUiProperties}).
 *
 * <p>Both routes are rendering-only: neither declares a
 * {@code @PathVariable} and both return {@code String}, satisfying
 * {@code arch.CreateOnlyBoundaryTest}'s R3 (GET is allowed only for
 * rendering, never for a lookup-by-identifier). This is a deliberately
 * minimal, unbranded skeleton that proves the schema-driven wiring
 * (flow resolution -&gt; {@link FlowRegistry#describePassengerFields}); the
 * branded Thymeleaf markup lands in a later slice alongside the actual
 * templates.
 */
@Controller
public class ReservationPageController {

    private final FlowRegistry flowRegistry;
    private final WebUiProperties webUiProperties;

    public ReservationPageController(FlowRegistry flowRegistry, WebUiProperties webUiProperties) {
        this.flowRegistry = flowRegistry;
        this.webUiProperties = webUiProperties;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String showForm() {
        return render(defaultFormView(), "reservation-form");
    }

    @GetMapping(value = "/ui/passenger-row", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String passengerRow() {
        return render(defaultFormView(), "passenger-row");
    }

    private FormView defaultFormView() {
        FlowDefinition flow = flowRegistry.require(
                webUiProperties.getDefaultFlowId(), webUiProperties.getDefaultSchemaVersion());
        return FormView.blank(flowRegistry.describePassengerFields(flow));
    }

    private static String render(FormView form, String fragmentName) {
        StringBuilder html = new StringBuilder("<section data-fragment=\"").append(fragmentName).append("\">");
        for (FieldView field : form.fields()) {
            html.append("<input name=\"").append(field.name())
                    .append("\" data-required=\"").append(field.required()).append("\">");
        }
        return html.append("</section>").toString();
    }
}
