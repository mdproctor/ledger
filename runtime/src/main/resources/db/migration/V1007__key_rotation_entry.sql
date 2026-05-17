CREATE TABLE key_rotation_entry (
    id               UUID PRIMARY KEY REFERENCES ledger_entry(id),
    previous_key_ref TEXT,
    new_key_ref      TEXT,
    reason           TEXT NOT NULL,
    effective_since  TIMESTAMP WITH TIME ZONE NOT NULL
);
