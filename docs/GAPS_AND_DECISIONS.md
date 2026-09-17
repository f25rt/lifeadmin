# Life Admin — Gaps & Decisions (Engineering Addendum)

> **Status:** Authoritative for the MVP build where it differs from `spec.md`.
> This document records (a) **locked scope decisions**, (b) **gaps in the original spec** that must
> be resolved before/while building, and (c) the **added entities and mechanisms** the spec implies
> but does not define. It complements — does not replace — `spec.md`.
>
> "MVP" throughout = **Minimum Viable Product**: the smallest releasable version that proves a user
> can upload a document, have it understood, verify it, create a reminder, and receive it reliably.

---

## Part 0 — Locked scope decisions

These were decided explicitly and constrain the MVP build.

| # | Decision | Rationale / effect on MVP |
|---|----------|---------------------------|
| D1 | **Billing = limits-only.** The MVP enforces plan **limits** (e.g. free = 5 documents) but integrates **no payment provider**. Plans are assigned manually (default `FREE`). | Keeps the free-tier constraint real without the cost/complexity of Stripe/PayMongo. Payment integration is a post-validation task. |
| D2 | **OAuth deferred to Phase 2.** MVP ships **email/password only**. Google/Apple sign-in come later. | Google, and especially Apple, sign-in add redirect/callback flows, key rotation, and private-relay email handling — real integration risk in Phase 1. |
| D3 | **Natural-language search deferred.** MVP uses **PostgreSQL full-text search** over indexed fields. LLM/NL search is post-MVP. | NL search is much larger than it reads in the spec; FTS covers title/type/person/org/extracted-text/dates well. |
| D4 | **AI-output testing excluded** from this plan (per instruction). | Not addressed here. (Providers are still mocked in code so the pipeline is testable, but asserting model output quality is out of scope for this doc.) |
| D5 | **Family/People is a data-model concern from day 1, a feature in Phase 2.** Ownership is modeled at an **Account** level from the start (see G12) even though the family UI ships later. | Avoids a painful ownership migration when families/business arrive. |

---

## Part 1 — Gaps that must be resolved before/within their phase

### G1 — Missing `Notification` entity (resolve in Phase 1 data model)
The spec promises in-app notifications (Feature 12) and the dashboard shows notification state, but
§19 has no `Notification` table — reminders and notifications are conflated. **Add** a `Notification`
entity: `id, userId, type, title, body, readFlag, referenceType (REMINDER|DOCUMENT), referenceId,
createdAt`. A `Reminder` firing produces one or more `Notification` rows (in-app) and/or an email.

### G2 — Reminder scheduler design + multi-instance safety (Phase 4; the product's core promise)
The North Star hinges on reminders firing **reliably**, yet the firing mechanism is undefined.
**Decision:**
- A periodic job (`@Scheduled`, e.g. every 5 minutes) queries reminders that are **due** and not yet
  sent, emits `REMINDER_TRIGGERED`, creates the `Notification`/email, and marks the reminder sent.
- **Due** is computed as: `reminderInstantUtc <= now()`, where `reminderInstantUtc` is precomputed
  from the reminder's local date/time + the user's IANA timezone at creation (see G16).
- **Multi-instance safety:** guard the scheduled job with **ShedLock** (DB-backed lock) so running
  more than one backend instance never double-sends. Reminder send is additionally idempotent (G11):
  a `Notification` uniqueness key `(reminderId, type, scheduledFor)` prevents duplicates. (The
  `Reminder` carries the `channel`; the `Notification` carries `type` — the key uses `type`.)

### G3 — Plan-limit enforcement (Phase 2 for the limit; entity in Phase 1)
Monetization defines hard limits but there's no entity or enforcement point. **Add** `Subscription`
(see G-entities) and enforce limits in a small `QuotaService` called by the **upload** endpoint:
reject with `402`/`403` + a machine-readable `code: QUOTA_EXCEEDED` when `activeDocumentCount >=
plan.documentLimit`. Also gate AI features and people-count by plan.

### G4 — AI/OCR cost, failure, retry & dead-letter policy (Phase 3)
Every upload triggers paid OCR + LLM calls. **Add:**
- **Guards before processing:** enforce max file size (10 MB), **max PDF pages** (e.g. 15), allowed
  MIME (G8), and a **per-user processing rate limit**.
- **Retry:** worker retries transient failures with capped exponential backoff (e.g. 3 attempts).
- **Dead-letter queue:** after final failure, the message goes to a **DLQ** and the document is set
  to `status = FAILED` with a stored `failureReason`. The UI surfaces this in "Needs Attention"
  (Feature 9) and offers **retry**.
