# Life Admin — Build Progress

Tracks implementation against the phased roadmap (`ROADMAP.md`). Each phase ends with a
runnable, testable application.

> ## ▶ Resume here (session checkpoint)
> **Done so far:** Phases 1–4 + post-phase work: Tesseract OCR, real OCR date parsing, stricter date
> extraction (decision 1b), reminder before/after direction, in-app confirm dialogs, and the full
> **platform admin** (SUPER_ADMIN: overview, users, documents, upload rules, document types & AI
> templates). Backend **49 tests green**; frontend `npm run build` green.
>
> **Run it again (from `backend/`):** set `JAVA_HOME` to JDK 21, then `mvn spring-boot:run` with:
> `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/lifeadmin`,
> `LIFEADMIN_JWT_SECRET=<≥32 chars>`, `LIFEADMIN_STORAGE_ENABLED=false`,
> `SPRING_RABBITMQ_USERNAME/PASSWORD=guest`, `LIFEADMIN_EMAIL_PROVIDER=log`,
> `LIFEADMIN_OCR_PROVIDER=tesseract` (optional: `LIFEADMIN_REMINDER_POLL_INTERVAL_MS=10000` for demos).
> Frontend: `npm run dev` (→ :3000). Postgres: `POSTGRES_PORT=5433 docker compose up -d postgres`;
> RabbitMQ = existing `rabbitmq` container (guest/guest). Build/test require JDK 21 (machine default
> `java` is JDK 8). Test user `juan@example.com` / `ChangeMe123!`; admin `admin@lifeadmin.local` /
> `ChangeMeAdmin123!`.
>
> **Next up (agreed):** admin ability to **promote/demote a user's role** (and optionally
> disable/enable an account) from the Users page. Then Phase 5 (search, mobile polish, hardening,
> usage limits). Optional backlog: dynamic document-type *codes*; real AI field extraction; provenance
> "read from …" labels on the review screen (decision 2).

| Phase | Scope | Status |
|-------|-------|--------|
| 0 | Pre-implementation deliverables (architecture, ERD, API, structure, roadmap) | Done |
| 1 | Foundation — scaffold, auth (register/login/refresh/logout/me), Docker Compose, PWA shell | Done |
| 2 | Documents — upload, object storage, list/detail/delete | Done |
| 3 | AI processing — OCR, classification, extraction, review | Done |
| 4 | Reminders — important dates, scheduler, notifications, dashboard | Done |
| 5 | Polish — search, mobile UX, hardening, usage limits, analytics | Pending |

## Phase 1 — what was built & verified

**Backend** (`backend/`, Spring Boot 3.5.6 / Java 21):
- Flyway `V1` — `account`, `app_user`, `subscription`, `refresh_token` (ownership at the account
  level, G12; subscription is the single source of plan/limits; refresh tokens stored as hashes).
- Entities + repositories; `Auditable` mapped superclass; `Plan`/`UserRole` enums.
- JWT security: short-lived HS256 access tokens + **stateful refresh tokens** with rotation &
  revocation (G18). `BCrypt` password hashing. Stateless filter chain; method security enabled.
- Auth module: `POST /auth/register` (creates Account + owner User + FREE Subscription),
  `/auth/login`, `/auth/refresh` (rotates), `/auth/logout` (revokes), `GET /auth/me`.
- Cross-cutting: standard error envelope + `GlobalExceptionHandler`, correlation-id filter (G14),
  `/api/v1` prefix via a controller path-prefix (not servlet context-path — keeps matching
  consistent across MockMvc/security/prod), Actuator health, springdoc OpenAPI.
- **Tests: 9 green** (4 JWT unit + 5 auth-flow integration via Testcontainers Postgres) on JDK 21.

**Frontend** (`frontend/`, React 19 + TS + Vite + Tailwind, PWA):
- axios client with JWT interceptor + automatic one-time refresh-on-401; token store; auth API.
- Auth context/provider; protected route.
- Pages: Landing, Login, Register (auto-login after register), empty Dashboard (metrics
  placeholders + logout).
- PWA manifest + service worker (vite-plugin-pwa). `npm run build` green (tsc + vite).

**Infra** (`docker-compose.yml`): postgres, rabbitmq, minio (+ bucket init), mailpit; backend;
frontend under the `full` profile. Frontend Dockerfile (node build → nginx) + nginx `/api` proxy.

