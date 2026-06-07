# On-Read Trust Score Computation

**Issue:** casehubio/ledger#118
**Branch:** issue-118-on-read-trust-computation
**Date:** 2026-06-07

> **Naming note:** Issue #118 originally proposed `TrustSourceProvider` in `casehub-engine-api`.
> This spec uses `TrustScoreSource` in `casehub-ledger-api` — more consistent with project
> naming conventions (`TrustScoreComputer`, `TrustScoreJob`, `ActorTrustScore`), and ledger-api
> is the correct module per consumer-spi-placement protocol.

## Problem

Trust scores are a materialized view of attestation history. `TrustGateService` reads pre-computed rows from `actor_trust_score`. The engine-side `TrustScoreCache` maintains a parallel in-memory copy refreshed by batch CDI events. Both paths are stale — the cache by up to 24h (batch job interval), and the cache also misses incremental updates because it observes `TrustScoreFullPayload` but not `TrustScoreActorUpdatedEvent`.

For lightweight deployments (QuarkMind: 4 actors, bounded games), the materialized store is unnecessary overhead. Scores can be computed on demand from raw attestations at query time, eliminating staleness entirely.

## Design

### TrustScoreSource SPI

**Location:** `api/src/main/java/io/casehub/ledger/api/spi/TrustScoreSource.java`

Per consumer-spi-placement protocol — engine-ledger injects this, so it goes in `api/spi/`.

```java
public interface TrustScoreSource {
    OptionalDouble globalScore(String actorId);
    OptionalDouble capabilityScore(String actorId, String capabilityTag);
    OptionalDouble dimensionScore(String actorId, String dimensionKey);
    OptionalDouble capabilityDimensionScore(String actorId, String capabilityTag, String dimensionKey);
    int decisionCount(String actorId, String capabilityTag);
    Map<String, Double> allCapabilityScores(String actorId);
    Map<String, Double> allDimensionScores(String actorId);
    Map<String, Double> qualityScores(String actorId, String capabilityTag);
}
```

**Return types:** `OptionalDouble` throughout (primitive specialization, no boxing). `TrustGateService` score-returning methods change from `Optional<Double>` to `OptionalDouble` to match — breaking change for engine consumers, deliberate. This platform has no end users; forcing every call site to be explicit about the primitive type is the right design. The conversion boilerplate (`Optional<Double>` ↔ `OptionalDouble`) at every delegation site is the wrong answer.

### TrustScoreCalculator — Pure Computation Extraction

**Location:** `runtime/src/main/java/io/casehub/ledger/runtime/service/TrustScoreCalculator.java`

Extracts all pure computation logic from `PerActorTrustComputer` into a stateless CDI bean. Takes (decisions, attestations) → scores. No persistence, no CDI events.

```java
@ApplicationScoped
public class TrustScoreCalculator {

    record ComputedScores(
        Map<String, ActorScore> capabilityScores,
        Map<String, Double> dimensionScores,
        Map<String, Map<String, Double>> capabilityDimensionScores,
        ActorScore globalScore,
        int totalDecisionCount
    ) {}

    ComputedScores computeAll(
        List<LedgerEntry> decisions,
        Map<UUID, List<LedgerAttestation>> attestationsByEntry,
        Instant now);
}
```

Encapsulates:
- `buildEffectiveAttestations()` — attestation aggregation (currently private on `PerActorTrustComputer`)
- Four-pass grouping logic (capability → dimension → capability×dimension → global)
- Delegates to `TrustScoreComputer` for Bayesian Beta
- Delegates to `GlobalScoreStrategy` for global score derivation — both `selectAttestations()` AND `derive()` paths

Injected dependencies: `DecayFunction`, `AttestationAggregator`, `GlobalScoreStrategy`, `LedgerConfig` (for aggregation strategy). All stateless — singleton-safe.

**`PerActorTrustComputer` simplifies to:** load → `calculator.computeAll()` → upsert results → fire events.

**`ComputedTrustScoreSource` uses:** load → `calculator.computeAll()` → return scores.

**Testing benefit:** `TrustScoreCalculator` is independently testable with no persistence or CDI event dependencies. The current coupling of computation + persistence in `PerActorTrustComputer` is a design smell this extraction fixes.

### Three TrustScoreSource Implementations

All in `runtime/src/main/java/io/casehub/ledger/runtime/service/`.

**MaterializedTrustScoreSource** — `@DefaultBean`
- Injects `ActorTrustScoreRepository`
- Each method maps to a repo query
- No caching, no startup hydration — DB read per call
- Active by default

**CachedTrustScoreSource** — `@Alternative`

Separate `ConcurrentHashMap` per score type (not a single map with composite key — avoids key format ambiguity):
```
ConcurrentHashMap<String, Double> globalScores;                    // key: actorId
ConcurrentHashMap<String, CachedCapabilityScore> capabilityScores; // key: actorId:capabilityKey
ConcurrentHashMap<String, Double> dimensionScores;                 // key: actorId:dimensionKey
ConcurrentHashMap<String, Double> capDimScores;                    // key: actorId:capabilityKey:dimensionKey
```

