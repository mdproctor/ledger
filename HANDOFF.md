# HANDOFF.md — casehub-ledger

**Session:** 2026-06-07
**Branch closed:** issue-118-on-read-trust-computation → main

## What happened

Implemented `TrustScoreSource` SPI (#118) — three pluggable trust score read-path implementations (materialized, cached, computed). Extracted `TrustScoreCalculator` from `PerActorTrustComputer` for shared computation. Refactored `TrustGateService` to inject `TrustScoreSource` with `OptionalDouble` return types. Fixed `CachedTrustScoreSource` to observe `TrustScoreActorUpdatedEvent` from the incremental observer.

## Immediate next step

Run `/work` to pick up one of the open issues. Working tree is clean, on main, upstream and fork in sync.

## Cross-Module

**We're blocking:**
- `casehub-engine` — needs `TrustScoreSource` SPI migration (#123): delete `TrustScoreCache`, update `TrustCandidateClassifier` + `TrustWeightedAgentStrategy` to inject `TrustScoreSource`, fix `LedgerActorStateContributor` `findScore()` → `currentScore()` · M · Low

## What's Left

- #123 — engine-side `TrustScoreSource` migration · M · Low (mechanical, blocked on this session's work being published — now done)
- casehubio/parent#190 — deep-dive doc sync for ledger · S · Low

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #122 | PostgreSQL DevServices for integration tests | M | Med | |
| #100 | Fix race-safe sequence numbering under concurrent writers | M | Med | |
| #110 | ScimDIDResolver — synthetic DIDDocument from SCIM | S | Med | |
| #108 | JwtVCValidator — W3C VC JWT credential validation | M | High | |
| #124 | Targeted per-capability queries for ComputedTrustScoreSource | S | Low | Optimization, when real-world data warrants |
| #125 | Disable batch/incremental machinery for computed-only deployments | S | Med | |
| #102 | Cloud KMS AgentSigner adapters | L | Med | |

## References

- `specs/issue-118-on-read-trust-computation/SPEC.md` — full design spec
- `blog/2026-06-07-mdp01-trust-scores-always-stale.md` — session diary entry
