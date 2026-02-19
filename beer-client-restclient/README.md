# Beer Client — RestClient + Resilience4j Demo

## Prehľad

Spring Boot 4.x REST klient demonštrujúci:

- **`RestClient`** — moderný synchronný HTTP klient (náhrada za `RestTemplate`, Spring 6+)
- **Resilience4j** — Retry, CircuitBreaker a Fallback vzory (cez anotácie)
- **Globálne spracovanie chýb** — mapovanie HTTP stavov na doménové výnimky s `ProblemDetail` (RFC 7807)
- **Docker Compose** — spustenie celého stacku jedným príkazom

---

## Stack

| Technológia | Verzia | Účel |
|---|---|---|
| Java | 25 | Runtime |
| Spring Boot | 4.0.2 | Framework |
| Spring Cloud (Resilience4j) | 2025.1.0 | Resilience patterns |
| spring-boot-starter-aop | 3.4.2 | AOP proxy pre Resilience4j anotácie |
| spring-data-commons | – | `Page` / `Pageable` abstrakcia |
| Lombok | 1.18.x | Boilerplate redukcia |

> **Poznámka:** Resilience4j je zahrnutý cez `spring-cloud-starter-circuitbreaker-resilience4j`.
> Tento modul používa **anotačnú** konfiguráciu (`@CircuitBreaker`, `@Retry`) — na rozdiel od
> `beer-client-resttemplate`, ktorý používa programatickú konfiguráciu.

---

## Architektúra

```
Postman / klient
     │
     ▼
BeerController              ← REST API vrstva (:8083/client/beers)
     │
     ▼
BeerRestClientService       ← Resilience4j vrstva (@Retry + @CircuitBreaker)
     │
     ▼
RestClient (beerRestClient) ← HTTP klient s baseUrl, loggingom a JSON hlavičkami
     │
     ▼
beer-store server           ← vzdialená API (:8080/api/v1/beers)
```

---

## Prečo RestClient?

`RestClient` je moderná náhrada za `RestTemplate` predstavená v Spring Framework 6.

Tento modul demonštruje:
- Fluent (builder) API pre HTTP volania
- Centrálnu konfiguráciu `baseUrl` a hlavičiek v jednom beane
- Logovanie requestov/response cez `ClientHttpRequestInterceptor`
- Generickú deserializáciu stránkovaných odpovedí cez `ParameterizedTypeReference`
- Propagáciu downstream HTTP chýb ako doménových výnimiek
- Anotačnú integráciu Resilience4j (`@CircuitBreaker`, `@Retry`)

---

## Resilience4j — ako a prečo

### Problém

Vzdialený `beer-store` server môže byť dočasne nedostupný. Bez ochrany:
- požiadavka okamžite zlyhá s `500 Internal Server Error`
- pri dlhodobom výpadku sa zbytočne zahlcujú vlákna čakaním na timeout
- klient nemá žiadnu informáciu čo sa stalo

### Riešenie — dva vzory

#### 1. Retry

```yaml
resilience4j:
  retry:
    instances:
      beerService:
        maxAttempts: 3       # celkovo 3 pokusy (1 + 2 opakovania)
        waitDuration: 500ms  # čakanie medzi pokusmi
```

Pri sieťovej chybe sa volanie automaticky zopakuje až 3-krát s pauzou 500 ms.
Opakujú sa len **sieťové chyby** — HTTP 4xx chyby (404, 409) sa **neopakujú**.

#### 2. CircuitBreaker (Istič)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      beerService:
        slidingWindowSize: 10                          # sleduje posledných 10 volaní
        minimumNumberOfCalls: 5                        # minimálny počet volaní pred vyhodnotením
        failureRateThreshold: 50                       # pri ≥50% chybovosti sa otvorí
        waitDurationInOpenState: 10s                   # 10s čaká kým prejde do HALF_OPEN
        permittedNumberOfCallsInHalfOpenState: 3
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

**Pravidlá fallback metódy:**
1. Musí mať **rovnaké parametre** ako hlavná metóda + `Throwable` na konci
2. Musí mať **rovnaký návratový typ** ako hlavná metóda
3. HTTP 4xx chyby (404, 409) sa mapujú na doménové výnimky — **nie** na `BeerServiceUnavailableException`
4. `getAllBeers` fallback vracia **prázdnu stránku** (UI-friendly — lepšie ako 503 pri listingu)

**Poradie anotácií je dôležité:**
```java
@CircuitBreaker(name = "beerService")  // vonkajší — obalí Retry
@Retry(name = "beerService")           // vnútorný — volá priamo API
```
CircuitBreaker → Retry → API. Každý pokus sa počíta do sliding window isteča.

---

## RestClient konfigurácia

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

| Parameter | Hodnota | Účel |
|---|---|---|
| `baseUrl` | `${beer-store.base-url}` | Prefix všetkých relatívnych volaní |
| Default headers | `application/json` | Vynútenie JSON kontraktu |
| `loggingInterceptor` | – | Loguje metódu, URI a HTTP status každého volania |

