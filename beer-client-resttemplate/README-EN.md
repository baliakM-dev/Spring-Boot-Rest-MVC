# Beer Client — RestTemplate + Resilience4j Demo

## Overview

Spring Boot 4.x REST client demonstrating:

- **`RestTemplate`** — classic synchronous HTTP client (legacy, but still common in enterprise)
- **Resilience4j** — Retry, CircuitBreaker and RateLimiter patterns (programmatic, not annotation-based)
- **Error handling** — mapping HTTP status codes to domain exceptions using `ProblemDetail` (RFC 7807)
- **Docker Compose** — run the entire stack with a single command

---

## Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 | Runtime |
| Spring Boot | 4.0.2 | Framework |
| Resilience4j | 2.3.0 | Resilience patterns |
| spring-boot-starter-aop | 3.4.2 | AOP proxy for Resilience4j |
| spring-data-commons | – | `Page` / `Pageable` abstraction |
| Lombok | 1.18.x | Boilerplate reduction |

> **Note:** Resilience4j is not included in the Spring Boot BOM — the version must be defined explicitly.
> This module uses programmatic configuration via `CircuitBreakerRegistry` / `RetryRegistry`
> (not YAML + `@CircuitBreaker` / `@Retry` annotations).

---

## Architecture

```
Postman / client
     │
     ▼
BeerController              ← REST API layer (:8082/client/beers)
     │
     ▼
BeerRestTemplateService     ← Resilience4j layer (Retry + CircuitBreaker)
     │
     ▼
RestTemplate (beerRestTemplate) ← HTTP client with rootUri, timeouts and JSON headers
     │
     ▼
beer-store server           ← remote API (:8080/api/v1/beers)
```

---

## Why RestTemplate?

`RestTemplate` has been in maintenance mode since Spring 5 (replacements: `RestClient`, `WebClient`).
Despite this, it is still widely used in enterprise applications.

This module demonstrates:
- Proper `rootUri` and timeout configuration
- Generic deserialization of paginated responses via `ParameterizedTypeReference`
- Propagating downstream HTTP errors as domain exceptions
- Programmatic Resilience4j integration (without Spring AOP annotations)

---

## Resilience4j — How and Why

### Problem

The remote `beer-store` server may be temporarily unavailable. Without protection:
- requests immediately fail with `500 Internal Server Error`
- during prolonged outages, threads are wasted waiting for timeouts
- the client has no information about what went wrong

### Solution — Three Patterns

#### 1. Retry

```java
RetryConfig config = RetryConfig.custom()
        .maxAttempts(3)                          // initial call + 2 retries
        .waitDuration(Duration.ofMillis(500))
        .retryExceptions(
                SocketTimeoutException.class,
                ConnectException.class           // network errors only
        )
        .build();
```

On a network error (I/O error, connection refused), the call is automatically retried up to 3 times with a 500 ms pause.
Only **network exceptions** are retried — HTTP 4xx errors (400, 404, 409) are **not retried**.

#### 2. CircuitBreaker

```java
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
        .slidingWindowType(COUNT_BASED)
        .slidingWindowSize(10)                   // evaluates the last 10 calls
        .failureRateThreshold(50)                // opens if ≥50% failure rate
        .waitDurationInOpenState(Duration.ofSeconds(10))
        .permittedNumberOfCallsInHalfOpenState(3)
        .build();
```

**Circuit breaker states:**

```
CLOSED → (≥50% failures) → OPEN → (after 10s) → HALF_OPEN → (3 test calls)
  ▲                                                                  │
  └──────────────────── (successful) ────────────────────────────────┘
                                          │
                                   (failed) → OPEN
```

| State | Behavior |
|---|---|
| `CLOSED` | Normal operation, calls go through |
| `OPEN` | Calls go directly to fallback, downstream is not loaded |
| `HALF_OPEN` | Allows 3 test calls to decide whether to close or reopen |

#### 3. RateLimiter

```java
RateLimiterConfig config = RateLimiterConfig.custom()
        .limitForPeriod(10)                      // max 10 calls per second
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ZERO)          // fail immediately if limit exceeded
        .build();
```

The RateLimiter is configured in `Resilience4jConfig`, but in the current version of
`BeerRestTemplateService` it is not actively applied. It is ready for future use
(e.g., before `withResilience()` or as a standalone decorator).

### Programmatic vs. Annotation-Based Configuration

This module uses **programmatic** configuration (not `@CircuitBreaker` / `@Retry` annotations):

```java
// Decoration: Retry wraps CircuitBreaker, CircuitBreaker wraps the actual call
Supplier<T> decorated = CircuitBreaker.decorateSupplier(cb,
        Retry.decorateSupplier(retry, call));
```

**Order matters:** `Retry → CircuitBreaker → API`.
Retry repeats the call *through* the CircuitBreaker — each attempt counts toward the sliding window.

**Advantages of programmatic configuration:**
- Explicit control over fallback logic directly in code
- No Spring AOP proxy required
- Fallback can be a lambda, not a separate method with a matching signature

---

## RestTemplate Configuration

```java
@Bean
public RestTemplate beerRestTemplate(RestTemplateBuilder builder,
        @Value("${beer-store.base-url}") String baseUrl) {
    return builder
            .rootUri(baseUrl)                    // prefix for all relative URIs
            .connectTimeout(Duration.ofSeconds(3))
            .readTimeout(Duration.ofSeconds(5))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
}
```

| Parameter | Value | Purpose |
|---|---|---|
| `rootUri` | `${beer-store.base-url}` | Prefix for all relative calls |
| `connectTimeout` | 3 s | Protection against hanging TCP connections |
| `readTimeout` | 5 s | Protection against slow responses |
| Default headers | `application/json` | Enforces JSON contract |