## Running locally (verified)

- Postgres via compose (this run used host port **5433** because 5432 was already in use):
  `POSTGRES_PORT=5433 docker compose up -d postgres`
- Backend: `mvn spring-boot:run` with `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/lifeadmin`
  and a `LIFEADMIN_JWT_SECRET` ≥ 32 bytes. API at `http://localhost:8080/api/v1`,
  Swagger at `http://localhost:8080/swagger-ui.html`, health at `http://localhost:8080/actuator/health`.
- Frontend: `npm run dev` → `http://localhost:3000` (dev server proxies `/api` → `:8080`).
- **Verified:** health UP; register → login → `/auth/me` (role OWNER, plan FREE); `/auth/me`
  without a token → 401; SPA serves and its `/api` proxy reaches the backend.

## Phase 2 — what was built & verified

**Backend:**
- Flyway `V2` — `document` (incl. `title` G19, status, nullable documentType/personId), `document_artifact`
  (ORIGINAL/PROCESSED/THUMBNAIL storage keys), `audit_log`.
- **Object storage abstraction** `ObjectStorageProvider` with two impls: `S3ObjectStorageProvider`
  (MinIO/S3, path-style, pre-signed GET URLs; active when `lifeadmin.storage.enabled=true`) and a
  dev `FilesystemObjectStorageProvider` fallback (active when disabled). Swappable per spec §36.
- **File-safety pipeline** (`FileValidator`, G8): magic-byte MIME detection via Tika (declared
  type/extension NOT trusted), size guard, PDF page-limit + encrypted/malformed rejection, and
  EXIF/GPS stripping by re-encoding images through ImageIO.
- **QuotaService** (G3/D1): enforces the plan document limit from `Subscription` at upload.
- Document module: upload (validate → store → doc+artifact → audit), list (paged, ownership-scoped),
  get, PATCH (title/type/personId/archive), **hard-delete** (purges storage + artifact rows + audit,
  G5), and `GET /{id}/download` returning a short-lived signed URL (G10). Ownership enforced via
  `findByIdAndAccountId` → 404 (not 403) on a foreign document to avoid leaking existence.
- **Tests: 17 green total** (Phase 1 + FileValidator unit + DocumentFlow integration:
  upload/list/get/download/delete, MIME rejection, cross-account 404, unauthenticated).

**Frontend:**
- `documents` API module + types; **Upload page** with camera capture (`capture="environment"`) and
  a file picker + progress bar; **Documents list** (cards, status badges); **Document detail**
  (metadata, download, archive, delete-with-confirm); shared `AppHeader` nav; dashboard shows the
  document count + "Add document" CTA. Mobile-friendly. `npm run build` green.

**Verified end-to-end (real backend):** upload a PNG → list → get → signed download URL → delete →
404; text-as-PNG rejected `400 UNSUPPORTED_MEDIA_TYPE` on detected content.

> **Infra note:** `minio/minio` could not be pulled in this environment (Docker Hub blocked; only a
> private Harbor mirror without MinIO is available). The S3 path is code-complete and covered by
> tests (in-memory stub); local runtime verification used the filesystem storage fallback
> (`LIFEADMIN_STORAGE_ENABLED=false`). In an environment with MinIO, set it `true` to exercise S3.

## Phase 3 — what was built & verified

**Backend:**
- **Async pipeline over RabbitMQ** (spec §21/§22): upload publishes `DOCUMENT_UPLOADED` (after commit)
  to a work queue; a worker consumes it. Work queue is bound to a **dead-letter exchange/queue**;
  listener retries with backoff, and exhausted messages dead-letter → the document is set FAILED (G4).
- **Provider abstractions** (spec §24/§36): `OcrProvider` + `AiExtractionProvider` behind interfaces
  with **deterministic stub impls** (default) — the whole pipeline runs with **no cloud keys and no
  PII leaving the machine**. `StubOcrProvider` reads real text from PDFs (PDFBox) and uses the file
  name as a hint for images; `StubAiExtractionProvider` classifies by keyword and emits fields/dates/
  actions, including a **DERIVED** warranty-expiration (purchase + term), marked as such (spec §9/§25).
