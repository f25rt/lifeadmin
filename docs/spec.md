# LIFE ADMIN — Product & Technical Specification (MVP)

> **Version:** 1.0 · **Status:** Product Proposal / MVP Specification
> **Target:** Web Application + Mobile-friendly PWA · **Primary Market:** Philippines (expandable internationally)
>
> This file is the original product spec, preserved verbatim for reference. Engineering
> clarifications, added entities, and locked scope decisions live in `GAPS_AND_DECISIONS.md`.
> Where the two disagree, `GAPS_AND_DECISIONS.md` is authoritative for the MVP build.

---

## 1. Product Overview
**Name (working):** Life Admin
**Tagline:** “Never forget an important document, deadline, warranty, or renewal again.”

**Problem:** Important documents and obligations are scattered across email, Drive, phone galleries,
Messenger, physical folders, PDFs, screenshots, paper receipts, Excel, and calendar reminders. Users
forget expirations and renewals (documents, insurance, vehicle registration, passport, license,
warranties, contracts, subscriptions, property deadlines, bills, government deadlines). Existing
reminder apps require manual entry. **Life Admin should minimize manual data entry.**

## 2. Core Product Concept
Upload document → OCR → AI document understanding → structured record (type, important dates,
expiration, amount, organization, person, recommended actions) → reminder engine → notification →
action. **Differentiator:** users should not need to manually understand their documents before
creating reminders — the app understands the document and proposes what to track.

## 3. Target Users
- **Primary:** adults 25–55 managing multiple personal documents/obligations (vehicle, property,
  insurance, government docs, warranties/contracts; heavy smartphone use).
- **Secondary — Families:** one account manages husband/wife/children/parents.
- **Secondary — Small Businesses:** permits, contracts, insurance, equipment warranties, licenses,
  employee documents, supplier contracts.

## 4. MVP Objective
Answer one question: “Can users upload an important document and get useful reminders without
manually entering all the information?” The MVP should **not** become a full document-management
platform.

## 5. Feature 1 — User Registration
Support email/password, Google login, Apple login. Profile: `id, name, email, timezone, country,
createdAt`. Default timezone `Asia/Manila`.

## 6. Feature 2 — Document Upload
Upload JPG/JPEG/PNG/PDF. Max initial file size 10 MB. UI must support **Take Photo** on mobile.

## 7. Feature 3 — Document Classification
Categories: PASSPORT, DRIVERS_LICENSE, VEHICLE_REGISTRATION, INSURANCE, WARRANTY, RECEIPT, CONTRACT,
PROPERTY_DOCUMENT, GOVERNMENT_DOCUMENT, LICENSE, CERTIFICATE, BILL, SUBSCRIPTION, OTHER.
Returns `{ "documentType": "INSURANCE", "confidence": 0.94 }`. If low confidence, ask the user and
allow correction.

## 8. Feature 4 — Information Extraction
Extract structured info, e.g. `documentType, organization, documentNumber, holderName, issueDate,
expirationDate, amount, currency`. Extracted info is **not** automatically correct — UI shows “We
found these details. Please verify.” Every field is editable.

## 9. Feature 5 — Important Date Detection
Detect expiration, renewal, payment deadline, contract start/end, warranty expiration, inspection,
appointment. Distinguish **explicit dates** (printed on the document) from **derived dates**
(calculated, e.g. purchase date + 2-year warranty). Derived dates must be clearly marked.

## 10. Feature 6 — AI Suggested Actions
After processing, suggest actions (e.g. renew registration, prepare documents, check insurance;
renew passport before expiration; keep receipt/warranty until expiration). The AI must **not**
present legal/government requirements as guaranteed facts unless from a verified source.

## 11. Feature 7 — Reminder Creation
Offer reminders (7/30/60/90 days before, or custom). Users can create multiple reminders per date.

