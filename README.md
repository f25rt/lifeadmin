# Life Admin

> "Never forget an important document, deadline, warranty, or renewal again."

Upload a document → the system reads it (OCR + AI) → you verify the extracted details → it creates
reminders and notifies you before dates matter.

**Status:** Phases 1–5 implemented. Auth, document upload + object storage, async OCR/AI extraction,
reminders + notifications, a platform admin console (SUPER_ADMIN), full-text document search,
self-service data export/delete, plan-usage surfacing, and security hardening (headers + rate
limiting) are all in place. See [`docs/BUILD_PROGRESS.md`](docs/BUILD_PROGRESS.md) for the detailed
per-phase log.

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, Flyway, RabbitMQ, Maven
- **Frontend:** React 19 + TypeScript + Vite + Tailwind CSS (PWA)
- **Data:** PostgreSQL 16; object storage (S3-compatible; MinIO locally)
- **Async:** RabbitMQ (work queue + dead-letter queue)
- **Pluggable providers:** OCR (stub / Tesseract), AI extraction (stub), email (SMTP / log) — all behind interfaces
- **Infra:** Docker + Docker Compose (Postgres, RabbitMQ, MinIO, Mailpit)

## Repository layout

```
backend/    Spring Boot API (Java 21, Maven)
frontend/   React + Vite PWA
docs/        Architecture, data model, API spec, roadmap, build progress
docker-compose.yml   Local infra + optional app containers
.env.example         Copy to .env and adjust
```

---

## Prerequisites

- **JDK 21** (build/test require it; the app targets Java 21). Set `JAVA_HOME` to a JDK 21 install.
- **Maven 3.8+** (or use the bundled wrapper if present).
- **Node.js 20+** and **npm** (frontend).
- **Docker + Docker Compose** (Postgres, RabbitMQ, MinIO, Mailpit).

> Tesseract OCR is optional and bundled via the `lept4j`/`tess4j` dependencies (native libs + an
> `eng.traineddata` language file ship in the jar) — no system install needed. It's only active when
> `LIFEADMIN_OCR_PROVIDER=tesseract`; the default `stub` provider needs nothing.

---

## Installation (local development)

### 1. Configure environment

```bash
cp .env.example .env
```

Edit `.env` and, at minimum, set a strong `LIFEADMIN_JWT_SECRET` (**must be ≥ 32 bytes**). Change
`LIFEADMIN_ADMIN_PASSWORD` too if you'll expose the app beyond your machine.

### 2. Start infrastructure

Bring up Postgres, RabbitMQ, MinIO (+ bucket init), and Mailpit:

```bash
docker compose up -d postgres rabbitmq minio minio-init mailpit
```

If port 5432 is already taken, override it: `POSTGRES_PORT=5433 docker compose up -d postgres …`
(then use that port in `SPRING_DATASOURCE_URL` below).

### 3. Run the backend

From `backend/`, with `JAVA_HOME` pointing at JDK 21:

```bash
# minimum env for a local run
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lifeadmin
export LIFEADMIN_JWT_SECRET=dev-only-insecure-secret-change-me-please-32bytes-minimum!!
export SPRING_RABBITMQ_USERNAME=lifeadmin
export SPRING_RABBITMQ_PASSWORD=lifeadmin

mvn spring-boot:run
```

- API base: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

Flyway migrates the schema on startup. A platform admin is seeded on first run from
`lifeadmin.admin.*` (default `admin@lifeadmin.local` / `ChangeMeAdmin123!` — **dev only**).

Useful optional env:

| Variable | Purpose | Default |
|----------|---------|---------|
| `LIFEADMIN_OCR_PROVIDER` | `stub` or `tesseract` (real local OCR) | `stub` |
| `LIFEADMIN_EMAIL_PROVIDER` | `smtp` (Mailpit/prod) or `log` | `smtp` |
| `LIFEADMIN_STORAGE_ENDPOINT` | S3/MinIO endpoint | `http://localhost:9000` |
| `LIFEADMIN_MESSAGING_ENABLED` | set `false` to process inline without RabbitMQ | `true` |
| `LIFEADMIN_RATE_LIMIT_ENABLED` | toggle the request rate limiter | `true` |

