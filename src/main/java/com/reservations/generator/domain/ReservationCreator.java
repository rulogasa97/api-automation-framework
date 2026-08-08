package com.reservations.generator.domain;

import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.Pnr;
import com.reservations.generator.domain.model.Session;

/**
 * Outbound port: synchronously creates a single reservation for one
 * passenger within an already-acquired session.
 *
 * <p>Implemented in {@code apireplay/} (e.g. {@code CreateReservationReplay},
 * added in a later slice). Domain code depends only on this interface.
 */
public interface ReservationCreator {

    Pnr create(Session session, FlowDefinition flow, Passenger passenger);
}
