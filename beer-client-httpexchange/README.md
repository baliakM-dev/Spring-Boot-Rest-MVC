# Beer Client — HttpExchange + Resilience4j Demo

## Prehľad

Spring Boot 4.x REST klient demonštrujúci:

- **`@HttpExchange`** — deklaratívny HTTP klient (náhrada za Feign)
- **Resilience4j** — Retry, CircuitBreaker a Fallback vzory
- **Globálne spracovanie chýb** — jednotný `ProblemDetail` formát (RFC 7807)
- **Docker Compose** — spustenie celého stacku jedným príkazom

---

## Stack

| Technológia | Verzia | Účel |
|---|---|---|
| Java | 25 | Runtime |
| Spring Boot | 4.0.2 | Framework |
| Resilience4j | 2.3.0 | Resilience patterns |
| spring-boot-starter-aspectj | 4.0.2 | AOP proxy pre Resilience4j anotácie |
| Lombok | 1.18.x | Boilerplate redukcia |

> **Poznámka:** Spring Boot 4.x premenoval `spring-boot-starter-aop` na `spring-boot-starter-aspectj`.
> Resilience4j nie je v Spring Boot BOM — verzia sa musí definovať explicitne.

---

## Architektúra

```
Postman / klient
     │
     ▼
BeerController          ← REST API vrstva (:8084/client/beers)
     │
     ▼
BeerClientService       ← Resilience4j vrstva (@Retry + @CircuitBreaker)
     │
     ▼
BeerClientApi           ← @HttpExchange deklaratívny klient
     │
     ▼
beer-store server       ← vzdialená API (:8080/api/v1/beers)
```

---

## Resilience4j — ako a prečo

### Problém

Vzdialený `beer-store` server môže byť dočasne nedostupný. Bez ochrany:
- požiadavka okamžite zlyhá s `500 Internal Server Error`
- pri dlhodobom výpadku sa zbytočne zahlcujú vlákna čakaním na timeout
- klient nemá žiadnu informáciu čo sa stalo

### Riešenie — tri vzory

#### 1. Retry
```yaml
resilience4j:
  retry:
    instances:
      beerClient:
        max-attempts: 3        # celkovo 3 pokusy (1 + 2 opakovania)
        wait-duration: 500ms   # čakanie medzi pokusmi
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
          - java.io.IOException
```

Pri sieťovej chybe (I/O error, connection refused) sa volanie automaticky zopakuje až 3-krát s pauzou 500ms. Opakujú sa len sieťové chyby — 4xx chyby (409 Conflict, 404 Not Found) sa **neopakujú**.

#### 2. CircuitBreaker (Istič)
```yaml
resilience4j:
  circuitbreaker:
    instances:
      beerClient:
        sliding-window-size: 10               # sleduje posledných 10 volaní
        failure-rate-threshold: 50            # pri ≥50% chybovosti sa otvorí
        wait-duration-in-open-state: 10s      # 10s čaká kým prejde do HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3
```

**Stavy isteča:**

```
CLOSED → (≥50% chýb) → OPEN → (po 10s) → HALF_OPEN → (3 testovacie volania)
  ▲                                                            │
  └──────────────── (úspešné) ─────────────────────────────────┘
                                      │
                              (neúspešné) → OPEN
```

| Stav | Správanie |
|---|---|
| `CLOSED` | Normálna prevádzka, volania prechádzajú |
| `OPEN` | Volania okamžite smerujú do fallbacku, server sa nezaťažuje |
| `HALF_OPEN` | Pustí 3 testovacie volania, rozhodne či zavrieť alebo znovu otvoriť |

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

**Pravidlá fallback metódy:**
1. Musí mať **rovnaké parametre** ako hlavná metóda + `Throwable` na konci
2. Musí mať **rovnaký návratový typ** ako hlavná metóda
3. Nesmie vrátiť `null` — vždy vráti zmysluplnú hodnotu alebo hodí výnimku
4. **Nikdy** nekombinuj `@ResponseStatus` na exception triede s `@ExceptionHandler` — Spring uprednostní anotáciu na triede a obíde handler

**Poradie anotácií je dôležité:**
```java
@Retry(name = "beerClient")           // vonkajší — obalí CircuitBreaker
@CircuitBreaker(name = "beerClient")  // vnútorný — volá priamo API
```
Retry → CircuitBreaker → API. Retry teda opakuje volanie cez CircuitBreaker, nie priamo API.

