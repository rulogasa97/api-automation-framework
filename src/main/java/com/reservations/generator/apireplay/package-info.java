/**
 * Volatile outbound adapter that talks to the sandbox upstream system by
 * replaying its browser session flows. Implements {@code domain} ports
 * ({@code SessionProvider}, {@code ReservationCreator}). Depends inward on
 * {@code domain}; nothing in {@code domain} or {@code api} depends outward
 * on this package (enforced by an ArchUnit rule). Implemented starting in
 * slice 2.
 */
package com.reservations.generator.apireplay;
