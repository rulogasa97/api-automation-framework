package com.reservations.generator.web;

import com.reservations.generator.config.WebUiProperties;
import com.reservations.generator.domain.ErrorCode;
import com.reservations.generator.domain.OrphanRisk;
import com.reservations.generator.domain.ReservationCreationException;
import com.reservations.generator.domain.ReservationFailureClassifier;
import com.reservations.generator.domain.flow.FlowRegistry;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.web.view.FormView;
import com.reservations.generator.web.view.ResultView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

/**
 * Translates a whole-batch failure raised anywhere in {@code web} into a
 * differentiated HTML error fragment/page (real Thymeleaf rendering via
 * {@code fragments/error.html}/{@code reservation-page.html} — replacing the
 * hand-built HTML string this class's earlier skeleton returned), mirroring
 * what {@code api.GlobalExceptionHandler} does for JSON (design D5). Scoped
 * to {@code com.reservations.generator.web} via {@code basePackages} so it
 * never intercepts an {@code api} failure — those must always stay JSON,
 * handled by {@code api.GlobalExceptionHandler} (proven by {@code
 * WebControllersIntegrationTest}).
 *
 * <p>Classification reuses the framework-free
 * {@link ReservationFailureClassifier} (a {@code domain} concept), never
 * {@code api.ErrorMapper}: {@code web} must not depend on {@code api}
 * (design D5 / {@code arch.LayeringRulesTest}).
 */
@ControllerAdvice(basePackages = "com.reservations.generator.web")
public class WebExceptionHandler {

    private final FlowRegistry flowRegistry;
    private final WebUiProperties webUiProperties;

    public WebExceptionHandler(FlowRegistry flowRegistry, WebUiProperties webUiProperties) {
        this.flowRegistry = flowRegistry;
        this.webUiProperties = webUiProperties;
    }

    @ExceptionHandler(RuntimeException.class)
    public ModelAndView handleWebFailure(RuntimeException ex, HttpServletRequest request) {
        ErrorCode errorCode = ReservationFailureClassifier.classify(ex);
        OrphanRisk orphanRisk = ex instanceof ReservationCreationException rce ? rce.getOrphanRisk() : OrphanRisk.NONE;
        ResultView resultView = ResultView.from(errorCode, orphanRisk, safeMessage(ex));

        boolean fragmentOnly = request.getHeader(HtmxRequests.HX_REQUEST_HEADER) != null;
        ModelAndView mav = fragmentOnly ? new ModelAndView("fragments/error :: errorSection") : fullPage();
        mav.addObject("view", resultView);
        mav.setStatus(statusFor(resultView));
        return mav;
    }

    /** See {@code ReservationFormController#submit}'s Javadoc for why the no-JS fallback shows a blank form. */
    private ModelAndView fullPage() {
        ModelAndView mav = new ModelAndView("reservation-page");
        FlowDefinition flow = flowRegistry.require(
                webUiProperties.getDefaultFlowId(), webUiProperties.getDefaultSchemaVersion());
        mav.addObject("rows", List.of(FormView.blank(flowRegistry.describePassengerFields(flow))));
        mav.addObject("result", null);
        mav.addObject("failureRows", List.of());
        return mav;
    }

    private static HttpStatus statusFor(ResultView view) {
        return view.treatment() == ResultView.Treatment.INLINE ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
    }

    private static String safeMessage(Throwable ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