---

## HTTP Exchange klient

```java
@HttpExchange(url = "/api/v1/beers", accept = "application/json")
public interface BeerClientApi {

    @GetExchange
    PagedResponse<BeerResponseDTO> getAllBeers(...);

    @GetExchange(url = "/{beerId}")
    BeerResponseDTO getBeerById(@PathVariable UUID beerId);   // @PathVariable, nie @RequestParam!

    @PostExchange
    ResponseEntity<Void> createBeer(@RequestBody BeerCreateRequestDTO dto);

    @PutExchange(url = "/{beerId}")
    BeerResponseDTO updateBeerById(@PathVariable UUID beerId, @RequestBody BeerCreateRequestDTO dto);
}
```

**Dôležité:** Pri path parametroch (`/{beerId}`) musíš použiť `@PathVariable`. Použitie `@RequestParam` spôsobí `IllegalArgumentException: Map has no value for 'beerId'`.

**Vytvorenie beeru — Location header pattern:**

Server vracia `201 Created` s prázdnym telom a UUID v `Location` hlavičke:
```
Location: /api/v1/beers/550e8400-e29b-41d4-a716-446655440000
```

Preto `@PostExchange` vracia `ResponseEntity<Void>` (nie `UUID`) a UUID sa extrahuje z hlavičky:
```java
URI location = response.getHeaders().getLocation();
String path = location.getPath();
UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
```

---

## Globálne spracovanie chýb

Všetky chyby vracajú jednotný `ProblemDetail` formát (RFC 7807):

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

| Exception | HTTP Status | Kedy |
|---|---|---|
| `BeerServiceUnavailableException` | `503 Service Unavailable` | Server vypnutý, circuit breaker OPEN |
| `ResourceNotFoundException` | `404 Not Found` | Beer s daným ID neexistuje |
| `ResourceAlreadyExistsExceptions` | `409 Conflict` | Beer s rovnakým názvom už existuje |
| `MethodArgumentNotValidException` | `400 Bad Request` | Validácia request body zlyhala |

> **Dôležité:** `@ResponseStatus` na exception triede **nesmie koexistovať** s `@ExceptionHandler`
> pre rovnakú triedu v `@RestControllerAdvice`. Spring uprednostní `@ResponseStatus` na triede,
> obíde handler a vráti prázdne telo namiesto JSON.

---

## API Endpointy

Base URL: `http://localhost:8084/client/beers`

| Metóda | URL | Popis | Success | Error |
|---|---|---|---|---|
| `GET` | `/client/beers` | Zoznam pív (stránkovaný) | `200 OK` | `503` |
| `GET` | `/client/beers/{id}` | Detail piva | `200 OK` | `404`, `503` |
| `POST` | `/client/beers` | Vytvorenie piva | `201 Created` + `Location` header | `409`, `503` |
| `PUT` | `/client/beers/{id}` | Aktualizácia piva | `200 OK` | `404`, `409`, `503` |

### Príklad — vytvorenie piva
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

### Príklad — odpoveď pri výpadku servera
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

## Spustenie

### Predpoklady
- Docker + Docker Compose
- Java 25
- Maven

### Príkazy
```bash
# Build
./mvnw clean package -DskipTests

# Spustenie celého stacku
docker compose up

# Len beer-client
./mvnw spring-boot:run
```

### Environment premenné

| Premenná | Default | Popis |
|---|---|---|
| `BEER_API_BASE_URL` | `http://localhost:8080` | URL beer-store servera |

---

## Testovanie resilience

### Simulácia výpadku

1. Spusti oba servery
2. Vypni `beer-store`
3. Volaj ľubovoľný endpoint — prvé 2 pokusy zlyhajú (Retry), tretí aktivuje fallback → `503`
4. Po ~5 ďalších zlyhaniach sa CircuitBreaker **otvorí** — volania idú priamo do fallbacku bez čakania
5. Po 10 sekundách prejde do `HALF_OPEN` a pustí 3 testovacie volania
6. Ak `beer-store` beží → CircuitBreaker sa **zatvorí** → normálna prevádzka

### Health check
```
GET http://localhost:8084/actuator/health
```

### 👤 Autor

Ing. Martin Baliak
Backend Developer – Spring Boot
