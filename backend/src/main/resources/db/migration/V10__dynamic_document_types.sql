-- V10: Dynamic document types. Admins can now create their own document types at runtime, so the
-- fixed CHECK constraint on document.document_type (which only permitted the original enum names)
-- is dropped. The document_type_config table (V7) is the source of truth for valid type codes; a
-- document's document_type is a free code referencing a config row. No data migration is needed —
-- existing values remain valid codes.

ALTER TABLE document DROP CONSTRAINT ck_document_type;
