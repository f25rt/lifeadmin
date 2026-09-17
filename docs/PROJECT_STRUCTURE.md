# Life Admin — Project Structure

> Deliverable **D** of spec §37. A modular monorepo: `backend/`, `frontend/`, `infrastructure/`,
> `docs/`. Backend is modular-by-domain (spec §36: clean architecture without over-abstraction).

## 1. Top level

```
life-admin/
├── README.md
├── docker-compose.yml            # local dev: postgres, rabbitmq, minio, mailpit, backend, frontend
├── .env.example                  # all secrets/config as env vars (never hard-coded)
├── backend/
├── frontend/
├── infrastructure/
└── docs/
```

## 2. Backend (`backend/`)

Spring Boot 3 / Java 21 / Maven. Packages grouped by **domain module**; each module keeps its
entity, repository, service, DTOs, and REST controller together. Cross-cutting concerns
(security, storage, messaging, providers) live under `common`/`platform`.

```
backend/
├── pom.xml
├── Dockerfile
├── src/main/java/com/lifeadmin/
│   ├── LifeAdminApplication.java
│   ├── account/           # Account, User, roles, ownership resolution
│   │   ├── api/           # AuthController, DTOs (register/login/me)
│   │   ├── Account.java  User.java  *Repository  *Service
│   ├── document/          # Document, DocumentArtifact
│   │   ├── api/           # DocumentController, DTOs
│   │   ├── Document.java  DocumentArtifact.java  *Repository  DocumentService
│   ├── extraction/        # ExtractedField, ImportantDate, verify flow
│   ├── reminder/          # Reminder, scheduler (ShedLock), reminder API
│   ├── notification/      # Notification entity + NotificationService (in-app + email)
│   ├── people/            # Person
│   ├── subscription/      # Subscription/plan + QuotaService (limit enforcement)
│   ├── dashboard/         # Dashboard aggregation (read-only)
│   ├── search/            # Full-text search (tsvector) query support
│   ├── rules/             # VerifiedSourceRule (curated government facts)
│   ├── processing/        # Async worker: consumers, OCR->AI->dates pipeline, DLQ
│   │   ├── DocumentWorker.java
│   │   ├── events/        # event envelope, publisher, ProcessedEvent (idempotency)
│   ├── provider/          # Pluggable provider interfaces + impls
│   │   ├── ocr/           # OcrProvider + CloudVisionOcr / TesseractOcr / StubOcr
│   │   ├── ai/            # AiExtractionProvider + LlmExtraction / StubExtraction
│   │   ├── email/         # EmailProvider + SmtpEmail / MailpitEmail
│   │   └── storage/       # ObjectStorageProvider + S3ObjectStorage
│   ├── security/          # JWT, filters, method-security, ownership guard, rate limiting
│   ├── audit/             # AuditLog + audit aspect/service
│   └── common/            # error envelope, correlation-id filter, config props, pagination
├── src/main/resources/
│   ├── application.yml            # ddl-auto=validate; flyway; provider profiles
│   ├── application-local.yml
│   └── db/migration/              # Flyway V1..V6 (see DATA_MODEL.md §5)
└── src/test/java/com/lifeadmin/
    ├── ...ServiceTest              # unit tests for business logic (quota, dates, reminders)
    └── ...IntegrationTest          # Testcontainers (postgres, rabbitmq, minio); providers stubbed
```

**Notes**
- Providers are selected by Spring profile/config; `Stub*` impls let the full pipeline run locally
  without cloud keys.
- The worker + scheduler run as beans inside the single backend deployable for MVP; the messaging
  contract + ShedLock allow splitting them into a separate process later with no code change.
- All secrets via env vars referenced in `application*.yml` (spec §36 #11–12).

## 3. Frontend (`frontend/`)

React 19 + TypeScript + Vite + Tailwind, as a **PWA** (installable, camera capture for "Take Photo").

```
frontend/
├── package.json  vite.config.ts  tailwind.config.js  tsconfig*.json
├── Dockerfile  nginx.conf         # multi-stage build -> nginx; proxies /api
├── public/  manifest.webmanifest  # PWA manifest + icons
├── index.html
└── src/
    ├── main.tsx  App.tsx  theme/  queryClient.ts
    ├── api/            # axios client (JWT interceptor), typed endpoints, types.ts
    ├── auth/           # AuthProvider/context, ProtectedRoute
    ├── components/     # layout, PageHeader, uploader (camera), status chips
    ├── pages/          # Landing, Login/Register, Dashboard, Upload, Processing,
    │                   #   DocumentReview, DocumentDetails, Reminders, Settings
    ├── features/       # document, reminder, notification hooks (TanStack Query)
    ├── pwa/            # service worker registration, offline shell
    └── i18n/           # message catalogs (English first; Filipino-ready) (G17)
```
MVP screens exactly per spec §29; no admin panel.

## 4. Infrastructure (`infrastructure/`)

```
infrastructure/
├── compose/              # optional split compose files / overrides
├── minio/                # bucket bootstrap (create private bucket, policy)
├── rabbitmq/             # definitions.json (queues, DLX/DLQ bindings)
├── postgres/             # init scripts if needed
└── ci/                   # CI pipeline definitions (build/test/lint)
```

## 5. Local dev — `docker-compose.yml` services

| Service | Image | Purpose |
|---------|-------|---------|
| `postgres` | `postgres:16-alpine` | Primary DB. |
| `rabbitmq` | `rabbitmq:3-management` | Async broker + management UI + DLQ. |
| `minio` | `minio/minio` | S3-compatible object storage. |
| `mailpit` | `axllent/mailpit` | Captures outbound email + web UI. |
| `backend` | built from `backend/Dockerfile` | API + worker + scheduler. |
| `frontend` | built from `frontend/Dockerfile` | PWA served by nginx (under a `full` profile). |

`docker compose up -d postgres rabbitmq minio mailpit` for infra, then run backend/frontend locally
or `--profile full` for everything.

## 6. Conventions

- **Ownership check** helper in `security` used by every resource endpoint (G12).
- **Error envelope** + **correlation-id** filter in `common` (G14, API_SPEC §10).
- **Migrations** are the single source of truth for schema; entities are `validate`-only.
- **OpenAPI** via springdoc; **health** via Actuator.
