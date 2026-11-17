package com.melqlima.pix.psp;

import java.time.Instant;

/** Port to the payment service provider (bank) that registers charges in the SPI/DICT. */
public interface PspClient {

    PspCharge register(String txid, long amountCents, String pixKey, Instant expiresAt);
}
