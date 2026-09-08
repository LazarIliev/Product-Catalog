# Product Catalog Service

A REST microservice managing a product catalog, backed by PostgreSQL.

Java 25 · Spring Boot 4.1 · Gradle (Kotlin DSL) · Flyway · Testcontainers

---

## Run it

One command, from a clean checkout — no local JDK, Gradle or Postgres needed:

```bash
docker compose up --build
```

This builds the application image, starts PostgreSQL, waits until it is genuinely accepting
connections, runs the Flyway migrations at startup and serves the API on
**http://localhost:8080**.

| | |
|---|---|
| API base | `http://localhost:8080/api/v1/products` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Health | `http://localhost:8080/actuator/health` |

Stop and remove everything, including the database volume:

```bash
docker compose down -v
```

## Build and test locally

Requires a JDK 25 and a running Docker daemon (the integration tests start a real PostgreSQL via
Testcontainers).

```bash
./gradlew build          # compile + run all tests
./gradlew test           # tests only
./gradlew bootRun        # run against a local Postgres on :5432
```

For `bootRun` you need a database. The compose file can provide just that one:

```bash
docker compose up -d db
./gradlew bootRun
```

Connection settings are overridable via `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD`; the defaults point at `localhost:5432/catalog` with user/password
`catalog`/`catalog`.

---

## API

Base path `/api/v1/products`. All request and response bodies are JSON; errors are
[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (`application/problem+json`).

| Method | Path | Success | Notes |
|---|---|---|---|
| `POST` | `/api/v1/products` | `201 Created` | `Location` header points at the new resource |
| `GET` | `/api/v1/products` | `200 OK` | Ordered by id; optional `?category=` filter |
| `GET` | `/api/v1/products/{id}` | `200 OK` | `404` if unknown |
| `PUT` | `/api/v1/products/{id}` | `200 OK` | Full replacement |
| `DELETE` | `/api/v1/products/{id}` | `204 No Content` | `404` if unknown |

### Status codes

| Code | When |
|---|---|
| `201` | Product created |
| `200` | Product read or updated |
| `204` | Product deleted |
| `400` | Validation failed, malformed JSON, or a non-numeric id |
| `404` | No product with that id |
| `409` | Duplicate name, or the product changed since the client read it |
| `415` | Body sent without `Content-Type: application/json` |
| `500` | Unexpected failure — logged server-side, opaque to the client |

### Validation rules

| Field | Rule |
|---|---|
| `name` | Non-empty after trimming, max 200 chars, unique case-insensitively |
| `price` | Strictly greater than 0, at most 2 decimal places |
| `category` | Non-empty after trimming, max 100 chars |
| `quantity` | Integer, 0 or greater |

All violations in a request are reported together, so a client fixes them in one round trip:

```json
{
  "type": "https://example.com/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "errors": [
    { "field": "name",     "message": "must not be blank" },
    { "field": "price",    "message": "must be greater than 0.00" },
    { "field": "quantity", "message": "must be greater than or equal to 0" }
  ]
}
```

### Try it

```bash
# Create
curl -isX POST http://localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Espresso Machine","price":249.99,"category":"kitchen","quantity":12}'

# List
curl -s http://localhost:8080/api/v1/products

# List one category (case-insensitive; an unknown category is an empty list, not a 404)
curl -s 'http://localhost:8080/api/v1/products?category=kitchen'

# Get one
curl -s http://localhost:8080/api/v1/products/1

# Update (version is optional — see Concurrency below)
curl -sX PUT http://localhost:8080/api/v1/products/1 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Espresso Machine","price":199.00,"category":"kitchen","quantity":5,"version":0}'

# Delete
curl -isX DELETE http://localhost:8080/api/v1/products/1

# Rejected: price must be positive, quantity non-negative
curl -sX POST http://localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"","price":-1,"category":"kitchen","quantity":-2}'
```

---

## Design decisions

### Layering

Classes are grouped **by layer**: each technical role gets its own top-level package, and the domain
types for a feature live together under `model/`. A second feature adds a class to each of the layer
packages plus a sibling package under `model/`. Application-wide infrastructure sits in `config/`.

```
config/ApiExceptionHandler       exceptions -> HTTP problem details
config/OpenApiConfig             API documentation

controller/ProductController     HTTP: routing, status codes, headers
service/ProductService           transactions and business rules
repository/ProductRepository     persistence (Spring Data JPA)
model/product/Product            entity; owns its own invariants
model/product/dto/*              request and response records
model/product/exception/*        domain failures the web layer maps
```

Three rules keep the layers honest: the controller holds no business logic, the service knows
nothing about HTTP, and the entity is never exposed directly — DTO records are the API contract, so
adding a column does not silently change the payload.

Because the collaborators now sit in different packages, the controller, service and repository are
**public**; the layering rules above are a review convention rather than something the compiler
enforces. Nothing stops a future class from injecting `ProductRepository` and skipping the service.

### SQL schema

See [`V1__create_products.sql`](src/main/resources/db/migration/V1__create_products.sql) and
[`V2__add_category_index.sql`](src/main/resources/db/migration/V2__add_category_index.sql).

- **Flyway owns the schema**, and Hibernate runs with `ddl-auto: validate`. The schema is reviewable,
  versioned and identical in tests, CI and production; a mismatch between entity and migration fails
  at startup rather than at the first query.
- **`NUMERIC(12,2)` for price**, never a floating-point type — money must not be subject to binary
  rounding.
- **CHECK constraints mirror the bean-validation rules.** Validation gives callers a helpful `400`;
  the constraints make the rules true of the *data*, including for rows written by a backfill script
  or a future second service.
- **A functional unique index on `lower(name)`** enforces case-insensitive uniqueness. Doing it in
  the database rather than with a read-then-write check in the service means it holds under
  concurrency — a "does it exist?" query is a lost race by construction.
- **`BIGINT` identity keys.** Compact, sequential and index-friendly. UUIDs would be the choice if
  ids had to be generated by clients or merged across regions; neither applies here.
- **A functional index on `lower(category)`** (V2) backs the `?category=` filter. It is on the
  expression rather than the bare column because the filter matches case-insensitively, the same
  choice as the name index: a plain index on `category` would not be usable by a
  `lower(category) = lower(?)` predicate at all, leaving the filter a sequential scan.
  The repository spells that predicate out in a `@Query` instead of deriving it from a
  `findByCategoryIgnoreCase` method name, because Spring Data derives `IgnoreCase` as
  **`upper(category) = upper(?)`** — which this index does not cover. A functional index is only as
  good as the query that matches it character for character, so `ProductSchemaTest` runs `EXPLAIN`
  over the SQL Hibernate actually emits rather than trusting that the two agree.
- **`created_at` / `updated_at` as `TIMESTAMPTZ`** — audit columns cost nothing now and are painful
  to add retroactively.

### Concurrency

Products carry a `version` column (`@Version`). It is returned on every read and **optional** on
update:

- Send the `version` you last read → a competing write is rejected with `409` instead of silently
  overwriting the other change.
- Omit it → last-write-wins, which keeps curl and simple clients usable.

Hibernate's own optimistic lock still guards the flush, so two writers who both read version 3 cannot
both succeed. An `ETag` / `If-Match` header pair would be the more RESTful expression of the same
thing and would be the natural next step; the request body was chosen here to keep the example
readable in a single curl command.

### Error handling

A single `@RestControllerAdvice` extending `ResponseEntityExceptionHandler` produces one error shape
for everything, including Spring's own failures (unreadable JSON, wrong method, bad path variable
type). Clients therefore have exactly one error format to parse. The catch-all handler logs the
exception and returns an opaque `500` — stack traces and SQL never reach the client.

