# Life Admin — REST API Specification

> Deliverable **C** of spec §37. Base path: **`/api/v1`**. Auth: **JWT** (access + refresh) in the
> `Authorization: Bearer <token>` header. Every non-auth endpoint requires authentication and
> **verifies account ownership** of the target resource (spec §18, `GAPS_AND_DECISIONS.md` G12).
> OpenAPI is generated at runtime (`/v3/api-docs`, Swagger UI at `/swagger-ui.html`).

## Conventions

- **Content type:** `application/json` except document upload (`multipart/form-data`).
- **IDs:** UUID strings. **Timestamps:** ISO-8601 UTC. **Dates:** `yyyy-MM-dd`.
- **Pagination:** list endpoints accept `?page=0&size=20&sort=field,dir`; responses wrap items in
  `{ "items": [...], "page": 0, "size": 20, "totalElements": n, "totalPages": m }`.
- **Auth outcomes:** `401` (missing/invalid token), `403` (authenticated but not owner / forbidden).
- **Standard error envelope:**
  ```json
  {
    "timestamp": "2026-09-17T10:00:00Z",
    "status": 409,
    "code": "QUOTA_EXCEEDED",
    "message": "Free plan allows 5 documents.",
    "path": "/api/v1/documents",
    "correlationId": "uuid"
  }
  ```
- **Common error codes:** `VALIDATION_ERROR` (400), `UNAUTHENTICATED` (401), `FORBIDDEN` (403),
  `NOT_FOUND` (404), `QUOTA_EXCEEDED` (402/403), `UNSUPPORTED_MEDIA_TYPE` (415),
  `PAYLOAD_TOO_LARGE` (413), `RATE_LIMITED` (429), `CONFLICT` (409), `INTERNAL_ERROR` (500).

---

## 1. Authentication — `/auth`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/auth/register` | none | Create account + owner user. |
| POST | `/auth/login` | none | Obtain access + refresh tokens. |
| POST | `/auth/refresh` | refresh token | Rotate access + refresh token (old refresh is revoked). |
| POST | `/auth/logout` | access token | Revoke the caller's refresh token (stateful; see G18). |
| GET | `/auth/me` | access token | Current user + account + plan. |

**POST `/auth/register`**
```json
// request
{ "name": "Juan Dela Cruz", "email": "juan@example.com", "password": "••••••••",
  "timezone": "Asia/Manila", "country": "PH" }
// 201
{ "userId": "uuid", "accountId": "uuid", "email": "juan@example.com" }
```
Errors: `400 VALIDATION_ERROR`, `409 CONFLICT` (email exists).

**POST `/auth/login`**
```json
// request
{ "email": "juan@example.com", "password": "••••••••" }
// 200
{ "accessToken": "jwt", "refreshToken": "jwt", "tokenType": "Bearer",
  "expiresInSeconds": 3600, "userId": "uuid", "accountId": "uuid", "name": "Juan Dela Cruz" }
```
Errors: `401 UNAUTHENTICATED`.

**GET `/auth/me`** → `200 { userId, accountId, name, email, timezone, country, role, plan }`.

> OAuth (`/auth/oauth/google`, `/auth/oauth/apple`) is **Phase 2** (D2) — not in MVP.

---

## 2. Documents — `/documents`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/documents` | ✔ | Upload a document (multipart). Async processing. |
| GET | `/documents` | ✔ | List account documents (filter/paginate). |
| GET | `/documents/{id}` | ✔ owner | Document detail incl. fields, dates, artifacts. |
| PATCH | `/documents/{id}` | ✔ owner | Update metadata (title, personId, documentType, status→ARCHIVED). |
| DELETE | `/documents/{id}` | ✔ owner | Hard-delete document + storage artifacts (G5). |
| POST | `/documents/{id}/process` | ✔ owner | Re-enqueue processing (e.g. retry after FAILED). |
| POST | `/documents/{id}/verify` | ✔ owner | Submit verified/edited fields → status ACTIVE. |
| GET | `/documents/{id}/download` | ✔ owner | Returns a short-lived signed URL (G10). |

**POST `/documents`** — `multipart/form-data`: `file` (JPG/JPEG/PNG/PDF ≤ 10 MB), optional
`personId`. Validates magic-byte MIME, size, and **quota** (G3). 
```json
// 202 Accepted
{ "id": "uuid", "status": "UPLOADED", "fileName": "passport.jpg" }
```
Errors: `413 PAYLOAD_TOO_LARGE`, `415 UNSUPPORTED_MEDIA_TYPE`, `402/403 QUOTA_EXCEEDED`,
`429 RATE_LIMITED`.

**GET `/documents`** — query: `status`, `documentType`, `personId`, `q` (full-text, G9), `page`,
`size`, `sort`. Returns paginated summaries (id, title, fileName, documentType, status, thumbnailUrl,
nextImportantDate, createdAt).

**GET `/documents/{id}`** →
```json
{
  "id": "uuid", "title": "ABC Insurance Policy", "fileName": "insurance.pdf",
  "documentType": "INSURANCE",
  "classificationConfidence": 0.94, "status": "REVIEW_REQUIRED", "personId": null,
  "failureReason": null,
  "fields": [
    { "id": "uuid", "fieldName": "organization", "fieldValue": "ABC Insurance",
      "rawValue": "ABC Insurance Co.", "confidence": 0.93, "source": "AI", "verified": false }
  ],
  "dates": [
    { "id": "uuid", "dateType": "EXPIRATION", "dateValue": "2027-01-15",
      "source": "OCR", "confidence": 0.97, "sourceRuleId": null }
  ],
  "artifacts": [ { "kind": "THUMBNAIL", "downloadUrl": "https://…signed…" } ],
  "createdAt": "2026-09-17T10:00:00Z"
}
```

