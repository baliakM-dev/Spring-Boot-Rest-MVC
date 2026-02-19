# Beer Client — RestClient + Resilience4j Demo

## Overview

Spring Boot 4.x REST client demonstrating:

- **`RestClient`** — modern synchronous HTTP client (replacement for `RestTemplate`, Spring 6+)
- **Resilience4j** — Retry, CircuitBreaker and Fallback patterns (annotation-based)
- **Global error handling** — mapping HTTP status codes to domain exceptions using `ProblemDetail` (RFC 7807)
- **Docker Compose** — run the full stack with a single command

---

## Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 | Runtime |
| Spring Boot | 4.0.2 | Framework |
| Spring Cloud (Resilience4j) | 2025.1.0 | Resilience patterns |
| spring-boot-starter-aop | 3.4.2 | AOP proxy required for Resilience4j annotations |
| spring-data-commons | – | `Page` / `Pageable` abstraction |
| Lombok | 1.18.x | Boilerplate reduction |

> **Note:** Resilience4j is included via `spring-cloud-starter-circuitbreaker-resilience4j`.
> This module uses **annotation-based** configuration (`@CircuitBreaker`, `@Retry`) — in contrast to
> `beer-client-resttemplate`, which uses programmatic configuration.

---

## Architecture

```
Postman / client
     │
     ▼
BeerController              ← REST API layer (:8083/client/beers)
     │
     ▼
BeerRestClientService       ← Resilience4j layer (@Retry + @CircuitBreaker)
     │
     ▼
RestClient (beerRestClient) ← HTTP client with baseUrl, logging and JSON headers
     │
     ▼
beer-store server           ← remote API (:8080/api/v1/beers)
```

---

## Why RestClient?

`RestClient` is the modern replacement for `RestTemplate` introduced in Spring Framework 6.

This module demonstrates:
- Fluent (builder) API for HTTP calls
- Centralised `baseUrl` and header configuration in a single bean
- Request/response logging via `ClientHttpRequestInterceptor`
- Generic deserialization of paginated responses via `ParameterizedTypeReference`
- Propagating downstream HTTP errors as domain exceptions
- Annotation-based Resilience4j integration (`@CircuitBreaker`, `@Retry`)

---

## Resilience4j — How and Why

### The Problem

The remote `beer-store` server can be temporarily unavailable. Without protection:
- requests fail immediately with `500 Internal Server Error`
- during a prolonged outage, threads are unnecessarily blocked waiting for timeouts
- the client receives no meaningful information about what went wrong

### Solution — Two Patterns

#### 1. Retry

```yaml
resilience4j:
  retry:
    instances:
      beerService:
        maxAttempts: 3       # 3 total attempts (1 initial + 2 retries)
        waitDuration: 500ms  # wait between attempts
```

On a network error, the call is automatically retried up to 3 times with a 500 ms pause.
Only **network errors** are retried — HTTP 4xx errors (404, 409) are **not retried**.

#### 2. CircuitBreaker

```yaml
resilience4j:
  circuitbreaker:
    instances:
      beerService:
        slidingWindowSize: 10                          # evaluates the last 10 calls
        minimumNumberOfCalls: 5                        # minimum calls before evaluation
        failureRateThreshold: 50                       # opens at ≥50% failure rate
        waitDurationInOpenState: 10s                   # waits 10s before transitioning to HALF_OPEN
        permittedNumberOfCallsInHalfOpenState: 3
```

**Circuit breaker states:**

```
CLOSED → (≥50% failures) → OPEN → (after 10s) → HALF_OPEN → (3 probe calls)
  ▲                                                                │
  └────────────────── (successful) ────────────────────────────────┘
                                           │
                                   (failed) → OPEN
```

| State | Behaviour |
|---|---|
| `CLOSED` | Normal operation, calls pass through |
| `OPEN` | Calls go directly to fallback, downstream is not hit |
| `HALF_OPEN` | Allows 3 probe calls, decides whether to close or reopen |

#### 3. Fallback

```java
@CircuitBreaker(name = "beerService", fallbackMethod = "getBeerByIdFallback")
@Retry(name = "beerService")
public BeerResponseDTO getBeerById(UUID beerId) { ... }

private BeerResponseDTO getBeerByIdFallback(UUID beerId, Throwable t) {
    if (t instanceof HttpClientErrorException.NotFound) {
        throw new ResourceNotFoundException("Beer", "id", beerId.toString());
    }
    throw new BeerServiceUnavailableException("Beer service unavailable");
}
```

**Fallback method rules:**
1. Must have the **same parameters** as the main method + `Throwable` at the end
2. Must have the **same return type** as the main method
3. HTTP 4xx errors (404, 409) are mapped to domain exceptions — **not** to `BeerServiceUnavailableException`
4. `getAllBeers` fallback returns an **empty page** (UI-friendly — better than 503 for listing endpoints)

