-- Plain concrete subclass of LedgerEntry for domain-agnostic event writes (OutcomeRecorder).
-- No domain-specific columns — all payload lives in ledger_entry and ledger_attestation.
CREATE TABLE plain_ledger_entry (
    id UUID NOT NULL,
    CONSTRAINT pk_plain_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_plain_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry (id)
);
