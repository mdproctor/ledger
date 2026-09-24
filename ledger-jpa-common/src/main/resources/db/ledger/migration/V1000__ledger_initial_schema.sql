-- Consolidated initial schema for casehub-ledger.
-- Replaces V1000–V1012 incremental migrations (no production database exists).

-- ── ledger_entry ─────────────────────────────────────────────────────────────
-- Domain-agnostic immutable audit log.
-- Columns dropped in V1002 (plan_ref, rationale, evidence, detail, decision_context,
-- source_entity_id, source_entity_type, source_entity_system) are omitted.

CREATE TABLE ledger_entry (
    id                 UUID            NOT NULL,
    dtype              VARCHAR(50)     NOT NULL,
    subject_id         UUID            NOT NULL,
    tenancy_id         VARCHAR(255)    NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    sequence_number    INT             NOT NULL,
    entry_type         VARCHAR(20)     NOT NULL,
    actor_id           VARCHAR(255),
    actor_type         VARCHAR(20),
    actor_role         VARCHAR(100),
    caused_by_entry_id UUID,
    trace_id           VARCHAR(255),
    digest             VARCHAR(64),
    occurred_at        TIMESTAMP       NOT NULL,
    supplement_json    TEXT,
    agent_signature    BYTEA,
    agent_public_key   BYTEA,
    agent_key_ref      TEXT,
    actor_did          TEXT,
    metadata           TEXT,
    domain_data        TEXT,
    CONSTRAINT pk_ledger_entry PRIMARY KEY (id),
    CONSTRAINT chk_agent_signature_pair CHECK (
        (agent_signature IS NULL) = (agent_public_key IS NULL)
    ),
    CONSTRAINT chk_agent_key_ref_nullability CHECK (
        (agent_key_ref IS NULL) = (agent_signature IS NULL)
    )
);

CREATE UNIQUE INDEX idx_ledger_entry_subject_seq ON ledger_entry (subject_id, tenancy_id, sequence_number);
CREATE INDEX idx_ledger_entry_subject_id  ON ledger_entry (subject_id);
CREATE INDEX idx_ledger_entry_tenancy     ON ledger_entry (tenancy_id);
CREATE INDEX idx_ledger_entry_trace       ON ledger_entry (trace_id);
CREATE INDEX idx_ledger_entry_caused_by   ON ledger_entry (caused_by_entry_id);
CREATE INDEX idx_ledger_entry_actor       ON ledger_entry (actor_id);

-- ── ledger_attestation ───────────────────────────────────────────────────────

CREATE TABLE ledger_attestation (
    id               UUID             NOT NULL,
    ledger_entry_id  UUID             NOT NULL,
    subject_id       UUID             NOT NULL,
    attestor_id      VARCHAR(255)     NOT NULL,
    attestor_type    VARCHAR(20)      NOT NULL,
    attestor_role    VARCHAR(100),
    verdict          VARCHAR(20)      NOT NULL,
    evidence         TEXT,
    confidence       DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    capability_tag   VARCHAR(255)     NOT NULL DEFAULT '*',
    trust_dimension  VARCHAR(255),
    dimension_score  DOUBLE PRECISION,
    occurred_at      TIMESTAMP        NOT NULL,
    CONSTRAINT pk_ledger_attestation PRIMARY KEY (id),
    CONSTRAINT fk_attestation_entry FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entry (id)
);

CREATE INDEX idx_ledger_attestation_entry     ON ledger_attestation (ledger_entry_id);
CREATE INDEX idx_ledger_attestation_subject   ON ledger_attestation (subject_id);
CREATE INDEX idx_ledger_attestation_capability ON ledger_attestation (ledger_entry_id, capability_tag);
CREATE INDEX idx_ledger_attestation_actor_cap  ON ledger_attestation (attestor_id, capability_tag);
CREATE INDEX idx_ledger_attestation_dimension ON ledger_attestation (attestor_id, trust_dimension);

-- ── ledger_merkle_frontier ───────────────────────────────────────────────────

CREATE TABLE ledger_merkle_frontier (
    id          UUID         NOT NULL,
    subject_id  UUID         NOT NULL,
    tenancy_id  VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    level       INTEGER      NOT NULL,
    hash        VARCHAR(64)  NOT NULL,
    CONSTRAINT pk_ledger_merkle_frontier PRIMARY KEY (id),
    CONSTRAINT uq_merkle_frontier_subject_tenancy_level UNIQUE (subject_id, tenancy_id, level)
);

CREATE INDEX idx_merkle_frontier_subject ON ledger_merkle_frontier (subject_id, tenancy_id);

