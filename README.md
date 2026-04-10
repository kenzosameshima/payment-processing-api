# Payment Processing API

Production-ready REST API for payment creation, authorization, and settlement with idempotent processing guarantees and comprehensive error handling.

## Quick Start

### Prerequisites

- JDK 21+
- Maven 3.9+
- PostgreSQL 16+ (or use Docker)

### Option 1: Docker Compose (Recommended)

```bash
docker-compose up
```

The API starts on `http://localhost:8080` and PostgreSQL is configured automatically.

### Option 2: Local Development

1. **Create PostgreSQL database:**
   ```bash
   createdb payments
   createuser payments_user with password 'payments_pass'
   ```

2. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

3. **Verify it's running:**
   ```bash
   curl http://localhost:8080/health
   ```

## API Usage

All endpoints require the `Idempotency-Key` header to ensure idempotent processing.

### Create Payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-001" \
  -d '{
    "merchantReference": "ORDER-1001",
    "amount": 125.50,
    "currency": "USD"
  }'
```

Response:
```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "merchantReference": "ORDER-1001",
  "amount": 125.50,
  "currency": "USD",
  "status": "CREATED",
  "createdAt": "2026-04-10T10:15:30Z",
  "updatedAt": "2026-04-10T10:15:30Z"
}
```

### Authorize Payment

```bash
curl -X POST http://localhost:8080/api/v1/payments/550e8400-e29b-41d4-a716-446655440000/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-002" \
  -d '{
    "authorizationCode": "AUTH-20260410-001"
  }'
```

### Settle Payment

```bash
curl -X POST http://localhost:8080/api/v1/payments/550e8400-e29b-41d4-a716-446655440000/settle \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-003" \
  -d '{
    "settlementReference": "SET-20260410-001"
  }'
```

## Technology Stack

- **Java 21** — previous LTS release
- **Spring Boot 3** — Framework for REST APIs
- **Spring Data JPA** — Data persistence layer
- **PostgreSQL** — Relational database
- **Flyway** — Database migrations
- **JUnit 5 + Mockito** — Testing framework
- **Maven** — Build tool

## Project Structure

```
src/
├── main/
│   ├── java/com/example/payments/
│   │   ├── api/              # REST controllers, DTOs, error handlers
│   │   ├── service/          # Business logic, idempotency, mappers
│   │   ├── repository/       # JPA repositories
│   │   ├── domain/           # Entities, enums
│   │   └── config/           # Spring configuration
│   └── resources/
│       └── db/migration/     # Flyway SQL migrations
└── test/
    └── java/com/example/payments/
        ├── service/          # Unit tests for services
        └── repository/       # Integration tests for repositories
```

## Payment State Machine

```
CREATED --[authorize]--> AUTHORIZED --[settle]--> SETTLED
```

Invalid transitions are rejected with HTTP 422.

## Idempotency Guarantees

Each operation uses operation-scoped idempotency keys and an atomic database flow:

1. **First request for (operation, key)** inserts a `PROCESSING` marker row.
2. **Only the request that inserted the marker executes business logic.**
3. On success, marker row is updated to `COMPLETED` with the resulting `payment_id`.
4. Retries with the same key replay the persisted result.
5. If another request arrives while the first one is still running, API returns HTTP 409 (`IDEMPOTENCY_REQUEST_IN_PROGRESS`).

This guarantees no duplicate business execution for the same `(operation, idempotency_key)` pair under concurrency.

## Error Handling

All API responses include standardized error envelopes with:
- `timestamp` — ISO 8601
- `status` — HTTP status code
- `errorCode` — Machine-readable error identifier
- `message` — Human-readable message
- `details` — Array of additional details

### Error Codes

| Code | Status | Scenario |
|------|--------|----------|
| `VALIDATION_ERROR` | 400 | Invalid input |
| `MISSING_IDEMPOTENCY_KEY` | 400 | Missing header |
| `PAYMENT_NOT_FOUND` | 404 | Payment not found |
| `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 409 | Same key currently processing |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | Corrupted or inconsistent idempotency record |
| `CONCURRENT_MODIFICATION` | 409 | Optimistic locking conflict |
| `INVALID_PAYMENT_STATE` | 422 | State transition error |
| `INTERNAL_SERVER_ERROR` | 500 | Server error |

## Database Schema

Migrations apply automatically via Flyway on startup.

**Payments Table:**
- `id` (UUID) — Primary key
- `merchant_reference` (VARCHAR 128) — Merchant's order ID
- `amount` (NUMERIC 19,4) — Payment amount
- `currency` (VARCHAR 3) — ISO 4217 currency code
- `status` (VARCHAR 32) — CREATED | AUTHORIZED | SETTLED
- `version` (BIGINT) — Optimistic locking version column
- `created_at`, `updated_at`, `authorized_at`, `settled_at` — Timestamps

**Idempotency Records Table:**
- `id` (BIGSERIAL) — Primary key
- `operation` (VARCHAR 32) — Operation type
- `idempotency_key` (VARCHAR 128) — Client key
- `status` (VARCHAR 16) — PROCESSING | COMPLETED
- `payment_id` (UUID FK, nullable while PROCESSING) — Associated payment
- `updated_at` (TIMESTAMPTZ) — Last status update timestamp
- **Unique constraint:** `(operation, idempotency_key)`

## Testing

### Run All Tests

```bash
mvn clean test
```

**Test Coverage:**
- 14 total tests
- Payment service layer (5 tests)
- Idempotency orchestration (6 tests)
- Repository persistence (3 tests)

All tests use H2 with PostgreSQL compatibility mode.

## Docker Deployment

```bash
# Build
docker build -t payment-processing-api:latest .

# Run locally
docker-compose up

# Push to registry
docker tag payment-processing-api:latest registry.example.com/payment-processing-api:latest
docker push registry.example.com/payment-processing-api:latest
```

## CI/CD

GitHub Actions workflows:
- **Build & Test** — On push/PR to main/develop
- **CodeQL** — Security scanning
- **Dependabot** — Dependency updates

## Future Enhancements

- [ ] Authentication & authorization (Spring Security + OAuth 2.0)
- [ ] API rate limiting & throttling
- [ ] Structured logging (SLF4J + ELK)
- [ ] Metrics collection (Micrometer + Prometheus)
- [ ] Distributed tracing (Spring Cloud Sleuth)
- [ ] OpenAPI/Swagger documentation
- [ ] Load testing (Gatling/JMeter)
- [ ] Data encryption at rest
- [ ] Audit logging
