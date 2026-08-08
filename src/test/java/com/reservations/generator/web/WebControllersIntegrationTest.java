package com.reservations.generator.web;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end MockMvc coverage of the {@code web} driving adapter's skeleton:
 * routing, HX-Request-driven fragment-vs-full-page rendering, and the
 * threat-matrix guard rails from the design's D4/D5 (advice scoping, static
 * resource exposure).
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
    void getRootRendersOneInputForTheDefaultFlowsOnlyField() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"name\"")));
    }

    @Test
    void getPassengerRowFragmentRendersTheSameSchemaDrivenFields() throws Exception {
        mockMvc.perform(get("/ui/passenger-row"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"name\"")));
    }

    @Test
    void postWithoutHxRequestHeaderReturnsAFullPageWrapper() throws Exception {
        when(sessionProvider.acquire(any(FlowDefinition.class))).thenReturn(new StubSession());
        when(reservationCreator.create(any(), any(), any())).thenReturn(new Pnr("ABC123"));

        mockMvc.perform(post("/ui/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passengers":[{"name":"Ada Lovelace"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<html>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ABC123")));
    }

    @Test
    void postWithHxRequestHeaderReturnsAFragmentOnly() throws Exception {
        when(sessionProvider.acquire(any(FlowDefinition.class))).thenReturn(new StubSession());
        when(reservationCreator.create(any(), any(), any())).thenReturn(new Pnr("XYZ999"));

        mockMvc.perform(post("/ui/reservations")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passengers":[{"name":"Ada Lovelace"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html>"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("XYZ999")));
    }

    @Test
    void webFailuresRenderAsHtmlWhileApiFailuresForTheSameProblemStayJson() throws Exception {
        mockMvc.perform(post("/ui/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passengers":[{"name":"Ada Lovelace","extra":"unexpected"}]}
                                """))
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
    void unregisteredStaticAssetPathReturnsNotFoundRatherThanADirectoryListing() throws Exception {
        mockMvc.perform(get("/css/does-not-exist.css"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/css/"))
                .andExpect(status().isNotFound());
    }

    /** Minimal {@link Session} stand-in; the mocked ports never inspect it. */
    private static final class StubSession implements Session {
    }
}
