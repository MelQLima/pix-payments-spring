package com.melqlima.pix.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.melqlima.pix.charge.ChargeNotFoundException;
import com.melqlima.pix.charge.ChargeStateException;
import com.melqlima.pix.idempotency.IdempotencyConflictException;
import com.melqlima.pix.psp.PspUnavailableException;
import com.melqlima.pix.webhook.InvalidSignatureException;

/** Maps exceptions to RFC 9457 (RFC 7807) application/problem+json responses. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ChargeNotFoundException.class)
    ProblemDetail notFound(ChargeNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Charge not found", e.getMessage());
    }

    @ExceptionHandler(ChargeStateException.class)
    ProblemDetail invalidState(ChargeStateException e) {
        return problem(HttpStatus.CONFLICT, "Invalid charge state", e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Concurrent update", "the charge was modified concurrently, retry");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotency(IdempotencyConflictException e) {
        return problem(e.getStatus(), "Idempotency conflict", e.getMessage());
    }

    @ExceptionHandler(InvalidSignatureException.class)
    ProblemDetail signature(InvalidSignatureException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid signature", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, JsonProcessingException.class})
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(PspUnavailableException.class)
    ProblemDetail pspDown(PspUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "PSP unavailable", "payment provider unavailable, retry later");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
