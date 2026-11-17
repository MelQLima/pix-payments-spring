package com.melqlima.pix.charge;

public class ChargeNotFoundException extends RuntimeException {

    public ChargeNotFoundException(String reference) {
        super("charge not found: " + reference);
    }
}
