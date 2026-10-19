# Token Router

Middle proxy between a client app and an LLM deployment: tracks token usage, blocks requests when
the company runs out of credit (`HTTP 402`), and shows usage statistics.

# TODO

- [x] PostgreSQL service
- [x] simple query demo
- [ ] Subscribe event
  - ?run out of credits event

- [x] InfluxDB service
- [x] simple query demo

- [x] Company Schema Definition
- [x] Migrations

- [ ] Aggregation job (InfluxDB → PostgreSQL `tokens_spent`)
- [ ] 402 limit check + fake LLM proxy endpoint
- [ ] Usage monitoring UI (app ↔ Influx)

# Definitions

- API key
  - "${Company prefix}-${uuid}"
- Company
  - info
  - valid API keys
  - valid LOGINS

# Goals

- Usage tracking per company
- API Key validity
- Usage monitoring UI

# Architecture

```
docker compose                      server (:8080)                  desktop app
┌─────────────────────┐            ┌──────────────────────┐        ┌─────────────────┐
│ postgres (:5432)    │◄──Flyway───│ Database.init()      │        │  Companies list │
│  companies, users,  │◄──Exposed──│  Exposed queries     │◄─HTTP──│  → detail view  │
│  api_keys           │            │                      │        │  (TokenRouterApi)│
│ influxdb2 (:8086)   │◄──client───│ Influx.writeUsage()  │        └─────────────────┘
│  bucket "usage"     │◄──Flux─────│ Influx.usageSummary()│
└─────────────────────┘            └──────────────────────┘
```

- **PostgreSQL** = state: companies, limits, users, API keys (Flyway owns the schema, Exposed only queries)
- **InfluxDB** = events: every token usage is an immutable point in bucket `usage`
  - measurement `token_usage`, tags `company_id`/`user_id` (filter + group), fields
    `prompt_tokens`/`completion_tokens`/`total_tokens` (sum), time = event time
- **core** (KMP: jvm/android/ios/wasm) = shared models, `ApiKey.generate`, `TokenRouterApi` client — no DB drivers
- **server** (plain JVM) = all DB access, routes, (later) aggregation job
- **app/shared** = one Compose Multiplatform UI for desktop/android/web

# Running the demo

```bash
cd TokenRouter

docker compose up -d        # postgres + influxdb
./kotlin run -m server      # API on :8080 (leave this terminal running)
./kotlin run -m desktopApp  # open a NEW terminal; app window opens (slow first start)
```

Stop:

```bash
lsof -ti tcp:8080 | xargs kill   # server
docker compose down               # DBs (add -v to wipe data + force re-seed)
```

Config (env vars, defaults shown):

```
JDBC_DATABASE_URL=jdbc:postgresql://localhost:5432/tokenrouter
JDBC_DATABASE_USER=tokenrouter
JDBC_DATABASE_PASSWORD=tokenrouter
INFLUX_URL=http://localhost:8086
INFLUX_TOKEN=tokenrouter-demo-token
```

# Routes

## PostgreSQL

```bash
curl localhost:8080/                          # hello check
curl localhost:8080/demo/pg                   # company id=1 full (demo)
curl localhost:8080/api/companies             # list (no keys/users)
curl localhost:8080/api/companies/1           # detail (keys + users)
curl localhost:8080/api/companies/99          # → 404
```

## InfluxDB

```bash
curl localhost:8080/demo/influx/ping                          # {"influx":"up"}
curl -X POST localhost:8080/demo/influx/usage                 # write 1 RANDOM event
curl -X POST localhost:8080/demo/influx/usage -H 'Content-Type: application/json' \
  -d '{"companyId":1,"userId":1,"promptTokens":500,"completionTokens":300,"timestampMs":'$(date +%s000)}'
curl 'localhost:8080/demo/influx/usage?range=-1h'             # aggregates (?range=-10m / -24h)
```

# Looking at the data

```bash
# Influx web UI (raw events + charts):
open http://localhost:8086        # admin / adminpassword → Data Explorer → bucket "usage"

# Raw Influx rows from CLI:
docker exec -it tokenrouter-influxdb-1 influx query \
  'from(bucket:"usage") |> range(start:-1h) |> filter(fn:(r) => r._measurement=="token_usage")' \
  --org tokenrouter --token tokenrouter-demo-token

# Postgres tables:
docker compose exec postgres psql -U tokenrouter -d tokenrouter -c "SELECT * FROM companies;"
docker compose exec postgres psql -U tokenrouter -d tokenrouter -c "SELECT * FROM users;"
docker compose exec postgres psql -U tokenrouter -d tokenrouter -c "SELECT * FROM api_keys;"
docker compose exec postgres psql -U tokenrouter -d tokenrouter -c "SELECT * FROM flyway_schema_history;"
```

# Demo flow (copy-paste)

```bash
curl -X POST localhost:8080/demo/influx/usage   # ×5 (run repeatedly)
curl -X POST localhost:8080/demo/influx/usage -H 'Content-Type: application/json' \
  -d '{"companyId":1,"userId":2,"promptTokens":1200,"completionTokens":800,"timestampMs":'$(date +%s000)}'
curl 'localhost:8080/demo/influx/usage?range=-1h'   # totals grow, split per user
open http://localhost:8086                          # see the same points visually
```

# Project structure

This is a Kotlin Multiplatform project targeting Android, Desktop (JVM), iOS, Web, Server, built with
the [Kotlin Toolchain](https://kotlin-toolchain.org/dev/).

- [/app/androidApp](./app/androidApp) — Android application
- [/app/desktopApp](./app/desktopApp) — desktop (JVM) application
- [/app/iosApp](./app/iosApp) — iOS application
- [/app/shared](./app/shared) — Compose UI shared across all apps
- [/app/webApp](./app/webApp) — web application (Kotlin/Wasm, future PWA)
- [/core](./core) — shared models (`Company`, `User`, `ApiKey`, `Usage`), `TokenRouterApi` client
- [/server](./server) — Ktor server: Postgres (`postgres/`), Influx (`influx/`), routes (`routes/`)

The `kotlin` script is a self-bootstrapping wrapper for the Kotlin Toolchain — no install needed.

- Android app: `./kotlin run -m androidApp`
- Desktop app: `./kotlin run -m desktopApp`
- iOS app: `./kotlin run -m iosApp`
- Web app: `./kotlin run -m webApp`
- Server: `./kotlin run -m server`

Known environment notes:

- iOS builds require Xcode 16+ (`./kotlin build -m ...` fails with Xcode 15.4)
- `./kotlin build -m desktopApp` / `-m server` / `-m core` work on this machine

---

Learn more: [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/dev/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/), [Kotlin/Wasm](https://kotl.in/wasm/), [Ktor](https://ktor.io/).
