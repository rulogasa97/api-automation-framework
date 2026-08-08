package com.reservations.generator.config;

import com.reservations.generator.apireplay.CreateReservationReplay;
import com.reservations.generator.apireplay.PnrExtractor;
import com.reservations.generator.apireplay.session.StubSessionProvider;
import com.reservations.generator.domain.CreateReservationsUseCase;
import com.reservations.generator.domain.ReservationCreator;
import com.reservations.generator.domain.SessionProvider;
import com.reservations.generator.domain.flow.FlowRegistry;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root for the domain ports: builds the concrete
 * {@code apireplay} adapters ({@link StubSessionProvider},
 * {@link CreateReservationReplay}) and wires them, along with the currently
 * supported flow(s), into {@link CreateReservationsUseCase}.
 *
 * <p>{@link StubSessionProvider} and {@link CreateReservationReplay} are
 * PLACEHOLDER adapters (see their own Javadoc): this configuration will need
 * no changes once real implementations replace them, since it only depends
 * on the {@code SessionProvider}/{@code ReservationCreator} port types.
 */
@Configuration
@EnableConfigurationProperties({SandboxProperties.class, WebUiProperties.class})
public class ReservationBeansConfiguration {

    // PLACEHOLDER: the only supported flow so far, matching the fixture used
    // throughout apireplay/ (see CreateReservationReplay's Javadoc).
    private static final String FLOW_ID = "ual-create-v1";
    private static final String SCHEMA_VERSION = "1";

    @Bean
    public FlowRegistry flowRegistry() {
        FlowRegistry registry = new FlowRegistry();
        registry.register(new FlowDefinition(FLOW_ID, SCHEMA_VERSION, Passenger.class));
        return registry;
    }

    @Bean
    public SessionProvider sessionProvider(
            @Value("${reservations.apireplay.session.cookie:}") String cookie,
            @Value("${reservations.apireplay.session.token:}") String token) {
        return new StubSessionProvider(cookie, token);
    }

    @Bean
    public PnrExtractor pnrExtractor() {
        return new PnrExtractor();
    }

    @Bean
    public ReservationCreator reservationCreator(SandboxProperties sandboxProperties, PnrExtractor pnrExtractor) {
        return new CreateReservationReplay(sandboxProperties.getBaseUrl(), pnrExtractor);
    }

    @Bean
    public CreateReservationsUseCase createReservationsUseCase(
            SessionProvider sessionProvider,
            ReservationCreator reservationCreator,
            @Value("${reservations.concurrency.max-concurrent-creates:8}") int maxConcurrentCreates) {
        return new CreateReservationsUseCase(sessionProvider, reservationCreator, maxConcurrentCreates);
    }
}
