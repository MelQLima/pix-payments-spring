package com.melqlima.pix.webhook;

public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException() {
        super("invalid webhook signature");
    }
}
