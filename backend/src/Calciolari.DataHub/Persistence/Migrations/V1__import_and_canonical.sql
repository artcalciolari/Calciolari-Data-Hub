-- Import pipeline + canonical domain (Fase 3A)
-- Scales are provisional pending broader fixture confirmation (see IMPLEMENTATION_PLAN §6.2).

CREATE TABLE raw_artifact (
    id              UUID PRIMARY KEY,
    sha256          VARCHAR(64) NOT NULL,
    byte_size       BIGINT NOT NULL CHECK (byte_size >= 0),
    storage_key     TEXT NOT NULL,
    detected_type   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_raw_artifact_sha256 UNIQUE (sha256),
    CONSTRAINT uq_raw_artifact_storage_key UNIQUE (storage_key)
);

CREATE TABLE import_job (
    id              UUID PRIMARY KEY,
    status          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT ck_import_job_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED'
    ))
);

CREATE TABLE parse_attempt (
    id                  UUID PRIMARY KEY,
    raw_artifact_id     UUID NOT NULL REFERENCES raw_artifact (id),
    parser_name         TEXT NOT NULL,
    parser_version      TEXT NOT NULL,
    status              TEXT NOT NULL,
    records_found       INTEGER,
    attempt_count       INTEGER NOT NULL,
    lease_until         TIMESTAMPTZ,
    lease_owner         TEXT,
    lease_generation    BIGINT NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    error_summary       TEXT,
    CONSTRAINT uq_parse_attempt_version UNIQUE (raw_artifact_id, parser_name, parser_version, attempt_count),
    CONSTRAINT uq_parse_attempt_artifact_id UNIQUE (raw_artifact_id, id),
    CONSTRAINT ck_parse_attempt_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'VALID', 'WARNING', 'INVALID', 'FAILED'
    )),
    CONSTRAINT ck_parse_attempt_count CHECK (attempt_count >= 1)
);

CREATE TABLE import_file (
    id                          UUID PRIMARY KEY,
    import_job_id               UUID NOT NULL REFERENCES import_job (id),
    raw_artifact_id             UUID NOT NULL REFERENCES raw_artifact (id),
    parse_attempt_id            UUID REFERENCES parse_attempt (id),
    original_filename           TEXT NOT NULL,
    source                      TEXT NOT NULL,
    filename_hints              JSONB,
    status                      TEXT NOT NULL,
    deduplicated                BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_of_import_file_id UUID REFERENCES import_file (id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at                TIMESTAMPTZ,
    CONSTRAINT ck_import_file_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'IMPORTED', 'WARNING', 'INVALID', 'FAILED'
    ))
);

CREATE TABLE artifact_publication (
    raw_artifact_id         UUID PRIMARY KEY REFERENCES raw_artifact (id),
    active_parse_attempt_id UUID NOT NULL,
    published_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_artifact_publication_attempt UNIQUE (active_parse_attempt_id),
    CONSTRAINT fk_artifact_publication_attempt
        FOREIGN KEY (raw_artifact_id, active_parse_attempt_id)
        REFERENCES parse_attempt (raw_artifact_id, id)
);

CREATE TABLE validation_result (
    id                  UUID PRIMARY KEY,
    parse_attempt_id    UUID NOT NULL REFERENCES parse_attempt (id),
    code                TEXT NOT NULL,
    status              TEXT NOT NULL,
    source_value        NUMERIC(19, 6),
    calculated_value    NUMERIC(19, 6),
    difference          NUMERIC(19, 6),
    tolerance           NUMERIC(19, 6),
    rule_version        TEXT NOT NULL,
    source_locator      TEXT,
    CONSTRAINT ck_validation_result_status CHECK (status IN ('VALID', 'WARNING', 'INVALID'))
);

CREATE INDEX idx_validation_result_attempt ON validation_result (parse_attempt_id);

CREATE TABLE parsed_movement (
    id                      UUID PRIMARY KEY,
    parse_attempt_id        UUID NOT NULL REFERENCES parse_attempt (id),
    source_record_index     INTEGER NOT NULL,
    direction               TEXT NOT NULL,
    external_product_id     TEXT,
    product_name            TEXT,
    external_sale_id        TEXT,
    occurred_at             TIMESTAMP WITHOUT TIME ZONE,
    quantity                NUMERIC(19, 6),
    unit_price              NUMERIC(19, 2),
    discount_percentage     NUMERIC(19, 6),
    total                   NUMERIC(19, 2),
    previous_stock          NUMERIC(19, 6),
    resulting_stock         NUMERIC(19, 6),
    manufacturer            TEXT,
    source_locator          TEXT,
    CONSTRAINT uq_parsed_movement_attempt_index UNIQUE (parse_attempt_id, source_record_index),
    CONSTRAINT ck_parsed_movement_direction CHECK (direction IN ('OUT', 'IN', 'RETURN', 'UNKNOWN'))
);

CREATE TABLE product (
    id                          UUID PRIMARY KEY,
    external_source             TEXT NOT NULL,
    external_id                 TEXT NOT NULL,
    name                        TEXT NOT NULL,
    unit                        TEXT,
    first_seen_parse_attempt_id UUID NOT NULL REFERENCES parse_attempt (id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_external UNIQUE (external_source, external_id)
);

CREATE TABLE sale (
    id                          UUID PRIMARY KEY,
    external_source             TEXT NOT NULL,
    external_sale_id            TEXT NOT NULL,
    occurred_at                 TIMESTAMP WITHOUT TIME ZONE,
    first_seen_parse_attempt_id UUID NOT NULL REFERENCES parse_attempt (id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sale_external UNIQUE (external_source, external_sale_id)
);

CREATE TABLE sale_item (
    id                      UUID PRIMARY KEY,
    sale_id                 UUID NOT NULL REFERENCES sale (id),
    product_id              UUID NOT NULL REFERENCES product (id),
    parse_attempt_id        UUID NOT NULL REFERENCES parse_attempt (id),
    source_record_index     INTEGER NOT NULL,
    quantity                NUMERIC(19, 6) NOT NULL,
    unit_price              NUMERIC(19, 2) NOT NULL,
    discount_percentage     NUMERIC(19, 6),
    total                   NUMERIC(19, 2) NOT NULL,
    previous_stock          NUMERIC(19, 6),
    resulting_stock         NUMERIC(19, 6),
    CONSTRAINT uq_sale_item_attempt_index UNIQUE (parse_attempt_id, source_record_index)
);

CREATE INDEX idx_sale_item_sale ON sale_item (sale_id);
CREATE INDEX idx_sale_item_product ON sale_item (product_id);
CREATE INDEX idx_sale_item_attempt ON sale_item (parse_attempt_id);
CREATE INDEX idx_import_file_job ON import_file (import_job_id);
CREATE INDEX idx_import_file_artifact ON import_file (raw_artifact_id);
CREATE INDEX idx_parse_attempt_artifact ON parse_attempt (raw_artifact_id);
