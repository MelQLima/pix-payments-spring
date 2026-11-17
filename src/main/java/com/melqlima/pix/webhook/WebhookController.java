package com.melqlima.pix.webhook;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.melqlima.pix.charge.ChargeService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Webhooks")
public class WebhookController {

    private final HmacSignatureVerifier verifier;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final ChargeService charges;

    public WebhookController(HmacSignatureVerifier verifier, ObjectMapper mapper, Validator validator,
                             ChargeService charges) {
        this.verifier = verifier;
        this.mapper = mapper;
        this.validator = validator;
        this.charges = charges;
    }

    /** The signature is computed over the raw body, so it is read as a String before parsing. */
    @PostMapping(path = "/v1/webhooks/psp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@RequestHeader(name = "X-Signature", required = false) String signature,
                                        @RequestBody String rawBody) throws JsonProcessingException {
        verifier.verify(rawBody, signature);
        PspWebhook hook = mapper.readValue(rawBody, PspWebhook.class);
        Set<ConstraintViolation<PspWebhook>> violations = validator.validate(hook);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
        charges.settle(hook);
        return ResponseEntity.noContent().build();
    }
}