- **Idempotent worker** (G11) via the `processed_event` ledger; re-delivery is a no-op. AI extraction
  gated on `Subscription.aiExtractionEnabled` (D-decision: on for FREE by default).
- Flyway `V3`: `extracted_field`, `important_date`, `suggested_action`, `processed_event`.
- Status flow: UPLOADED → PROCESSING → REVIEW_REQUIRED (or FAILED). `POST /documents/{id}/verify`
  marks corrected fields USER/verified → ACTIVE; `POST /documents/{id}/process` re-enqueues; manual
  important-date CRUD added. Document detail now returns fields/dates/actions + classification confidence.
- **Dual publisher**: AMQP (default; defers publish until commit so the worker sees committed data)
  and a **synchronous** publisher for tests / no-broker dev (`lifeadmin.messaging.enabled=false`),
  which processes inline. Selected by config.
- **Tests: 23 green** (adds stub-extractor unit tests + a processing integration test:
  upload → processed → REVIEW_REQUIRED with fields/dates/actions → verify → ACTIVE, and a
  warranty derived-date case).

**Frontend:**
- Document detail **polls** while UPLOADED/PROCESSING and shows a processing banner; a
  **Review screen** ("We found these details") with editable document type + fields (source/confidence
  badges), important dates with **explicit vs. derived** badges, and suggested actions; Confirm →
  ACTIVE. FAILED shows a **Try again** (reprocess) action. Extracted dates shown on the detail page.
  `npm run build` green.

**Verified end-to-end (real async, RabbitMQ):** uploaded `my_car_registration.png` → worker
classified VEHICLE_REGISTRATION with an expiration date + actions → REVIEW_REQUIRED → verify → ACTIVE;
warranty upload produced an explicit PURCHASE (OCR) + DERIVED WARRANTY_EXPIRATION (purchase + 2y).

> **Provider note:** real OCR/AI providers are a drop-in behind the interfaces (set
> `lifeadmin.ocr.provider` / `lifeadmin.ai.provider` and supply the bean). The MVP ships the stubs so
> the flow is fully demonstrable without cloud credentials; this also keeps the PII-to-third-party
> decision open. RabbitMQ can be disabled (`lifeadmin.messaging.enabled=false`) to run without a broker.

## Phase 4 — what was built & verified

**Backend:**
- Flyway `V4`: `reminder`, `notification`, `shedlock`. Reminder timing is **timezone-aware** (G16):
  we persist the local date + the user's IANA timezone **and** a precomputed `scheduled_for_utc` the
  scheduler queries by; `ReminderScheduling` holds the pure (unit-tested) tz math. A partial index
  `(scheduled_for_utc) WHERE sent_at IS NULL AND status='SCHEDULED'` backs the scheduler's hot query.
- **Reminder module**: `POST /reminders` creates one reminder per "N days before" offset relative to
  an important date (7/30/60/90/custom) or one for an explicit date; `GET /reminders?upcoming=`,
  `PATCH /reminders/{id}` (reschedule/change channel/cancel), `DELETE`. All ownership-scoped.
- **Scheduler** (`@Scheduled` + ShedLock `@SchedulerLock`, G2): polls for due reminders and fires each
  in its own transaction (`ReminderFiringService`) so one failure can't abort the batch. Firing
  creates an in-app notification per account user **idempotently** — a `(user_id, reminder_id, type)`
  unique index + an existence guard guarantee at-most-once delivery across poll runs/instances — and,
  for the EMAIL channel, sends via the `EmailProvider` (best-effort; email failure doesn't roll back
  the in-app notification). The reminder is then stamped SENT.
- **Email abstraction** (spec §16/§36): `EmailProvider` with `SmtpEmailProvider` (default, Mailpit/
  prod via Spring Mail) and `LogEmailProvider` (`lifeadmin.email.provider=log`) — no SMTP needed for
  dev/tests.
- **Notifications**: `GET /notifications?unread=`, `GET /notifications/unread-count`,
  `POST /notifications/{id}/read`, `POST /notifications/read-all` (all caller-scoped).
- **Dashboard** `GET /dashboard` (spec §5): headline counts (total/active/needsReview/expiringSoon),
  upcoming important dates with a **timezone-aware `daysUntil`** (computed against "today" in the
  user's zone so it's never off-by-one across the dateline), and a needs-attention list flagging
  actionable dates within 30 days that have **no reminder configured**.