- **Partial success:** if OCR succeeds but extraction is weak, set `REVIEW_REQUIRED`, not `FAILED`.

### G5 — PII retention & deletion policy (design in Phase 1; enforce by Phase 5)
Documents contain highly sensitive PII (passports, IDs). The spec covers **access** security but not
**lifecycle**. **Add a documented policy:**
- **Account/document deletion** purges DB rows **and** all object-storage artifacts (original,
  processed, thumbnail) — a hard delete, not just a flag.
- A **"delete my data" / export my data** flow (PH **Data Privacy Act 2012** & GDPR-style rights).
- A stated **retention period** for soft-deleted/archived items before purge.
- Audit-log entries for access, download, and deletion of documents.

### G6 — Locale, date & currency handling (Phase 3)
PH-first means **English + Filipino** text and **mixed date formats** (`September 17, 2026`,
`17/09/2026`, `2026-09-17`). **Decision:**
- OCR/AI configured for English + Filipino.
- All extracted dates normalized to **ISO-8601 (`yyyy-MM-dd`)** before persistence; the raw string is
  retained on the `ExtractedField` for verification.
- Currency **detected** (default `PHP`); amounts stored as `BigDecimal` + ISO currency code.

### G7 — Verified-source rules for government facts (Phase 3/4)
§10 & §25 require government/legal facts to come from "verified external sources with a source date,"
but no source is defined. **Decision for MVP:** a curated internal **`VerifiedSourceRule`** table
(e.g. "PH adult passport validity = 10 years", "LTO registration renewal cadence") with
`sourceName`, `sourceUrl`, `sourceDate`. Derived suggestions cite the rule + date; anything not
backed by a rule is shown as an AI suggestion, never as a guaranteed fact. External government APIs
remain out of MVP (§33).

### G8 — File-safety pipeline (Phase 2)
Uploads are user-controlled binaries. **Add:**
- **Magic-byte MIME validation** (not just file extension) against the allow-list (JPG/JPEG/PNG/PDF).
- **Strip EXIF/GPS metadata** from images before storage (a passport photo carrying home GPS is a
  privacy leak).
- **PDF page-count limit** and rejection of encrypted/malformed PDFs.
- Re-encode/normalize images; generate thumbnails (G15).
- Never trust the client-provided filename for storage keys; generate server-side keys.

### G9 — Search scope clarified (Phase 5)
Per **D3**: MVP search = PostgreSQL full-text (`tsvector`) index over document title, type, person,
organization, verified extracted text, and dates. NL/semantic search is post-MVP.

### G10 — Signed URL specifics (Phase 2)
The spec says "signed/private short-lived URLs." **Decision:** downloads use **short-lived
pre-signed object-storage URLs** (e.g. 5-minute TTL) issued only after the API verifies the
authenticated caller owns the document. No public bucket access; the API never streams large files
itself where a pre-signed URL suffices.

### G11 — Idempotency mechanics (Phase 3)
The spec **requires** idempotent workers but not **how**. **Decision:** a **`ProcessedEvent`** table
keyed by `(eventId)` (and/or a natural key like `documentId + eventType`). A worker checks/inserts
before performing side effects; a duplicate delivery short-circuits. Reminder/notification creation
also uses a natural uniqueness constraint (G2).

### G12 — Account / ownership model (Phase 1 — foundational)
§18 says "user owns document," but Family (§15) and Business (§3) mean documents belong to an
**account/workspace** with member **roles**. **Decision:** introduce an **`Account`** as the
ownership root from day 1. Every `User` belongs to an `Account`; `Document`, `Person`, `Reminder`,
etc. reference `accountId` (not `userId`) for ownership. In MVP an account has exactly one user
(role `OWNER`); families/business add members later **without a data migration**. All ownership
checks are "does the caller's account own this resource."

### G13 — Central config / limits (Phase 1)
Centralize (not hard-code) in config + a small `plan` table: max file size, allowed MIME types,
reminder-offset options (7/30/60/90/custom), max PDF pages, per-plan document/people limits, signed-
URL TTL, processing rate limits.

### G14 — Observability (cross-cutting, from Phase 1)
For an async pipeline this is not optional. **Add:** health/readiness endpoints (Spring Actuator),
metrics, structured JSON logging, and a **correlation id** propagated across the whole flow (HTTP
request → queue message headers → worker → reminder) so a document's journey is traceable. Audit
logging (§18) is a distinct, security-focused log.

