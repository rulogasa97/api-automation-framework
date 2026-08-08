/**
 * Composition root: wires the domain ports ({@code SessionProvider},
 * {@code ReservationCreator}) to their {@code apireplay} adapters, registers
 * supported flows, and validates sandbox-only configuration at startup.
 *
 * <p>Unlike {@code api} and {@code domain}, this package is allowed to
 * depend on {@code apireplay} — someone has to build the concrete beans, and
 * that someone must not be the hexagon's inner layers (see
 * {@code arch.LayeringRulesTest}, which only restricts {@code api} and
 * {@code domain}).
 */
package com.reservations.generator.config;
