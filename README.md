# Pokédex — PokéAPI Backend For Frontend

A full-stack Pokédex. A **Spring Boot** back-end acts as a *Backend For Frontend* (BFF) for the public
[PokéAPI](https://pokeapi.co/): it exposes a clean, versioned REST API, caches PokéAPI's resources, and replicates the
Pokémon it reads into a **MySQL** database, where they can be enriched with proprietary data or managed as local
records (full CRUD). A **React** single-page application, served by the back-end itself, consumes that API.

**Highlights**

- Paginated browsing, exact search and a detailed view (stats, description, evolution tree) of every PokéAPI Pokémon.
- A local database with full CRUD, proprietary fields (localized name, region, classification) on top of replicated
  PokéAPI data, and optimistic concurrency over HTTP (`ETag` / `If-Match`).
- Resilience towards PokéAPI: per-resource cache, bounded parallelism, retries with a time budget, a global pause on
  rate limits, graceful degradation of partial data.
- Pre-populated with demonstration data; runs with a single `docker compose up`.

---

## Contents

1. [Quick start with Docker](#1-quick-start-with-docker)
2. [Demonstration data](#2-demonstration-data)
3. [Local development setup](#3-local-development-setup)
4. [Configuration reference](#4-configuration-reference)
5. [Architecture](#5-architecture)
6. [REST API](#6-rest-api)
7. [Database](#7-database)
8. [Front-end](#8-front-end)
9. [Testing](#9-testing)

---

## 1. Quick start with Docker

Requirements: Docker with Compose v2 (Docker Desktop on Windows/macOS). Internet access is needed for the live PokéAPI
data; the demonstration data works offline.

```bash
docker compose up --build
```

This builds the image from the [`Dockerfile`](Dockerfile) and starts two containers ([`docker-compose.yml`](docker-compose.yml)):

| Service | What it is | Port |
|---|---|---|
| `app` | The application: Spring Boot back-end serving the React front-end | `8080` |
| `db` | MySQL 8.4, database `pokemon` (user `pokemon` / password `pokebola`), data kept in the `pokedex-db` volume | internal |

When `app` reports `healthy` (`docker compose ps`), open:

| URL | |
|---|---|
| http://localhost:8080 | The Pokédex (switch to **Local database** to see the demonstration data) |
| http://localhost:8080/swagger-ui.html | Interactive API documentation (OpenAPI) |
| http://localhost:8080/actuator/health | Health, including the database and the PokéAPI integration |

```bash
docker compose down        # stop (the database volume is kept)
docker compose down -v     # stop and erase the database (the demonstration data comes back on the next start)
```

**The Dockerfile** builds in three stages: the React front-end with Node 24, the back-end with Maven and JDK 21 (the
front-end build becomes its static resources, so the image never serves a stale copy), and a final image that only
holds a Java 21 runtime and the application jar, running as a non-root user with a health check on
`/actuator/health/readiness`. To use the image with a MySQL of your own:

```bash
docker build -t pokedex-bff .
docker run -p 8080:8080 \
  -e DB_URL="jdbc:mysql://<host>:3306/pokemon?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -e DB_USERNAME=<user> -e DB_PASSWORD=<password> \
  pokedex-bff
```

Any variable of the [configuration reference](#4-configuration-reference) can be passed with `-e` (or under
`environment:` in the compose file). `JAVA_OPTS` sets JVM options (default `-XX:MaxRAMPercentage=75`).

---

## 2. Demonstration data

A new database is created **with demonstration data**, by the Flyway migration
[`V2__demo_data.sql`](src/main/resources/db/migration/V2__demo_data.sql). It shows every feature without depending
on PokéAPI being reachable:

| Records | What they demonstrate |
|---|---|
| **#1 bulbasaur, #4 charmander, #7 squirtle, #25 pikachu, #133 eevee, #150 mewtwo** | Copies of PokéAPI Pokémon exactly as the synchronisation stores them (real image, measures, category, description, stats and abilities, generated from PokéAPI), **enriched with proprietary fields**: Japanese name, region `Kanto` and a classification tag (`STARTER`, `MASCOT`, `EVOLUTION-HUB`, `LEGENDARY`). They stay replicated: reading them from PokéAPI refreshes their PokéAPI data and never touches the proprietary fields. |
| **#90001 voltmouse, #90002 embercub, #90003 tidalfin, #90004 sproutling** | Pokémon **created locally** — they exist only in this database (with a Poké Ball picture, stats, abilities and, for three of them, local data). They can be edited and deleted freely. |

Try it: switch to **Local database**, search for `pikachu` or `voltmouse`, open a card, use **Edit Pokémon**,
**Local data**, **Delete Pokémon**, or create one with **+ New Pokémon**.

Notes:

- The migration is idempotent with existing data (`INSERT IGNORE` / `WHERE NOT EXISTS`), so it can also be applied
  to a database that already holds some of these Pokémon.
- When PokéAPI is reachable, the **cache warm-up** additionally reads the first 100 PokéAPI Pokémon at start-up, and
  the synchronisation replicates them into the local database (about 106 local records in total). Set
  `POKEMON_CACHE_WARMUP_ENABLED=false` to keep only the demonstration data.
- To start over with Docker, `docker compose down -v`; with a local MySQL, drop and recreate the `pokemon` database.

---

## 3. Local development setup

### Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | 21 or newer | Back-end (the Maven wrapper `./mvnw` downloads Maven) |
| MySQL | 8 | Database (or run it with Docker, below) |
| Node.js | 22.13+ or 24 | Front-end development and tests only — the back-end already serves a built copy |

### 1) Database

Any MySQL 8 works. The quickest is a container:

```bash
docker run -d --name pokedex-mysql -p 3306:3306 \
  -e MYSQL_DATABASE=pokemon -e MYSQL_USER=pokemon -e MYSQL_PASSWORD=pokebola -e MYSQL_ROOT_PASSWORD=root \
  mysql:8.4
```

Tables and demonstration data are created by Flyway on the first start; nothing has to be run by hand.

### 2) Configuration

```bash
cp .env.example .env      # then adjust DB_URL / DB_USERNAME / DB_PASSWORD if needed
```

The back-end reads `.env` from the project root automatically (`spring.config.import`), so nothing needs to be
exported. Environment variables, when set, take precedence. See the
[configuration reference](#4-configuration-reference).

### 3) Back-end

```bash
./mvnw spring-boot:run       # Windows: mvnw.cmd spring-boot:run
```

The application starts on http://localhost:8080 and serves the front-end build found in
`src/main/resources/static/`.

### 4) Front-end (development)

```bash
cd WebAppClient
npm ci
npm run dev                  # http://localhost:5173, /api proxied to http://localhost:8080
```

To publish front-end changes to the back-end, build and replace its static copy:

```bash
cd WebAppClient
npm run build
rm -rf ../src/main/resources/static/assets && cp -r dist/* ../src/main/resources/static/
```

(The Docker image always builds the front-end itself.)

### Troubleshooting

| Symptom | Fix |
|---|---|
| Flyway fails with a migration that no longer exists in `src/` | Maven keeps deleted resources in `target/`: run `./mvnw clean`. |
| `503` with "PokeAPI is limiting requests" | PokéAPI's rate limit: the application pauses its calls and tells when to retry (`Retry-After`). The local database keeps working. |

---

## 4. Configuration reference

All settings are environment variables (or entries in `.env`); only the database connection is required.

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/pokemon?createDatabaseIfNotExist=true&…` | JDBC URL of the MySQL database |
| `DB_USERNAME` / `DB_PASSWORD` | — | Database credentials |
| `POKEAPI_URL` | `https://pokeapi.co/api/v2` | PokéAPI base URL |
| `POKEAPI_CONNECT_TIMEOUT_MS` / `POKEAPI_READ_TIMEOUT_MS` | `3000` / `5000` | Timeouts of each PokéAPI call |
| `POKEAPI_RETRY_MAX_ATTEMPTS` | `3` | Attempts per retried call, the first included (`1` disables retries) |
| `POKEAPI_RETRY_PERIOD` / `POKEAPI_RETRY_MAX_PERIOD` | `100ms` / `1s` | Exponential backoff between attempts |
| `POKEAPI_RETRY_BUDGET` | `6s` | Time budget of the retries of one request |
| `POKEAPI_RATE_LIMIT_DEFAULT_PAUSE` | `10s` | Pause of every PokéAPI call after a `429` without `Retry-After` |
| `POKEMON_CACHE_PROVIDER` | `caffeine` | `caffeine` (in memory), `redis` (shared) or `none` |
| `POKEMON_CACHE_PAGE_TTL` / `POKEMON_CACHE_PAGE_MAXIMUM_SIZE` | `5m` / `128` | Cache of PokéAPI's reference lists |
| `POKEMON_CACHE_RESOURCE_TTL` / `POKEMON_CACHE_RESOURCE_MAXIMUM_SIZE` | `15m` / `4096` | Cache of PokéAPI's resources (Pokémon, species, evolution chains) and of the sync marks |
| `POKEMON_CACHE_WARMUP_ENABLED` | `true` | Load the first pages of PokéAPI in the background at start-up |
| `POKEMON_CACHE_WARMUP_PAGES` / `POKEMON_CACHE_WARMUP_PAGE_SIZE` | `5` / `20` | Size of the warm-up |
| `POKEMON_FETCH_PARALLELISM` | `8` | Threads loading the entries of a page from PokéAPI |
| `POKEMON_SYNC_THREADS` / `POKEMON_SYNC_QUEUE_CAPACITY` | `2` / `1000` | Background replication into the database |
| `POKEMON_HTTP_MAX_AGE` | `0s` | `Cache-Control` of complete PokéAPI responses: `0` = `no-cache` (revalidate with the `ETag`), more = `max-age` |
| `MANAGEMENT_ENDPOINTS_INCLUDE` | `health,info` | Actuator endpoints exposed over HTTP |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_TIMEOUT` | `localhost` / `6379` / — / `2s` | Redis, when `POKEMON_CACHE_PROVIDER=redis` |
| `MANAGEMENT_HEALTH_REDIS_ENABLED` | `false` | Include Redis in the health check |

---

## 5. Architecture

```
 Browser ── React SPA (served as static files by the back-end)
    │  REST /api/v1
    ▼
┌──────────────────────────────── Spring Boot (:8080) ────────────────────────────────┐
│ API layer          PokemonController (/pokemons, read-only)                          │
│                    LocalPokemonController (/local-pokemons, CRUD)                     │
│                    ApiExceptionHandler (RFC 9457) · ApiEtagFilter · EntityTags        │
│                                │                                                      │
│ Business layer     PokeApiPokemonService · LocalPokemonService · Pagination           │
│                    PokemonWriteValidator · PokemonCatalogService · VersionCondition   │
│                    PokemonSyncService (replication) · PokemonCacheWarmup              │
│                                │                                │                     │
│ Data access        PokeApiCache ─ PokeApiRetrier ─ PokemonClient   PokemonRepository  │
│                    (Spring Cache)  (retries,       (OpenFeign)     (Spring Data JPA)  │
│                                     rate limit)        │                 │            │
└────────────────────────────────────────────────────────┼─────────────────┼────────────┘
                                                         ▼                 ▼
                                                      PokéAPI      MySQL (Flyway)
```

| Layer | Packages | Depends on |
|---|---|---|
| API | `controller`, `exception`, `config` (web) | The business layer. Owns HTTP: routes, status codes, headers, `ETag`/`If-Match` (`EntityTags`), problem bodies |
| Business | `service`, `sync` | The data access layer (repository interfaces, JPA entities as the shared model) — no Spring MVC, HTTP or Feign types. Optimistic concurrency is a `VersionCondition` |
| Data access | `persistence` (repositories, entities), `client` (PokéAPI) | The client reports "not in PokéAPI" as `PokeApiNotFoundException`, so Feign stays inside it |

**Technology:** Java 21 · Spring Boot 4.1 (Web MVC) · Spring Cloud OpenFeign · Spring Data JPA / Hibernate ·
MySQL 8 · Flyway · Spring Cache (Caffeine / Redis) · SpringDoc OpenAPI · Lombok · React 19 · Vite 8 · Vitest ·
Testing Library · Oxlint · JUnit 5 · Mockito · WireMock · H2 (tests) · JaCoCo · Docker.

**Project structure**

```
├── Dockerfile · docker-compose.yml · .env.example
├── src/main/java/com/pokemon/bff/
│   ├── controller/   PokemonController, LocalPokemonController, ApiEtagFilter, EntityTags
│   ├── exception/    ApiExceptionHandler (RFC 9457) and the domain exceptions
│   ├── service/      PokeApiPokemonService (PokéAPI reads), LocalPokemonService (local CRUD),
│   │                 PokemonWriteValidator, PokemonCatalogService, PokeApiCache, PokeApiMapper,
│   │                 PokemonCacheWarmup, Pagination, VersionCondition
│   ├── sync/         PokemonSyncService, PokemonSyncThrottle, PokemonSnapshot
│   ├── client/       PokemonClient (OpenFeign), PokeApiRetrier, PokeApiErrorDecoder, PokeApiStatus, dto/
│   ├── persistence/  entity/ (JPA), repository/ (Spring Data + PokemonVersioning fragment)
│   ├── dto/          request and response bodies
│   └── config/       Flyway, caches, thread pools, OpenAPI, CORS, strict JSON for writes
├── src/main/resources/
│   ├── application.yaml
│   ├── db/migration/ V1__create_pokemon_tables.sql, V2__demo_data.sql
│   └── static/       built front-end (served by Spring Boot)
├── src/test/java/…   unit, Web MVC, JPA (H2), contract (WireMock) and smoke tests
└── WebAppClient/     React front-end (src/api, src/hooks, src/components)
```

---

## 6. REST API

The API is versioned (`/api/v1`) and exposes two resources, so each URI names exactly one thing. Interactive
documentation: http://localhost:8080/swagger-ui.html.

| | `/api/v1/pokemons` | `/api/v1/local-pokemons` |
|---|---|---|
| Data | Live PokéAPI data (cached) | The local MySQL database |
| Contents | Every Pokémon of PokéAPI | Copies replicated from PokéAPI reads, plus records created locally |
| Operations | Read-only (writes answer `405`) | Full CRUD |
| Local edits | Not shown (always PokéAPI's version) | Shown |
| Order | PokéAPI's | By `id` |

### Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/pokemons?page=0&size=20` | Page of PokéAPI Pokémon (image, category, mass, abilities). `size` 1–100. An entry PokéAPI could not deliver comes with `available: false` instead of failing the page; one whose species alone failed keeps its data with `categoryAvailable: false`. |
| `GET` | `/api/v1/pokemons/{nameOrId}` | Full detail: image, category, height, mass, abilities, the six base stats, description and evolution tree. Alternate forms (`charizard-mega-x`) resolve through their species; a species name (`lycanroc`) resolves to its default variety. If the description or the evolution chain cannot be loaded, the detail still comes, with `descriptionAvailable` / `evolutionAvailable` `false`. Includes the proprietary fields (`metadata`) stored locally for that id. |
| `GET` | `/api/v1/local-pokemons?page=0&size=20` | Page of local records (same body as above, with `metadata`). |
| `GET` | `/api/v1/local-pokemons/{nameOrId}` | Detail of a local record; the `ETag` header is its version. Local records have no evolution data: the lineage holds only the Pokémon itself. |
| `POST` | `/api/v1/local-pokemons` | Creates a local record → `201` + `Location` + `ETag`. Neither its id nor its name may belong to a PokéAPI Pokémon (to add data to one of those, enrich it instead — below). |
| `POST` | `/api/v1/local-pokemons/{nameOrId}/sync` | Replicates a PokéAPI Pokémon into the local database now → `{"outcome": "CREATED" \| "UPDATED" \| "KEPT_LOCAL_CHANGES", "pokemon": {…}}`, `201` or `200`. Keeps proprietary fields and never overwrites local changes. |
| `PUT` | `/api/v1/local-pokemons/{id}` | Replaces a record: every field takes the value sent, a field left out is cleared; `name` required. |
| `PATCH` | `/api/v1/local-pokemons/{id}` | Changes only the fields sent (a blank `image`, `description` or `category` clears it). A `PATCH` with only proprietary fields keeps the record replicated, and replicates a PokéAPI Pokémon first when it is not stored yet — one call enriches any PokéAPI Pokémon. |
| `DELETE` | `/api/v1/local-pokemons/{id}` | Deletes a record → `204`. The deletion is remembered: reading the Pokémon from PokéAPI does not bring it back, nor does a `PATCH` (`404` with `"deleted": true`); `POST` or `/sync` do. |

### Representation

Every endpoint of a resource answers with the same structure. The writes (`POST`, `PUT`, `PATCH`, the `pokemon` of
`/sync`) return exactly what `GET /local-pokemons/{id}` returns:

```json
{
  "id": 25, "name": "pikachu", "species": "pikachu",
  "image": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png",
  "height": 0.4, "mass": 6.0, "category": "Mouse Pokémon",
  "skills": [{ "name": "static", "url": "https://pokeapi.co/api/v2/ability/9/" }],
  "stats": [{ "name": "hp", "value": 35 }],
  "description": "When several of these POKéMON gather, their electricity could build and cause lightning storms.",
  "descriptionAvailable": true,
  "evolutionLineage": { "name": "pikachu", "evolvesTo": [] }, "evolutionAvailable": true,
  "metadata": { "localizedName": "ピカチュウ", "region": "Kanto", "classificationTag": "MASCOT" }
}
```

Request bodies are flat: `localizedName`, `region` and `classificationTag` sit next to the other fields. Every
request and response uses the same names (`image`, `mass`, `stats`, `skills`); `imageUrl` and `weight` are still
accepted on input. Persistence internals (`version`, `locallyModified`, `syncedAt`) are never exposed; the version
travels only as the `ETag` header.

### Validation

Writes are checked before anything reaches the database, so a bad value is a `400` that names the field.

| Field | Rule |
|---|---|
| `id` | Positive integer, from the path; an `id` in the body must be the same. |
| `name` | Required on `POST`/`PUT`. Stored in lower case; letters, digits and single hyphens (`mr-mime`); ≤ 100 characters; must contain a letter. Unique, and not the name of another PokéAPI Pokémon. |
| `image` | Absolute `http`/`https` URL, ≤ 512 characters. Blank clears it. |
| `height` / `mass` | `0`–`1000` m / `0`–`10000` kg. |
| `description` / `category` | ≤ 5000 / ≤ 100 characters. Blank clears them. |
| `stats` | ≤ 20; each a name (≤ 100, unique ignoring case) and a whole `value` `0`–`255`. |
| `skills` | ≤ 20; each a name (≤ 100, unique ignoring case) and an optional `http`/`https` `url`. |
| `localizedName` / `region` / `classificationTag` | ≤ 200 / 100 / 100 characters. Blank removes the value. |

Write bodies are read strictly: an unknown field (`"heigth"`), a value of the wrong type (`1.7` for an integer,
`"5"` for a number, `123` for a text) or a repeated field is a `400`, never silently ignored or converted.

### Proprietary fields and replication

Reading a Pokémon from PokéAPI stores a copy of it in the local database (asynchronously, at most once per cache TTL,
and only when PokéAPI's data changed), so that **proprietary fields** — data PokéAPI does not have — can be added on
top of it:

| Change | Effect on the replication |
|---|---|
| `PATCH` with only `localizedName` / `region` / `classificationTag` | None: the record keeps receiving PokéAPI updates; the synchronisation never touches these fields. |
| `PATCH` changing PokéAPI data (name, image, stats…) or `PUT` | The record is taken over (`locally_modified`): the synchronisation stops updating it. |
| `DELETE` | Remembered (`pokemon_tombstone`): the synchronisation does not bring it back. |

### Optimistic concurrency

The local detail and every write answer with an `ETag` — the record's version. Sending it back in `If-Match` on
`PUT`, `PATCH` or `DELETE` makes the write fail with `412 Precondition Failed` (and the current `ETag`) if someone
changed the record in between, instead of silently overwriting that change. `If-Match` is optional; the front-end
always sends it. A `409 Concurrent modification` (another write won a race inside the server) means the same: reload
and retry.

### HTTP caching

Successful `GET`s carry an `ETag`; a matching `If-None-Match` gets `304 Not Modified`. PokéAPI responses are sent with
`Cache-Control: no-cache` by default (`POKEMON_HTTP_MAX_AGE` allows `max-age`); partial responses are `no-store`, and
a detail with local fields is always `no-cache`. Local responses are `no-cache`.

### Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` body with `title`,
`status`, `detail` and `instance` (`type` is omitted, which means `about:blank`). A `400` adds `field` when the error
comes from a single field (`stats[0].value`, `heigth`, `id`); a `404` for a deleted record adds `"deleted": true`.

```json
{ "status": 400, "title": "Invalid request", "field": "stats[0].value",
  "detail": "Missing or invalid value for 'stats[0].value': expected an integer", "instance": "/api/v1/local-pokemons/25" }
```

| Status | When |
|---|---|
| `400` | Invalid parameters or body (title always `Invalid request`) |
| `404` | Not found locally or in PokéAPI (title `Resource not found`) |
| `405` | Write on the read-only PokéAPI resource |
| `409` | Concurrent modification, or a duplicate id/name created at the same time |
| `412` | `If-Match` does not match the current version |
| `415` | A write body that is not `application/json` |
| `502` / `504` | PokéAPI failed / did not answer in time (after the retries) |
| `503` | PokéAPI is rate limiting; `Retry-After` says when to try again |
| `500` | Unexpected error (logged, never detailed in the response) |

### Resilience towards PokéAPI

- **Cache per resource** (`PokeApiCache`): `/pokemon`, `/pokemon-species` and `/evolution-chain` are cached
  separately, so the list card and the detail share them and never disagree. Failures are not cached; lookup keys are
  normalized (`Pikachu` = `pikachu`).
- **Bounded parallelism:** the entries of a page are loaded on a fixed pool (`POKEMON_FETCH_PARALLELISM`).
- **Retries with a budget** (`PokeApiRetrier`): only calls that answer a request directly (reference list, detail)
  are retried, on transient failures only, and only while they fit `POKEAPI_RETRY_BUDGET`; page entries degrade
  instead.
- **Rate limit:** a `429` pauses every PokéAPI call until `Retry-After`; responses that lost data to the pause carry
  `retryAfterSeconds` and `Retry-After`, and the front-end shows when to retry.
- **Warm-up:** the first pages are loaded at start-up so the first visitors do not pay the cold cache.

### Actuator

Only `health` and `info` are exposed. `/actuator/health` includes `db` and a passive `pokeApi` component built from
the real calls (`UNKNOWN`, `UP`, `DOWN`; a rate limit shows as `rateLimitedUntil`, not as an outage). Liveness and
readiness probes (`/actuator/health/liveness`, `/readiness`) do not depend on PokéAPI.

---

## 7. Database

Flyway migrations, applied automatically at start-up:

| Migration | Contents |
|---|---|
| `V1__create_pokemon_tables.sql` | Schema |
| `V2__demo_data.sql` | [Demonstration data](#2-demonstration-data) |

| Table | Purpose |
|---|---|
| `pokemon` | Primary entity: `id` (PK), `name` (unique), `image_url`, `height`, `weight`, `description`, `category`, `synced_at`, `locally_modified`, `version` (optimistic lock) |
| `pokemon_stat` | Base stats of a Pokémon (`name`, `value`) |
| `pokemon_skill` | Abilities of a Pokémon (`name`, `url`) |
| `pokemon_metadata` | Proprietary fields: `localized_name`, `region`, `classification_tag` (never written by the synchronisation) |
| `pokemon_tombstone` | Ids deleted through the API, which the synchronisation must not recreate |

Spring Boot 4.1 no longer auto-configures Flyway; `FlywayConfig` and `FlywayMigrationRunner` (registered in
`META-INF/spring.factories`) run the migrations before JPA validates the schema.

---

## 8. Front-end

React 19 single-page application (`WebAppClient/`), built with Vite and served by the back-end. All HTTP lives in
`src/api/pokemonApi.js`; the state lives in three hooks; components are presentational.

| Part | Responsibility |
|---|---|
| `App.jsx` | Composes the list, the search and the detail panel, and decides what to show. |
| `hooks/usePokemonPage` | Source, page, size and the page loaded for them. A late answer for a page, size or source no longer selected is discarded. |
| `hooks/usePokemonSearch` | Live filter of the loaded page (derived, no request) and exact search (only the latest answer is shown). |
| `hooks/useDetailPanel` | Detail panel and every write made from it (local data, edit, delete, restore), each with `If-Match`. |
| `SearchBar`, `SourceToggle`, `Pagination`, `PokemonCard` | Search box, PokéAPI / Local database switch, page navigation with page size, card of a Pokémon (with degraded states). |
| `PokemonDetailPanel` | Detail dialog: picture, measures, abilities, stats with bars, description, evolution tree (navigable in the PokéAPI source). |
| `LocalRecordEditor` | Edit and delete (with confirmation) a local record. |
| `LocalDataSection` | Add or edit the proprietary fields of any Pokémon; offers to reload on a conflict and to restore a deleted record. |
| `CreatePokemonDialog` | Create a local record. |
| `Modal`, `PokemonFormFields`, `localRecordForm.js` | Shared dialog (focus trap, Escape, focus return), shared form fields, form rules (the API's, checked before sending). |

**CRUD in the interface** (source *Local database*): **+ New Pokémon** (create), list / search / detail (read),
**Edit Pokémon** and **Local data** (update), **Delete Pokémon** (delete). In the PokéAPI source, any Pokémon can be
enriched with **Local data**.

**Search:** typing filters the page already loaded; Enter (or **Search**) looks the name or id up in the selected
source; clearing the box returns to the list. Cards from the list and from the search look the same and open the
same detail.

---

## 9. Testing

```bash
./mvnw test                  # back-end tests
./mvnw verify                # + JaCoCo coverage report: target/site/jacoco/index.html
cd WebAppClient && npm test  # front-end tests (Vitest + Testing Library)
cd WebAppClient && npm run lint
```

| Suite | Count | Line coverage |
|---|---|---|
| Back-end (JUnit 5, Mockito, Spring test slices) | 337 | 97% |
| Front-end (Vitest, Testing Library) | 139 | 98% |

Every core component has its own unit tests (services, validator, mapper, PokéAPI access, retry policy, error
handler, `ETag`/`If-Match`, controllers, hooks, components). Besides them:

- `ApplicationSmokeTest` starts the whole application — the real Flyway migrations (schema and demonstration data)
  on an in-memory H2 database in MySQL mode, JPA, caches, Spring MVC — and runs a record through its life over HTTP.
- `LocalRecordVersioningJpaTest` checks the optimistic concurrency against real Hibernate.
- `PokemonClientContractTest` checks the Feign client and the retry policy against a simulated PokéAPI (WireMock).

H2 and WireMock are test-scope dependencies only.
