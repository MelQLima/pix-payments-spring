# pix-pagamentos-spring

**Pix charges API** in **Java 21 + Spring Boot 3**: create Pix charges (QR Code / *copia e cola*), receive signed settlement webhooks from the PSP, and publish payment events to **Kafka** through a **transactional outbox**. Persistence on **PostgreSQL** with **Spring Data JPA/Hibernate** and **Flyway**; resilience with **Resilience4j**.

Portfolio project focused on the problems real payment backends face: duplicate requests, at-least-once webhooks, the dual-write problem between database and broker, and unstable third-party providers.

## Highlights

| Area | What is implemented |
|---|---|
| API | Spring Web MVC REST, **Bean Validation** (Jakarta), **RFC 9457/7807 `ProblemDetail`** errors via `@RestControllerAdvice`, **OpenAPI/Swagger UI** (springdoc) at `/docs` |
| Idempotency | `Idempotency-Key` header: first request executed and stored, retries replay the stored response, different payload → `422`, concurrent duplicate → `409` |
| Persistence | Spring Data JPA, Hibernate (`ddl-auto=validate`), **Flyway** migrations, **optimistic locking** (`@Version`), unique constraints, partial index |
| Messaging | **Transactional outbox** + scheduled publisher with `SELECT … FOR UPDATE SKIP LOCKED` (safe with multiple replicas), **spring-kafka** producer with `acks=all` and idempotence, ordering by message key |
| Webhooks | **HMAC-SHA256** signature over the raw body with constant-time comparison; replays are no-ops |
| Resilience | **Resilience4j** circuit breaker + retry with exponential backoff on the PSP client (`RestClient`), fallback → `503` |
| Domain | Rich domain model (state transitions inside the `Charge` aggregate), money in integer cents, expiry rules |
| Observability | **Spring Boot Actuator**, **Micrometer** + **Prometheus** (`/actuator/prometheus`, outbox gauge/counters), **OpenTelemetry** tracing (OTLP), liveness/readiness probes |
| Runtime | **Java 21 virtual threads**, layered Docker image on JRE 21 (non-root), Docker Compose (PostgreSQL, Kafka KRaft, Jaeger), **Kubernetes** manifests (Deployment, Service, HPA, Secrets) |
| Tests | **JUnit 5**, **Mockito**, **AssertJ**, `@WebMvcTest` + MockMvc, **Testcontainers** (PostgreSQL + Kafka) end-to-end test with `@ServiceConnection`, Awaitility, JaCoCo coverage; GitHub Actions CI |

## Flow

```
client ──POST /v1/charges (Idempotency-Key)──▶ ChargeService ──▶ PSP (circuit breaker + retry)
                                                   │
                                   one DB transaction: charge + outbox event
                                                   │
PSP ──POST /v1/webhooks/psp (X-Signature)──▶ settle charge + outbox "pix.settled"
                                                   │
                         OutboxPublisher (SKIP LOCKED) ──▶ Kafka "payments.events"
                                                   │
                                   consumed by the Go ledger (conciliacao-ledger-go)
```

## Run

```bash
docker compose up --build      # API on :8080, Swagger UI on /docs, Jaeger on :16686
mvn verify                     # unit + integration tests (needs Docker for Testcontainers)
```

```bash
curl -i -X POST localhost:8080/v1/charges \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: 6f1c2a' \
  -d '{"amountCents": 15990, "pixKey": "loja@exemplo.com.br", "description": "Pedido 42"}'

BODY='{"txid":"<txid>","endToEndId":"E00000000202611161200abc","amountCents":15990,"paidAt":"2026-11-16T12:01:00Z"}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac local-dev-secret | cut -d' ' -f2)
curl -i -X POST localhost:8080/v1/webhooks/psp -H 'Content-Type: application/json' -H "X-Signature: $SIG" -d "$BODY"
```

## Configuration

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | local PostgreSQL | JDBC connection |
| `KAFKA_BROKERS` | `localhost:9092` | Kafka bootstrap servers |
| `PIX_WEBHOOK_SECRET` | `local-dev-secret` | HMAC secret shared with the PSP |
| `PIX_PSP_MODE` | `fake` | `fake` (local BR Code) or `http` (real PSP at `PIX_PSP_BASE_URL`) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` | tracing collector |
