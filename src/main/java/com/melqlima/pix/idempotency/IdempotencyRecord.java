package com.melqlima.pix.idempotency;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    public enum Status { IN_PROGRESS, COMPLETED }

    @Id
    @Column(name = "idempotency_key", length = 64)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 16)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // JPA
    }

    public IdempotencyRecord(String key, String requestHash, Instant createdAt) {
        this.key = key;
        this.requestHash = requestHash;
        this.status = Status.IN_PROGRESS;
        this.createdAt = createdAt;
    }

    public void complete(String responseBody) {
        this.status = Status.COMPLETED;
        this.responseBody = responseBody;
    }

    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public Status getStatus() { return status; }
    public String getResponseBody() { return responseBody; }
}