- `@PostConstruct` hydrates from `ActorTrustScoreRepository.findAll()`
- `@Observes TrustScoreFullPayload` — batch refresh
- `@Observes TrustScoreActorUpdatedEvent` — incremental refresh (**fixes** the staleness bug where incremental updates wrote to DB but the cache never saw them)
- Replaces engine-side `TrustScoreCache` — same logic, moved to ledger runtime, behind the SPI

**ComputedTrustScoreSource** — `@Alternative`

Injects `LedgerEntryRepository` and `TrustScoreCalculator`.

**Per-actor computation cache** — eliminates multiplicative query cost. Without caching, `TrustCandidateClassifier` issues per candidate: `capabilityScore()` + `decisionCount()` + N × `capabilityDimensionScore()`. For 3 candidates with 2 quality dimensions: 12 full event loads and computations loading the same data.

Design: `ConcurrentHashMap<String, ComputedScores>` keyed by actorId. On first score request for an actor, loads events + attestations, calls `TrustScoreCalculator.computeAll()`, caches the full `ComputedScores` bundle. Subsequent requests for the same actor return from cache. Invalidation: `@Observes AttestationRecordedEvent` → remove cache entry for the affected actor.

This is zero-staleness caching: the cache is invalidated exactly when the underlying data changes (new attestation). Between attestations, the cached result is correct because no new data exists. Conceptually different from `CachedTrustScoreSource` — computed caches on-demand computation results, cached caches materialized store snapshots.

**Global score — `derive()` path:** `computeAll()` runs the full four-pass flow including `GlobalScoreStrategy.derive(capabilityScores, allAttestations)`. `FrequencyWeightedGlobalStrategy.derive()` returns a score computed from capability scores, bypassing attestations entirely. This works correctly because capability scores are computed in pass 1 before the global pass uses them. EigenTrust global scores are not available (requires full actor graph) — `globalScore()` returns the Bayesian Beta or derived score.

**CDI activation** (follows `GlobalScoreStrategy` pattern):
```properties
# Cached (high-throughput routing):
quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.service.CachedTrustScoreSource

# Computed (lightweight, zero-staleness):
quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.service.ComputedTrustScoreSource
```

### TrustGateService Refactoring

Injects `TrustScoreSource` instead of `ActorTrustScoreRepository`. Becomes a pure policy layer — threshold checks and CAPABILITY-to-GLOBAL fallback — on top of the source-agnostic SPI.

**Return type change:** Score-returning methods change from `Optional<Double>` to `OptionalDouble` to align with the SPI. Breaking change for engine consumers — deliberate, forces call sites to be explicit. Threshold methods (`meetsThreshold`) return `boolean` — unchanged.

**Naming normalization:** `dimensionScores(actorId)` renamed to `allDimensionScores(actorId)` to match the SPI and align with the existing `allCapabilityScores(actorId)`. One method rename.

