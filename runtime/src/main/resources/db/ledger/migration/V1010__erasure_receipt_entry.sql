-- CaseHub Ledger V1009 — erasure receipt entry
-- Records tamper-evident GDPR Art.17 erasure events in the Merkle chain.
-- Opt-in: casehub.ledger.erasure.receipt.enabled=true (default false).
-- Compatible with H2 (dev/test) and PostgreSQL (production).

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
