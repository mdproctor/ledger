# 0010 — Two-column key model replaces scope_key in actor_trust_score

Date: 2026-05-14
Status: Accepted
Supersedes: ADR 0006

## Context and Problem Statement

ADR 0006 introduced a `(actor_id, score_type, scope_key)` discriminator for `actor_trust_score`,
where `scope_key` encodes different concepts depending on `score_type`: null for GLOBAL, capability
tag for CAPABILITY, dimension name for DIMENSION. Adding `CAPABILITY_DIMENSION` (#76) — a composite
of both a capability tag and a dimension name — cannot be expressed in a single `scope_key` column
without encoding two values into one string (e.g. `"security-review:thoroughness"`). That encoding
leaks into every query site and is error-prone.

## Decision Drivers

* A composite score type requires two independent key dimensions simultaneously
* String encoding of composite keys creates hidden coupling between producers and consumers
* No deployed instances exist — schema can be redesigned cleanly without migration constraints
* A self-enforcing schema is preferable to application-layer constraints

## Considered Options

* **Two explicit nullable columns** — `capability_key VARCHAR(255)` and `dimension_key VARCHAR(255)`
* **Composite string encoding** — `scope_key = "capability:dimension"` for CAPABILITY_DIMENSION rows, single-value for others
* **Separate table per score type** — one table each for GLOBAL, CAPABILITY, DIMENSION, CAPABILITY_DIMENSION

## Decision Outcome

Chosen option: **Two explicit nullable columns**, because the schema directly expresses what the
data means, queries need no encoding/decoding, and a CHECK constraint can enforce type-key
consistency at the database level.

The four score types fall out naturally from which columns are non-null:

| score_type           | capability_key | dimension_key |
|----------------------|---------------|---------------|
| GLOBAL               | null          | null          |
| CAPABILITY           | set           | null          |
| DIMENSION            | null          | set           |
| CAPABILITY_DIMENSION | set           | set           |

`score_type` is retained as an explicit discriminator column for indexed queries
(`WHERE score_type = 'CAPABILITY'`) — without it, every type query requires a two-column
IS NULL expression.

The unique constraint simplifies: `UNIQUE NULLS NOT DISTINCT (actor_id, capability_key,
dimension_key)` — `score_type` drops out because the type is now deterministic from the keys.

A CHECK constraint enforces the state machine at the database level, making inconsistent rows
impossible to insert regardless of application bugs.

### Positive Consequences

* Schema is self-documenting — no knowledge of encoding format required to read a row
* No encoding/decoding anywhere in the codebase — `capability_key` and `dimension_key` are
  stored and retrieved directly
* CHECK constraint makes the schema self-enforcing
* Unique constraint is simpler — three columns instead of four
* Adding a future score type with a third key dimension follows the same pattern

### Negative Consequences / Tradeoffs

* One additional column in the schema vs the single `scope_key` design
* `score_type` column is now technically redundant (deterministic from key nullity) but
  retained for query clarity and indexability

## Pros and Cons of the Options

### Two explicit nullable columns

* ✅ Schema expresses intent directly — no encoding
* ✅ CHECK constraint enforces consistency at the DB level
* ✅ No LIKE queries — clean equality predicates on both key columns
* ✅ Typed SPI methods — `findCapabilityDimension(actorId, capabilityTag, dimension)` vs generic `findByTypeAndKey(actorId, type, scopeKey)`
* ❌ One extra column

### Composite string encoding

* ✅ No schema change beyond adding a new ScoreType value
* ❌ Encoding format (`":"` separator) leaks into all call sites
* ❌ LIKE queries required for prefix scans (`WHERE scope_key LIKE 'security-review:%'`)
* ❌ Separator collision risk if capability tags or dimension names ever contain `":"`

### Separate table per score type

* ✅ Each table is fully typed with no nullable columns
* ❌ Four tables instead of one — joins required for cross-type queries
* ❌ `TrustExportService`, `TrustScoreJob`, and federation export would all need type dispatch
* ❌ Significant schema and code complexity for a minor typing gain

## Links

* ADR 0006 — original scope_key discriminator model (superseded by this ADR)
* ADR 0009 — continuous scores use decay-weighted average (companion decision for CAPABILITY_DIMENSION)
* Issue #76 — CAPABILITY_DIMENSION composite trust score (the change that prompted this redesign)
