package com.melqlima.pix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import com.melqlima.pix.charge.ChargeResponse;
import com.melqlima.pix.charge.ChargeStatus;
import com.melqlima.pix.outbox.OutboxRepository;
import com.melqlima.pix.webhook.HmacSignatureVerifier;

/** End-to-end test against real PostgreSQL and Kafka (Testcontainers). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.tracing.sampling.probability=0",
        "pix.outbox-poll-interval=200ms"
})
@Testcontainers
class PixApplicationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private HmacSignatureVerifier signer;

    @Autowired
    private OutboxRepository outbox;

    @Test
    void chargeLifecycleWithIdempotencyOutboxAndKafka() {
        String body = "{\"amountCents\": 4990, \"pixKey\": \"loja@exemplo.com.br\", \"description\": \"Pedido 7\"}";

        // 1. Create with an Idempotency-Key; a retry returns the very same charge.
        ResponseEntity<ChargeResponse> first = createCharge("key-1", body);
        ResponseEntity<ChargeResponse> retry = createCharge("key-1", body);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getBody().id()).isEqualTo(first.getBody().id());
        assertThat(first.getBody().copyPaste()).startsWith("000201");

        // Same key, different payload -> 422.
        ResponseEntity<String> conflict = http.postForEntity("/v1/charges",
                jsonEntity("key-1", body.replace("4990", "5000")), String.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // 2. Signed PSP webhook settles the charge; replays are accepted and ignored.
        String txid = first.getBody().txid();
        String webhook = "{\"txid\":\"%s\",\"endToEndId\":\"E2E%s\",\"amountCents\":4990,\"paidAt\":\"%s\"}"
                .formatted(txid, txid.substring(0, 20), Instant.now());
        assertThat(sendWebhook(webhook, signer.sign(webhook)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(sendWebhook(webhook, signer.sign(webhook)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(sendWebhook(webhook, "bad-signature").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ChargeResponse paid = http.getForObject("/v1/charges/" + first.getBody().id(), ChargeResponse.class);
        assertThat(paid.status()).isEqualTo(ChargeStatus.PAID);

        // 3. The outbox publisher delivers both events to Kafka.
        await().atMost(Duration.ofSeconds(30)).until(() -> outbox.countByPublishedAtIsNull() == 0);
        List<ConsumerRecord<String, String>> records = consumeAll("payments.events", 2);
        assertThat(records).extracting(ConsumerRecord::key).containsOnly(txid);
        assertThat(records).filteredOn(r -> r.value().contains("\"type\":\"pix.settled\"")).hasSize(1);
    }

    @Test
    void unknownChargeReturnsProblemDetail() {
        ResponseEntity<String> resp = http.getForEntity("/v1/charges/" + UUID.randomUUID(), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private ResponseEntity<ChargeResponse> createCharge(String key, String body) {
        return http.postForEntity("/v1/charges", jsonEntity(key, body), ChargeResponse.class);
    }

    private HttpEntity<String> jsonEntity(String idempotencyKey, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<String> sendWebhook(String body, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Signature", signature);
        return http.postForEntity("/v1/webhooks/psp", new HttpEntity<>(body, headers), String.class);
    }

    private List<ConsumerRecord<String, String>> consumeAll(String topic, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 20_000;
            while (out.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(out::add);
            }
        }
        return out;
    }
}
