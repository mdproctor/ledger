ALTER TABLE ledger_entry ADD COLUMN agent_key_ref TEXT;

ALTER TABLE ledger_entry ADD CONSTRAINT chk_agent_key_ref_nullability
    CHECK ((agent_key_ref IS NULL) = (agent_signature IS NULL));
