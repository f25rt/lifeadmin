# Life Admin — Development Roadmap

> Deliverable **E** of spec §37. Milestones aligned to spec §30's five phases. **Each milestone ends
> with a runnable, testable application** (spec §36 #20). Scope reflects `GAPS_AND_DECISIONS.md`
> (email/password only in MVP; billing = limits-only; NL search deferred).

## Milestone 0 — Deliverables approval (this step)
Produce & approve: Architecture, ERD, API spec, Project structure, Roadmap. **No code until
approved.** ✅ (docs complete; awaiting sign-off)

---

## Phase 1 — Foundation
**Goal:** a user can register and log in; the app runs end-to-end locally.

- Scaffold monorepo (`backend/`, `frontend/`, `infrastructure/`, `docs/`) + `docker-compose.yml`
  (postgres, rabbitmq, minio, mailpit).
- Backend: Spring Boot 3 / Java 21, Flyway `V1` (**Account, User, Subscription, RefreshToken** —
  ownership root G12), Spring Security + JWT (short-lived access + **stateful refresh** with
  rotation/revocation, G18), error envelope + correlation-id filter (G14), Actuator health, OpenAPI.
- Endpoints: `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/me`.
- Frontend: Vite + TS + Tailwind PWA shell; Landing, Login/Register, empty Dashboard; auth context +
  protected routes.
- Basic CI (build + test + lint).
- **Runnable check:** register → login → see (empty) dashboard; `/actuator/health` green.
- **Tests:** auth/service unit tests; one Testcontainers integration test (register+login).

---

## Phase 2 — Documents (secure storage)
**Goal:** a user can securely upload, list, view, and delete documents.

- Flyway `V2` (**Document** incl. `title` G19, **DocumentArtifact**), object-storage provider
  (S3/MinIO), signed URLs (G10).
- Upload endpoint with **magic-byte MIME validation**, size limit, **EXIF/GPS stripping**, PDF
  page-limit (G8); server-generated storage keys.
- **QuotaService** enforcing plan document limits (G3/D1) at upload.
- Endpoints: `POST/GET/GET{id}/PATCH/DELETE /documents`, `GET /documents/{id}/download`.
- **Hard-delete** purges DB + storage artifacts; audit-log view/download/delete (G5, §18).
- Frontend: Upload screen (incl. camera "Take Photo"), Document list + details (no AI yet),
  archive/delete.
- **Runnable check:** upload a file → see it listed → open details → download via signed URL →
  delete (gone from storage too).
- **Tests:** ownership checks (404 on foreign doc), quota exceeded, MIME rejection, delete purges
  storage (integration).

---

## Phase 3 — AI Processing (understanding)
**Goal:** upload a document and receive structured, verifiable information.

- RabbitMQ topology (work queue + **DLX/DLQ**); event envelope + `ProcessedEvent` idempotency (G11).
- Provider interfaces + impls: `OcrProvider`, `AiExtractionProvider` (strict JSON schema), plus
  `Stub*` impls for keyless local runs; `application` profiles select impls.
- Worker pipeline: OCR → classify → extract → **date derivation (marked DERIVED)** → **thumbnail** →
  persist `ExtractedField`/`ImportantDate`; status transitions UPLOADED→PROCESSING→REVIEW_REQUIRED,
  with retry/backoff and **FAILED + failureReason** on DLQ (G4). Locale/date/currency normalization
  (G6). Flyway `V3`.
- Endpoints: `POST /documents/{id}/process`, `POST /documents/{id}/verify`, `/documents/{id}/dates`.
- Frontend: Processing screen (poll status), Document Review ("We found these details — verify"),
  edit fields, confirm → ACTIVE.
- **Runnable check:** upload → processing → review screen shows extracted fields + dates with
  source/confidence → verify → ACTIVE.
- **Tests:** idempotent re-delivery creates no duplicates; DLQ path sets FAILED; verify flow marks
  fields USER/verified. (AI output-quality testing excluded per decision D4.)

---

## Phase 4 — Reminders (the core promise)
**Goal:** the user reliably receives useful reminders.

- Flyway `V4` (**Reminder, Notification**); reminder creation from offsets/explicit dates with
  precomputed `scheduledForUtc` + IANA timezone (G16).
- **Scheduler** (`@Scheduled` + **ShedLock**) finds due reminders, triggers **NotificationService**
  (in-app rows + email via provider/Mailpit), marks sent; notification uniqueness prevents
  double-send (G2). Emails rendered in the user's timezone.
- Endpoints: `/reminders` CRUD, `/notifications` (list/read), `/dashboard` (upcoming, counts,
  **needs-attention** G-Feature 9).
- Frontend: reminder creation from a date, Reminder list, in-app notification center, Dashboard
  "Upcoming" + "Needs Attention".
- **Runnable check:** create a reminder with a near-future `scheduledForUtc` → scheduler fires →
  email appears in Mailpit + in-app notification shows.
- **Tests:** due-selection query, ShedLock single-fire under two instances, dashboard aggregation.

---

## Phase 5 — Polish & hardening
**Goal:** production-ready MVP.

- **Search:** PostgreSQL full-text (`tsvector` + GIN), Flyway `V6`; `GET /documents?q=…` (G9/D3).
- Mobile UX polish + PWA install/offline shell; accessibility pass (WCAG 2.1 AA basics) + i18n
  scaffolding (G17).
- Error handling & processing **retry** UX; rate limiting on auth/upload/process (G4).
- Security hardening review (headers, token rotation, signed-URL TTLs), **PII export/delete** flow
  (G5), usage limits surfaced in UI.
- Analytics/metrics for the success funnel (spec §31): activation, processing success, reminder
  conversion, retention.
- **Runnable check:** search finds a document by org/person/date; delete-my-data removes all traces;
  metrics visible.

---

## Phase 2+ (post-MVP, planned — not now)
OAuth (Google/Apple) (D2); payment/billing + plan upgrade (D1); family/business workspaces + roles
(D5); natural-language/semantic search (D3); push/SMS/Messenger/WhatsApp; Gmail/Drive integration;
smart calendar; the broader "personal administrative assistant" behavior (spec §34–35).

---

## Cross-cutting (every phase)
- Flyway migrations own the schema; Hibernate `validate`.
- Providers stay behind interfaces; secrets via env vars only.
- Every resource endpoint verifies **account ownership**.
- Docker Compose stays the source of truth for local dev.
- Unit tests for business logic; integration tests for the document pipeline (Testcontainers),
  providers stubbed.
```