- **Tests: 28 green** (adds `ReminderScheduling` tz-math unit tests + a reminder-flow integration
  test: create reminder → backdate → invoke scheduler → notification created + reminder SENT +
  idempotent second poll + mark-all-read, and a dashboard upcoming-dates case).

**Frontend:**
- API modules + types for reminders, notifications, dashboard.
- **Notification bell** in the header with an unread badge (polls the count every 30s) and a dropdown
  notification center (list, mark-one-read, mark-all-read).
- **Set-reminder** control inline on each important date in the document detail (7/30/60/90/custom
  offsets + In-app/Email channel).
- **Reminders page** (`/reminders`): upcoming vs. past/cancelled, cancel & delete.
- **Dashboard** wired to `/dashboard`: live counts, upcoming dates with day counts and a "no reminder"
  hint, and a needs-attention section linking back to the document. `npm run build` green.

**Verified end-to-end (real scheduler + RabbitMQ + Postgres):** created a reminder (Manila 09:00 →
correct 01:00 UTC instant), the scheduler polled and fired it → reminder SENT + in-app notification;
repeated poll cycles produced **no duplicate notifications** (idempotent); dashboard returned the
upcoming warranty-expiration with its `daysUntil`. Email used `LogEmailProvider` for this run
(Mailpit image unavailable in-env, same Docker Hub limitation noted in Phase 2).

## Real OCR — Tesseract (Tess4J) added post-Phase-4 for the demo

The stub `OcrProvider` is fine for keyless/test runs but only reads text from *digital* PDFs (images
fell back to a filename hint). A real, **free, fully local** OCR engine is now wired in behind the
same interface so scanned images and photos are actually read.

- **`TesseractOcrProvider`** (Tess4J 5.13.0 / JNA), active when `lifeadmin.ocr.provider=tesseract`
  (the stub stays the default, so tests and keyless runs are unchanged). The native Tesseract +
  Leptonica libraries are bundled by the `lept4j` transitive dep — **no system install of Tesseract
  is required**.
- **Strategy:** digital PDFs still use the PDFBox text layer (no OCR needed); image-only PDFs are
  rasterized per page (`PDFRenderer` at a configurable DPI) and OCR'd; images are OCR'd directly.
- **Free & private:** everything runs on-device — no cloud keys, no PII leaves the machine (keeps the
  D-decision on PII open). Uses the `eng` language.
- **Bundled language data:** `backend/src/main/resources/tessdata/eng.traineddata` (tessdata_fast,
  ~4 MB) ships in the jar so it works out of the box; `TesseractOcrProvider` auto-detects it (or set
  `lifeadmin.ocr.datapath`). `.gitattributes` marks `*.traineddata` binary.
- **Graceful degradation:** if the native engine or tessdata is missing, or OCR throws, it logs and
  falls back to the filename hint — a demo never crashes on an OCR-less environment.
- **Config:** `lifeadmin.ocr.{provider,datapath,language,pdf-render-dpi}` (env:
  `LIFEADMIN_OCR_PROVIDER`, `LIFEADMIN_OCR_DATAPATH`, `LIFEADMIN_OCR_LANGUAGE`, `LIFEADMIN_OCR_PDF_DPI`).

**Verified end-to-end:** with `LIFEADMIN_OCR_PROVIDER=tesseract`, uploaded an image named
`scan001.png` (a neutral filename with **no** keyword hint) containing the drawn text
"INSURANCE POLICY / Policy Number …" → the pipeline classified it **INSURANCE** (0.95). Because the
filename carries no hint, the classification could only come from Tesseract reading the pixels —
confirming real OCR. All **28 tests remain green** (stub still the default in tests).

> Note: document *classification* and *field* extraction are still the deterministic keyword stub; a
> real model is a drop-in behind `AiExtractionProvider` (`lifeadmin.ai.provider`).

### Real date reading (deterministic, no AI model)

The stub used to emit **synthetic** important dates (e.g. `today + 3 months`), which didn't match the
actual document. It now reads **real dates from the OCR text** via a rule-based `DateTextParser`:

- **Formats:** ISO (`2026-09-30`), numeric (`30/09/2026`, `09/30/2026`, `30.09.2026`), spelled month
  (`30 September 2026`, `Sep 30, 2026`), and month/year-only (`09/2026`, `Sep 2026`).
