-- CaseHub Ledger — actor_trust_score two-column key schema (V1005)
--
-- Replaces the single scope_key column with two typed nullable columns:
--   capability_key  — set for CAPABILITY and CAPABILITY_DIMENSION rows; null otherwise
--   dimension_key   — set for DIMENSION and CAPABILITY_DIMENSION rows; null otherwise
--
-- The CHECK constraint makes the schema self-enforcing.
-- The unique constraint no longer includes score_type (it is now deterministic from the keys).

ALTER TABLE actor_trust_score DROP CONSTRAINT uq_actor_trust_score_key;
ALTER TABLE actor_trust_score DROP COLUMN scope_key;

ALTER TABLE actor_trust_score ADD COLUMN capability_key VARCHAR(255);
ALTER TABLE actor_trust_score ADD COLUMN dimension_key  VARCHAR(255);

ALTER TABLE actor_trust_score
    ADD CONSTRAINT uq_actor_trust_score_key
        UNIQUE NULLS NOT DISTINCT (actor_id, capability_key, dimension_key);

ALTER TABLE actor_trust_score
    ADD CONSTRAINT chk_actor_trust_score_keys CHECK (
        (score_type = 'GLOBAL'               AND capability_key IS NULL      AND dimension_key IS NULL    ) OR
        (score_type = 'CAPABILITY'           AND capability_key IS NOT NULL   AND dimension_key IS NULL    ) OR
        (score_type = 'DIMENSION'            AND capability_key IS NULL       AND dimension_key IS NOT NULL) OR
        (score_type = 'CAPABILITY_DIMENSION' AND capability_key IS NOT NULL   AND dimension_key IS NOT NULL)
    );
