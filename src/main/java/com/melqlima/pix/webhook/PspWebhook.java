package com.melqlima.pix.webhook;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Settlement notification sent by the PSP when a charge is paid. */
public record PspWebhook(
        @NotBlank String txid,
        @NotBlank String endToEndId,
        @Positive long amountCents,
        @NotNull Instant paidAt) {
}