**POST `/documents/{id}/verify`**
```json
// request — user-corrected fields; each becomes source=USER, verified=true
{ "documentType": "INSURANCE",
  "fields": [ { "fieldName": "holderName", "fieldValue": "Juan Dela Cruz" } ],
  "dates": [ { "dateType": "EXPIRATION", "dateValue": "2027-01-15" } ] }
// 200 -> document status ACTIVE
```

---

## 3. Important dates — `/documents/{id}/dates` and `/dates`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/documents/{id}/dates` | ✔ owner | List a document's important dates. |
| POST | `/documents/{id}/dates` | ✔ owner | Add a date (source=USER). |
| PATCH | `/dates/{id}` | ✔ owner | Edit a date. |
| DELETE | `/dates/{id}` | ✔ owner | Remove a date. |

```json
// POST body
{ "dateType": "RENEWAL", "dateValue": "2027-03-01" }
// 201
{ "id": "uuid", "dateType": "RENEWAL", "dateValue": "2027-03-01", "source": "USER", "confidence": 1.0 }
```

---

## 4. Reminders — `/reminders`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/reminders` | ✔ | List account reminders (filter: `status`, `documentId`, `upcoming=true`). |
| POST | `/reminders` | ✔ owner (of doc) | Create one or more reminders for a date. |
| PATCH | `/reminders/{id}` | ✔ owner | Reschedule / change channel / cancel. |
| DELETE | `/reminders/{id}` | ✔ owner | Delete a reminder. |

**POST `/reminders`** — accepts offsets relative to an important date, or an explicit date.
```json
// request
{ "documentId": "uuid", "importantDateId": "uuid",
  "offsetsDaysBefore": [60, 30, 7], "channel": "EMAIL" }
// 201 -> creates 3 reminders with computed scheduledForUtc (user timezone)
{ "created": [ { "id": "uuid", "title": "Insurance expires in 60 days",
  "reminderLocalDate": "2026-11-16", "scheduledForUtc": "2026-11-16T01:00:00Z",
  "channel": "EMAIL", "status": "SCHEDULED" } ] }
```

---

## 5. People — `/people`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/people` | ✔ | List people in the account. |
| POST | `/people` | ✔ | Add a person. |
| PATCH | `/people/{id}` | ✔ owner | Edit a person. |
| DELETE | `/people/{id}` | ✔ owner | Remove a person (documents keep `personId=null`). |

```json
// POST body
{ "name": "Maria Dela Cruz", "relationship": "SPOUSE" }
```
> Family management UI is Phase 2 (D5), but the endpoints exist for MVP data modeling.

---

## 6. Notifications — `/notifications`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/notifications` | ✔ | List the user's in-app notifications (`unread=true` filter). |
| POST | `/notifications/{id}/read` | ✔ owner | Mark one read. |
| POST | `/notifications/read-all` | ✔ | Mark all read. |

```json
// GET item
{ "id": "uuid", "type": "REMINDER", "title": "Vehicle registration expires in 30 days",
  "body": "…", "readFlag": false, "referenceType": "DOCUMENT", "referenceId": "uuid",
  "createdAt": "2026-09-17T01:00:00Z" }
```

---

## 7. Plan & usage — `/subscription`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/subscription` | ✔ | Current plan, limits, and usage. |

```json
// 200
{ "plan": "FREE", "documentLimit": 5, "documentsUsed": 3,
  "peopleLimit": 1, "peopleUsed": 1, "aiExtractionEnabled": true }
```
> Limit values (e.g. `peopleLimit` for FREE) come from the plan config table (G13), not hard-coded;
> the numbers above are indicative defaults pending product confirmation. Plan is read from the
> single `Subscription` row per account (no `Account.plan`). No `POST`/upgrade endpoint in MVP —
> plans are assigned manually (D1). Upgrade/billing is Phase 2+.

---

## 8. Dashboard — `/dashboard`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/dashboard` | ✔ | Aggregated home view: greeting data, upcoming dates, doc counts, needs-attention. |

```json
// 200
{
  "upcoming": [ { "documentId": "uuid", "documentType": "VEHICLE_REGISTRATION",
                  "label": "Vehicle Registration", "dateValue": "2026-10-30", "daysUntil": 43 } ],
  "counts": { "total": 12, "expiringSoon": 3, "active": 8, "needsReview": 1 },
  "needsAttention": [ { "documentId": "uuid", "reason": "MISSING_EXPIRATION" } ]
}
```
`needsAttention` reasons (Feature 9): `MISSING_EXPIRATION`, `VERIFY_NAME`, `LOW_CONFIDENCE_TYPE`,
`NO_REMINDER_CONFIGURED`.

---

## 9. Health & docs (ops)

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/actuator/health` | none | Liveness/readiness (G14). |
| GET | `/v3/api-docs`, `/swagger-ui.html` | none (non-prod) | OpenAPI + Swagger UI. |

---

## 10. Cross-cutting behaviors

- **Ownership:** `GET/PATCH/DELETE /documents/{id}` (and children) return `404 NOT_FOUND` — not
  `403` — when the resource exists but belongs to another account, to avoid leaking existence.
- **Rate limiting (G4):** `429 RATE_LIMITED` with `Retry-After` on auth, upload, and `/process`.
- **Correlation id (G14):** accepted via `X-Correlation-Id` or generated; echoed in responses and
  propagated into async messages.
- **Audit (§18, G5):** document view/download/delete and auth events are audit-logged server-side.
