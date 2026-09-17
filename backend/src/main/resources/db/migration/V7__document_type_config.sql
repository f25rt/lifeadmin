-- V7: Admin-managed document types + AI extraction templates (one row per DocumentType).
-- Combines "manage the type" (enabled flag + display label) with the "AI template" (keywords that
-- identify the type, which date types matter, and default reminder offsets). The classifier and
-- date extractor read this table; an empty/again-seeded table reproduces the hardcoded behavior.
CREATE TABLE document_type_config (
    type_code       VARCHAR(32) PRIMARY KEY,           -- matches DocumentType enum name
    label           VARCHAR(64)  NOT NULL,             -- admin-editable display label
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE, -- disabled types are hidden from upload/classify
    keywords        TEXT         NOT NULL DEFAULT '',   -- CSV; any match classifies as this type
    relevant_date_types TEXT     NOT NULL DEFAULT '',   -- CSV of DateType names relevant to this type
    default_offsets_days TEXT    NOT NULL DEFAULT '30', -- CSV of default reminder "days before" offsets
    sort_order      INT          NOT NULL DEFAULT 100,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(255)
);

-- Seed from the current enum + StubAiExtractionProvider/DateTextParser knowledge. Keywords mirror
-- the classifier; relevant date types and offsets are reasonable defaults per type.
INSERT INTO document_type_config (type_code, label, keywords, relevant_date_types, default_offsets_days, sort_order) VALUES
 ('PASSPORT',            'Passport',            'passport',                              'EXPIRATION',                 '30,90', 10),
 ('DRIVERS_LICENSE',     'Driver''s License',   'driver,license,licence',                'EXPIRATION',                 '30,60', 20),
 ('VEHICLE_REGISTRATION','Vehicle Registration','registration,vehicle,ltop,plate',       'EXPIRATION',                 '7,30',  30),
 ('INSURANCE',           'Insurance',           'insurance,policy,insur',                'EXPIRATION,RENEWAL',         '30,60', 40),
 ('WARRANTY',            'Warranty',            'warranty',                              'WARRANTY_EXPIRATION,PURCHASE','30',   50),
 ('RECEIPT',             'Receipt',             'receipt,official receipt,invoice',      'PURCHASE',                   '30',    60),
 ('CONTRACT',            'Contract',            'contract,agreement,lease',              'CONTRACT_START,CONTRACT_END','30,60', 70),
 ('PROPERTY_DOCUMENT',   'Property Document',   'title,deed,property',                   'EXPIRATION',                 '60',    80),
 ('GOVERNMENT_DOCUMENT', 'Government Document',  'government,permit,clearance',           'EXPIRATION',                 '30',    90),
 ('LICENSE',             'License',             'license,licence,permit',                'EXPIRATION',                 '30,60', 100),
 ('CERTIFICATE',         'Certificate',         'certificate',                           'EXPIRATION',                 '30',    110),
 ('BILL',                'Bill',                'bill,statement,amount due',             'PAYMENT_DEADLINE',           '7,3',   120),
 ('SUBSCRIPTION',        'Subscription',        'subscription',                          'RENEWAL,PAYMENT_DEADLINE',   '7',     130),
 ('OTHER',               'Other',               '',                                      '',                           '30',    900);
