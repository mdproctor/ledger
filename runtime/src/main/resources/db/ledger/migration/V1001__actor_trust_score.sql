-- CaseHub Ledger — actor trust score table (V1001)
-- Compatible with H2 2.4.240+ (dev/test) and PostgreSQL 15+ (production)
--
-- actor_trust_score: nightly-computed trust scores per actor and score type.
--
-- score_type:  GLOBAL             — one row per actor; classic Bayesian Beta score across all decisions
--              CAPABILITY         — one row per (actor, capability tag); scoped Beta score (#61)
--              DIMENSION          — one row per (actor, trust dimension); e.g. thoroughness (#62)
--              CAPABILITY_DIMENSION — one row per (actor, capability tag, trust dimension); #76
--
-- capability_key: NULL for GLOBAL and DIMENSION rows; capability tag for CAPABILITY and CAPABILITY_DIMENSION.
-- dimension_key:  NULL for GLOBAL and CAPABILITY rows; dimension name for DIMENSION and CAPABILITY_DIMENSION.
--
-- Uniqueness: UNIQUE NULLS NOT DISTINCT (actor_id, capability_key, dimension_key)
--   NULLs are treated as equal for this constraint (PostgreSQL 15+ / H2 2.4.240+), so
--   two GLOBAL rows for the same actor correctly produce a constraint violation.
--
-- trust_score:        Bayesian Beta direct score: alpha_value / (alpha_value + beta_value)
-- global_trust_score: EigenTrust eigenvector component; values sum to ≤ 1.0 across all actors.
--                     Zero when EigenTrust is disabled or not yet computed (GLOBAL rows only).
--
-- CHECK constraint enforces the state machine between score_type and key columns.

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
    CONSTRAINT uq_actor_trust_score_key
        UNIQUE NULLS NOT DISTINCT (actor_id, capability_key, dimension_key),
    CONSTRAINT chk_actor_trust_score_keys CHECK (
        (score_type = 'GLOBAL'               AND capability_key IS NULL      AND dimension_key IS NULL    ) OR
        (score_type = 'CAPABILITY'           AND capability_key IS NOT NULL   AND dimension_key IS NULL    ) OR
        (score_type = 'DIMENSION'            AND capability_key IS NULL       AND dimension_key IS NOT NULL) OR
        (score_type = 'CAPABILITY_DIMENSION' AND capability_key IS NOT NULL   AND dimension_key IS NOT NULL)
    )
);
