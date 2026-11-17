package com.melqlima.pix.charge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateChargeRequest(
        @Positive @Max(100_000_000L) long amountCents,
        @NotBlank @Size(max = 77) String pixKey,
        @Size(max = 140) String description,
        @Min(60) @Max(86_400) Integer expiresInSeconds) {
}
