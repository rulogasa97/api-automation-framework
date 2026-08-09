package com.reservations.generator.web;

import com.reservations.generator.config.WebUiProperties;
import com.reservations.generator.domain.flow.FlowRegistry;
import com.reservations.generator.domain.flow.UnknownFlowException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link WebExceptionHandler} covering an edge case
 * {@link WebControllersIntegrationTest} cannot exercise: the handler's own
 * full-page rendering path failing a SECOND time.
 *
 * <p>Uses a plain (nothing-registered) {@link FlowRegistry} so that both the
 * original request's own {@code flowRegistry.require(...)} call AND the
 * handler's {@code fullPage()} re-invocation of the same call fail with
 * {@link UnknownFlowException} — proving {@link WebExceptionHandler
 * #handleWebFailure} itself never throws, even when its own error-rendering
 * path is also broken.
 */
class WebExceptionHandlerTest {

    private final WebUiProperties webUiProperties = new WebUiProperties();

    @Test
    void handleWebFailureDoesNotPropagateWhenTheFullPageRenderingPathAlsoFails() {
        WebExceptionHandler handler = new WebExceptionHandler(new FlowRegistry(), webUiProperties);
        MockHttpServletRequest request = new MockHttpServletRequest(); // no HX-Request header -> full-page path
        RuntimeException original = unknownFlowFailure();

        assertThatCode(() -> handler.handleWebFailure(original, request)).doesNotThrowAnyException();
    }

    @Test
    void handleWebFailureStillReturnsARenderableFallbackWhenTheFullPageRenderingPathAlsoFails() {
        WebExceptionHandler handler = new WebExceptionHandler(new FlowRegistry(), webUiProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RuntimeException original = unknownFlowFailure();

        ModelAndView mav = handler.handleWebFailure(original, request);

        assertThat(mav).isNotNull();
        assertThat(mav.getViewName()).isNotNull();
        assertThat(mav.getStatus()).isNotNull();
    }

    private RuntimeException unknownFlowFailure() {
        return new UnknownFlowException(webUiProperties.getDefaultFlowId(), webUiProperties.getDefaultSchemaVersion());
    }
}
