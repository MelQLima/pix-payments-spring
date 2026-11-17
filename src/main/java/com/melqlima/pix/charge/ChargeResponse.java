package com.melqlima.pix.charge;

import java.time.Instant;
import java.util.UUID;

public record ChargeResponse(
        UUID id,
        String txid,
        long amountCents,
        String pixKey,
        String description,
        ChargeStatus status,
        String copyPaste,
        String endToEndId,
        Instant createdAt,
        Instant expiresAt,
        Instant paidAt) {

    public static ChargeResponse from(Charge c) {
        return new ChargeResponse(c.getId(), c.getTxid(), c.getAmountCents(), c.getPixKey(), c.getDescription(),
                c.getStatus(), c.getCopyPaste(), c.getEndToEndId(), c.getCreatedAt(), c.getExpiresAt(),
                c.getPaidAt());
    }
}