## 12. Feature 8 — Dashboard
Prioritize actions over files: greeting, UPCOMING list (with “expires in N days”), and DOCUMENTS
summary (total, expiring soon, active, needs review).

## 13. Feature 9 — “Needs Attention”
Flag incomplete/suspicious records: missing expiration date, verify extracted name, low-confidence
classification, reminder not configured. Prevents silent incorrect automation.

## 14. Feature 10 — Search
Search across document title, type, person, organization, extracted text, and dates.

## 15. Feature 11 — Family / People
Documents can belong to a Person (Juan/Maria/Child …). May be Phase 2.

## 16. Feature 12 — Notifications
MVP channels: email + in-app. Future: push, SMS, Messenger, WhatsApp. Must respect user timezone.

## 17. Document Storage
Store originals securely in object storage (original, processed, thumbnail). Do **not** store large
binaries in PostgreSQL. Options: AWS S3, Cloudflare R2, GCS, Azure Blob. MVP: S3-compatible
abstraction.

## 18. Security Requirements
HTTPS; encryption at rest & in transit; access control; per-user document ownership; signed/private
short-lived document URLs; secure password hashing; OAuth security; rate limiting; audit logging.
Never expose `/document/{id}` without verifying the authenticated user owns it.

## 19. Data Model (initial)
`User(id, email, passwordHash, name, timezone, country, createdAt, updatedAt)`;
`Person(id, userId, name, relationship, createdAt, updatedAt)`;
`Document(id, userId, personId, fileName, storageKey, mimeType, fileSize, documentType,
classificationConfidence, status, createdAt, updatedAt)` — status: UPLOADED, PROCESSING,
REVIEW_REQUIRED, ACTIVE, ARCHIVED, FAILED;
`ExtractedField(id, documentId, fieldName, fieldValue, confidence, source, verified)` — source: OCR,
AI, DERIVED, USER;
`Reminder(id, userId, documentId, title, reminderDate, status, notificationType, createdAt)`;
`ImportantDate(id, documentId, dateType, dateValue, source, confidence)`.

## 20. API Design
REST, base `/api/v1`. Auth: register/login/logout/refresh/me. Documents: CRUD + `/process` +
`/verify`. Dates, Reminders, People: CRUD.

## 21. Processing Architecture
Async: upload does not wait for OCR + AI. Flow: Web/Mobile → API → Object Storage + Processing Queue
→ Document Worker → OCR + AI → Structured Data → PostgreSQL → Reminder Engine.

## 22. Message Broker
RabbitMQ. Events: DOCUMENT_UPLOADED, DOCUMENT_PROCESSING_STARTED, OCR_COMPLETED, DOCUMENT_CLASSIFIED,
DOCUMENT_DATA_EXTRACTED, DOCUMENT_VERIFIED, REMINDER_CREATED, REMINDER_TRIGGERED. Workers must be
idempotent (no duplicate reminders/records on re-processing).

## 23. Technology Stack
**Backend:** Java 21+, Spring Boot 3+, Spring Security, Spring Data JPA, PostgreSQL, RabbitMQ,
Flyway, Maven. **Frontend:** React + TypeScript + Vite + Tailwind CSS. **Infra:** Docker, Docker
Compose, PostgreSQL, RabbitMQ, Object Storage. Production later: AWS/GCP/Azure. No Kubernetes in MVP
unless a specific operational reason exists.

## 24. AI Architecture
AI for classification, extraction, date identification, suggested actions, natural-language search.
AI must not directly modify important data without validation. Pattern: Document → OCR/Vision → AI
Extraction → Structured JSON → Validation → User Verification → Persist. Use strict JSON schemas.

## 25. AI Safety / Accuracy
Distinguish detected vs calculated vs AI-interpreted vs user-provided vs verified-external info.
Mark derived info (e.g. “Warranty expiration calculated from purchase date + 2 years”). Don’t assert
certainty unless the document confirms it. For government/legal/tax info, use verified external
sources and include the source date.

