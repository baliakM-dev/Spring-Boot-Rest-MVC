# Beer Client — RestTemplate + Resilience4j Demo

## Prehľad

Spring Boot 4.x REST klient demonštrujúci:

- **`RestTemplate`** — klasický synchronný HTTP klient (legacy, ale stále bežný v enterprise)
- **Resilience4j** — Retry, CircuitBreaker a RateLimiter vzory (programaticky, nie cez anotácie)
- **Globálne spracovanie chýb** — mapovanie HTTP stavov na doménové výnimky s `ProblemDetail` (RFC 7807)
- **Docker Compose** — spustenie celého stacku jedným príkazom

---

## Stack

| Technológia | Verzia | Účel |
|---|---|---|
| Java | 25 | Runtime |
| Spring Boot | 4.0.2 | Framework |
| Resilience4j | 2.3.0 | Resilience patterns |
| spring-boot-starter-aop | 3.4.2 | AOP proxy pre Resilience4j |
| spring-data-commons | – | `Page` / `Pageable` abstrakcia |
| Lombok | 1.18.x | Boilerplate redukcia |

> **Poznámka:** Resilience4j nie je v Spring Boot BOM — verzia sa musí definovať explicitne.
> Tento modul používa programatickú konfiguráciu cez `CircuitBreakerRegistry` / `RetryRegistry`
> (nie YAML + `@CircuitBreaker` / `@Retry` anotácie).

---

## Architektúra

```
Postman / klient
     │
     ▼
BeerController              ← REST API vrstva (:8082/client/beers)
     │
     ▼
BeerRestTemplateService     ← Resilience4j vrstva (Retry + CircuitBreaker)
     │
     ▼
RestTemplate (beerRestTemplate) ← HTTP klient s rootUri, timeoutmi a JSON hlavičkami
     │
     ▼
beer-store server           ← vzdialená API (:8080/api/v1/beers)
```

---

## Prečo RestTemplate?

`RestTemplate` je v maintenance móde od Spring 5 (náhrada: `RestClient`, `WebClient`).
Napriek tomu ho nájdeš v mnohých enterprise projektoch.

Tento modul demonštruje:
- Správnu konfiguráciu `rootUri` a timeoutov
- Generickú deserializáciu stránkovaných odpovedí cez `ParameterizedTypeReference`
- Propagáciu downstream HTTP chýb ako doménových výnimiek
- Programatickú integráciu Resilience4j (bez Spring AOP anotácií)

---

## Resilience4j — ako a prečo

### Problém

Vzdialený `beer-store` server môže byť dočasne nedostupný. Bez ochrany:
- požiadavka okamžite zlyhá s `500 Internal Server Error`
- pri dlhodobom výpadku sa zbytočne zahlcujú vlákna čakaním na timeout
- klient nemá žiadnu informáciu čo sa stalo

### Riešenie — tri vzory

#### 1. Retry

```java
RetryConfig config = RetryConfig.custom()
        .maxAttempts(3)                          // initial call + 2 retries
        .waitDuration(Duration.ofMillis(500))
        .retryExceptions(
                SocketTimeoutException.class,
                ConnectException.class           // len sieťové chyby
        )
        .build();
```

Pri sieťovej chybe (I/O error, connection refused) sa volanie automaticky zopakuje až 3-krát s pauzou 500 ms.
Opakujú sa **len sieťové výnimky** — HTTP 4xx chyby (400, 404, 409) sa **neopakujú**.

#### 2. CircuitBreaker (Istič)

```java
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
        .slidingWindowType(COUNT_BASED)
        .slidingWindowSize(10)                   // sleduje posledných 10 volaní
        .failureRateThreshold(50)                // pri ≥50% chybovosti sa otvorí
        .waitDurationInOpenState(Duration.ofSeconds(10))
        .permittedNumberOfCallsInHalfOpenState(3)
        .build();
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

#### 3. RateLimiter

```java
RateLimiterConfig config = RateLimiterConfig.custom()
        .limitForPeriod(10)                      // max 10 volaní za sekundu
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ZERO)          // pri prekročení okamžite zlyhá
        .build();