-- ── ledger_subject_sequence ──────────────────────────────────────────────────

CREATE TABLE ledger_subject_sequence (
    subject_id UUID         NOT NULL,
    tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    next_seq   INT          NOT NULL DEFAULT 1,
    CONSTRAINT pk_ledger_subject_sequence PRIMARY KEY (subject_id, tenancy_id)
);

-- ── actor_trust_score ────────────────────────────────────────────────────────

CREATE TABLE actor_trust_score (
    id                   UUID             NOT NULL,
    actor_id             VARCHAR(255)     NOT NULL,
    score_type           VARCHAR(20)      NOT NULL DEFAULT 'GLOBAL',
    capability_key       VARCHAR(255),
    dimension_key        VARCHAR(255),
    actor_type           VARCHAR(20),
    trust_score          DOUBLE PRECISION NOT NULL,
    global_trust_score   DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    alpha_value          DOUBLE PRECISION NOT NULL,
    beta_value           DOUBLE PRECISION NOT NULL,
    decision_count       INT              NOT NULL,
    overturned_count     INT              NOT NULL,
    attestation_positive INT              NOT NULL,
    attestation_negative INT              NOT NULL,
    last_computed_at     TIMESTAMP,
    CONSTRAINT pk_actor_trust_score PRIMARY KEY (id),
    CONSTRAINT uq_actor_trust_score_key UNIQUE (actor_id, score_type, capability_key, dimension_key),
    CONSTRAINT chk_actor_trust_score_keys CHECK (
        (score_type = 'GLOBAL'               AND capability_key IS NULL      AND dimension_key IS NULL    ) OR
        (score_type = 'CAPABILITY'           AND capability_key IS NOT NULL   AND dimension_key IS NULL    ) OR
        (score_type = 'DIMENSION'            AND capability_key IS NULL       AND dimension_key IS NOT NULL) OR
        (score_type = 'CAPABILITY_DIMENSION' AND capability_key IS NOT NULL   AND dimension_key IS NOT NULL)
    )
);

-- ── ledger_supplement_compliance ──────────────────────────────────────────────
-- Self-contained (no JOINED inheritance base table).

CREATE TABLE ledger_supplement_compliance (
    id                       UUID          NOT NULL,
    ledger_entry_id          UUID          NOT NULL,
    supplement_type          VARCHAR(30)   NOT NULL,
    plan_ref                 VARCHAR(500),
    rationale                TEXT,
    evidence                 TEXT,
    detail                   TEXT,
    decision_context         TEXT,
    algorithm_ref            VARCHAR(500),
    confidence_score         DOUBLE PRECISION,
    contestation_uri         VARCHAR(2000),
    human_override_available BOOLEAN,
    CONSTRAINT pk_ledger_supplement_compliance PRIMARY KEY (id),
    CONSTRAINT fk_compliance_entry FOREIGN KEY (ledger_entry_id)
        REFERENCES ledger_entry (id)
);

CREATE INDEX idx_compliance_entry ON ledger_supplement_compliance (ledger_entry_id);

-- ── ledger_supplement_provenance ─────────────────────────────────────────────
-- Self-contained (no JOINED inheritance base table).

CREATE TABLE ledger_supplement_provenance (
    id                   UUID          NOT NULL,
    ledger_entry_id      UUID          NOT NULL,
    supplement_type      VARCHAR(30)   NOT NULL,
    source_entity_id     VARCHAR(255),
    source_entity_type   VARCHAR(255),
    source_entity_system VARCHAR(100),
    agent_config_hash    VARCHAR(64),
    CONSTRAINT pk_ledger_supplement_provenance PRIMARY KEY (id),
    CONSTRAINT fk_provenance_entry FOREIGN KEY (ledger_entry_id)
        REFERENCES ledger_entry (id)
);

CREATE INDEX idx_provenance_entry ON ledger_supplement_provenance (ledger_entry_id);

-- ── ledger_supplement_compensation ──────────────────────────────────────────
-- Self-contained (no JOINED inheritance base table).

CREATE TABLE ledger_supplement_compensation (
    id                   UUID          NOT NULL,
    ledger_entry_id      UUID          NOT NULL,
    supplement_type      VARCHAR(30)   NOT NULL,
    original_entry_id    UUID          NOT NULL,
    compensation_reason  TEXT,
    regulatory_basis     VARCHAR(100),
    compensation_mode    VARCHAR(20)   NOT NULL,
    CONSTRAINT pk_ledger_supplement_compensation PRIMARY KEY (id),
    CONSTRAINT fk_compensation_entry FOREIGN KEY (ledger_entry_id)
        REFERENCES ledger_entry (id),
    CONSTRAINT fk_compensation_original FOREIGN KEY (original_entry_id)
        REFERENCES ledger_entry (id)
);

