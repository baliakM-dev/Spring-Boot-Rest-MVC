# Beer Client — HttpExchange + Resilience4j Demo

## Overview

Spring Boot 4.x REST client demonstrating:

- **`@HttpExchange`** — declarative HTTP client (Feign alternative)
- **Resilience4j** — Retry, CircuitBreaker and Fallback patterns
- **Global error handling** — unified `ProblemDetail` format (RFC 7807)
- **Docker Compose** — run the full stack with a single command

---

## Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 | Runtime |
| Spring Boot | 4.0.2 | Framework |
| Resilience4j | 2.3.0 | Resilience patterns |
| spring-boot-starter-aspectj | 4.0.2 | AOP proxy required for Resilience4j annotations |
| Lombok | 1.18.x | Boilerplate reduction |

> **Note:** Spring Boot 4.x renamed `spring-boot-starter-aop` to `spring-boot-starter-aspectj`.
> Resilience4j is not in the Spring Boot BOM — the version must be defined explicitly.

---

## Architecture

```
Postman / client
     │
     ▼
BeerController          ← REST API layer (:8084/client/beers)
     │
     ▼
BeerClientService       ← Resilience4j layer (@Retry + @CircuitBreaker)
     │
     ▼
BeerClientApi           ← @HttpExchange declarative client
     │
     ▼
beer-store server       ← remote API (:8080/api/v1/beers)
```

---

## Resilience4j — How and Why

### The Problem

The remote `beer-store` server can be temporarily unavailable. Without protection:
- requests fail immediately with `500 Internal Server Error`
- during a prolonged outage, threads are unnecessarily blocked waiting for timeouts
- the client receives no meaningful information about what went wrong

### Solution — Three Patterns

#### 1. Retry
```yaml
resilience4j:
  retry:
    instances:
      beerClient:
        max-attempts: 3        # 3 total attempts (1 initial + 2 retries)
        wait-duration: 500ms   # wait between attempts
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
          - java.io.IOException
```

On a network error (I/O error, connection refused) the call is automatically retried up to 3 times with a 500ms pause. Only network errors are retried — 4xx errors (409 Conflict, 404 Not Found) are **not retried**.

#### 2. CircuitBreaker
```yaml
resilience4j:
  circuitbreaker:
    instances:
      beerClient:
        sliding-window-size: 10               # tracks last 10 calls
        failure-rate-threshold: 50            # opens at ≥50% failure rate
        wait-duration-in-open-state: 10s      # waits 10s before transitioning to HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3
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
| `OPEN` | Calls go directly to fallback, server is not hit |
| `HALF_OPEN` | Allows 3 probe calls, decides whether to close or reopen |

#### 3. Fallback
```java
@Retry(name = "beerClient")
@CircuitBreaker(name = "beerClient", fallbackMethod = "createBeerFallback")
public UUID createBeer(BeerCreateRequestDTO dto) { ... }

UUID createBeerFallback(BeerCreateRequestDTO dto, Throwable ex) {
    if (ex instanceof HttpClientErrorException.Conflict) {
        throw new ResourceAlreadyExistsExceptions(...);  // → 409
    }
    throw new BeerServiceUnavailableException(...);      // → 503
}
```

**Fallback method rules:**
1. Must have the **same parameters** as the main method + `Throwable` at the end
2. Must have the **same return type** as the main method
3. Must never return `null` — always return a meaningful value or throw an exception
4. **Never** combine `@ResponseStatus` on the exception class with `@ExceptionHandler` for the same class — Spring prioritises the annotation on the class and bypasses the handler, returning an empty body instead of JSON

**Annotation order matters:**
```java
@Retry(name = "beerClient")           // outer — wraps CircuitBreaker
@CircuitBreaker(name = "beerClient")  // inner — calls the API directly
```
Retry → CircuitBreaker → API. Retry therefore repeats the call through the CircuitBreaker, not directly against the API.

---

## HTTP Exchange Client

```java
@HttpExchange(url = "/api/v1/beers", accept = "application/json")
public interface BeerClientApi {

    @GetExchange
    PagedResponse<BeerResponseDTO> getAllBeers(...);