**`findScore(actorId)` removed.** Returns `Optional<ActorTrustScore>` — the full JPA entity with alpha, beta, decisionCount. Only production consumer is `LedgerActorStateContributor` in engine, which extracts `.trustScore` — replaced with `currentScore(actorId)` (see #123). This is a **deliberate simplification**: it removes the only access path to rich score data outside the repository. Currently no consumer needs alpha/beta/counts through TrustGateService. If rich data is needed in the future, a richer SPI method can be added — but the need should be demonstrated, not assumed.

**`meetsThresholdAsync(actorId, minTrust)`** — no changes needed. The reactive wrapper calls blocking `meetsThreshold()`, which now delegates to `TrustScoreSource`. For `ComputedTrustScoreSource`, the full computation runs on the worker pool (via `Infrastructure.getDefaultWorkerPool()`) — safe.

**`TrustExportService` stays on `ActorTrustScoreRepository` directly.** Export is a materialized-store concern — it needs rich entity data (alpha, beta, counts) that the SPI's scalar returns don't carry. In a computed-only deployment, `ActorTrustScoreRepository` is empty (no one writes to it), so trust export and federation return nothing. This is an **accepted limitation** for the target use case (QuarkMind: 4 actors, no federation). Compliance deployments that need federation use materialized or cached source.

### Engine-Side Changes (casehubio/ledger#123)

Out of scope for this issue. Tracked in #123:
- Delete `TrustScoreCache` from engine-ledger
- `TrustCandidateClassifier.classify()` takes `TrustScoreSource` instead of `TrustScoreCache`
- `TrustWeightedAgentStrategy` injects `TrustScoreSource` instead of `TrustScoreCache`
- `LedgerActorStateContributor` — `findScore()` to `currentScore()` (returns `OptionalDouble` after this issue)

### EigenTrust Constraint

EigenTrust computes transitive global trust via power iteration over the full actor graph — can't compute for a single actor on-read. `ComputedTrustScoreSource.globalScore()` returns the Bayesian Beta global score (or the `GlobalScoreStrategy.derive()` result), not the EigenTrust share. Lightweight deployments that choose computed scores don't use EigenTrust. Compliance deployments that need EigenTrust use materialized or cached source.

### Configuration

**Source selection** (follows `GlobalScoreStrategy` pattern):

| Source | Activation | Reads from |
|--------|-----------|------------|
| Materialized (default) | No config needed | `ActorTrustScoreRepository` per call |
| Cached | `selected-alternatives=CachedTrustScoreSource` | In-memory maps, event-driven refresh |
| Computed | `selected-alternatives=ComputedTrustScoreSource` | Raw attestation history, on-demand |

**Interaction effects for computed-only deployments:**

| Combination | Effect |
|-------------|--------|
| Computed + `trust-score.enabled=true` | Nightly job computes + persists scores nobody reads. Silent waste. |
| Computed + `incremental.enabled=true` | Observer computes + persists on every attestation. Also silent waste. |
| Computed + `eigentrust.enabled=true` | EigenTrust runs, persists results, computed source ignores them. |
| Computed + `routing-enabled=true` | Events fire carrying materialized data the computed source doesn't produce. |

Issue #125 tracks disabling this machinery. Until then, these run harmlessly — DB rows accumulate but aren't read.

**Recommended computed-only profile:**
```properties
quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.service.ComputedTrustScoreSource
casehub.ledger.trust-score.enabled=false
casehub.ledger.trust-score.incremental.enabled=false
```

This disables the batch job and incremental observer. Trust scores are computed purely on-read. EigenTrust is implicitly disabled (`trust-score.enabled=false` gates the batch job). The computed source does not check `trust-score.enabled` — it computes on every call regardless.

### Testing Strategy

**Contract test setup pattern:** All three implementations must agree on results given the same attestation history. Setup:
1. Seed `LedgerEntry` (EVENT type) + `LedgerAttestation` rows via in-memory repos
2. For materialized/cached: run `PerActorTrustComputer.computeForActor()` to populate `ActorTrustScore` rows (the computed source reads raw attestations; materialized/cached read pre-computed rows)
3. Assert all three sources return equivalent results for every method

This pattern ensures agreement is by contract, not coincidence.

**Specific tests:**
- **Contract test** — parameterized across all three implementations using the setup pattern above. Covers `capabilityScore()`, `globalScore()`, `dimensionScore()`, `capabilityDimensionScore()`, `decisionCount()`, and all batch methods.
- **CachedTrustScoreSource event wiring test** — verifies that firing `TrustScoreActorUpdatedEvent` updates all four cache maps (the bug fix). Also verifies `TrustScoreFullPayload` batch refresh.
- **ComputedTrustScoreSource cache invalidation test** — verifies that firing `AttestationRecordedEvent` invalidates the per-actor computation cache, causing the next call to recompute.
- **TrustScoreCalculator unit test** — pure computation tests with no persistence. Seeds decisions + attestations, verifies all four score types. Tests `derive()` path with `FrequencyWeightedGlobalStrategy`.
- **TrustGateService unit tests** — updated to construct with mock `TrustScoreSource` (simpler — mocking `OptionalDouble` returns instead of entity wrappers). Threshold and fallback logic unchanged.

### Follow-On Issues

- **#123** — engine-side TrustScoreSource migration (blocked by this issue)
- **#124** — targeted per-capability queries for ComputedTrustScoreSource (optimization, when real-world data warrants)
- **#125** — disable batch/incremental machinery for computed-only deployments (simplification, not urgent)

## Coherence

- **PLATFORM.md:** Trust scoring owned by casehub-ledger. SPI in api/. No capability ownership conflicts.
- **consumer-spi-placement:** `TrustScoreSource` in `api/spi/` — engine-ledger is an external consumer.
- **alternative-extension-patterns:** Pattern C — `@DefaultBean` + `@Alternative` implementations.
- **trust-maturity-model:** Four-phase model unchanged. Routing uses CAPABILITY scores via SPI.
- **Garden:** GE-20260602 (engine-ledger classpath), GE-20260605 (mixed-pool gap), GE-20260529 (bootstrap SPI) — all orthogonal.

## Files Changed (ledger repo)

| File | Change |
|------|--------|
| `api/src/.../spi/TrustScoreSource.java` | New — SPI interface |
| `runtime/src/.../service/TrustScoreCalculator.java` | New — pure computation logic extracted from PerActorTrustComputer |
| `runtime/src/.../service/MaterializedTrustScoreSource.java` | New — @DefaultBean, reads repo |
| `runtime/src/.../service/CachedTrustScoreSource.java` | New — @Alternative, in-memory cache with event refresh |
| `runtime/src/.../service/ComputedTrustScoreSource.java` | New — @Alternative, on-read computation with per-actor cache |
| `runtime/src/.../service/TrustGateService.java` | Refactor — inject TrustScoreSource, return OptionalDouble, remove findScore(), rename dimensionScores→allDimensionScores |
| `runtime/src/.../service/PerActorTrustComputer.java` | Simplify — delegate computation to TrustScoreCalculator, keep only load + persist + events |
| `persistence-memory/` | No changes — MaterializedTrustScoreSource uses InMemoryActorTrustScoreRepository automatically |
| Tests | Contract test, calculator unit test, cached event test, computed cache invalidation test, TrustGateService test update |
