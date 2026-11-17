package com.melqlima.pix.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.melqlima.pix.config.PixProperties;

/** Verifies the X-Signature header: hex HMAC-SHA256 of the raw request body. */
@Component
public class HmacSignatureVerifier {

    private final byte[] secret;

    public HmacSignatureVerifier(PixProperties props) {
        this.secret = props.webhookSecret().getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String body, String signature) {
        byte[] expected = sign(body).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature == null ? new byte[0] : signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        // Constant-time comparison prevents timing attacks.
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidSignatureException();
        }
    }

    public String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
