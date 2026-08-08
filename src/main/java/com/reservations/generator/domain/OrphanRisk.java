package com.reservations.generator.domain;

/**
 * Classifies whether a reservation-creation failure could have left an
 * orphaned reservation on the upstream system.
 *
 * <p>This is a signal only: the full HTTP error-code mapping that decides
 * how each risk level surfaces to callers (status code, error body, retry
 * guidance) is an {@code api}-layer concern (see {@code api/ErrorMapper},
 * not yet built as of this slice).
 */
public enum OrphanRisk {

    /**
     * The failure happened strictly before the request was dispatched (e.g.
     * session acquisition, request-building). No bytes reached the upstream
     * system, so no reservation could have been created.
     */
    NONE,

    /**
     * The failure happened at or after request dispatch (network I/O,
     * timeout, or a malformed/unexpected response). Bytes may have reached
     * the upstream system before the failure was observed, so a reservation
     * may have been created there even though this call could not confirm
     * it.
     */
    POSSIBLE_ORPHAN_RESERVATION
}
