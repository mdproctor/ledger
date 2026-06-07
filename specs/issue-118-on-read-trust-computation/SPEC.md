# On-Read Trust Score Computation

**Issue:** casehubio/ledger#118
**Branch:** issue-118-on-read-trust-computation
**Date:** 2026-06-07

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

Returns primitives (`OptionalDouble`, `int`, `Map<String, Double>`), not entities. Source-agnostic — no dependency on `ActorTrustScore`.

### Three Implementations

All in `runtime/src/main/java/io/casehub/ledger/runtime/service/`.

**MaterializedTrustScoreSource** — `@DefaultBean`
- Injects `ActorTrustScoreRepository`
- Each method maps to a repo query
- No caching, no startup hydration — DB read per call
- Active by default

**CachedTrustScoreSource** — `@Alternative`
- `ConcurrentHashMap` keyed by `actorId:capabilityKey` (same structure as current engine-side `TrustScoreCache`)
- `@PostConstruct` hydrates from `ActorTrustScoreRepository.findAll()`
- `@Observes TrustScoreFullPayload` — batch refresh
- `@Observes TrustScoreActorUpdatedEvent` — incremental refresh (fixes the staleness bug where incremental updates wrote to DB but the cache never saw them)
- Replaces engine-side `TrustScoreCache` — same logic, moved to ledger runtime, behind the SPI

**ComputedTrustScoreSource** — `@Alternative`
- Injects `LedgerEntryRepository`, `DecayFunction`, `AttestationAggregator`, `GlobalScoreStrategy`, `LedgerConfig`
- On each call: loads actor's EVENT entries via `findEventsByActorId()`, batch-loads attestations via `findAttestationsForEntries()`, runs `TrustScoreComputer` inline
- For `capabilityScore()`: filters attestations to requested capability, aggregates per (entryId, capabilityTag), computes Bayesian Beta
- For `globalScore()`: applies `GlobalScoreStrategy.selectAttestations()`, computes Bayesian Beta (not EigenTrust — EigenTrust requires the full actor graph)
- For `decisionCount()`: counts distinct entries with attestations matching the capability
- No persistence, no caching — every call computes fresh
- Uses `PerActorTrustComputer.buildEffectiveAttestations()` (made package-private) for attestation aggregation

**CDI activation** (follows `GlobalScoreStrategy` pattern):
```properties
# Cached (high-throughput routing):
quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.service.CachedTrustScoreSource

# Computed (lightweight, zero-staleness):
quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.service.ComputedTrustScoreSource
```

### TrustGateService Refactoring

Injects `TrustScoreSource` instead of `ActorTrustScoreRepository`. Becomes a pure policy layer — threshold checks and CAPABILITY-to-GLOBAL fallback — on top of the source-agnostic SPI.

- All threshold methods unchanged in behavior
- `findScore(actorId)` removed — only consumer (`LedgerActorStateContributor` in engine) replaced with `currentScore(actorId)` (see #123)
- `TrustExportService` stays on `ActorTrustScoreRepository` directly — export is a materialized-store concern

### Engine-Side Changes (casehubio/ledger#123)

Out of scope for this issue. Tracked in #123:
- Delete `TrustScoreCache` from engine-ledger
- `TrustCandidateClassifier.classify()` takes `TrustScoreSource` instead of `TrustScoreCache`
- `TrustWeightedAgentStrategy` injects `TrustScoreSource` instead of `TrustScoreCache`
- `LedgerActorStateContributor` — `findScore()` to `currentScore()`

### EigenTrust Constraint

EigenTrust computes transitive global trust via power iteration over the full actor graph — can't compute for a single actor on-read. `ComputedTrustScoreSource.globalScore()` returns the Bayesian Beta global score, not the EigenTrust share. Lightweight deployments that choose computed scores don't use EigenTrust. Compliance deployments that need EigenTrust use materialized or cached source.

### Configuration Matrix

| Config | Effect |
|--------|--------|
| Default (no alternatives) | `MaterializedTrustScoreSource` — DB reads per call |
| `selected-alternatives=CachedTrustScoreSource` | In-memory cache, event-driven refresh |
| `selected-alternatives=ComputedTrustScoreSource` | On-read computation from attestations |
| `trust-score.incremental.enabled=true` | Benefits materialized + cached; irrelevant for computed |
| `trust-score.eigentrust.enabled=true` | Benefits materialized + cached; ignored by computed |

### Testing Strategy

- **Contract test** — parameterized test running same assertions against all three implementations. Seeds attestations, verifies `capabilityScore()`, `globalScore()`, `dimensionScore()`, `decisionCount()`, batch methods return consistent results. Uses `persistence-memory` in-memory repos.
- **CachedTrustScoreSource event wiring test** — verifies `TrustScoreActorUpdatedEvent` updates cache (bug fix). Also verifies `TrustScoreFullPayload` refresh.
- **TrustGateService unit tests** — updated to construct with mock `TrustScoreSource` instead of mock `ActorTrustScoreRepository`.
- **ComputedTrustScoreSource integration test** — seeds entries + attestations, verifies computed scores match `TrustScoreComputer` output.

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
| `runtime/src/.../service/MaterializedTrustScoreSource.java` | New — @DefaultBean, reads repo |
| `runtime/src/.../service/CachedTrustScoreSource.java` | New — @Alternative, in-memory cache with event refresh |
| `runtime/src/.../service/ComputedTrustScoreSource.java` | New — @Alternative, on-read computation |
| `runtime/src/.../service/TrustGateService.java` | Refactor — inject TrustScoreSource, remove findScore() |
| `runtime/src/.../service/PerActorTrustComputer.java` | Make buildEffectiveAttestations() package-private |
| `persistence-memory/` | No changes — MaterializedTrustScoreSource uses InMemoryActorTrustScoreRepository automatically |
| Tests | Contract test, cached event test, TrustGateService test update, computed integration test |
