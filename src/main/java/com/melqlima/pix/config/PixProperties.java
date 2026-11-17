package com.melqlima.pix.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings bound from {@code pix.*}.
 *
 * @param merchantAccount ledger account credited when a charge is paid
 * @param webhookSecret   HMAC-SHA256 secret shared with the PSP
 * @param eventsTopic     Kafka topic for payment events
 * @param outboxBatchSize max outbox rows published per poll
 * @param defaultExpiry   charge expiry when the client does not send one
 */
@ConfigurationProperties(prefix = "pix")
public record PixProperties(
        String merchantAccount,
        String webhookSecret,
        String eventsTopic,
        int outboxBatchSize,
        Duration defaultExpiry) {
}
