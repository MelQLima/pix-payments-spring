package com.melqlima.pix.charge;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.melqlima.pix.idempotency.IdempotencyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/v1/charges")
@Tag(name = "Charges", description = "Pix immediate charges (QR Code / copia e cola)")
public class ChargeController {

    private final ChargeService service;
    private final IdempotencyService idempotency;

    public ChargeController(ChargeService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping
    @Operation(summary = "Create a charge", description = "Requires an Idempotency-Key header; safe to retry.")
    public ResponseEntity<ChargeResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody CreateChargeRequest request) {
        ChargeResponse body = idempotency.execute(idempotencyKey, request, ChargeResponse.class,
                () -> ChargeResponse.from(service.create(request)));
        return ResponseEntity.created(URI.create("/v1/charges/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a charge")
    public ChargeResponse get(@PathVariable UUID id) {
        return ChargeResponse.from(service.get(id));
    }
}