```

RateLimiter je nakonfigurovaný v `Resilience4jConfig`, ale v aktuálnej verzii
`BeerRestTemplateService` nie je aktívne aplikovaný. Je pripravený na budúce zapojenie
(napr. pred `withResilience()` alebo ako samostatný dekorátor).

### Programatická vs. anotačná konfigurácia

Tento modul používa **programatickú** konfiguráciu (nie `@CircuitBreaker` / `@Retry` anotácie):

```java
// Dekorovanie: Retry obalí CircuitBreaker, CircuitBreaker obalí samotné volanie
Supplier<T> decorated = CircuitBreaker.decorateSupplier(cb,
        Retry.decorateSupplier(retry, call));
```

**Poradie je dôležité:** `Retry → CircuitBreaker → API`.
Retry teda opakuje volanie *cez* CircuitBreaker — každý pokus sa počíta do sliding window isteča.

**Výhody programatickej konfigurácie:**
- Explicitná kontrola nad fallback logikou priamo v kóde
- Nie je potrebný Spring AOP proxy
- Fallback môže byť lambda, nie samostatná metóda s rovnakou signatúrou

---

## RestTemplate konfigurácia

```java
@Bean
public RestTemplate beerRestTemplate(RestTemplateBuilder builder,
        @Value("${beer-store.base-url}") String baseUrl) {
    return builder
            .rootUri(baseUrl)                    // prefix pre všetky relatívne URL
            .connectTimeout(Duration.ofSeconds(3))
            .readTimeout(Duration.ofSeconds(5))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
}
```

| Parameter | Hodnota | Účel |
|---|---|---|
| `rootUri` | `${beer-store.base-url}` | Prefix všetkých relatívnych volaní |
| `connectTimeout` | 3 s | Ochrana pred visiacimi TCP spojeniami |
| `readTimeout` | 5 s | Ochrana pred pomalými odpoveďami |
| Default headers | `application/json` | Vynútenie JSON kontraktu |

---

## Spracovanie HTTP chýb

`RestTemplate` hádže `HttpStatusCodeException` pri 4xx/5xx stavoch.
Tieto sa zachytia v `withResilience()` a namapujú na doménové výnimky:

```
HTTP 409 Conflict  →  BeerAlreadyExistsException
HTTP 400 Bad Request  →  BeerValidationException
HTTP 404 Not Found  →  BeerNotFoundException
ostatné  →  rethrow (zachová pôvodný status + telo pre debugging)
```

Detail chyby sa číta z `ProblemDetail` (RFC 7807 / RFC 9457) ak ho downstream vracia:

```java
ProblemDetail pd = ex.getResponseBodyAs(ProblemDetail.class);
String detail = (pd != null && pd.getDetail() != null) ? pd.getDetail() : ex.getMessage();
```

**Pravidlá pre fallback:**
- `getAllBeers` → vráti prázdnu stránku (UI môže vykresliť prázdny zoznam)
- `getBeerById`, `createBeer`, `updateBeerById`, `patchBeerById` → hodí `ServiceUnavailableException`
  (nemôžeme vymyslieť konkrétny zdroj ani potvrdiť vytvorenie)

---

## API Endpointy

Base URL: `http://localhost:8082/client/beers`

| Metóda | URL | Popis | Success | Error |
|---|---|---|---|---|
| `GET` | `/client/beers` | Zoznam pív (stránkovaný, filtrovateľný) | `200 OK` | `503` |
| `GET` | `/client/beers/{id}` | Detail piva | `200 OK` | `404`, `503` |
| `POST` | `/client/beers` | Vytvorenie piva | `201 Created` + `Location` header | `400`, `409`, `503` |
| `PUT` | `/client/beers/{id}` | Úplná aktualizácia piva | `200 OK` | `400`, `404`, `409`, `503` |
| `PATCH` | `/client/beers/{id}` | Čiastočná aktualizácia piva | `200 OK` | `404`, `503` |