    @GetExchange(url = "/{beerId}")
    BeerResponseDTO getBeerById(@PathVariable UUID beerId);   // @PathVariable, NOT @RequestParam!

    @PostExchange
    ResponseEntity<Void> createBeer(@RequestBody BeerCreateRequestDTO dto);

    @PutExchange(url = "/{beerId}")
    BeerResponseDTO updateBeerById(@PathVariable UUID beerId, @RequestBody BeerCreateRequestDTO dto);
}
```

**Important:** For path parameters (`/{beerId}`) you must use `@PathVariable`. Using `@RequestParam` causes `IllegalArgumentException: Map has no value for 'beerId'` because Spring tries to resolve the URI template variable from the query string instead of the path.

**Create beer — Location header pattern:**

The server returns `201 Created` with an empty body and the UUID in the `Location` header:
```
Location: /api/v1/beers/550e8400-e29b-41d4-a716-446655440000
```

This is why `@PostExchange` returns `ResponseEntity<Void>` (not `UUID`) and the UUID is extracted from the header:
```java
URI location = response.getHeaders().getLocation();
String path = location.getPath();
UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
```

---

## Global Error Handling

All errors return a unified `ProblemDetail` format (RFC 7807):

```json
{
  "status": 503,
  "title": "Service Unavailable",
  "detail": "Beer service is currently unavailable. Please try again later.",
  "instance": "/client/beers",
  "cause": "I/O error on PUT request: null",
  "timestamp": "2026-02-18T21:00:00Z"
}
```

| Exception | HTTP Status | When |
|---|---|---|
| `BeerServiceUnavailableException` | `503 Service Unavailable` | Server down, circuit breaker OPEN |
| `ResourceNotFoundException` | `404 Not Found` | Beer with given ID does not exist |
| `ResourceAlreadyExistsExceptions` | `409 Conflict` | Beer with the same name already exists |
| `MethodArgumentNotValidException` | `400 Bad Request` | Request body validation failed |

> **Important:** `@ResponseStatus` on an exception class **must not coexist** with `@ExceptionHandler`
> for the same class in `@RestControllerAdvice`. Spring prioritises `@ResponseStatus` on the class,
> bypasses the handler and returns an empty body instead of JSON.

---

## API Endpoints

Base URL: `http://localhost:8084/client/beers`

| Method | URL | Description | Success | Error |
|---|---|---|---|---|
| `GET` | `/client/beers` | List beers (paginated) | `200 OK` | `503` |
| `GET` | `/client/beers/{id}` | Get beer by ID | `200 OK` | `404`, `503` |
| `POST` | `/client/beers` | Create beer | `201 Created` + `Location` header | `409`, `503` |
| `PUT` | `/client/beers/{id}` | Update beer | `200 OK` | `404`, `409`, `503` |

### Example — create beer
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

### Example — response when server is down
```json
HTTP/1.1 503 Service Unavailable
{
  "status": 503,
  "title": "Service Unavailable",
  "detail": "Beer service is currently unavailable. Please try again later.",
  "cause": "Beer store server is unavailable: Connection refused"
}
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

# Run the full stack
docker compose up

# Run beer-client only
./mvnw spring-boot:run
```

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `BEER_API_BASE_URL` | `http://localhost:8080` | URL of the beer-store server |

> **Note:** When running inside Docker, `localhost` refers to the container itself.
> Use the Docker Compose service name instead (e.g. `http://beer-store:8080`).

---

## Testing Resilience

### Simulating a Server Outage

1. Start both services
2. Stop `beer-store`
3. Call any endpoint — the first 2 attempts fail (Retry), the third triggers the fallback → `503`
4. After ~5 more failures the CircuitBreaker **opens** — calls go directly to the fallback without waiting
5. After 10 seconds it transitions to `HALF_OPEN` and allows 3 probe calls
6. If `beer-store` is running → CircuitBreaker **closes** → normal operation resumes

### Health Check
```
GET http://localhost:8084/actuator/health
```

### 👤 Autor

Ing. Martin Baliak
Backend Developer – Spring Boot
