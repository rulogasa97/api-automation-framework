package com.reservations.generator.web;

import com.reservations.generator.domain.DriftDetectedException;
import com.reservations.generator.domain.PostDispatchReservationException;
import com.reservations.generator.domain.ReservationCreator;
import com.reservations.generator.domain.SessionProvider;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Pnr;
import com.reservations.generator.domain.model.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end MockMvc coverage of the {@code web} driving adapter's real
 * Thymeleaf rendering: routing, HX-Request-driven fragment-vs-full-page
 * rendering, the branded/schema-driven form, the spec's "Error-Code-Specific
 * Rendering" requirement (each banner-level code visually distinct, inline
 * for field-level failures), and the threat-matrix guard rails from the
 * design's D4/D5 (advice scoping, static resource exposure).
 *
 * <p>Submits via the real {@code passengers[N].fieldName} form-encoded
 * convention {@code fragments/passenger-row.html} renders (see {@link
 * PassengerFormBinding}), not the JSON body Phase 3's skeleton used.
 *
 * <p>The "no directory listing" threat-matrix guard is deliberately NOT
 * asserted here: {@code SpringBootMockServletContext}'s {@code
 * ServletContext}-based resource resolution returns a directory's listing
 * for a bare directory path in a way the real embedded server never does
 * (confirmed by direct comparison — see {@code
 * StaticResourceDirectoryGuardEndToEndTest}, which asserts this against a
 * real running server instead).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebControllersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionProvider sessionProvider;

    @MockBean
    private ReservationCreator reservationCreator;

    @Test
    void getRootRendersOneInputPerDescriptorForTheDefaultFlow() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("name=\"passengers[0].name\"")))
                .andExpect(content().string(containsString("ual-header")));
    }

    @Test
    void getPassengerRowFragmentRendersTheSameSchemaDrivenFieldsAtTheGivenIndex() throws Exception {
        mockMvc.perform(get("/ui/passenger-row").param("index", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("name=\"passengers[2].name\"")));
    }

    /**
     * R2-001 regression: the Add button must derive the new row's index from
     * the {@code #passenger-rows} container's monotonic {@code
     * data-next-index} counter, never from
     * {@code document.querySelectorAll('[data-passenger-row]').length}. The
     * live-DOM-count approach let a removed row's index be reused by the next
     * Add (3 rows 0,1,2 -> remove row 1 -> DOM count is 2 -> Add would mint
     * another index 2), so two rows would collide on the same {@code
     * passengers[N].*} form-field names and {@link
     * PassengerFormBinding#parseRows} would silently drop one row's
     * submitted data. Seeding the counter to the server-rendered row count
     * and only ever incrementing it (Remove never decrements it, see
     * fragments/passenger-row.html) guarantees indices are never reused
     * regardless of how many rows are removed in between.
     */
    @Test
    void passengerRowsContainerSeedsAMonotonicNextIndexCounterInsteadOfLiveDomCount() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-next-index=\"1\"")))
                .andExpect(content().string(not(containsString(
                        "document.querySelectorAll('[data-passenger-row]').length"))));
    }

    @Test
    void postWithoutHxRequestHeaderReturnsAFullPageWrapperWithThePnr() throws Exception {
        when(sessionProvider.acquire(any(FlowDefinition.class))).thenReturn(new StubSession());
        when(reservationCreator.create(any(), any(), any())).thenReturn(new Pnr("ABC123"));

        mockMvc.perform(post("/ui/reservations")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("passengers[0].name", "Ada Lovelace"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html")))
                .andExpect(content().string(containsString("ABC123")));
    }

    @Test
    void postWithHxRequestHeaderReturnsAResultFragmentWithThePnrAndACopyControl() throws Exception {
        when(sessionProvider.acquire(any(FlowDefinition.class))).thenReturn(new StubSession());
        when(reservationCreator.create(any(), any(), any())).thenReturn(new Pnr("XYZ999"));

        mockMvc.perform(post("/ui/reservations")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("passengers[0].name", "Ada Lovelace"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(containsString("XYZ999")))
                .andExpect(content().string(containsString("data-copy-pnr=\"XYZ999\"")));
    }

    /**
     * VALIDATION_FAILED/UNKNOWN_FIELD render as a field-level inline alert,
     * never a banner. "Values preserved": the response is only the error
     * fragment (no {@code <input>} anywhere in it) — the passenger-rows form
     * itself is never re-rendered/replaced by an htmx swap, so the browser
     * keeps whatever the user already typed.
     */
    @Test
    void unknownFieldRendersAsAnInlineAlertAndNeverReRendersTheForm() throws Exception {
        mockMvc.perform(post("/ui/reservations")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("passengers[0].name", "Ada Lovelace")
                        .param("passengers[0].extra", "unexpected"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("ual-alert--inline")))
                .andExpect(content().string(containsString("extra")))
                .andExpect(content().string(not(containsString("<input"))));
    }

    /**
     * A failure carrying {@code sideEffect=POSSIBLE_ORPHAN_RESERVATION}
     * (here: one passenger's create dispatch drifted) renders as a banner
     * with NO retry control — the spec's "Possible orphan reservation gets
     * no easy retry" scenario.
     */
    @Test
    void possibleOrphanReservationRendersAsABannerWithNoRetryControl() throws Exception {
        when(sessionProvider.acquire(any(FlowDefinition.class))).thenReturn(new StubSession());
        when(reservationCreator.create(any(), any(), any()))
                .thenThrow(new DriftDetectedException("response shape drifted"));

        mockMvc.perform(post("/ui/reservations")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("passengers[0].name", "Ada Lovelace"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ual-banner--drift-detected")))
                .andExpect(content().string(containsString("ual-banner--no-retry")))
                .andExpect(content().string(not(containsString("ual-btn--retry"))));
    }

    /**
     * UPSTREAM_TIMEOUT gets its own distinct banner styling (different CSS
     * variant from DRIFT_DETECTED above — the spec's "visually distinct"
     * requirement is not satisfied by a single generic banner class).
     * "Values preserved": same mechanism as the inline case above — the
     * fragment response never contains a re-rendered {@code <input>}.
     */
    @Test
    void upstreamTimeoutRendersItsOwnDistinctBannerAndNeverReRendersTheForm() throws Exception {
        when(sessionProvider.acquire(any(FlowDefinition.class))).thenReturn(new StubSession());
        when(reservationCreator.create(any(), any(), any()))
                .thenThrow(new PostDispatchReservationException("upstream call did not complete", null));

        mockMvc.perform(post("/ui/reservations")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("passengers[0].name", "Ada Lovelace"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ual-banner--upstream-timeout")))
                .andExpect(content().string(not(containsString("<input"))));
    }

    @Test
    void webFailuresRenderAsHtmlWhileApiFailuresForTheSameProblemStayJson() throws Exception {
        mockMvc.perform(post("/ui/reservations")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("passengers[0].name", "Ada Lovelace")
                        .param("passengers[0].extra", "unexpected"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flowId":"ual-create-v1","schemaVersion":"1","passengers":[{"name":"Ada Lovelace","extra":"unexpected"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void unregisteredStaticAssetPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/css/does-not-exist.css"))
                .andExpect(status().isNotFound());
    }

    /** Minimal {@link Session} stand-in; the mocked ports never inspect it. */
    private static final class StubSession implements Session {
    }
}
