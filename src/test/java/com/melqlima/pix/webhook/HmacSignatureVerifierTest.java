package com.melqlima.pix.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.melqlima.pix.config.PixProperties;

class HmacSignatureVerifierTest {

    private final HmacSignatureVerifier verifier =
            new HmacSignatureVerifier(new PixProperties("m", "s3cr3t", "t", 10, Duration.ofHours(1)));

    @Test
    void acceptsValidSignature() {
        String body = "{\"txid\":\"abc\"}";
        String sig = verifier.sign(body);
        assertThat(sig).hasSize(64);
        assertThatCode(() -> verifier.verify(body, sig)).doesNotThrowAnyException();
        assertThatCode(() -> verifier.verify(body, sig.toUpperCase())).doesNotThrowAnyException();
    }

    @Test
    void rejectsTamperedBodyOrMissingSignature() {
        String sig = verifier.sign("{\"amountCents\":100}");
        assertThatThrownBy(() -> verifier.verify("{\"amountCents\":1}", sig)).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> verifier.verify("{}", null)).isInstanceOf(InvalidSignatureException.class);
    }
}
