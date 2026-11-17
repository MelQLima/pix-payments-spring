package com.melqlima.pix.psp;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * HTTP adapter to the PSP. Retry with exponential backoff absorbs transient
 * failures; the circuit breaker fails fast when the PSP is down so request
 * threads are not exhausted (configuration in application.yml).
 */
@Component
@ConditionalOnProperty(name = "pix.psp.mode", havingValue = "http")
public class HttpPspClient implements PspClient {

    private final RestClient http;

    public HttpPspClient(RestClient.Builder builder, @Value("${pix.psp.base-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    @Override
    @CircuitBreaker(name = "psp", fallbackMethod = "unavailable")
    @Retry(name = "psp")
    public PspCharge register(String txid, long amountCents, String pixKey, Instant expiresAt) {
        return http.put()
                .uri("/v2/cob/{txid}", txid)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "valor", Map.of("original", String.format("%d.%02d", amountCents / 100, amountCents % 100)),
                        "chave", pixKey,
                        "calendario", Map.of("expiracao", expiresAt.getEpochSecond() - Instant.now().getEpochSecond())))
                .retrieve()
                .body(PspCharge.class);
    }

    @SuppressWarnings("unused")
    private PspCharge unavailable(String txid, long amountCents, String pixKey, Instant expiresAt, Throwable cause) {
        throw new PspUnavailableException("PSP unavailable while registering " + txid, cause);
    }
}
