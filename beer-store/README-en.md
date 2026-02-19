# 🍺 Beer Store – REST API

A Spring Boot REST API for managing a beer catalog with category support. Provides full CRUD operations for beers and categories, including bulk import from a CSV file.

---

## 📋 Table of Contents

- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Data Model](#data-model)
- [API Endpoints](#api-endpoints)
- [Prerequisites](#prerequisites)
- [Running the Application](#running-the-application)
- [Running Tests](#running-tests)
- [Configuration](#configuration)
- [Importing Beers from CSV](#importing-beers-from-csv)

---

## Tech Stack

| Technology | Version |
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

## Project Structure

```
beer-store/
├── src/main/java/com/restmvc/beer_store/
│   ├── BeerStoreApplication.java       # Application entry point
│   ├── config/
│   │   ├── JpaConfig.java              # JPA Auditing configuration
│   │   └── WebConfig.java              # Pagination and Spring Data Web configuration
│   ├── controllers/
│   │   ├── BeerController.java         # Beer CRUD endpoints
│   │   ├── CategoryController.java     # Category CRUD endpoints
│   │   ├── BeerCategoryController.java # Beer-category relationship endpoints
│   │   └── BeerImportController.java   # CSV import endpoint
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
│   │   ├── beer/                       # Beer DTOs
│   │   ├── category/                   # Category DTOs
│   │   └── beerCategory/               # List-view DTOs
│   ├── mappers/
│   │   ├── BeerMapper.java             # MapStruct mapper for beers
│   │   └── CategoryMapper.java         # MapStruct mapper for categories
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

## Data Model

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

- **Beer → Category**: unidirectional `@ManyToMany` (Beer owns the join table)
- Deleting a beer automatically removes its rows in `beer_category` (CASCADE DELETE)
- Deleting a category automatically removes its rows in `beer_category` (CASCADE DELETE)
- A category is **not** deleted when a beer is deleted

---

## API Endpoints

### Beers – `POST /api/v1/beers`

Creates a new beer. Category IDs are optional.

```json
POST /api/v1/beers
Content-Type: application/json

{
  "beerName": "Pilsner Urquell",
  "upc": "8594002000018",
  "quantityOnHand": 200,
  "price": 2.49,
  "categoryIds": ["uuid-of-category-1", "uuid-of-category-2"]
}
```

**Response:** `201 Created` + `Location: /api/v1/beers/{id}`

---

### Beers – `GET /api/v1/beers`

Returns a paginated list of beers with optional filtering.

| Parameter | Description | Example |
|---|---|---|
| `beerName` | Filter by name (partial, case-insensitive) | `?beerName=IPA` |
| `upc` | Filter by UPC barcode | `?upc=859` |
| `showInventoryOnHand` | Include/exclude `quantityOnHand` in response | `?showInventoryOnHand=true` |
| `page` | Page number (0-indexed) | `?page=0` |
| `size` | Page size (max 100, default 10) | `?size=20` |
| `sort` | Sorting | `?sort=beerName,asc` |

**Examples:**
```
GET /api/v1/beers
GET /api/v1/beers?beerName=pilsner&showInventoryOnHand=true
GET /api/v1/beers?upc=859&page=0&size=5&sort=beerName,desc
```

---

### Beers – `GET /api/v1/beers/{beerId}`

Returns a single beer including its assigned categories.

**Response:** `200 OK`

---

### Beers – `PUT /api/v1/beers/{beerId}`

Fully replaces a beer. All fields are required.

```json
PUT /api/v1/beers/{beerId}
Content-Type: application/json

{
  "beerName": "Pilsner Urquell 12°",
  "upc": "8594002000018",
  "quantityOnHand": 150,
  "price": 2.79
}
```

**Response:** `200 OK` with updated beer data

---

### Beers – `PATCH /api/v1/beers/{beerId}`

Partially updates a beer. Only provided (non-null) fields are applied.

```json
PATCH /api/v1/beers/{beerId}
Content-Type: application/json

{
  "price": 2.99
}
```

**Response:** `200 OK` with updated beer data

---

### Beers – `DELETE /api/v1/beers/{beerId}`

Deletes a beer. **Response:** `204 No Content`

---

### Categories – `POST /api/v1/categories`

```json
POST /api/v1/categories
Content-Type: application/json

{
  "description": "IPA"
}
```

**Response:** `201 Created` + `Location: /api/v1/categories/{id}`

---

### Categories – `GET /api/v1/categories`

| Parameter | Description |
|---|---|
| `description` | Filter by description (partial, case-insensitive) |
| `page`, `size`, `sort` | Pagination and sorting |

```
GET /api/v1/categories
GET /api/v1/categories?description=ale
```

---

### Categories – `GET /api/v1/categories/{categoryId}`

Returns a single category. **Response:** `200 OK`

---

### Categories – `PUT /api/v1/categories/{categoryId}`

Updates a category description. **Response:** `204 No Content`

---

### Categories – `DELETE /api/v1/categories/{categoryId}`

Deletes a category. **Response:** `204 No Content`

---

### Beer-Category – `GET /api/v1/categories/{categoryId}/beers`

Returns a paginated list of beers belonging to the given category.

```
GET /api/v1/categories/{categoryId}/beers
GET /api/v1/categories/{categoryId}/beers?page=0&size=10&sort=beerName,asc
```

---

### Import – `POST /api/v1/import/beers`

Bulk imports beers from a CSV file.

```
POST /api/v1/import/beers
Content-Type: multipart/form-data

file=<csv_file>
```

**CSV Format:**
```csv
beerName,upc,quantityOnHand,price,categories
Pilsner Urquell,8594002000018,200,2.49,Pilsner;Lager
Golden Pheasant,8585000123456,100,1.29,Pale Lager
```

> The `categories` field uses semicolon (`;`) as separator. Non-existing categories are created automatically.

**Example response:**
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

### Error Responses (RFC 7807)

All errors follow the RFC 7807 ProblemDetail standard:

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

| HTTP Status | Situation |
|---|---|
| `400 Bad Request` | Input validation failure |
| `404 Not Found` | Resource with given ID does not exist |
| `409 Conflict` | Beer or category with the same name already exists |

---

## Prerequisites

- **Java 25**
- **Maven 3.x** (or use the included `./mvnw` wrapper)
- **MySQL 8+** – database `restdb`, user `restadmin`, password `password`
- **Docker** (optional, for containerized run)

---

## Running the Application

### 1. Prepare the MySQL Database

The application expects a running MySQL instance with these settings:

```
Host:     localhost:3306
Database: restdb
Username: restadmin
Password: password
```

The schema is created automatically via **Flyway** on first startup.

### 2a. Run Locally (Maven)

```bash
# Clone the repository
git clone <repository-url>
cd beer-store

# Start the application (active profile: dev)
./mvnw spring-boot:run
```

Application runs at: **http://localhost:8080**

### 2b. Run with Docker

```bash
# Build the Docker image
docker build -t beer-store .

# Run the container
# (MySQL must be accessible via host.docker.internal or in the same network)
docker run -p 8080:8080 -p 5005:5005 beer-store
```

> Port `5005` is exposed for remote debugging (JDWP).

### 2c. Run with Custom Database Settings

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:mysql://localhost:3306/restdb --spring.datasource.username=myuser --spring.datasource.password=mypassword"
```

---

## Running Tests

### Unit Tests

```bash
./mvnw test
```

### Integration Tests (Testcontainers)

Integration tests automatically spin up a MySQL container via Docker (Testcontainers) — no manual database setup required.

```bash
./mvnw verify
```

> Integration tests are located in `*.integreation.*` packages and are executed by `maven-failsafe-plugin`.

---

## Configuration

Key settings in `application.yaml`:

| Property | Value | Description |
|---|---|---|
| `spring.profiles.active` | `dev` | Active profile |
| `spring.data.web.pageable.default-page-size` | `10` | Default page size |
| `spring.data.web.pageable.max-page-size` | `100` | Maximum page size |
| `spring.servlet.multipart.max-file-size` | `50MB` | Maximum upload file size |

Database settings in `application-dev.yaml`:

| Property | Value |
|---|---|
| `datasource.url` | `jdbc:mysql://localhost:3306/restdb` |
| `datasource.username` | `restadmin` |
| `datasource.password` | `password` |
| `jpa.hibernate.ddl-auto` | `validate` |
| `hibernate.default_batch_fetch_size` | `20` |

---

## Importing Beers from CSV

Sample CSV files are available in `src/main/resources/CSV/`:

| File | Records |
|---|---|
| `beers_1.csv` | 1 |
| `beers_10000.csv` | 10 000 |

**Example import using `curl`:**

```bash
curl -X POST http://localhost:8080/api/v1/import/beers \
  -F "file=@src/main/resources/CSV/beers_10000.csv"
```

The import is optimized for large files:
- Batch processing in groups of 100 records
- Category ID caching to avoid repeated DB lookups
- Persistence context clearing between batches to prevent memory growth
- Error aggregation (returns first 10 errors)