- **Ambiguity rules:** a bare month/year resolves to the **last day of the month** (safe for an
  expiration); ambiguous numeric like `03/04/2026` uses `lifeadmin.ai.date-day-first` (default
  **false** = MM/DD, common on PH docs; set true for DD/MM). Invalid dates (`2026-13-40`) are dropped.
- **Type inference:** the wording near each date sets its `DateType` (`valid until`/`expires` →
  EXPIRATION, `renewal` → RENEWAL, `amount due`/`payment` → PAYMENT_DEADLINE, `date of purchase` →
  PURCHASE, etc.); unlabeled dates fall back to the document type's primary date. Read dates are
  marked `OCR` (medium confidence, pending user verification).
- **Warranty:** if a real purchase date is read, the DERIVED warranty expiration is recomputed as
  purchase + 2 years from the *real* date. When the text has **no** dates, the old synthetic
  placeholders still apply so the demo always shows something.
- **Tests: 38 green** (adds 8 `DateTextParser` format/ambiguity tests + real-date extraction cases).

**Verified end-to-end:** an image reading "INSURANCE POLICY … Valid until 09/2026" (neutral filename)
→ OCR → classified INSURANCE with **EXPIRATION = 2026-09-30** (source OCR), i.e. the real date off the
document, not a synthetic placeholder.

### Reminder direction — before / after the date

Reminders can now be scheduled **before or after** an important date (previously only "N days
before"). Use case: "remind me 1 week *after* the expiration" (e.g. a grace period follow-up).

- **API:** `CreateReminderRequest` gained `direction` (`BEFORE` default, or `AFTER`). The backend
  computes `date − offset` for BEFORE and `date + offset` for AFTER; titles read "… in N days" vs
  "… N days after …". Offsets and the explicit-`reminderDate` path are unchanged.
- **Frontend:** the set-reminder form has a **Before / After toggle** next to the 7/30/60/90/custom
  presets, with a live summary line ("7 days after the date") and the heading flipping accordingly.
- **Tests:** added an integration case asserting AFTER → `date + 7` and BEFORE → `date − 7` on the
  same date. Also fixed a latent test-flakiness bug: the `@Scheduled` reminder poll could race with
  tests' manual `scheduler.poll()`; the test base now sets a ~1-year poll interval so the auto-run
  never fires and tests drive the scheduler deterministically. **39 tests green.**

**Verified end-to-end:** an insurance doc read as **EXPIRATION 2026-09-30** (exact, from OCR) → a
BEFORE-7 reminder landed 2026-09-23 and an AFTER-7 reminder landed 2026-10-07.

### Stricter date extraction (decision 1b) — stop pulling in unrelated dates

The date reader used to keep *every* date it found and force-label unlabeled ones as the document's
primary type, so footer timestamps, "date issued", reference numbers, etc. showed up as (often
mislabeled) important dates. Now it's conservative:

- **`DateTextParser`** classifies each date's surrounding words on the **same line** (so a keyword
  from a previous line can't attach to this line's date) and reports whether the date was
  **keyword-matched**. It also drops **noise** dates whose context says they aren't reminder-worthy:
  `issued`, `printed`/`printed on`, `date of birth`/`dob`, `as of`, `generated`, and reference/number
  contexts (`reference`, `ref no`, `invoice no`, `account no`, `receipt/or no`, `transaction`).
- **`StubAiExtractionProvider` (1b):** keeps **all keyworded dates** at medium confidence; **drops
  unlabeled dates** — with one exception: if the document has **no keyworded date of its primary
  type** (e.g. an insurance doc with no explicit "expiration"), it keeps the **single most-plausible
  unlabeled date** (latest future, else latest) as that primary type at **low confidence (0.45)** so
  it surfaces for the user to confirm instead of being silently lost. Synthetic placeholder dates
  still apply only when the text has no usable dates at all.
