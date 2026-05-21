ALTER TABLE ledger_entry
    ADD COLUMN agent_signature  BYTEA;

ALTER TABLE ledger_entry
    ADD COLUMN agent_public_key BYTEA;

ALTER TABLE ledger_entry
    ADD CONSTRAINT chk_agent_signature_pair
        CHECK ((agent_signature IS NULL) = (agent_public_key IS NULL));
