package com.reservations.generator.web;

import com.reservations.generator.domain.ErrorCode;
import com.reservations.generator.domain.OrphanRisk;
import com.reservations.generator.domain.ReservationCreationException;
import com.reservations.generator.domain.ReservationFailureClassifier;
import com.reservations.generator.web.view.ResultView;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Translates a failure raised anywhere in {@code web} into an HTML error
 * fragment, mirroring what {@code api.GlobalExceptionHandler} does for JSON
 * (design D5). Scoped to {@code com.reservations.generator.web} via
 * {@code basePackages} so it never intercepts an {@code api} failure — those
 * must always stay JSON, handled by {@code api.GlobalExceptionHandler}
 * (proven by {@code WebControllersIntegrationTest}).
 *
 * <p>Classification reuses the framework-free
 * {@link ReservationFailureClassifier} (a {@code domain} concept), never
 * {@code api.ErrorMapper}: {@code web} must not depend on {@code api}
 * (design D5 / {@code arch.LayeringRulesTest}).
 */
@ControllerAdvice(basePackages = "com.reservations.generator.web")
public class WebExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleWebFailure(RuntimeException ex) {
        ErrorCode errorCode = ReservationFailureClassifier.classify(ex);
        OrphanRisk orphanRisk = ex instanceof ReservationCreationException rce ? rce.getOrphanRisk() : OrphanRisk.NONE;
        ResultView resultView = ResultView.from(errorCode, orphanRisk, safeMessage(ex));

        return ResponseEntity.status(statusFor(resultView))
                .contentType(MediaType.TEXT_HTML)
                .body(render(resultView));
    }

    private static HttpStatus statusFor(ResultView view) {
        return view.treatment() == ResultView.Treatment.INLINE ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
    }

    private static String render(ResultView view) {
        return "<section data-fragment=\"error\" data-treatment=\"" + view.treatment()
                + "\" data-retry-allowed=\"" + view.retryAllowed() + "\">" + escape(view.message()) + "</section>";
    }

    private static String safeMessage(Throwable ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private static String escape(String message) {
        return message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
