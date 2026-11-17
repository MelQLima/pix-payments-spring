package com.melqlima.pix.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implements the Idempotency-Key pattern (as in Stripe and the BCB Pix API):
 * the first request with a key is executed and its response stored; retries
 * with the same key and payload get the stored response; a different payload
 * is rejected with 422 and a concurrent duplicate with 409.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate newTx;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper mapper, Clock clock,
                              PlatformTransactionManager txManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(String key, Object request, Class<T> responseType, Supplier<T> action) {
        String hash = sha256(toJson(request));
        Optional<String> stored;
        try {
            stored = newTx.execute(status -> reserve(key, hash));
        } catch (DataIntegrityViolationException race) {
            throw new IdempotencyConflictException(HttpStatus.CONFLICT, "a request with this Idempotency-Key is in progress");
        }
        if (stored != null && stored.isPresent()) {
            return fromJson(stored.get(), responseType);
        }
        T response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            // Failed requests release the key so the client can retry.
            newTx.executeWithoutResult(status -> repository.deleteById(key));
            throw e;
        }
        String body = toJson(response);
        newTx.executeWithoutResult(status -> repository.findById(key).ifPresent(r -> r.complete(body)));
        return response;
    }

    private Optional<String> reserve(String key, String hash) {
        Optional<IdempotencyRecord> existing = repository.findById(key);
        if (existing.isEmpty()) {
            repository.saveAndFlush(new IdempotencyRecord(key, hash, Instant.now(clock)));
            return Optional.empty();
        }
        IdempotencyRecord record = existing.get();
        if (!record.getRequestHash().equals(hash)) {
            throw new IdempotencyConflictException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency-Key was already used with a different payload");
        }
        if (record.getStatus() == IdempotencyRecord.Status.IN_PROGRESS) {
            throw new IdempotencyConflictException(HttpStatus.CONFLICT, "a request with this Idempotency-Key is in progress");
        }
        return Optional.of(record.getResponseBody());
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
