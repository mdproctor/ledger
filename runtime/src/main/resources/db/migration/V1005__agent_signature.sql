ALTER TABLE ledger_entry
    ADD COLUMN agent_signature  BYTEA,
    ADD COLUMN agent_public_key BYTEA;