### Query parametre pre `GET /client/beers`

| Parameter | Typ | Popis |
|---|---|---|
| `beerName` | `String` | Filter podľa názvu |
| `upc` | `String` | Filter podľa UPC kódu |
| `showInventoryOnHand` | `Boolean` | Zahrnutie skladového množstva |
| `page` | `Integer` | Číslo stránky (default `0`) |
| `size` | `Integer` | Veľkosť stránky (default `25`) |

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
  "type": "about:blank",
  "status": 503,
  "title": "Service Unavailable",
  "detail": "Beer service unavailable - cannot fetch beer: 550e8400-e29b-41d4-a716-446655440000"
}
```

---

## Strom projektu

```
beer-client-resttemplate
│
├── controller/
│   └── BeerController              ← REST API vrstva
│
├── services/
│   └── BeerRestTemplateService     ← Resilience4j + HTTP volania
│
├── config/
│   ├── RestTemplateConfig          ← Bean konfigurácia RestTemplate
│   └── Resilience4jConfig          ← CircuitBreaker, Retry, RateLimiter registries
│
├── dto/
│   ├── BeerCreateRequestDTO
│   ├── BeerPatchRequestDTO
│   ├── BeerResponseDTO
│   ├── BeerUpdateRequestDTO
│   ├── CategoriesDTO
│   └── PagedResponse               ← generická stránkovaná odpoveď
│
└── exceptions/
    ├── BeerAlreadyExistsException   ← 409 Conflict
    ├── BeerNotFoundException        ← 404 Not Found
    ├── BeerValidationException      ← 400 Bad Request
    └── ServiceUnavailableException  ← 503 / CB OPEN / sieťový výpadok
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

# Spustenie celého stacku (beer-store + beer-client-resttemplate)
docker compose up

# Len tento modul (beer-store musí bežať samostatne)
./mvnw spring-boot:run
```

### Environment premenné

| Premenná | Default | Popis |
|---|---|---|
| `BEER_API_BASE_URL` | `http://localhost:8080` | URL beer-store servera |

### Debug port

Dockerfile exponuje port `5005` pre vzdialený debugger (JDWP).

---

## Testovanie resilience

### Simulácia výpadku

1. Spusti oba servery (`beer-store` + `beer-client-resttemplate`)
2. Vypni `beer-store`
3. Volaj ľubovoľný endpoint:
   - `getAllBeers` → vráti prázdnu stránku (fallback)
   - `getBeerById` / `createBeer` → Retry zopakuje 3× (3 × 500 ms), potom `503`
4. Po ~5 ďalších zlyhaniach sa CircuitBreaker **otvorí** — volania idú priamo do fallbacku bez čakania
5. Po 10 sekundách prejde do `HALF_OPEN` a pustí 3 testovacie volania
6. Ak `beer-store` beží → CircuitBreaker sa **zatvorí** → normálna prevádzka

### Logging

Zapnutý DEBUG logging pre RestTemplate v `application-dev.yaml`:

```yaml
logging:
  level:
    org.springframework.web.client.RestTemplate: DEBUG
    org.springframework.http.converter.json: DEBUG
```

### Health check

```
GET http://localhost:8082/actuator/health
```

---

## Porovnanie s ostatnými klientmi

| Vlastnosť | RestTemplate | RestClient | HttpExchange |
|---|---|---|---|
| API štýl | Imperatívny | Fluent / builder | Deklaratívny (interface) |
| Resilience | Programaticky | Anotácie (@Retry, @CB) | Anotácie (@Retry, @CB) |
| Stav | Maintenance | Aktívny (Spring 6+) | Aktívny (Spring 6+) |
| Async | Nie | Nie (sync verzia) | Nie (sync verzia) |
| Vhodné pre | Legacy projekty | Nové projekty | Nové projekty s Feign-like štýlom |

---

### 👤 Autor

Ing. Martin Baliak
Backend Developer – Spring Boot