### 4. Run the frontend

From `frontend/`:

```bash
npm install
npm run dev
```

Open `http://localhost:3000`. The dev server proxies `/api` → `http://localhost:8080`.

### 5. Log in

Register a new user in the UI (creates an account + FREE subscription), or sign in as the seeded
admin to reach the `/admin` console.

---

## Running tests

**Backend** (needs Docker running — integration tests use Testcontainers Postgres; JDK 21 required):

```bash
cd backend
mvn test
```

**Frontend** (type-check + production build):

```bash
cd frontend
npm run build
```

---

## Deployment

### Option A — Docker Compose (all-in-one)

The compose file can build and run the app containers alongside infra. The frontend is behind the
`full` profile (built as a static bundle served by nginx, which proxies `/api` to the backend).

1. Set production values in `.env` — **always** override:
   - `LIFEADMIN_JWT_SECRET` (≥ 32 bytes, unique per environment)
   - `LIFEADMIN_ADMIN_PASSWORD`
   - `POSTGRES_PASSWORD`, `RABBITMQ_DEFAULT_PASS`, `MINIO_ROOT_PASSWORD`
2. Build and start everything:

   ```bash
   docker compose --profile full up -d --build
   ```

3. The backend runs with `SPRING_PROFILES_ACTIVE=docker` and talks to the compose-internal
   `postgres`, `rabbitmq`, `minio`, and `mailpit` services. Frontend is exposed on
   `FRONTEND_PORT` (default 3000 → container port 80); backend on `BACKEND_PORT` (default 8080).

### Option B — build artifacts and deploy separately

**Backend jar:**

```bash
cd backend
mvn clean package        # produces target/*.jar
java -jar target/lifeadmin-backend-*.jar
```

Provide configuration via environment variables (same names as the compose `backend` service).
Point it at a managed PostgreSQL, RabbitMQ, and S3-compatible bucket.

**Frontend static bundle:**

```bash
cd frontend
VITE_API_BASE_URL=/api/v1 npm run build   # outputs dist/
```

Serve `dist/` from any static host/CDN and reverse-proxy `/api` to the backend. The included
`frontend/Dockerfile` does exactly this with nginx.

### Production checklist

- **Secrets:** every secret is env-driven; never commit real values. Rotate `LIFEADMIN_JWT_SECRET`
  and all infra passwords per environment.
- **TLS:** terminate HTTPS at your proxy/ingress. The backend already sends HSTS; it assumes TLS
  upstream.
- **Database:** Flyway owns the schema (`ddl-auto=validate`). Back up Postgres; run migrations on
  deploy (they run automatically at startup).
- **Object storage:** use a real S3/MinIO bucket with the documents bucket pre-created and kept
  private (signed URLs are short-lived).
- **Rate limiting:** the built-in limiter is per-instance (in-memory). Behind multiple instances,
  either front it with a shared limiter (e.g. an API gateway / Redis-backed limiter) or accept
  per-instance windows. Toggle with `LIFEADMIN_RATE_LIMIT_ENABLED`.
- **Admin account:** change the seeded admin password immediately; self-registration only ever
  creates OWNER, never SUPER_ADMIN.
- **OCR/AI:** the default providers are deterministic stubs (no cloud keys, no PII leaves the host).
  Swap in real providers behind the `OcrProvider` / `AiExtractionProvider` interfaces when ready.

---

## Documentation

| Doc | Purpose |
|-----|---------|
| [`docs/BUILD_PROGRESS.md`](docs/BUILD_PROGRESS.md) | Per-phase implementation log + resume checkpoint. |
| [`docs/spec.md`](docs/spec.md) | Original product & technical spec (v1.0). |
| [`docs/GAPS_AND_DECISIONS.md`](docs/GAPS_AND_DECISIONS.md) | Locked scope decisions; authoritative for the MVP. |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System architecture + data-flow. |
| [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) | ERD, entities, enums, relationships. |
| [`docs/API_SPEC.md`](docs/API_SPEC.md) | REST API specification. |
| [`docs/PROJECT_STRUCTURE.md`](docs/PROJECT_STRUCTURE.md) | Repository layout. |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Milestone-based roadmap. |
