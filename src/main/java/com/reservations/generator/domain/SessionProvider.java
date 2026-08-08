package com.reservations.generator.domain;

import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Session;

/**
 * Outbound port: acquires a usable upstream session for a given flow.
 *
 * <p>Implemented in {@code apireplay/session/} (e.g. {@code StubSessionProvider},
 * added in a later slice). Domain code depends only on this interface.
 */
public interface SessionProvider {

    Session acquire(FlowDefinition flow);
}