- **Tests:** +6 (noise-drop, keyworded vs unlabeled flag, "expiration kept while issued/printed
  dropped", and "unlabeled-only → one low-confidence primary date"). **44 tests green.**

**Verified end-to-end:**
- Doc with "Date issued: 2026-01-05 / Valid until 2027-03-31 / Printed on 2026-01-06" → **only**
  EXPIRATION 2027-03-31 (conf 0.80); issued/printed dropped.
- Doc whose only date is a bare "2027-06-30" → a single EXPIRATION 2027-06-30 at **conf 0.45** (low,
  flagged for verification).

> The low-confidence value (< 0.5) is the signal the review UI can use to prompt "please confirm this
> date". Surfacing that hint visually (and a "read from …" provenance label) is decision 2 — not done
> yet; this change is decision 1b only.

## Platform admin (SUPER_ADMIN) — users, documents, upload rules, document types & AI templates

A cross-account platform administrator, gated behind a new **SUPER_ADMIN** role, with a dedicated
admin section in the UI. Built in phases; everything is admin-editable at runtime (no redeploy).

**Backend:**
- **Role & seed:** `UserRole.SUPER_ADMIN`; Flyway `V5` widens the user-role CHECK. `AdminSeeder`
  (an `ApplicationRunner`) creates the admin account/user/subscription at startup **only if missing**
  (idempotent), hashing the password with the app `PasswordEncoder` — no bcrypt hash or password in
  SQL. Credentials via `lifeadmin.admin.{email,password,name}` (env-overridable; default
  `admin@lifeadmin.local` / `ChangeMeAdmin123!` for dev).
- **Authorization:** `SecurityConfig` restricts `/api/v1/admin/**` to `hasRole('SUPER_ADMIN')`; the
  role flows through the JWT claim → `ROLE_SUPER_ADMIN` authority. Regular users get 403, anonymous 401.
- **Read dashboards** (`AdminService`/`AdminController`): `GET /admin/overview` (users, accounts,
  documents, active/processing/failed counts), `GET /admin/users` (each with their document count),
  `GET /admin/documents?accountId=&page=` (cross-account browser, paged).
- **Upload rules** (`AppSetting` key/value table `V6` + `UploadRulesService`): admin-editable allowed
  MIME types (restricted to what the pipeline supports), max file size, max PDF pages. `FileValidator`
  reads these at request time; unset → code defaults (`UploadProperties`). `GET/PUT
  /admin/settings/upload`.
- **Document types + AI templates** (`document_type_config` table `V7`, seeded from the enum):
  per-type `enabled` flag, display `label`, classification `keywords`, `relevant_date_types`, and
  `default_offsets_days`. `DocumentTypeConfigService` is now the single source the AI classifier and
  primary-date logic read — `StubAiExtractionProvider` classifies via `typeConfig.classify()` and
  picks the primary date from the configured relevant types. `GET /admin/document-types` +
  `PATCH /admin/document-types/{code}`.
- **Tests: 49 green** (adds `AdminApiIntegrationTest`: 403/401 authorization, overview, users, upload
  rules incl. validation + restore-to-defaults, document-type edits; `FileValidatorTest` and
  `StubAiExtractionProviderTest` updated for the new collaborators).

**Frontend:**
- `AdminRoute` guard (auth + SUPER_ADMIN); an **Admin** button in the app header shown only to admins;
  a dark admin shell (`AdminLayout`) with sub-nav.
- Pages: **Overview** (stat tiles), **Users** (table + per-user doc count linking to a filtered doc
  view), **Documents** (paged cross-account browser), **Upload rules** (file-type checkboxes + size +
  PDF pages), **Document types & AI** (per-type editor: label, enabled, keywords, relevant date types,
  default offsets). `admin` API module + types. `npm run build` green.

**Verified end-to-end (live):** admin login → overview/users/documents; set uploads to **PDF-only** →
a PNG upload was rejected (HTTP 400) → restored; added keyword `kontrata` to the CONTRACT type → an
image reading "KONTRATA" was OCR-classified as **CONTRACT** (proving the classifier reads the
admin-edited template at runtime) → restored.

> Security note: the default admin password is for local/dev only — set `LIFEADMIN_ADMIN_PASSWORD`
> per environment. Self-registration always creates OWNER; SUPER_ADMIN is only seeded or promoted.

## Notes / decisions carried from planning
- Local builds require **JDK 21** on `JAVA_HOME` (machine default `java` is JDK 8). Maven 3.8.6.
- Integration tests use the Testcontainers **singleton container** pattern.
- `ddl-auto=validate` — Flyway owns the schema.
- All secrets via env vars; JWT secret must be overridden per environment.
