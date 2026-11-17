package com.melqlima.pix.charge;

/** Raised when a state transition violates a business rule (e.g. paying an expired charge). */
public class ChargeStateException extends RuntimeException {

    public ChargeStateException(String message) {
        super(message);
    }
}
