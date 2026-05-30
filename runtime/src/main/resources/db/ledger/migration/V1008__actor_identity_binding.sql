-- V1008__actor_identity_binding.sql
-- Adds actor_did to ledger_entry base table and creates the actor_identity_binding join table
-- for ActorIdentityBindingEntry (LedgerEntry subclass, JOINED inheritance).

ALTER TABLE ledger_entry ADD COLUMN actor_did TEXT;

CREATE TABLE actor_identity_binding (
    id                     UUID NOT NULL,
    bound_did              TEXT NOT NULL,
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
