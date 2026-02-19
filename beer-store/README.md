# 🍺 Beer Store – REST API

Spring Boot REST API pre správu katalógu pív a kategórií. Umožňuje CRUD operácie nad pivami a kategóriami, vrátane hromadného importu z CSV súboru.

---

## 📋 Obsah

- [Technológie](#technológie)
- [Architektúra projektu](#architektúra-projektu)
- [Dátový model](#dátový-model)
- [API endpointy](#api-endpointy)
- [Požiadavky](#požiadavky)
- [Spustenie aplikácie](#spustenie-aplikácie)
- [Spustenie testov](#spustenie-testov)
- [Konfigurácia](#konfigurácia)
- [Import pív z CSV](#import-pív-z-csv)

---

## Technológie

| Technológia | Verzia |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.2 |
| Spring Data JPA | – |
| MySQL | 8+ |
| Flyway | – |
| MapStruct | 1.6.3 |
| Lombok | 1.18.42 |
| OpenCSV | 5.12.0 |
| Testcontainers | 1.21.4 |
| Maven | 3.x |

---

## Architektúra projektu

```
beer-store/
├── src/main/java/com/restmvc/beer_store/
│   ├── BeerStoreApplication.java       # Vstupný bod aplikácie
│   ├── config/
│   │   ├── JpaConfig.java              # Konfigurácia JPA auditingu
│   │   └── WebConfig.java              # Konfigurácia stránkovania a Spring Data Web
│   ├── controllers/
│   │   ├── BeerController.java         # CRUD endpointy pre pivá
│   │   ├── CategoryController.java     # CRUD endpointy pre kategórie
│   │   ├── BeerCategoryController.java # Endpointy pre vzťah pivo-kategória
│   │   └── BeerImportController.java   # Endpoint pre import z CSV
│   ├── services/
│   │   ├── BeerService.java
│   │   ├── CategoryService.java
│   │   ├── BeerCategoryService.java
│   │   └── BeerImportService.java
│   ├── repositories/
│   │   ├── BeerRepository.java
│   │   └── CategoryRepository.java
│   ├── entities/
│   │   ├── Beer.java
│   │   ├── Category.java
│   │   └── Customer.java
│   ├── dtos/
│   │   ├── beer/                       # DTOs pre pivá
│   │   ├── category/                   # DTOs pre kategórie
│   │   └── beerCategory/               # DTOs pre zoznamové pohľady
│   ├── mappers/
│   │   ├── BeerMapper.java             # MapStruct mapper pre pivá
│   │   └── CategoryMapper.java         # MapStruct mapper pre kategórie
│   └── exceptions/
│       ├── GlobalExceptionsHandler.java
│       ├── ResourceNotFoundException.java
│       └── ResourceAlreadyExistsExceptions.java
└── src/main/resources/
    ├── application.yaml
    ├── application-dev.yaml
    ├── db/migration/V1__Beer_Customer_Category.sql
    └── CSV/
        ├── beers_1.csv
        └── beers_10000.csv
```

---

## Dátový model

```
┌──────────────────┐       ┌─────────────────────┐       ┌──────────────────┐
│      beers       │       │    beer_category      │       │    categories    │
├──────────────────┤       ├─────────────────────┤       ├──────────────────┤
│ beer_id (PK)     │──┐    │ beer_id (FK, PK)     │    ┌──│ category_id (PK) │
│ beer_name        │  └───>│ category_id (FK, PK) │<───┘  │ description      │
│ upc              │       └─────────────────────┘       │ version          │
│ price            │                                       │ created_at       │
│ quantity_on_hand │                                       │ updated_at       │
│ version          │                                       └──────────────────┘
│ created_at       │
│ updated_at       │
└──────────────────┘
```

- **Beer → Category**: unidirectionálny `@ManyToMany` (Beer vlastní vzťah)
- Mazanie piva automaticky vymaže záznamy v `beer_category` (CASCADE DELETE)
- Mazanie kategórie automaticky vymaže záznamy v `beer_category` (CASCADE DELETE)
- Kategória **nie** je vymazaná, keď sa vymaže pivo

---

## API endpointy

### Pivá – `POST /api/v1/beers`

Vytvorí nové pivo. Voliteľne je možné priradiť kategórie podľa UUID.

```json
POST /api/v1/beers
Content-Type: application/json

{
  "beerName": "Zlatý Bažant",
  "upc": "8585000123456",
  "quantityOnHand": 200,
  "price": 1.29,
  "categoryIds": ["uuid-kategorie-1", "uuid-kategorie-2"]
}
```

**Odpoveď:** `201 Created` + `Location: /api/v1/beers/{id}`

---

### Pivá – `GET /api/v1/beers`

Vráti stránkovaný zoznam pív. Podporuje filtrovanie a zobrazenie skladu.

| Parameter | Popis | Príklad |
|---|---|---|
| `beerName` | Filter podľa názvu (čiastočná zhoda, case-insensitive) | `?beerName=IPA` |
| `upc` | Filter podľa UPC kódu | `?upc=858` |
| `showInventoryOnHand` | Zobrazí/skryje `quantityOnHand` | `?showInventoryOnHand=true` |
| `page` | Číslo stránky (od 0) | `?page=0` |
| `size` | Veľkosť stránky (max 100, default 10) | `?size=20` |
| `sort` | Zoradenie | `?sort=beerName,asc` |

**Príklady:**
```
GET /api/v1/beers
GET /api/v1/beers?beerName=pilsner&showInventoryOnHand=true
GET /api/v1/beers?upc=858&page=0&size=5&sort=beerName,desc
```

---

### Pivá – `GET /api/v1/beers/{beerId}`

Vráti detail piva vrátane priradených kategórií.

**Odpoveď:** `200 OK`

---

### Pivá – `PUT /api/v1/beers/{beerId}`

Úplná aktualizácia piva. Všetky polia sú povinné.

```json
PUT /api/v1/beers/{beerId}
Content-Type: application/json

{
  "beerName": "Zlatý Bažant 10°",
  "upc": "8585000123456",
  "quantityOnHand": 150,
  "price": 1.39
}
```

---

### Pivá – `PATCH /api/v1/beers/{beerId}`

Čiastočná aktualizácia – iba zadané polia budú aktualizované.

```json
PATCH /api/v1/beers/{beerId}
Content-Type: application/json

{
  "price": 1.49
}
```

---

### Pivá – `DELETE /api/v1/beers/{beerId}`

Vymaže pivo. **Odpoveď:** `204 No Content`

---

### Kategórie – `POST /api/v1/categories`

```json
POST /api/v1/categories
Content-Type: application/json

{
  "description": "IPA"
}
```

**Odpoveď:** `201 Created` + `Location: /api/v1/categories/{id}`

---

### Kategórie – `GET /api/v1/categories`

| Parameter | Popis |
|---|---|
| `description` | Filter podľa popisu (čiastočná zhoda) |
| `page`, `size`, `sort` | Stránkovanie |

```
GET /api/v1/categories
GET /api/v1/categories?description=ale
```

---

### Kategórie – `GET /api/v1/categories/{categoryId}`

Vráti detail kategórie. **Odpoveď:** `200 OK`

---

### Kategórie – `PUT /api/v1/categories/{categoryId}`

Aktualizuje kategóriu. **Odpoveď:** `204 No Content`

---

### Kategórie – `DELETE /api/v1/categories/{categoryId}`

Vymaže kategóriu. **Odpoveď:** `204 No Content`

---

### Pivo-Kategória – `GET /api/v1/categories/{categoryId}/beers`

Vráti stránkovaný zoznam pív patriacich do danej kategórie.

```
GET /api/v1/categories/{categoryId}/beers
GET /api/v1/categories/{categoryId}/beers?page=0&size=10&sort=beerName,asc
```

---

### Import – `POST /api/v1/import/beers`

Hromadný import pív z CSV súboru.

```
POST /api/v1/import/beers
Content-Type: multipart/form-data

file=<csv_súbor>
```

**Formát CSV:**
```csv
beerName,upc,quantityOnHand,price,categories
Zlatý Bažant,8585000123456,200,1.29,Lager;Pale Lager
Pilsner Urquell,8594002000018,100,2.49,Pilsner
```

> Pole `categories` je oddelené bodkočiarkou (`;`). Kategórie, ktoré neexistujú, budú automaticky vytvorené.

**Príklad odpovede:**
```json
{
  "imported": 9998,
  "categoriesCreated": 12,
  "skippedRows": 2,
  "durationMs": 3421,
  "errors": ["Line 5: Missing upc", "Line 12: Invalid price"]
}
```

---

### Chybové odpovede (RFC 7807)

Všetky chyby sú vrátené v štandarde RFC 7807 ProblemDetail:

```json
{
  "type": "about:blank",
  "title": "Resource not found",
  "status": 404,
  "detail": "Beer not found with id: '123e4567-...'",
  "instance": "/api/v1/beers/123e4567-...",
  "timestamp": "2026-02-19T10:00:00Z"
}
```

| HTTP Status | Situácia |
|---|---|
| `400 Bad Request` | Validačná chyba vstupných dát |
| `404 Not Found` | Zdroj s daným ID neexistuje |
| `409 Conflict` | Pivo alebo kategória s daným názvom už existuje |

---

## Požiadavky

- **Java 25**
- **Maven 3.x** (alebo použite priložený `./mvnw`)
- **MySQL 8+** – databáza `restdb`, používateľ `restadmin`, heslo `password`
- **Docker** (voliteľné, pre kontajnerizovaný beh)

---

## Spustenie aplikácie

### 1. Pripraviť databázu MySQL

Aplikácia predpokladá bežiaci MySQL server s týmito nastaveniami:

```
Host:     localhost:3306
Database: restdb
Username: restadmin
Password: password
```

Schéma sa automaticky vytvorí cez **Flyway** pri prvom štarte.

### 2a. Spustenie lokálne (Maven)

```bash
# Naklonuj repozitár
git clone <url-repozitara>
cd beer-store

# Spusti aplikáciu (aktívny profil: dev)
./mvnw spring-boot:run
```

Aplikácia beží na: **http://localhost:8080**

### 2b. Spustenie cez Docker

```bash
# Zostav Docker image
docker build -t beer-store .

# Spusti kontajner
# (MySQL musí byť dostupný na host.docker.internal alebo v tej istej sieti)
docker run -p 8080:8080 -p 5005:5005 beer-store
```

> Port `5005` je pre vzdialené debugovanie (JDWP).

### 2c. Spustenie s vlastnou konfiguráciou databázy

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:mysql://localhost:3306/restdb --spring.datasource.username=myuser --spring.datasource.password=mypassword"
```

---

## Spustenie testov

### Unit testy

```bash
./mvnw test
```

### Integračné testy (Testcontainers)

Integračné testy automaticky spustia MySQL cez Docker (Testcontainers) – nie je potrebné manuálne nastavenie databázy.

```bash
./mvnw verify
```

> Integračné testy sú vo balíku `*.integreation.*` a spúšťajú sa cez `maven-failsafe-plugin`.

---

## Konfigurácia

Hlavné nastavenia v `application.yaml`:

| Vlastnosť | Hodnota | Popis |
|---|---|---|
| `spring.profiles.active` | `dev` | Aktívny profil |
| `spring.data.web.pageable.default-page-size` | `10` | Predvolená veľkosť stránky |
| `spring.data.web.pageable.max-page-size` | `100` | Maximálna veľkosť stránky |
| `spring.servlet.multipart.max-file-size` | `50MB` | Max veľkosť nahrávaného súboru |

Nastavenia databázy v `application-dev.yaml`:

| Vlastnosť | Hodnota |
|---|---|
| `datasource.url` | `jdbc:mysql://localhost:3306/restdb` |
| `datasource.username` | `restadmin` |
| `datasource.password` | `password` |
| `jpa.hibernate.ddl-auto` | `validate` |
| `hibernate.default_batch_fetch_size` | `20` |

---

## Import pív z CSV

Vzorové CSV súbory sú dostupné v `src/main/resources/CSV/`:

| Súbor | Počet záznamov |
|---|---|
| `beers_1.csv` | 1 |
| `beers_10000.csv` | 10 000 |

**Príklad importu cez `curl`:**

```bash
curl -X POST http://localhost:8080/api/v1/import/beers \
  -F "file=@src/main/resources/CSV/beers_10000.csv"
```

Import je optimalizovaný pre veľké súbory:
- Dávkové spracovanie po 100 záznamov
- Cachovanie kategórií (zamedzuje opakovaným dotazom do DB)
- Čistenie persistence contextu medzi dávkami (zabraňuje rastu pamäte)
- Agregácia chýb (vracia prvých 10 chýb)
