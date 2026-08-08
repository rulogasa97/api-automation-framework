package com.reservations.generator.domain.model;

import java.util.Objects;

/**
 * A successfully created reservation's PNR (passenger name record) code, as
 * extracted from the upstream create-reservation response.
 */
public final class Pnr {

    private final String code;

    public Pnr(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("PNR code must not be blank");
        }
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pnr pnr)) {
            return false;
        }
        return code.equals(pnr.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return "Pnr{code='" + code + "'}";
    }
}
