# Life Admin

> “Never forget an important document, deadline, warranty, or renewal again.”

Upload a document → the system understands it (OCR + AI) → you verify the extracted details →
it creates reminders and notifies you before dates matter. See `docs/` for the full plan.

**Status:** Planning / pre-implementation. No application code yet — the section-37 deliverables
(architecture, ERD, API spec, project structure, roadmap) are being produced for approval first.

## Documentation

| Doc | Purpose |
|-----|---------|
| [`docs/spec.md`](docs/spec.md) | Original product & technical spec (v1.0), preserved for reference. |
| [`docs/GAPS_AND_DECISIONS.md`](docs/GAPS_AND_DECISIONS.md) | Engineering clarifications, added entities, and **locked scope decisions**. Authoritative for the MVP build where it differs from the spec. |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System architecture + data-flow diagrams. |
| [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) | ERD, entities, enums, relationships. |
| [`docs/API_SPEC.md`](docs/API_SPEC.md) | REST API specification. |
| [`docs/PROJECT_STRUCTURE.md`](docs/PROJECT_STRUCTURE.md) | Repository layout. |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Milestone-based development roadmap. |

## Tech stack (planned)

- **Backend:** Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Flyway, RabbitMQ, Maven
- **Frontend:** React + TypeScript + Vite + Tailwind CSS (PWA)
- **Data:** PostgreSQL; object storage (S3-compatible; MinIO locally)
- **Async:** RabbitMQ (with dead-letter queue)
- **Pluggable providers:** OCR, AI extraction, email — all behind interfaces
- **Infra:** Docker + Docker Compose (Postgres, RabbitMQ, MinIO, Mailpit)
