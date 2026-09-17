# Life Admin — Data Model (ERD)

> Deliverable **B** of spec §37. Extends spec §19 with the entities and corrections in
> `GAPS_AND_DECISIONS.md`. Ownership is at the **Account** level (G12). Files live in object
> storage; only **keys/metadata** live in PostgreSQL (spec §17).

## 1. Entity-relationship diagram

```mermaid
erDiagram
    ACCOUNT ||--o{ USER : "has members"
    ACCOUNT ||--o{ PERSON : owns
    ACCOUNT ||--o{ DOCUMENT : owns
    ACCOUNT ||--o{ REMINDER : owns
    ACCOUNT ||--|| SUBSCRIPTION : has
    ACCOUNT ||--o{ AUDIT_LOG : records

    USER ||--o{ DOCUMENT : "uploaded (createdBy)"
    USER ||--o{ REFRESH_TOKEN : has
    PERSON ||--o{ DOCUMENT : "subject of"

    DOCUMENT ||--o{ EXTRACTED_FIELD : has
    DOCUMENT ||--o{ IMPORTANT_DATE : has
    DOCUMENT ||--o{ REMINDER : "drives"
    DOCUMENT ||--o{ DOCUMENT_ARTIFACT : "stored as"

    REMINDER ||--o{ NOTIFICATION : "produces"
    USER ||--o{ NOTIFICATION : "recipient"

    IMPORTANT_DATE }o--o| VERIFIED_SOURCE_RULE : "derived from (optional)"

    ACCOUNT {
        uuid id PK
        string name
        timestamptz createdAt
        timestamptz updatedAt
    }
    USER {
        uuid id PK
        uuid accountId FK
        string email UK
        string passwordHash
        string name
        string role  "OWNER|ADMIN|MEMBER|VIEWER"
        string timezone  "IANA, default Asia/Manila"
        string country   "default PH"
        timestamptz createdAt
        timestamptz updatedAt
    }
    REFRESH_TOKEN {
        uuid id PK
        uuid userId FK
        string tokenHash UK  "hash of the refresh token, never the raw token"
        timestamptz expiresAt
        timestamptz revokedAt "nullable; set on logout/rotation"
        timestamptz createdAt
    }
    PERSON {
        uuid id PK
        uuid accountId FK
        string name
        string relationship  "SELF|SPOUSE|CHILD|PARENT|OTHER"
        timestamptz createdAt
        timestamptz updatedAt
    }
    DOCUMENT {
        uuid id PK
        uuid accountId FK
        uuid createdByUserId FK
        uuid personId FK "nullable"
        string title  "display label; defaulted from documentType/fileName, user-editable"
        string fileName
        string mimeType
        long fileSize
        string documentType  "enum, nullable until classified"
        numeric classificationConfidence
        string status  "UPLOADED|PROCESSING|REVIEW_REQUIRED|ACTIVE|ARCHIVED|FAILED"
        string failureReason "nullable"
        string sourceLocaleHint "nullable"
        timestamptz createdAt
        timestamptz updatedAt
    }
    DOCUMENT_ARTIFACT {
        uuid id PK
        uuid documentId FK
        string kind  "ORIGINAL|PROCESSED|THUMBNAIL"
        string storageKey
        string mimeType
        long fileSize
        timestamptz createdAt
    }
    EXTRACTED_FIELD {
        uuid id PK
        uuid documentId FK
        string fieldName
        string fieldValue
        string rawValue "nullable, pre-normalization"
        numeric confidence
        string source  "OCR|AI|DERIVED|USER"
        boolean verified
        timestamptz createdAt
        timestamptz updatedAt
    }
    IMPORTANT_DATE {
        uuid id PK
        uuid documentId FK
        string dateType  "EXPIRATION|RENEWAL|PAYMENT_DEADLINE|CONTRACT_START|CONTRACT_END|WARRANTY_EXPIRATION|INSPECTION|APPOINTMENT|PURCHASE|OTHER"
        date dateValue
        string source  "OCR|AI|DERIVED|USER"
        numeric confidence
        uuid sourceRuleId FK "nullable -> VERIFIED_SOURCE_RULE"
        timestamptz createdAt
    }
    REMINDER {
        uuid id PK
        uuid accountId FK
        uuid documentId FK
        uuid importantDateId FK "nullable"
        string title
        date reminderLocalDate
        string userTimezone "IANA snapshot"
        timestamptz scheduledForUtc
        string channel  "IN_APP|EMAIL"
        string status   "SCHEDULED|SENT|CANCELLED|FAILED"
        timestamptz sentAt "nullable"
        timestamptz createdAt
    }
    NOTIFICATION {
        uuid id PK
        uuid userId FK
        uuid reminderId FK "nullable"
        string type  "REMINDER|SYSTEM|NEEDS_ATTENTION"
        string title
        string body
        boolean readFlag
        string referenceType "DOCUMENT|REMINDER, nullable"
        uuid referenceId "nullable"
        timestamptz scheduledFor "for uniqueness"
        timestamptz createdAt
    }
    SUBSCRIPTION {
        uuid id PK
        uuid accountId FK UK
        string plan  "FREE|PERSONAL_PRO|FAMILY|BUSINESS"
        int documentLimit
        int peopleLimit
        boolean aiExtractionEnabled
        timestamptz currentPeriodEnd "nullable (no billing in MVP)"
        timestamptz createdAt
        timestamptz updatedAt
    }
    PROCESSED_EVENT {
        uuid id PK
        string eventId UK
        string eventType
        uuid documentId "nullable"
        timestamptz processedAt
    }
    VERIFIED_SOURCE_RULE {
        uuid id PK
        string ruleKey UK  "e.g. PH_PASSPORT_ADULT_VALIDITY"
        string country
        string documentType
        string description
        string sourceName
        string sourceUrl
        date sourceDate
        boolean active
    }
    AUDIT_LOG {
        uuid id PK
        uuid accountId "nullable"
        uuid actorUserId "nullable"
        string action  "DOCUMENT_VIEW|DOCUMENT_DOWNLOAD|DOCUMENT_DELETE|LOGIN|..."
        string entityType
        string entityId
        string detail
        string correlationId
        timestamptz occurredAt
    }
```

