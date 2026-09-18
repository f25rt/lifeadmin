-- V9: Full-text search over documents (Phase 5, G9/D3). Search spans a document's own metadata
-- (title, file name, document type) plus its extracted field values and important-date types, so a
-- user can find a document by organisation/person/keyword the AI pulled out of it.
--
-- Rather than denormalise a tsvector column that would need trigger upkeep across three tables, we
-- index the text each row contributes and let the search query OR the matches together (see
-- DocumentRepository.search). GIN indexes on the expression keep it fast; pg_trgm backs the ILIKE
-- prefix fallback for short/partial queries that full-text stemming would miss.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Full-text over the document's own textual metadata.
CREATE INDEX idx_document_fts ON document
    USING GIN (to_tsvector('simple',
        coalesce(title, '') || ' ' || coalesce(file_name, '') || ' ' || coalesce(document_type, '')));

-- Trigram index for fast ILIKE '%q%' / prefix matches on the title.
CREATE INDEX idx_document_title_trgm ON document USING GIN (title gin_trgm_ops);

-- Full-text + trigram over extracted field values (e.g. an insurer or policy number).
CREATE INDEX idx_field_value_fts ON extracted_field
    USING GIN (to_tsvector('simple', coalesce(field_value, '')));
CREATE INDEX idx_field_value_trgm ON extracted_field USING GIN (field_value gin_trgm_ops);