---

## HTTP Error Handling

`RestTemplate` throws `HttpStatusCodeException` on 4xx/5xx responses.
These are caught inside `withResilience()` and mapped to domain exceptions:

```
HTTP 409 Conflict      →  BeerAlreadyExistsException
HTTP 400 Bad Request   →  BeerValidationException
HTTP 404 Not Found     →  BeerNotFoundException
other                  →  rethrow (preserves original status + body for debugging)
```

Error details are read from `ProblemDetail` (RFC 7807 / RFC 9457) if the downstream returns it:

```java
ProblemDetail pd = ex.getResponseBodyAs(ProblemDetail.class);
String detail = (pd != null && pd.getDetail() != null) ? pd.getDetail() : ex.getMessage();
```

**Fallback rules:**
- `getAllBeers` → returns an empty page (UI can still render an empty list)
- `getBeerById`, `createBeer`, `updateBeerById`, `patchBeerById` → throws `ServiceUnavailableException`
  (cannot fabricate a specific resource or confirm creation)

---

## API Endpoints

Base URL: `http://localhost:8082/client/beers`

| Method | URL | Description | Success | Error |
|---|---|---|---|---|
| `GET` | `/client/beers` | List beers (paginated, filterable) | `200 OK` | `503` |
| `GET` | `/client/beers/{id}` | Get beer by ID | `200 OK` | `404`, `503` |
| `POST` | `/client/beers` | Create a beer | `201 Created` + `Location` header | `400`, `409`, `503` |
| `PUT` | `/client/beers/{id}` | Full update of a beer | `200 OK` | `400`, `404`, `409`, `503` |
| `PATCH` | `/client/beers/{id}` | Partial update of a beer | `200 OK` | `404`, `503` |

### Query Parameters for `GET /client/beers`

| Parameter | Type | Description |
|---|---|---|
| `beerName` | `String` | Filter by name |
| `upc` | `String` | Filter by UPC code |
| `showInventoryOnHand` | `Boolean` | Include inventory quantity in response |
| `page` | `Integer` | Page number (default `0`) |
| `size` | `Integer` | Page size (default `25`) |

### Example — Create a Beer

```json
POST /client/beers
{
  "beerName": "Pilsner Urquell",
  "upc": "123456789",
  "quantityOnHand": 100,
  "price": 2.50,
  "categoryIds": []
}
```

### Example — Response on Server Outage

```json
HTTP/1.1 503 Service Unavailable
{
  "type": "about:blank",
  "status": 503,
  "title": "Service Unavailable",
  "detail": "Beer service unavailable - cannot fetch beer: 550e8400-e29b-41d4-a716-446655440000"
}
```

---

## Project Structure

```
beer-client-resttemplate
│
├── controller/
│   └── BeerController              ← REST API layer
│
├── services/
│   └── BeerRestTemplateService     ← Resilience4j + HTTP calls
│
├── config/
│   ├── RestTemplateConfig          ← RestTemplate bean configuration
│   └── Resilience4jConfig          ← CircuitBreaker, Retry, RateLimiter registries
│
├── dto/
│   ├── BeerCreateRequestDTO
│   ├── BeerPatchRequestDTO
│   ├── BeerResponseDTO
│   ├── BeerUpdateRequestDTO
│   ├── CategoriesDTO
│   └── PagedResponse               ← generic paginated response wrapper
│
└── exceptions/
    ├── BeerAlreadyExistsException   ← 409 Conflict
    ├── BeerNotFoundException        ← 404 Not Found
    ├── BeerValidationException      ← 400 Bad Request
    └── ServiceUnavailableException  ← 503 / CB OPEN / network failure
```

---

## Running the Application

### Prerequisites
- Docker + Docker Compose
- Java 25
- Maven

### Commands

```bash
# Build
./mvnw clean package -DskipTests

# Run the entire stack (beer-store + beer-client-resttemplate)
docker compose up

# Run this module only (beer-store must be running separately)
./mvnw spring-boot:run
```

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `BEER_API_BASE_URL` | `http://localhost:8080` | URL of the beer-store server |

### Debug Port

The Dockerfile exposes port `5005` for remote debugging (JDWP).

---

## Testing Resilience

### Simulating an Outage

1. Start both servers (`beer-store` + `beer-client-resttemplate`)
2. Stop `beer-store`
3. Call any endpoint:
   - `getAllBeers` → returns an empty page (fallback)
   - `getBeerById` / `createBeer` → Retry attempts 3× (3 × 500 ms), then `503`
4. After ~5 more failures the CircuitBreaker **opens** — calls go directly to fallback without waiting
5. After 10 seconds it transitions to `HALF_OPEN` and allows 3 test calls
6. If `beer-store` is running → CircuitBreaker **closes** → normal operation resumes

### Logging

DEBUG logging for RestTemplate is enabled in `application-dev.yaml`:

```yaml
logging:
  level:
    org.springframework.web.client.RestTemplate: DEBUG
    org.springframework.http.converter.json: DEBUG
```

### Health Check

```
GET http://localhost:8082/actuator/health
```

---

## Comparison with Other Clients

| Feature | RestTemplate | RestClient | HttpExchange |
|---|---|---|---|
| API style | Imperative | Fluent / builder | Declarative (interface) |
| Resilience | Programmatic | Annotations (@Retry, @CB) | Annotations (@Retry, @CB) |
| Status | Maintenance | Active (Spring 6+) | Active (Spring 6+) |
| Async | No | No (sync version) | No (sync version) |
| Best suited for | Legacy projects | New projects | New projects with Feign-like style |

---

### 👤 Author

Ing. Martin Baliak
Backend Developer – Spring Boot