## 2. Enumerations

| Enum | Values |
|------|--------|
| `DocumentType` | PASSPORT, DRIVERS_LICENSE, VEHICLE_REGISTRATION, INSURANCE, WARRANTY, RECEIPT, CONTRACT, PROPERTY_DOCUMENT, GOVERNMENT_DOCUMENT, LICENSE, CERTIFICATE, BILL, SUBSCRIPTION, OTHER |
| `DocumentStatus` | UPLOADED, PROCESSING, REVIEW_REQUIRED, ACTIVE, ARCHIVED, FAILED |
| `FieldSource` / date `source` | OCR, AI, DERIVED, USER |
| `DateType` | EXPIRATION, RENEWAL, PAYMENT_DEADLINE, CONTRACT_START, CONTRACT_END, WARRANTY_EXPIRATION, INSPECTION, APPOINTMENT, PURCHASE, OTHER |
| `ReminderChannel` | IN_APP, EMAIL |
| `ReminderStatus` | SCHEDULED, SENT, CANCELLED, FAILED |
| `NotificationType` | REMINDER, SYSTEM, NEEDS_ATTENTION |
| `Plan` | FREE, PERSONAL_PRO, FAMILY, BUSINESS |
| `UserRole` | OWNER, ADMIN, MEMBER, VIEWER (MVP uses OWNER only) |
| `PersonRelationship` | SELF, SPOUSE, CHILD, PARENT, OTHER |
| `ArtifactKind` | ORIGINAL, PROCESSED, THUMBNAIL |

## 3. Key design notes

- **Ownership root = Account (G12).** All ownership checks resolve to "does the caller's account own
  this row." `Document.createdByUserId` records the uploader; `accountId` is the ownership key.
- **Files vs. metadata (spec §17).** Binaries never live in Postgres. `DOCUMENT_ARTIFACT` rows hold
  storage keys for the original/processed/thumbnail; deletion cascades to storage purge (G5).
