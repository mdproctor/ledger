# 0009 — Continuous quality dimension scores use decay-weighted average, not Bayesian Beta

Date: 2026-05-14
Status: Accepted

## Context and Problem Statement

`DIMENSION` (#62) and `CAPABILITY_DIMENSION` (#76) scores carry continuous quality assessments
in [0.0, 1.0] — e.g. "how thorough is this actor?" A statistical model must aggregate these
over time. The existing `GLOBAL` and `CAPABILITY` score types use Bayesian Beta, which was
designed for binary outcomes (SOUND/FLAGGED verdicts). A continuous input requires a different
approach.

## Decision Drivers

* Continuous inputs must not be forced into binary buckets — doing so loses information
* Recency must matter: older quality assessments should fade, consistent with the Bayesian Beta decay model
* The model must be simple enough to explain to consumers and audit
* The valence asymmetry in `ExponentialDecayFunction` (FLAGGED decays slower than SOUND) must
  not apply — a dimensionScore of 0.3 is "low quality", not "negative evidence"

## Considered Options

* **Decay-weighted average** — `Σ(weight × confidence × dimensionScore) / Σ(weight × confidence)`
* **Bayesian Beta on discretised input** — threshold continuous score to SOUND/FLAGGED, apply Beta model
* **Simple average** — unweighted mean of all dimensionScore values

## Decision Outcome

Chosen option: **Decay-weighted average**, because it handles continuous inputs natively,
preserves recency weighting consistent with the Beta model, and avoids information loss
from discretisation.

`computeDimensionScore()` in `TrustScoreComputer` passes `AttestationVerdict.SOUND` to the
`DecayFunction` for all dimension attestations, deliberately suppressing the valence asymmetry:
continuous scores have no verdict polarity, so FLAGGED's slower-decay multiplier must not apply.

### Positive Consequences

* Continuous inputs preserved — no information lost to thresholding
* Recency handled consistently with the Beta model — uses the same `DecayFunction` SPI
* Simple formula: a single weighted average, easy to audit and explain to consumers
* Adding a new continuous score type follows this pattern; new binary types continue to use Beta

### Negative Consequences / Tradeoffs

* `alpha_value` and `beta_value` columns are not meaningful for DIMENSION and
  CAPABILITY_DIMENSION rows — stored as 0.0, documented in Javadoc, but structurally wasteful
* DIMENSION scores and CAPABILITY scores use different statistical models — a raw number
  comparison between them is not meaningful without knowing the type

## Pros and Cons of the Options

### Decay-weighted average

* ✅ Native continuous input — no discretisation
* ✅ Recency-weighted — consistent with Beta model philosophy
* ✅ Valence asymmetry suppressible by passing `SOUND` to `DecayFunction`
* ❌ Alpha/beta columns unused — schema has dead columns for these rows

### Bayesian Beta on discretised input

* ✅ Reuses existing Beta infrastructure and columns
* ❌ Loses information: score 0.51 and score 0.99 both become SOUND
* ❌ Threshold choice is arbitrary and domain-dependent — wrong to bake into the foundation

### Simple average

* ✅ Simplest possible formula
* ❌ No recency: a year-old poor assessment weighs the same as yesterday's excellent one
* ❌ Inconsistent with the recency philosophy of the Beta model

## Links

* ADR 0003 — Bayesian Beta model (binary score foundation)
* ADR 0006 — ActorTrustScore discriminator model (note: scope_key column superseded by
  two-column key model in #76; ADR 0006 status should be updated separately)
* Issue #62 — Dimension-scoped trust scores (first use of this model)
* Issue #76 — CAPABILITY_DIMENSION composite scores (second use)
* Issue #78 — tracking issue for this ADR
