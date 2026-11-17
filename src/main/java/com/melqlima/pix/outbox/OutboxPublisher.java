package com.melqlima.pix.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.melqlima.pix.config.PixProperties;

import io.micrometer.core.instrument.MeterRegistry;

/** Polls the outbox and publishes pending events to Kafka (at-least-once). */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final PixProperties props;
    private final Clock clock;
    private final MeterRegistry meters;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka, PixProperties props,
                           Clock clock, MeterRegistry meters) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.props = props;
        this.clock = clock;
        this.meters = meters;
        meters.gauge("pix.outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull);
    }

    @Scheduled(fixedDelayString = "${pix.outbox-poll-interval:1s}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outbox.lockNextBatch(props.outboxBatchSize());
        for (OutboxEvent event : batch) {
            try {
                // Key = aggregate id keeps all events of a charge in the same partition (ordering).
                kafka.send(props.eventsTopic(), event.getAggregateId(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished(Instant.now(clock));
                meters.counter("pix.outbox.published", "type", event.getType()).increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                event.registerFailure();
                log.warn("failed to publish outbox event {} (attempt {})", event.getId(), event.getAttempts(), e);
                return; // keep ordering: retry this event on the next poll
            }
        }
    }
}