The service throws domain exceptions (`ProductNotFoundException`, `DuplicateProductNameException`,
`StaleProductException`); mapping them to status codes is the web layer's job alone.

### Deliberate omissions

Left out because the task did not ask for them, and each would be a real decision rather than a
default: authentication and authorisation, pagination on the list endpoint and filtering by anything
beyond category (the first thing to add once the catalog is non-trivial), soft deletes, a separate
`categories` table (worth it
once a category gains attributes of its own), rate limiting, and structured JSON access logging.

---

## Tests

```bash
./gradlew test
```

Four classes, each answering a different question. They are deliberately layered so that a failure
points at one thing.

| Test | Scope | What it protects |
|---|---|---|
| `ProductServiceTest` | Unit, mocked repository | Stale-version rejection leaves nothing written; whitespace and price scale are normalised; a blank category filter is not a filter; deleting an unknown id fails loudly |
| `ProductControllerTest` | `@WebMvcTest`, mocked service | Status codes, `Location` header, `?category=` binding, and the error contract — all field violations reported at once; malformed input is a `400`, never a `500` |
| `ProductApiIntegrationTest` | Full stack over HTTP, real PostgreSQL | The CRUD lifecycle end to end; a losing concurrent update leaves no trace; duplicate names conflict and category filtering matches, both case-insensitively |
| `ProductSchemaTest` | Raw SQL against the migrated schema | The CHECK constraints and `NUMERIC` semantics hold at the database level, independently of the API; the category filter's `EXPLAIN` plan really uses the category index |

The integration tests run against the same PostgreSQL version and the same migrations as production,
via Testcontainers. An in-memory database would not exercise the CHECK constraints, the functional
unique index or `NUMERIC` arithmetic — which is most of what there is to get wrong here. The
container is a JVM-wide singleton, so it starts once for the whole build.

**Requires a running Docker daemon.** Without one the two integration classes fail to start; the unit
and web-slice tests run regardless.

---

## Layout

```
├── docker-compose.yml            app + PostgreSQL, one command
├── Dockerfile                    multi-stage build, non-root runtime
├── build.gradle.kts
└── src
    ├── main
    │   ├── java/com/example/catalog
    │   │   ├── ProductCatalogApplication.java
    │   │   ├── config/           application-wide configuration (OpenAPI, error handling)
    │   │   ├── controller/       HTTP endpoints
    │   │   ├── service/          transactions and business rules
    │   │   ├── repository/       Spring Data JPA repositories
    │   │   └── model/product/    the catalog domain — entity
    │   │       ├── dto/          request and response records
    │   │       └── exception/    domain failures
    │   └── resources
    │       ├── application.yml
    │       └── db/migration/    V1__create_products.sql, V2__add_category_index.sql
    └── test/java/com/example/catalog
        ├── AbstractPostgresIntegrationTest.java
        ├── ProductApiIntegrationTest.java   end-to-end, spans every layer
        ├── controller/           web-slice tests
        ├── service/              unit tests
        └── repository/           schema tests against real SQL
```
