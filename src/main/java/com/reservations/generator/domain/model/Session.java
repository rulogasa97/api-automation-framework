package com.reservations.generator.domain.model;

/**
 * Domain-owned marker for an acquired upstream session.
 *
 * <p>Design note (hexagonal purity decision): the design's file list places
 * the concrete session type conceptually under {@code apireplay/session/},
 * since sandbox session material (cookies/tokens) is an outbound-adapter
 * concern. However {@code domain/} cannot depend outward on
 * {@code apireplay/}, and {@link SessionProvider#acquire} /
 * {@link com.reservations.generator.domain.ReservationCreator#create} are
 * domain ports that must reference a domain-owned type.
 *
 * <p>Resolution: this empty marker interface lives in {@code domain/model}.
 * The concrete sandbox session implementation added in
 * {@code apireplay/session/} (slice 2) will implement this interface,
 * keeping the dependency arrow pointing inward (apireplay -> domain) as
 * required by the hexagonal rule, while the domain ports stay
 * implementation-agnostic about what a "session" actually contains.
 */
public interface Session {
}
