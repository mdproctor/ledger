# ADR 0014 — AgentSigner SPI: Signing Responsibility Belongs in the SPI

**Status:** Accepted  
**Date:** 2026-05-29  
**Issue:** casehubio/ledger#85

## Context

ADR 0011 established per-actorId key pairs via `AgentKeyProvider.signingKey(actorId) →
Optional<SigningKey>`. `AgentSignatureEnricher` then performed local JCA signing with the
returned `KeyPair`. This model assumes the caller can access the private key — it breaks
for:

- **Vault Transit** — private key never leaves Vault; signing is a remote API call
- **Cloud KMS** (AWS KMS, GCP KMS, Azure Key Vault) — same remote-signing model
- **HSMs via non-JCA API** — hardware signing requires an API call, not local `Signature.getInstance()`

PKCS#11-backed HSMs that expose a JCA `Provider` return a `PrivateKey` handle that routes
signing into hardware without exporting key material — these are compatible with `AgentKeyProvider`.
The incompatible case is remote REST-based signers (Vault Transit, Cloud KMS).

## Decision

Replace `AgentKeyProvider` with `AgentSigner`:

```java
public interface AgentSigner {
    Optional<AgentSignature> sign(String actorId, byte[] data);
}

public record AgentSignature(byte[] signature, byte[] publicKey, String keyRef) { ... }
```

`AgentSignatureEnricher` calls `signer.sign()` and receives the complete result —
signature bytes, X.509 public key, and keyRef. The signing responsibility moves from
the enricher into the SPI.

## Two-Tier Implementation Structure

**`ConfiguredAgentSigner`** (`@DefaultBean`) — replaces `ConfiguredAgentKeyProvider`.
Direct implementation, NOT extending the abstract base. Eager `@PostConstruct` PEM loading
preserves startup-time misconfiguration visibility. Signs locally via `AgentSignature.signWith(KeyPair, byte[])`.

**`AbstractCachingAgentSigner<C>`** — abstract base for external providers with network/hardware overhead.
Per-actorId context cache (`ConcurrentHashMap<String, Optional<C>>`). Template methods:
`loadContext(actorId) → Optional<C>` (empty = not configured, cached; throw = transient error, not cached)
and `performSign(actorId, context, data) → AgentSignature`. Invalidation hooks: `invalidateAll()`,
`invalidate(actorId)`.

## Vault Transit Reference Implementation

`VaultTransitAgentSigner` in `examples/vault-transit-signing/` extends `AbstractCachingAgentSigner<VaultTransitContext>`.
`loadContext`: `GET /v1/transit/keys/<key-name>` → parses public key, caches.
`performSign`: `POST /v1/transit/sign/<key-name>` → strips `vault:v1:` prefix, returns `AgentSignature`.
Scheduled `invalidateAll()` for bounded-staleness refresh (default 5m).

## Breaking Change

`AgentKeyProvider`, `SigningKey`, and `ConfiguredAgentKeyProvider` are deleted. This is
intentional — there are no external consumers of `casehub-ledger` 0.2-SNAPSHOT.

## Extends

ADR 0011 — the per-actorId key model is unchanged and correct. This ADR replaces only the
SPI contract that implements it. The `signWith(KeyPair, byte[])` factory on `AgentSignature`
is algorithm-transparent (derives algorithm from `keyPair.getPrivate().getAlgorithm()`),
consistent with protocol PP-20260523-e7b577.

## Deferred

- #101 Vault AppRole/OIDC auth  
- #102 Cloud KMS adapters (AWS KMS, GCP, Azure)  
- #103 Rotation-triggered cache invalidation via CDI event  
- #104 `InMemoryAgentSigner` in `persistence-memory/`

## Related

- ADR 0011 — per-actorId key model
- ADR 0012 — key rotation design
- ADR 0013 — post-quantum algorithm migration
- Protocol PP-20260523-e7b577 — algorithm-transparent signing
