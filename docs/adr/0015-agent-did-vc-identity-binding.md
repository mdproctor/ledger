# ADR 0015 — Agent DID/VC Identity Binding: Two-Field Model

**Status:** Accepted  
**Date:** 2026-05-30  
**Issue:** casehubio/ledger#81

## Context

The `actorId` field is a convention string (`claude:reviewer@v1`) that anyone can claim.
Bilateral signing (ADR 0011, ADR 0014, #85) proves a signing key was used, but not who
controls that key. An operator could create a key pair and claim any `actorId` they like —
the signature would verify, but the identity would be unattested.

DID/VC binding provides cryptographic identity verification: a Decentralised Identifier (DID)
is controlled only by whoever holds the corresponding private key, and a Verifiable Credential
(VC) is an issuer-signed claim that a given subject possesses a given property. Binding an
`actorId` to a DID document, and optionally anchoring that with a VC, allows verifiers to
confirm that the entity signing ledger entries is the entity it claims to be.

This ADR documents the design decisions made for the DID/VC identity binding subsystem.

## Decision 1 — Two-Field Model

`actorId` and `actorDid` are separate fields serving separate concerns:

- **`actorId`** (`claude:reviewer@v1`) — the trust accumulation key. All historical trust
  scores, attestations, and capability scores are indexed by this value. Changing it resets
  the actor's reputation baseline (per ADR 0004). It is a stable, human-readable semantic
  identifier.

- **`actorDid`** (`did:web:casehub.io:agents:reviewer`) — the cryptographic identity anchor.
  It is resolvable to a DID document containing a public key and can be used to verify that
  the holder of the signing key is the entity the DID document describes.

These two fields must not be conflated. `actorId` is a convention; `actorDid` is a verifiable
claim. Merging them into one field would either break backward compatibility with all historical
entries or force a migration of every trust score and attestation record.

## Decision 2 — `alsoKnownAs` Verification Closes the Divergence Attack

If any operator could bind any DID to any `actorId`, a rogue operator could map
`claude:reviewer@v1` to their own DID and claim the identity of a well-trusted actor.

The mitigation: the DID document must contain `alsoKnownAs: ["claude:reviewer@v1"]`. The
binding validator (`DIDDocumentValidator`) checks this field before accepting a binding.
This means the DID document must assert the `actorId` it represents — the DID controller
must have explicitly declared the association. Without this, the binding is rejected.

## Decision 3 — `ActorIdentityBindingEntry` Is a Subclass, Not a Supplement

Supplements are metadata attached to existing entries. The DID/VC binding event is an
independent fact in the actor's lifecycle: it has its own timestamp, its own Merkle sequence
position, and its own lifecycle (revocation, expiry, replacement). Modelling it as a supplement
would attach it to a pre-existing entry, which is incorrect — the binding event itself is the
ledger fact.

`ActorIdentityBindingEntry` is modelled as a `LedgerEntry` subclass (JOINED inheritance),
following the same pattern as `KeyRotationEntry` (ADR 0012). Its `subjectId` is
`UUID.nameUUIDFromBytes(actorId.getBytes(UTF_8))` — the same deterministic derivation used
for key rotation entries, ensuring all lifecycle events for an actor share a stable subject
identifier.

## Decision 4 — Pipeline Ordering via `InjectableBean.getPriority()`

Arc proxies return `null` from `getClass().getAnnotation(Priority.class)`. The correct
approach is to resolve priority through the CDI `InjectableBean` handle:

```java
instance.handlesStream()
    .sorted(Comparator.comparingInt(h -> {
        if (h.getBean() instanceof InjectableBean<?> ib) return ib.getPriority();
        return Integer.MAX_VALUE;
    }))
```

All `LedgerEntryEnricher` implementations must carry `@Priority`. Implementations without
`@Priority` sort to `Integer.MAX_VALUE` (run last). The enricher pipeline order is:

1. `TraceIdEnricher` — populates `traceId` from active OTel span
2. `AgentSignatureEnricher` — signs canonical bytes
3. `AgentIdentityEnricher` — attaches `actorDid`, validates binding, sets `pendingIdentityStatus`
4. `ProvenanceCaptureEnricher` — attaches provenance supplement

## Decision 5 — ENFORCE Mode via Separate `@EntityListeners` Listener

The `LedgerEntryEnricher` contract forbids throwing checked or unchecked exceptions — enrichers
are called inside `@PrePersist` and a throw would roll back the entire transaction. ENFORCE
mode (reject entries with unverified identity) cannot be implemented inside the enricher.

ENFORCE is implemented as `LedgerIdentityEnforcementListener`, a separate JPA `@EntityListeners`
listener that reads `LedgerEntry.pendingIdentityStatus` (a `@Transient` field set by
`AgentIdentityEnricher`) and throws `IdentityVerificationException` when the status is
`UNVERIFIED` or `INVALID` and the mode is `ENFORCE`.

Constraints on this design:

- **ENFORCE is JPA-only.** `@EntityListeners` does not fire in `InMemoryLedgerEntryRepository`.
  Tests that exercise ENFORCE mode must use a JPA-backed store.
- **No sequence gap risk on rollback.** Callers compute sequence numbers via `SELECT MAX + 1`
  within the same transaction. A transaction rollback resets the sequence computation — the
  next successful persist will recompute correctly.

## Decision 6 — Binding Entry Persistence via Async CDI Observer in `REQUIRES_NEW`

Calling `entityManager.persist()` inside a `@PrePersist` callback of a different entity is
unsafe per the JPA specification — the persistence context is in an indeterminate state during
`@PrePersist` of the outer entity. Persisting a binding entry from within an enricher would
risk flushing partial state.

Instead, `AgentIdentityEnricher` fires an async CDI event (`ActorIdentityBindingRequired`)
after enrichment. A `@ObservesAsync @Transactional(REQUIRES_NEW)` observer in
`ActorIdentityBindingRecorder` persists the `ActorIdentityBindingEntry` in a separate
transaction. This decouples the binding entry's lifecycle from the triggering entry's
transaction.

## Decision 7 — SPI Module Placement Rule

SPIs that take and return simple value types (String, records, enums) and are expected to be
implemented by consumers belong in the `api` module. This keeps the implementation boundary
clean — consumers depend on `api`, not `runtime`.

SPIs tightly coupled to runtime types stay in `runtime`. `AgentSigner` (ADR 0014) takes
`byte[]` data but returns `AgentSignature`, which is a record in the `runtime` module — it
stays in `runtime`.

For the DID/VC subsystem:

| SPI | Module | Reason |
|-----|--------|--------|
| `DIDResolver` | `api` | Takes `String did`, returns `DIDDocument` (a record) |
| `VCValidator` | `api` | Takes `String vcJwt`, `String actorId`, returns `VCValidationResult` (an enum) |
| `AgentIdentityEnricher` | `runtime` | Coupled to `LedgerEntry` (runtime type) |

## Decision 8 — `IdentityBindingStatus` (Write-Path) vs `IdentityVerificationResult` (Read-Path)

Two distinct enums serve different concerns:

**`IdentityBindingStatus`** (write-path, stored on `LedgerEntry.pendingIdentityStatus` and
persisted on `ActorIdentityBindingEntry`): the result of the full validation pipeline at
persist time. Values: `BOUND`, `UNVERIFIED`, `INVALID`, `EXPIRED`. Stored in the database
as the definitive record of what was verified when the entry was written.

**`IdentityVerificationResult`** (read-path, computed): the result of verifying the DID↔key
relationship at query time, independent of the VC. Values: `VALID`, `INVALID`, `NO_DID`.
Computed on demand; not stored.

The split is intentional — write-path status reflects the full pipeline (DID document
resolution + VC validation + key binding); read-path verification reflects only the
cryptographic DID↔key check, which is cheap and repeatable.

## Decision 9 — Read Path Verifies DID↔Key Only

VC re-validation on the read path is not performed. Re-running the full credential check
(JWT signature verification, issuer trust, schema validation, `validUntil` check) at query
time is expensive and may require network calls. The read path
(`AgentSignatureVerificationService.verifyAgentSignature`) computes `IdentityVerificationResult`
from the stored `actorDid` and `agentPublicKey` fields — no network, no VC.

Consumers that need VC validation results for a given actor must query
`ActorIdentityBindingRepository.latestBindingFor(actorId)` and inspect the stored
`IdentityBindingStatus`.

## Decision 10 — Issue #103 as Required Upstream Dependency

Cache invalidation on key rotation currently uses a direct call from
`KeyRotationService.recordRotation()` into the `AgentIdentityCache`. This couples the key
rotation subsystem to the identity subsystem.

Issue #103 will replace this direct call with a CDI event-driven mechanism: `KeyRotationService`
fires a `KeyRotatedEvent`; the identity cache observes it and invalidates the relevant entry.
Until #103 is delivered, the direct call is used and the coupling is accepted as a known
technical debt.

## Decision 11 — Known Gap: VC `validUntil`-Bounded TTL Cache

When `JwtVCValidator` (#108) is implemented, the VALID credential cache TTL must be bounded
by `min(configuredTtl, vc.validUntil - now())`. A credential that is valid now but expires in
30 minutes must not be cached for the full configured TTL (e.g. 5 minutes), as the cached
result would be served after expiry.

`EXPIRED` results must not be cached at all — the next check may find a renewed credential.

This is not implemented in the current scope. The cache uses the configured TTL without
`validUntil` clamping. A follow-up issue will track this.

## Consequences

- **All `LedgerEntryEnricher` implementations must carry `@Priority`.** Without it, Arc proxy
  resolution falls through to `Integer.MAX_VALUE` and ordering is non-deterministic.
- **ENFORCE mode requires JPA.** It is not available in test-only in-memory mode
  (`InMemoryLedgerEntryRepository`). Integration tests for ENFORCE must use a JPA-backed store.
- **Two-enum split requires consumer awareness.** Consumers must know which result type to
  check for which operation: `IdentityBindingStatus` for write-path queries,
  `IdentityVerificationResult` for read-path verification.
- **`did:web` resolvers (`WebDIDResolver`) require HTTPS and SSRF protection configuration.**
  DID document resolution fetches an external URL. Operators must configure allowlists or a
  proxy to prevent SSRF. This is a deployment concern, not a library concern.
- **Binding entries consume Merkle sequence positions.** `ActorIdentityBindingEntry` is
  a full ledger entry with its own sequence number under the actor's `subjectId`. This is
  correct — binding events are first-class facts — but consumers should be aware that the
  Merkle sequence for an actor's `subjectId` includes both operational entries and lifecycle
  events (key rotation, identity binding).

## Related

- ADR 0004 — LLM agent identity model (versioned persona names, `actorId` semantics)
- ADR 0005 — LedgerEntry Enricher SPI (`LedgerEntryEnricher` contract)
- ADR 0011 — per-actorId signing key model
- ADR 0012 — key rotation design (`KeyRotationEntry` subclass pattern)
- ADR 0014 — AgentSigner SPI