CREATE INDEX idx_compensation_entry ON ledger_supplement_compensation (ledger_entry_id);
CREATE INDEX idx_compensation_original ON ledger_supplement_compensation (original_entry_id);

-- ── ledger_entry_archive ─────────────────────────────────────────────────────

CREATE TABLE ledger_entry_archive (
    id                UUID        NOT NULL,
    original_entry_id UUID        NOT NULL,
    subject_id        UUID        NOT NULL,
    sequence_number   INT         NOT NULL,
    entry_json        TEXT        NOT NULL,
    entry_occurred_at TIMESTAMP   NOT NULL,
    archived_at       TIMESTAMP   NOT NULL,
    CONSTRAINT pk_ledger_entry_archive PRIMARY KEY (id)
);

CREATE INDEX idx_archive_subject  ON ledger_entry_archive (subject_id);
CREATE INDEX idx_archive_occurred ON ledger_entry_archive (entry_occurred_at);

-- ── actor_identity ───────────────────────────────────────────────────────────

CREATE TABLE actor_identity (
    token      VARCHAR(255) NOT NULL,
    actor_id   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT pk_actor_identity PRIMARY KEY (token),
    CONSTRAINT uq_actor_identity_actor_id UNIQUE (actor_id)
);

-- ── key_rotation_entry ───────────────────────────────────────────────────────

CREATE TABLE key_rotation_entry (
    id               UUID PRIMARY KEY REFERENCES ledger_entry(id),
    previous_key_ref TEXT,
    new_key_ref      TEXT,
    reason           TEXT NOT NULL,
    effective_since  TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ── actor_identity_binding ───────────────────────────────────────────────────

CREATE TABLE actor_identity_binding (
    id                     UUID    NOT NULL,
    bound_did              TEXT    NOT NULL,
    validation_result      VARCHAR(32) NOT NULL,
    also_known_as_verified BOOLEAN NOT NULL DEFAULT FALSE,
    key_match_verified     BOOLEAN NOT NULL DEFAULT FALSE,
    verified_key_ref       TEXT,
    credential_result      VARCHAR(32),
    did_method             VARCHAR(32),
    CONSTRAINT pk_actor_identity_binding PRIMARY KEY (id),
    CONSTRAINT fk_actor_identity_binding_entry FOREIGN KEY (id) REFERENCES ledger_entry(id),
    CONSTRAINT chk_identity_binding_result CHECK (
        validation_result IN ('VALID','UNSIGNED','DID_UNRESOLVABLE','IDENTITY_MISMATCH',
                              'KEY_MISMATCH','CREDENTIAL_EXPIRED','CREDENTIAL_INVALID')
    ),
    CONSTRAINT chk_identity_credential_result CHECK (
        credential_result IS NULL OR
        credential_result IN ('VALID','EXPIRED','INVALID_SIGNATURE','ISSUER_UNKNOWN','NOT_FOUND')
    )
);

-- ── plain_ledger_entry ───────────────────────────────────────────────────────

CREATE TABLE plain_ledger_entry (
    id UUID NOT NULL,
    CONSTRAINT pk_plain_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_plain_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry (id)
);

-- ── erasure_receipt_entry ────────────────────────────────────────────────────

CREATE TABLE erasure_receipt_entry (
    id                   UUID         NOT NULL,
    erased_actor_id      TEXT         NOT NULL,
    erasure_reason       VARCHAR(50)  NOT NULL,
    affected_entry_count BIGINT       NOT NULL DEFAULT 0,
    mapping_found        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_erasure_receipt_entry PRIMARY KEY (id),
    CONSTRAINT fk_erasure_receipt_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_erasure_receipt_erased_actor ON erasure_receipt_entry (erased_actor_id);

-- ── trust_score_snapshot ─────────────────────────────────────────────────────

CREATE TABLE trust_score_snapshot (
    id              UUID             NOT NULL,
    actor_id        VARCHAR(255)     NOT NULL,
    score_type      VARCHAR(50)      NOT NULL,
    capability_tag  VARCHAR(255),
    dimension_key   VARCHAR(255),
    score           DOUBLE PRECISION NOT NULL,
    previous_score  DOUBLE PRECISION NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_trust_score_snapshot PRIMARY KEY (id)
);

CREATE INDEX idx_trust_score_snapshot_actor
    ON trust_score_snapshot (actor_id, score_type, occurred_at DESC);
