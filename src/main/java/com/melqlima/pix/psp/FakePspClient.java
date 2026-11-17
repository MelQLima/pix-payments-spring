package com.melqlima.pix.psp;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local/test adapter that builds a deterministic BR Code without calling a bank. */
@Component
@ConditionalOnProperty(name = "pix.psp.mode", havingValue = "fake", matchIfMissing = true)
public class FakePspClient implements PspClient {

    @Override
    public PspCharge register(String txid, long amountCents, String pixKey, Instant expiresAt) {
        String amount = String.format("%d.%02d", amountCents / 100, amountCents % 100);
        return new PspCharge("00020101021226" + pixKey.length() + pixKey + "5303986540" + amount.length() + amount
                + "5802BR62" + (txid.length() + 4) + "05" + txid.length() + txid + "6304FAKE");
    }
}
