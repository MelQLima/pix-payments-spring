package com.melqlima.pix.idempotency;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends RuntimeException {

    private final HttpStatus status;

    public IdempotencyConflictException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
