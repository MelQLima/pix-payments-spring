package com.melqlima.pix.charge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melqlima.pix.config.PixProperties;
import com.melqlima.pix.outbox.OutboxEvent;
import com.melqlima.pix.outbox.OutboxRepository;
import com.melqlima.pix.psp.PspCharge;
import com.melqlima.pix.psp.PspClient;
import com.melqlima.pix.webhook.PspWebhook;

class ChargeServiceTest {

    private final Instant now = Instant.parse("2026-11-16T12:00:00Z");
    private ChargeRepository charges;
    private OutboxRepository outbox;
    private PspClient psp;
    private ChargeService service;

    @BeforeEach
    void setUp() {
        charges = mock(ChargeRepository.class);
        outbox = mock(OutboxRepository.class);
        psp = mock(PspClient.class);
        PixProperties props = new PixProperties("merchant-1", "secret", "payments.events", 100, Duration.ofHours(1));
        service = new ChargeService(charges, outbox, psp, props, new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC), mock(PlatformTransactionManager.class));
        when(charges.save(any(Charge.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createRegistersAtPspAndWritesOutboxEvent() {
        when(psp.register(anyString(), anyLong(), anyString(), any())).thenReturn(new PspCharge("000201...FAKE"));

        Charge c = service.create(new CreateChargeRequest(2_500, "+5511999999999", "Pedido 1", null));

        assertThat(c.getCopyPaste()).isEqualTo("000201...FAKE");
        assertThat(c.getTxid()).hasSize(32).matches("[a-f0-9]+");
        assertThat(c.getExpiresAt()).isEqualTo(now.plus(Duration.ofHours(1)));
        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(event.capture());
        assertThat(event.getValue().getType()).isEqualTo(ChargeService.EVENT_CHARGE_CREATED);
    }

    @Test
    void settleEmitsLedgerEventOnlyOnce() {
        Charge c = Charge.create("tx1", 2_500, "k", null, now, Duration.ofHours(1));
        when(charges.findByTxid("tx1")).thenReturn(Optional.of(c));
        PspWebhook hook = new PspWebhook("tx1", "E2E123", 2_500, now.plusSeconds(30));

        service.settle(hook);
        service.settle(hook); // PSP retry

        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox, times(1)).save(event.capture());
        assertThat(event.getValue().getType()).isEqualTo(ChargeService.EVENT_PIX_SETTLED);
        assertThat(event.getValue().getPayload())
                .contains("\"end_to_end_id\":\"E2E123\"")
                .contains("\"payee_account\":\"merchant-1\"")
                .contains("\"amount_cents\":2500");
    }
}
