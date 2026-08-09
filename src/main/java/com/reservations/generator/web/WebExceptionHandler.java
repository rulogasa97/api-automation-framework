package com.reservations.generator.web;

import com.reservations.generator.config.WebUiProperties;
import com.reservations.generator.domain.ErrorCode;
import com.reservations.generator.domain.OrphanRisk;
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
        OrphanRisk orphanRisk = ReservationFailureClassifier.orphanRiskFor(ex);
        ResultView resultView = ResultView.from(errorCode, orphanRisk, safeMessage(ex));

        boolean fragmentOnly = request.getHeader(HtmxRequests.HX_REQUEST_HEADER) != null;
        ModelAndView mav = fragmentOnly ? new ModelAndView("fragments/error :: errorSection") : fullPageOrFallback();
        mav.addObject("view", resultView);
        mav.setStatus(statusFor(errorCode));
        return mav;
    }

    /**
     * The full-page error-rendering path — deliberately resilient to its own
     * failure. {@link #fullPage()} calls {@code flowRegistry.require(...)}
     * again (see its Javadoc), and that second call can itself throw (e.g. a
     * misconfigured default flow) independently of whatever failure this
     * handler is already reporting. Since this method backs an
     * {@code @ExceptionHandler}, letting that second failure propagate would
     * mean this handler throws out of exception handling itself, falling
     * through to Spring Boot's generic Whitelabel/500 page instead of any
     * deliberately-built error content. Falling back to the same
     * {@code fragments/error :: errorSection} fragment the htmx path already
     * uses keeps this method's contract ("never throws") intact without
     * needing any form/flow context the broken {@link FlowRegistry} cannot
     * supply.
     */
    private ModelAndView fullPageOrFallback() {
        try {
            return fullPage();
        } catch (RuntimeException renderingFailure) {
            return new ModelAndView("fragments/error :: errorSection");
        }
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

    /**
     * The HTTP status for a whole-batch failure, mirroring {@code
     * api.ErrorMapper#statusFor}'s per-{@link ErrorCode} mapping exactly
     * (duplicated here rather than shared: {@code web} must not depend on
     * {@code api}, see this class's own Javadoc/design D5) so a code like
     * {@code SESSION_UNAVAILABLE} keeps its distinct 503 instead of
     * collapsing into one generic non-{@code INLINE} status.
     */
    private static HttpStatus statusFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_FAILED, UNKNOWN_FIELD -> HttpStatus.BAD_REQUEST;
            case SESSION_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DRIFT_DETECTED -> HttpStatus.BAD_GATEWAY;
            case UPSTREAM_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String safeMessage(Throwable ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
