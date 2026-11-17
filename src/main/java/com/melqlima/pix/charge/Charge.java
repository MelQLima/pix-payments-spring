package com.melqlima.pix.charge;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A Pix immediate charge (cobrança imediata, "cob" in the BCB Pix API).
 * Rich domain model: state transitions are enforced here, not in services.
 */
@Entity
@Table(name = "charges")
public class Charge {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 35)
    private String txid;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "pix_key", nullable = false, length = 77)
    private String pixKey;

    @Column(length = 140)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChargeStatus status;

    @Column(name = "copy_paste", length = 512)
    private String copyPaste;

    @Column(name = "end_to_end_id", length = 32)
    private String endToEndId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** Optimistic locking: concurrent webhooks for the same charge cannot both win. */
    @Version
    private Long version;

    protected Charge() {
        // JPA
    }

    public static Charge create(String txid, long amountCents, String pixKey, String description,
                                Instant now, Duration expiresIn) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Charge c = new Charge();
        c.id = UUID.randomUUID();
        c.txid = txid;
        c.amountCents = amountCents;
        c.pixKey = pixKey;
        c.description = description;
        c.status = ChargeStatus.ACTIVE;
        c.createdAt = now;
        c.expiresAt = now.plus(expiresIn);
        return c;
    }

    public void attachCopyPaste(String brCode) {
        this.copyPaste = brCode;
    }

    /**
     * Settles the charge. Returns {@code true} when the state changed and {@code false}
     * for a replay of the same settlement (PSPs deliver webhooks at least once).
     */
    public boolean markPaid(String endToEndId, long paidCents, Instant paidAt) {
        if (status == ChargeStatus.PAID) {
            if (endToEndId.equals(this.endToEndId)) {
                return false;
            }
            throw new ChargeStateException("charge " + txid + " already paid by " + this.endToEndId);
        }
        if (paidAt.isAfter(expiresAt)) {
            throw new ChargeStateException("charge " + txid + " expired at " + expiresAt);
        }
        if (paidCents != amountCents) {
            throw new ChargeStateException("paid " + paidCents + " but charge is " + amountCents);
        }
        this.status = ChargeStatus.PAID;
        this.endToEndId = endToEndId;
        this.paidAt = paidAt;
        return true;
    }

    public UUID getId() { return id; }
    public String getTxid() { return txid; }
    public long getAmountCents() { return amountCents; }
    public String getPixKey() { return pixKey; }
    public String getDescription() { return description; }
    public ChargeStatus getStatus() { return status; }
    public String getCopyPaste() { return copyPaste; }
    public String getEndToEndId() { return endToEndId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
}