### G15 — Thumbnails (Phase 2/3)
Storage architecture lists thumbnails but not who makes them. **Decision:** the **document worker**
generates a thumbnail during processing and stores it alongside original/processed artifacts; the
dashboard/list uses the thumbnail via a signed URL.

### G16 — Timezone / DST correctness (Phase 4)
"Respect user timezone" needs a concrete rule. **Decision:** store the reminder's intended **local**
date/time + the user's **IANA timezone** (e.g. `Asia/Manila`), and precompute the **UTC instant** for
scheduling. Notifications render in the user's timezone. (PH has no DST today, but modeling by IANA
zone keeps international expansion correct.)

### G17 — Accessibility & internationalization (cross-cutting)
PH-first with English/Filipino implies planning for **i18n** message catalogs even if only English
ships first, and following **WCAG 2.1 AA** basics for the web/PWA (labels, contrast, keyboard nav,
focus management). Cheap to plan now, expensive to retrofit.

### G18 — Refresh-token strategy (Phase 1 — auth-foundational)
The API defines `/auth/logout` (invalidate) and `/auth/refresh` (rotate), but real invalidation
needs server-side token state. **Decision:** refresh tokens are **stateful** — a **`RefreshToken`**
table stores only a **hash** of each token with `expiresAt`/`revokedAt`. Logout revokes the row;
refresh **rotates** (revoke old + issue new); "revoke all sessions" revokes all rows for the user.
Access tokens stay short-lived stateless JWTs. Raw refresh tokens are never stored.

### G19 — `Document.title` (Phase 2)
The dashboard "label", search, and lists all reference a document **title**, but the spec's
`Document` has no such field. **Decision:** add **`Document.title`** — defaulted server-side
(humanized `documentType`, else `fileName`) and user-editable via `PATCH /documents/{id}`. This
removes the earlier ambiguity where "title" was referenced but not modeled.

---

## Part 2 — Added / corrected data entities (summary)

Full field lists and the ERD live in `DATA_MODEL.md`. Additions beyond the spec's §19:

| Entity | Why added |
|--------|-----------|
| **Account** | Ownership root (G12); enables family/business without migration. |
| **Notification** | In-app notifications, separated from reminders (G1). |
| **Subscription** (+ plan config) | Plan/limit enforcement (G3, D1). |
| **ProcessedEvent** | Idempotency ledger for the async workers (G11). |
| **VerifiedSourceRule** | Curated government/legal facts with source + date (G7). |
| **AuditLog** | Access/download/deletion audit trail (§18, G5). |
| **RefreshToken** | Stateful refresh-token store for real logout/rotation (G18). |
| **DocumentArtifact** *(optional)* | Track original/processed/thumbnail storage keys per document if we prefer rows over convention. |

Corrections to spec entities:
- `Document`, `Person`, `Reminder`, etc. gain **`accountId`** for ownership (G12); `userId` becomes
  "created by" rather than the ownership key.
- `Document` gains **`title`** (G19), `failureReason` (G4), and `sourceLocaleHint`/raw-value
  retention support (G6).
- `Reminder` gains `channel`, `scheduledForUtc`, `sentAt`, and a uniqueness key (G2).
- **`Account` does NOT carry a `plan` field.** Plan/limits live solely on `Subscription` (one per
  account) to avoid denormalization drift; quota checks join `Subscription` by `accountId`.

---

## Part 3 — Scope reductions pulled from MVP (for clarity)

Deferred out of MVP by the decisions above: payment/billing integration (D1), OAuth social login
(D2), natural-language/semantic search (D3), external government API integrations (§33), and the
larger "personal administrative assistant" agent behavior (§35). These are explicitly **planned**,
just not in the first release.

---

## Part 4 — Open questions for the product owner

1. **Processing provider choice** — OCR + AI provider (e.g. cloud Vision/Document-AI + an LLM with
   JSON mode) has cost and data-residency implications for PH PII. Which provider, and is sending
   document images to a third-party model acceptable under your privacy stance? (Interface stays
   swappable regardless.)
2. **Free-tier processing** — do free users get AI extraction, or only manual entry + reminders?
   (Affects cost and the D1 limit design.)
3. **Retention period** (G5) — how long are archived/soft-deleted documents kept before hard purge?
4. **Business/workspace roles** — which roles beyond `OWNER` are needed when Business ships
   (`ADMIN`, `MEMBER`, `VIEWER`?), so `Account` role modeling is future-proofed now.
