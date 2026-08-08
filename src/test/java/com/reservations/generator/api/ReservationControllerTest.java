package com.reservations.generator.api;

import com.reservations.generator.domain.DriftDetectedException;
import com.reservations.generator.domain.PreDispatchReservationException;
import com.reservations.generator.domain.ReservationCreator;
import com.reservations.generator.domain.SessionProvider;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.Pnr;
import com.reservations.generator.domain.model.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests exercising {@code POST /reservations} through
 * MockMvc, with {@link SessionProvider} and {@link ReservationCreator}
 * mocked out so no real network call happens: this focuses purely on
 * request validation, flow resolution, and error mapping.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionProvider sessionProvider;

    @MockBean
    private ReservationCreator reservationCreator;

    @Test
    void happyPathReturns200WithThePnrForEachPassenger() throws Exception {
        when(sessionProvider.acquire(any(FlowDefinition.class))).thenReturn(new StubSession());
        when(reservationCreator.create(any(), any(), any())).thenReturn(new Pnr("ABC123"));

        mockMvc.perform(post("/reservations")
                        .contentType("application/json")
                        .content("""
                                {"flowId":"ual-create-v1","schemaVersion":"1","passengers":[{"name":"Ada Lovelace"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pnrs[0]").value("ABC123"))
                .andExpect(jsonPath("$.failures").isEmpty());
    }

    @Test
    void blankFlowIdIsRejectedWithValidationFailed400() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType("application/json")
                        .content("""
                                {"flowId":"","schemaVersion":"1","passengers":[{"name":"Ada Lovelace"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.sideEffect").value("NONE"));
    }

    @Test
    void emptyPassengerListIsRejectedWithValidationFailed400() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType("application/json")
                        .content("""
                                {"flowId":"ual-create-v1","schemaVersion":"1","passengers":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownFlowIsRejectedWithValidationFailed400() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType("application/json")
                        .content("""
                                {"flowId":"no-such-flow","schemaVersion":"1","passengers":[{"name":"Ada Lovelace"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.flowId").value("no-such-flow"));
    }

    @Test
    void unknownPassengerFieldIsRejectedWithUnknownField400() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType("application/json")
                        .content("""
                                {"flowId":"ual-create-v1","schemaVersion":"1","passengers":[{"name":"Ada Lovelace","frequentFlyerNumber":"XY123"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.sideEffect").value("NONE"));
    }

    @Test
    void perPassengerDriftDetectedFailureSurfacesErrorCodeAndSideEffectWithoutLosingTheOtherPnr() throws Exception {
        when(sessionProvider.acquire(any(FlowDefinition.class))).thenReturn(new StubSession());
        when(reservationCreator.create(any(), any(), argThat(p -> p != null && "Ada Lovelace".equals(p.getName()))))
                .thenReturn(new Pnr("ABC123"));
        when(reservationCreator.create(any(), any(), argThat(p -> p != null && "Grace Hopper".equals(p.getName()))))
                .thenThrow(new DriftDetectedException("response shape drifted"));

        mockMvc.perform(post("/reservations")
                        .contentType("application/json")
                        .content("""
                                {"flowId":"ual-create-v1","schemaVersion":"1","passengers":[{"name":"Ada Lovelace"},{"name":"Grace Hopper"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pnrs.length()").value(1))
                .andExpect(jsonPath("$.pnrs[0]").value("ABC123"))
                .andExpect(jsonPath("$.failures.length()").value(1))
                .andExpect(jsonPath("$.failures[0].passengerIndex").value(1))
                .andExpect(jsonPath("$.failures[0].errorCode").value("DRIFT_DETECTED"))
                .andExpect(jsonPath("$.failures[0].sideEffect").value("POSSIBLE_ORPHAN_RESERVATION"));
    }

    @Test
    void sessionAcquisitionFailureIsRejectedWithSessionUnavailable503() throws Exception {
        when(sessionProvider.acquire(any(FlowDefinition.class)))
                .thenThrow(new PreDispatchReservationException("sandbox session unavailable", null));

        mockMvc.perform(post("/reservations")
                        .contentType("application/json")
                        .content("""
                                {"flowId":"ual-create-v1","schemaVersion":"1","passengers":[{"name":"Ada Lovelace"}]}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("SESSION_UNAVAILABLE"))
                .andExpect(jsonPath("$.sideEffect").value("NONE"));
    }

    /** Minimal {@link Session} stand-in; the mocked ports never inspect it. */
    private static final class StubSession implements Session {
    }
}
