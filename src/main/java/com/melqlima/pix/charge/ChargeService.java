package com.melqlima.pix.charge;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.melqlima.pix.config.PixProperties;
import com.melqlima.pix.outbox.OutboxEvent;
import com.melqlima.pix.outbox.OutboxRepository;
import com.melqlima.pix.psp.PspCharge;
import com.melqlima.pix.psp.PspClient;
import com.melqlima.pix.webhook.PspWebhook;

@Service
public class ChargeService {

    public static final String EVENT_CHARGE_CREATED = "pix.charge.created";
    public static final String EVENT_PIX_SETTLED = "pix.settled";

    private final ChargeRepository charges;
    private final OutboxRepository outbox;
    private final PspClient psp;
    private final PixProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ChargeService(ChargeRepository charges, OutboxRepository outbox, PspClient psp, PixProperties props,
                         ObjectMapper mapper, Clock clock, PlatformTransactionManager txManager) {
        this.charges = charges;
        this.outbox = outbox;
        this.psp = psp;
        this.props = props;
        this.mapper = mapper;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Registers the charge at the PSP and persists it. The remote call happens
     * outside the database transaction so no connection is held while waiting
     * on the network; the charge and its outbox event are saved atomically.
     */
    public Charge create(CreateChargeRequest request) {
        Instant now = Instant.now(clock);
        Duration expiry = request.expiresInSeconds() != null
                ? Duration.ofSeconds(request.expiresInSeconds())
                : props.defaultExpiry();
        String txid = UUID.randomUUID().toString().replace("-", "");
        Charge charge = Charge.create(txid, request.amountCents(), request.pixKey(), request.description(), now, expiry);

        PspCharge registered = psp.register(txid, charge.getAmountCents(), charge.getPixKey(), charge.getExpiresAt());
        charge.attachCopyPaste(registered.copyPaste());

        return tx.execute(status -> {
            Charge saved = charges.save(charge);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("charge_id", saved.getId().toString());
            payload.put("txid", saved.getTxid());
            payload.put("amount_cents", saved.getAmountCents());
            payload.put("expires_at", saved.getExpiresAt().toString());
            outbox.save(newEvent(saved.getTxid(), EVENT_CHARGE_CREATED, payload, now));
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public Charge get(UUID id) {
        return charges.findById(id).orElseThrow(() -> new ChargeNotFoundException(id.toString()));
    }

    /** Applies a PSP settlement webhook. Replays are no-ops (no duplicate events). */
    @Transactional
    public Charge settle(PspWebhook hook) {
        Charge charge = charges.findByTxid(hook.txid()).orElseThrow(() -> new ChargeNotFoundException(hook.txid()));
        if (charge.markPaid(hook.endToEndId(), hook.amountCents(), hook.paidAt())) {
            // Same contract consumed by the Go ledger (conciliacao-ledger-go).
            Map<String, Object> payload = new LinkedHashMap<>();
            UUID eventId = UUID.randomUUID();
            payload.put("event_id", eventId.toString());
            payload.put("type", EVENT_PIX_SETTLED);
            payload.put("end_to_end_id", hook.endToEndId());
            payload.put("payer_account", "external:pix");
            payload.put("payee_account", props.merchantAccount());
            payload.put("amount_cents", hook.amountCents());
            payload.put("occurred_at", hook.paidAt().toString());
            outbox.save(new OutboxEvent(eventId, charge.getTxid(), EVENT_PIX_SETTLED, json(payload), Instant.now(clock)));
        }
        return charge;
    }

    private OutboxEvent newEvent(String aggregateId, String type, Map<String, Object> payload, Instant now) {
        return new OutboxEvent(UUID.randomUUID(), aggregateId, type, json(payload), now);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
