package com.reservations.generator.api;

import com.reservations.generator.api.dto.CreateReservationsRequest;
import com.reservations.generator.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single point of exception-to-HTTP-response translation for every
 * controller in {@code api}, delegating the actual mapping decision to
 * {@link ErrorMapper} so the rules stay consistent across the whole edge
 * (currently only {@link ReservationController}, but this advice applies to
 * any future controller in {@code api} too).
 *
 * <p>Scoped to {@code com.reservations.generator.api} via {@code basePackages}
 * so it never intercepts failures from other driving adapters (e.g. a future
 * {@code web} package), which must always surface JSON error bodies here and
 * never have their own render-facing failures hijacked into JSON.
 */
@RestControllerAdvice(basePackages = "com.reservations.generator.api")
public class GlobalExceptionHandler {

    private final ErrorMapper errorMapper;

    public GlobalExceptionHandler(ErrorMapper errorMapper) {
        this.errorMapper = errorMapper;
    }

    /**
     * Bean Validation failures on the request body. Handled separately from
     * {@link #handleRuntimeException} because
     * {@link MethodArgumentNotValidException} is a checked exception raised
     * by the framework before the controller method body ever runs, so the
     * flowId must be read off the (partially bound) validation target rather
     * than off the request attribute set by the controller itself.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return toResponse(errorMapper.map(ex, extractFlowId(ex)));
    }

    /**
     * Every other failure: domain exceptions raised from inside the
     * controller (unknown flow, unknown passenger field, session/dispatch
     * failures) and any unexpected runtime failure.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        Object flowId = request.getAttribute(ReservationController.FLOW_ID_REQUEST_ATTRIBUTE);
        return toResponse(errorMapper.map(ex, flowId instanceof String s ? s : null));
    }

    private static String extractFlowId(MethodArgumentNotValidException ex) {
        Object target = ex.getBindingResult().getTarget();
        return target instanceof CreateReservationsRequest request ? request.flowId() : null;
    }

    private static ResponseEntity<ErrorResponse> toResponse(ErrorMapper.MappedError mapped) {
        return ResponseEntity.status(mapped.status()).body(mapped.body());
    }
}