---

## Spracovanie HTTP chýb

`RestClient` hádže `HttpClientErrorException` pri 4xx stavoch. Tieto sa zachytia
vo fallback metódach a namapujú na doménové výnimky:

```
HTTP 404 Not Found  →  ResourceNotFoundException
HTTP 409 Conflict   →  ResourceAlreadyExistsExceptions
I/O chyba / CB OPEN →  BeerServiceUnavailableException
```

**Pravidlá pre fallback:**
- `getAllBeers` → vráti prázdnu stránku (UI môže vykresliť prázdny zoznam)
- `getBeerById`, `createBeer`, `updateBeerById` → hodí `BeerServiceUnavailableException`
  pri infraštruktúrnych zlyhaniach (nemôžeme vymyslieť konkrétny zdroj)

---

## Globálne spracovanie chýb

Všetky chyby vracajú jednotný `ProblemDetail` formát (RFC 7807):

```json
{
  "status": 404,
  "title": "Resource not found",
  "detail": "Beer not found with id: '550e8400-e29b-41d4-a716-446655440000'",
  "instance": "/client/beers/550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-02-18T21:00:00Z"
}
```

| Exception | HTTP Status | Kedy |
|---|---|---|
| `BeerServiceUnavailableException` | `503 Service Unavailable` | Server vypnutý, CB OPEN |
| `ResourceNotFoundException` | `404 Not Found` | Beer s daným ID neexistuje |
| `ResourceAlreadyExistsExceptions` | `409 Conflict` | Beer s rovnakým názvom/UPC už existuje |
| `MethodArgumentNotValidException` | `400 Bad Request` | Validácia request body zlyhala |

---

## API Endpointy

Base URL: `http://localhost:8083/client/beers`

| Metóda | URL | Popis | Success | Error |
|---|---|---|---|---|
| `GET` | `/client/beers` | Zoznam pív (stránkovaný, filtrovateľný) | `200 OK` | `503` |
| `GET` | `/client/beers/{id}` | Detail piva | `200 OK` | `404`, `503` |
| `POST` | `/client/beers` | Vytvorenie piva | `201 Created` + `Location` header | `400`, `409`, `503` |
| `PUT` | `/client/beers/{id}` | Úplná aktualizácia piva | `200 OK` | `400`, `404`, `409`, `503` |

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
  "status": 503,
  "error": "Service Unavailable",
  "message": "Beer service is currently unavailable. Please try again later."
}
```

---

## Strom projektu

```
beer-client-restclient
│
├── controller/
│   └── BeerController              ← REST API vrstva
│
├── service/
│   └── BeerRestClientService       ← Resilience4j + HTTP volania
│
├── config/
│   └── RestClientConfig            ← Bean konfigurácia RestClient + logging interceptor
│
├── dto/
│   ├── BeerCreateRequestDTO
│   ├── BeerUpdateRequestDTO
│   ├── BeerResponseDTO
│   ├── CategoriesDTO
│   └── PagedResponse               ← generická stránkovaná odpoveď
│
└── exceptions/
    ├── ResourceAlreadyExistsExceptions  ← 409 Conflict
    ├── ResourceNotFoundException        ← 404 Not Found
    ├── BeerServiceUnavailableException  ← 503 / CB OPEN / sieťový výpadok
    └── GlobalExceptionHandler           ← RFC 7807 ProblemDetail handler
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

# Spustenie celého stacku (beer-store + beer-client-restclient)
docker compose up

# Len tento modul (beer-store musí bežať samostatne)
./mvnw spring-boot:run
```

### Environment premenné

| Premenná | Default | Popis |
|---|---|---|
| `BEER_API_BASE_URL` | `http://localhost:8080` | URL beer-store servera |

---

## Testovanie resilience

### Simulácia výpadku

1. Spusti oba servery (`beer-store` + `beer-client-restclient`)
2. Vypni `beer-store`
3. Volaj ľubovoľný endpoint:
   - `getAllBeers` → vráti prázdnu stránku (fallback)
   - `getBeerById` / `createBeer` → Retry zopakuje 3× (3 × 500 ms), potom `503`
4. Po ~5 ďalších zlyhaniach sa CircuitBreaker **otvorí** — volania idú priamo do fallbacku bez čakania
5. Po 10 sekundách prejde do `HALF_OPEN` a pustí 3 testovacie volania
6. Ak `beer-store` beží → CircuitBreaker sa **zatvorí** → normálna prevádzka

### Logging

Zapnutý DEBUG logging pre RestClient v `application-dev.yaml`:

```yaml
logging:
  level:
    org.springframework.web.client.RestClient: DEBUG
    org.springframework.http.converter.json: DEBUG
```

### Health check

```
GET http://localhost:8083/actuator/health
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