## 26. Monetization
- **Free:** 5 documents, basic reminders, basic dashboard, email notifications.
- **Personal Pro (₱99–₱199/mo):** 50–100 documents, AI extraction, unlimited reminders, advanced
  search, multiple people, document history, priority processing.
- **Family (₱299–₱499/mo):** multiple members, shared documents, family dashboard, higher storage.
- **Business (₱999+/mo):** multiple users, business documents, shared workspace, roles, audit logs,
  higher limits, API access.
Validate pricing via user interviews / conversion experiments.

## 27. Viral / Discovery Strategy
Free SEO tools funnel to signup: passport/license/registration/warranty/insurance/contract
calculators and a document expiration tracker. Landing → free calculator → “Save this reminder” →
create account → upload → Life Admin.

## 28. MVP User Journey
Landing → Sign Up → Dashboard → “Add Document” → Take Photo/Upload → Processing → “Here’s what we
found” → verify → “Create Reminder?” → 60 days before → Dashboard. Target: under 2 minutes.

## 29. MVP Screens
Landing, Login/Register, Dashboard, Upload Document, Processing, Document Review, Document Details,
Reminder List, Settings. No large admin panel initially.

## 30. MVP Development Phases
1. **Foundation:** Spring Boot, PostgreSQL, auth, React, Docker, migrations, basic CI/CD → user can
   register/log in.
2. **Documents:** upload, object storage, metadata, list, details, delete/archive → secure storage.
3. **AI Processing:** OCR, classification, structured extraction, confidence, review screen →
   structured info from an upload.
4. **Reminders:** important dates, reminder creation, scheduler, email notifications, upcoming
   dashboard → useful reminders.
5. **Polish:** search, mobile UX, error handling, processing retry, security hardening, usage
   limits, analytics.

## 31. MVP Success Metrics
Funnel: visitors → signups → documents uploaded → processed → verified → reminders created →
returning users → paid conversions. Key: activation (first upload), processing success, reminder
conversion, retention (7/30/90 days), paid conversion.

## 32. Product Principles
1. Minimize manual data entry. 2. AI suggests; users verify. 3. Never hide uncertainty. 4. Security
is a product feature. 5. Dashboard shows what needs attention, not just what exists. 6. Build the
smallest useful version first.

## 33. What NOT to Build in MVP
Full document editing, complex collaboration, chat, marketplace, government integrations, SMS,
Messenger/WhatsApp, native iOS/Android, Kubernetes, complex AI agents, financial management, bill
payment, legal advice, automatic government filing.

## 34. Future Features
Smart calendar, Gmail integration, Google Drive scan, family sharing, smart renewal
recommendations, subscription tracking, warranty tracking, property management, vehicle management,
business version.

## 35. Potential Differentiator
Evolve from “document storage” to “personal administrative assistant”: understand WHO / WHAT / WHEN
/ WHAT NEEDS TO HAPPEN / WHAT HAPPENS NEXT. Move storage → understanding → action.

## 36. Development Instructions (summary)
Architecture & DB first; build MVP incrementally; modular backend; clean architecture without
unnecessary abstraction; REST first; PostgreSQL primary; RabbitMQ for async; files in object
storage; AI providers behind an interface; env vars for secrets, never hard-code keys; auth &
authorization before document features; every document endpoint verifies ownership; automated tests
for critical business logic; integration tests for document processing; Docker Compose for local
dev; Flyway migrations; OpenAPI docs; small independently-testable increments.

## 37. First Implementation Task (pre-code deliverables)
Produce, for approval, **before** writing application code: (A) System Architecture, (B) Database
ERD, (C) REST API Specification, (D) Project Structure, (E) Development Roadmap.

## 38. North Star
“I don’t have to remember my administrative life anymore. Life Admin remembers it for me.” The MVP
proves a user can upload one important document, have the system understand it, verify the extracted
info, create a reminder, and receive that reminder reliably.
