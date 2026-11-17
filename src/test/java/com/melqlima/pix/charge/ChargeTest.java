package com.melqlima.pix.charge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class ChargeTest {

    private final Instant now = Instant.parse("2026-11-16T12:00:00Z");

    private Charge charge() {
        return Charge.create("tx1", 15_990, "loja@exemplo.com.br", "Pedido 42", now, Duration.ofMinutes(30));
    }

    @Test
    void paysAnActiveCharge() {
        Charge c = charge();
        assertThat(c.markPaid("E2E1", 15_990, now.plusSeconds(60))).isTrue();
        assertThat(c.getStatus()).isEqualTo(ChargeStatus.PAID);
        assertThat(c.getEndToEndId()).isEqualTo("E2E1");
    }

    @Test
    void replayOfSameSettlementIsANoOp() {
        Charge c = charge();
        c.markPaid("E2E1", 15_990, now.plusSeconds(60));
        assertThat(c.markPaid("E2E1", 15_990, now.plusSeconds(61))).isFalse();
    }

    @Test
    void rejectsSecondPaymentWithDifferentEndToEndId() {
        Charge c = charge();
        c.markPaid("E2E1", 15_990, now.plusSeconds(60));
        assertThatThrownBy(() -> c.markPaid("E2E2", 15_990, now.plusSeconds(70)))
                .isInstanceOf(ChargeStateException.class)
                .hasMessageContaining("already paid");
    }

    @Test
    void rejectsWrongAmount() {
        assertThatThrownBy(() -> charge().markPaid("E2E1", 100, now.plusSeconds(60)))
                .isInstanceOf(ChargeStateException.class);
    }

    @Test
    void rejectsPaymentAfterExpiry() {
        assertThatThrownBy(() -> charge().markPaid("E2E1", 15_990, now.plus(Duration.ofHours(1))))
                .isInstanceOf(ChargeStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Charge.create("tx", 0, "k", null, now, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