**Annotation order matters:**
```java
@CircuitBreaker(name = "beerService")  // outer — wraps Retry
@Retry(name = "beerService")           // inner — calls the API directly
```
CircuitBreaker → Retry → API. Each attempt counts toward the sliding window of the circuit breaker.

---

## RestClient Configuration

```java
@Bean
public RestClient restClient(@Value("${beer-store.base-url}") String baseUrl) {
    return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .requestInterceptors(interceptors -> interceptors.add(loggingInterceptor()))
            .build();
}
```

| Parameter | Value | Purpose |
|---|---|---|
| `baseUrl` | `${beer-store.base-url}` | Prefix for all relative calls |
| Default headers | `application/json` | Enforces JSON contract |
| `loggingInterceptor` | – | Logs method, URI and HTTP status for every outbound call |

---

## HTTP Error Handling

`RestClient` throws `HttpClientErrorException` on 4xx responses. These are caught
in the fallback methods and mapped to domain exceptions:

```
HTTP 404 Not Found  →  ResourceNotFoundException
HTTP 409 Conflict   →  ResourceAlreadyExistsExceptions
I/O error / CB OPEN →  BeerServiceUnavailableException
```

**Fallback rules:**
- `getAllBeers` → returns an empty page (UI can still render an empty list)
- `getBeerById`, `createBeer`, `updateBeerById` → throws `BeerServiceUnavailableException`
  on infrastructure failures (cannot fabricate a specific resource)

---

## Global Error Handling

All errors return a unified `ProblemDetail` format (RFC 7807):

```json
{
  "status": 404,
  "title": "Resource not found",
  "detail": "Beer not found with id: '550e8400-e29b-41d4-a716-446655440000'",
  "instance": "/client/beers/550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-02-18T21:00:00Z"
}
```

| Exception | HTTP Status | When |
|---|---|---|
| `BeerServiceUnavailableException` | `503 Service Unavailable` | Server down, CB OPEN |
| `ResourceNotFoundException` | `404 Not Found` | Beer with given ID does not exist |
| `ResourceAlreadyExistsExceptions` | `409 Conflict` | Beer with the same name/UPC already exists |
| `MethodArgumentNotValidException` | `400 Bad Request` | Request body validation failed |

---

## API Endpoints

Base URL: `http://localhost:8083/client/beers`

| Method | URL | Description | Success | Error |
|---|---|---|---|---|
| `GET` | `/client/beers` | List beers (paginated, filterable) | `200 OK` | `503` |
| `GET` | `/client/beers/{id}` | Get beer by ID | `200 OK` | `404`, `503` |
| `POST` | `/client/beers` | Create a beer | `201 Created` + `Location` header | `400`, `409`, `503` |
| `PUT` | `/client/beers/{id}` | Full update of a beer | `200 OK` | `400`, `404`, `409`, `503` |

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
  "status": 503,
  "error": "Service Unavailable",
  "message": "Beer service is currently unavailable. Please try again later."
}
```

---

## Project Structure

```
beer-client-restclient
│
├── controller/
│   └── BeerController              ← REST API layer
│
├── service/
│   └── BeerRestClientService       ← Resilience4j + HTTP calls
│
├── config/
│   └── RestClientConfig            ← RestClient bean + logging interceptor
│
├── dto/
│   ├── BeerCreateRequestDTO
│   ├── BeerUpdateRequestDTO
│   ├── BeerResponseDTO
│   ├── CategoriesDTO
│   └── PagedResponse               ← generic paginated response wrapper
│
└── exceptions/
    ├── ResourceAlreadyExistsExceptions  ← 409 Conflict
    ├── ResourceNotFoundException        ← 404 Not Found
    ├── BeerServiceUnavailableException  ← 503 / CB OPEN / network failure
    └── GlobalExceptionHandler           ← RFC 7807 ProblemDetail handler
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

# Run the full stack (beer-store + beer-client-restclient)
docker compose up

# Run this module only (beer-store must be running separately)
./mvnw spring-boot:run
```

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `BEER_API_BASE_URL` | `http://localhost:8080` | URL of the beer-store server |

---

## Testing Resilience

### Simulating a Server Outage

1. Start both servers (`beer-store` + `beer-client-restclient`)
2. Stop `beer-store`
3. Call any endpoint:
   - `getAllBeers` → returns an empty page (fallback)
   - `getBeerById` / `createBeer` → Retry attempts 3× (3 × 500 ms), then `503`
4. After ~5 more failures the CircuitBreaker **opens** — calls go directly to fallback without waiting
5. After 10 seconds it transitions to `HALF_OPEN` and allows 3 probe calls
6. If `beer-store` is running → CircuitBreaker **closes** → normal operation resumes

### Logging

DEBUG logging for RestClient is enabled in `application-dev.yaml`:

```yaml
logging:
  level:
    org.springframework.web.client.RestClient: DEBUG
    org.springframework.http.converter.json: DEBUG
```

### Health Check

```
GET http://localhost:8083/actuator/health
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
