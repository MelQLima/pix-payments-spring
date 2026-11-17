package com.melqlima.pix.charge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.melqlima.pix.idempotency.IdempotencyConflictException;
import com.melqlima.pix.idempotency.IdempotencyService;

@WebMvcTest(ChargeController.class)
class ChargeControllerTest {

    private static final String BODY = """
            {"amountCents": 15990, "pixKey": "loja@exemplo.com.br", "description": "Pedido 42"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ChargeService service;

    @MockitoBean
    private IdempotencyService idempotency;

    @Test
    void createsCharge() throws Exception {
        Charge charge = Charge.create("abc", 15_990, "loja@exemplo.com.br", "Pedido 42", Instant.now(), Duration.ofHours(1));
        when(service.create(any())).thenReturn(charge);
        when(idempotency.execute(anyString(), any(), eq(ChargeResponse.class), any()))
                .thenAnswer(inv -> inv.<Supplier<ChargeResponse>>getArgument(3).get());

        mvc.perform(post("/v1/charges").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/charges/" + charge.getId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.amountCents").value(15990));
    }

    @Test
    void rejectsInvalidPayloadWithProblemDetail() throws Exception {
        mvc.perform(post("/v1/charges").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountCents\": -1, \"pixKey\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void requiresIdempotencyKey() throws Exception {
        mvc.perform(post("/v1/charges").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsIdempotencyConflict() throws Exception {
        when(idempotency.execute(anyString(), any(), eq(ChargeResponse.class), any()))
                .thenThrow(new IdempotencyConflictException(HttpStatus.UNPROCESSABLE_ENTITY, "different payload"));

        mvc.perform(post("/v1/charges").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Idempotency conflict"));
    }

    @Test
    void returns404ForUnknownCharge() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ChargeNotFoundException(id.toString()));

        mvc.perform(get("/v1/charges/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