- **Uncertainty modeled explicitly (spec §8/§25).** `EXTRACTED_FIELD.source` + `confidence` +
  `verified`, and `IMPORTANT_DATE.source` distinguish printed vs. derived vs. user-entered. `rawValue`
  preserves the pre-normalized string for verification (G6).
- **Derived-with-source (G7).** An `IMPORTANT_DATE` derived from a rule references
  `VERIFIED_SOURCE_RULE`, so the UI can cite "calculated from <rule> (source dated <date>)".
- **Reminder timing (G16).** Store `reminderLocalDate` + `userTimezone` + precomputed
  `scheduledForUtc`. The scheduler queries by `scheduledForUtc <= now()`.
- **Idempotency (G11).** `PROCESSED_EVENT.eventId` is unique; workers insert-then-act.
- **Notification uniqueness (G2).** Unique on `(reminderId, type, scheduledFor)` prevents duplicate
  sends across scheduler runs/instances.
- **Search (G9/D3).** A generated `tsvector` (document `title`/type + verified extracted text +
  person + organization) with a GIN index backs full-text search; no separate search engine in MVP.
- **Plan is single-sourced.** There is exactly one `SUBSCRIPTION` per account and it is the **sole**
  source of truth for plan + limits (no denormalized copy on `Account`, to avoid drift). Quota checks
  join `SUBSCRIPTION` by `accountId`.
- **Document title.** `DOCUMENT.title` is the human display label used by the dashboard "label",
  search, and lists. It is defaulted server-side (humanized `documentType`, else `fileName`) and is
  user-editable via `PATCH /documents/{id}`. Modeling it explicitly removes the earlier ambiguity
  where "title" was referenced but not stored.
- **Refresh tokens (auth).** Refresh tokens are **stateful**: only a **hash** is stored in
  `REFRESH_TOKEN`. Logout revokes the row (`revokedAt`); refresh **rotates** (revoke old, insert new).
  This makes logout and "revoke all sessions" real rather than relying on token expiry alone. Access
  tokens remain short-lived stateless JWTs.
- **`Reminder.title` is a snapshot.** It captures the message (e.g. "Insurance expires in 60 days")
  at creation time; the embedded day-count is not recomputed later. Acceptable because a reminder
  fires once, but it is intentionally not a live value.
- **`ProcessedEvent` retention.** The idempotency ledger is pruned periodically (rows older than a
  configured window, e.g. 30 days) to bound growth.

## 4. Indicative indexes / constraints

- `USER.email` unique; `SUBSCRIPTION.accountId` unique; `PROCESSED_EVENT.eventId` unique;
  `REFRESH_TOKEN.tokenHash` unique.
- FKs: `USER.accountId`, `REFRESH_TOKEN.userId`, `DOCUMENT.accountId/createdByUserId/personId`,
  `EXTRACTED_FIELD.documentId`, `IMPORTANT_DATE.documentId(+sourceRuleId)`,
  `REMINDER.accountId/documentId`, `NOTIFICATION.userId(+reminderId)`, `DOCUMENT_ARTIFACT.documentId`.
- Query indexes: `DOCUMENT(accountId, status)`, `REMINDER(scheduledForUtc) WHERE sentAt IS NULL`,
  `NOTIFICATION(userId) WHERE readFlag = false`, `REFRESH_TOKEN(userId) WHERE revokedAt IS NULL`,
  GIN index on the search `tsvector`.
- Uniqueness: `NOTIFICATION(reminderId, type, scheduledFor)` — prevents duplicate sends across
  scheduler runs/instances.

## 5. Migrations

Schema owned by **Flyway** (`V1__account_users_refresh_tokens.sql`, `V2__documents_and_artifacts.sql`,
`V3__extraction_and_dates.sql`, `V4__reminders_and_notifications.sql`, `V5__subscription_audit_rules.sql`,
`V6__search_indexes.sql`). Hibernate runs in `validate` mode against the migrated schema.
