# ADR 0013 — Post-Quantum Algorithm Migration Path for Agent Signing

**Status:** Accepted
**Date:** 2026-05-23
**Issue:** casehubio/ledger#84

## Context

Agent signing (ADR 0011) uses Ed25519 for bilateral ledger entry signing and Merkle checkpoint signing. Ed25519 is secure against classical adversaries but vulnerable to Shor's algorithm on a cryptographically relevant quantum computer (CRQC). The "Harvest Now, Decrypt Later" (HNDL) threat is real: adversaries record signed entries today to forge or repudiate signatures once quantum capability matures. NIST SP 800-57 Rev.6 addresses this with two post-quantum (PQC) signature standards finalized in August 2024: FIPS 204 (ML-DSA / CRYSTALS-Dilithium) and FIPS 205 (SLH-DSA / SPHINCS+).

The question is: when to adopt ML-DSA, how to transition, and what code changes are needed.

## What Is Already Algorithm-Agnostic

Before deciding what to change, it is important to note what is not hardcoded:

- **`SigningKey` record** — holds `KeyPair`, computes `keyRef = Base64URL(SHA-256(publicKey.getEncoded()))`. SHA-256 of the raw encoded bytes makes `keyRef` algorithm-independent.
- **`AgentKeyProvider` SPI** — returns `Optional<SigningKey>`. No algorithm assumption in the contract.
- **`LedgerEntry.agentPublicKey`** — stored as `byte[]` in X.509 SubjectPublicKeyInfo DER format, which embeds the algorithm OID. A verifier can detect the algorithm from these bytes.
- **Key rotation mechanism** — `KeyRotationEntry` records `previousKeyRef` → `newKeyRef`. A rotation from an Ed25519 key to an ML-DSA key is structurally identical to any other rotation.

## What Is Hardcoded Ed25519 Today

Three call sites hardcode the algorithm string:

1. `AgentSignatureEnricher.sign()` — `Signature.getInstance("Ed25519")`
2. `AgentCryptographicVerifier.verifyCryptographic()` — `KeyFactory.getInstance("Ed25519")` and `Signature.getInstance("Ed25519")`
3. `LedgerMerklePublisher.signCheckpoint()` — `Signature.getInstance("Ed25519")`

## Decisions

### 1. Make the Signing Path Algorithm-Transparent Now

The three hardcoded call sites are removed in this session. The signing algorithm is derived from the key itself:

- **Signing** (`AgentSignatureEnricher`, `LedgerMerklePublisher`): `privateKey.getAlgorithm()` returns the algorithm name that `Signature.getInstance()` accepts. For Ed25519 keys this returns `"Ed25519"` — no behaviour change today.

- **Verification** (`AgentCryptographicVerifier`): the stored `agentPublicKey` bytes (X.509 DER) embed the algorithm OID. A `loadPublicKey(byte[])` helper tries the supported algorithms in order until one succeeds, then uses `pub.getAlgorithm()` for the `Signature` instance. For Ed25519 entries this behaves identically to today.

The supported algorithm list is `["Ed25519", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87"]`. `ML-DSA-*` entries throw `NoSuchAlgorithmException` (caught silently) on JVMs without ML-DSA support — forward-compatible with zero runtime cost today.

No schema changes. No SPI changes. The `agentPublicKey` bytes already carry the algorithm information.

`LedgerPemUtil.loadPrivateKey()` and `LedgerPemUtil.loadPublicKey()` apply the same trial-load approach for PEM files, so ML-DSA PEM files configured in `casehub.ledger.agent-signing.keys.*` and `casehub.ledger.merkle.publish.private-key` load correctly once BouncyCastle is present — no further operator-visible changes required.

### 2. Transition via Key Rotation — No Dual-Signing

When an agent switches from an Ed25519 key to an ML-DSA key:

1. Generate a new ML-DSA key pair (operator action).
2. Call `KeyRotationService.recordRotation()` with `reason = SCHEDULED`.
3. Configure `AgentKeyProvider` to return the new ML-DSA `SigningKey` for that actorId.

From that point, new entries are signed with ML-DSA. Old entries signed with the Ed25519 key remain `VALID` — `AgentCryptographicVerifier` detects Ed25519 from the stored public key bytes and verifies correctly. `keyRef` values change because the key material changes; this is expected and handled by the key rotation mechanism.

**Dual-signing is rejected.** Adding `agentSignature2`/`agentPublicKey2` fields would double the stored key material, require schema changes, and complicate verification logic for no benefit — old entries are already self-verifying via stored public key bytes.

### 3. Activation Trigger for ML-DSA Key Generation

Adoption of ML-DSA is not mandatory today. The trigger is:

> **Activate ML-DSA key generation when BouncyCastle ≥ 1.79 is present in the Quarkus dependency tree** (it ships FIPS 204 support). Existing Ed25519 key material continues indefinitely. No forced rotation.

The code already supports ML-DSA once BouncyCastle is available — no further code changes needed at adoption time.

Urgency classification:
- **Low** for most deployments: no cryptographically relevant quantum computers exist today.
- **Medium** for entries with ≥ 15-year confidentiality or non-repudiation requirements (EU AI Act audit records fall here by regulation).
- **High** if NIST or CISA issues a CRQC-ready timeline of < 5 years: rotate all active keys immediately.

Review this ADR and trigger rotation plans if a credible sub-5-year CRQC timeline is announced.

### 4. LedgerMerklePublisher Checkpoint Signing — Deferred

`LedgerMerklePublisher` now uses `privateKey.getAlgorithm()` (same mechanical fix as signing). However, the tlog-checkpoint format (`io.casehub.ledger/v1\n...`) does not embed the signing algorithm. A checkpoint verifier must know the algorithm out-of-band.

Full PQC for Merkle checkpoints requires a checkpoint format revision that includes the algorithm identifier in the checkpoint header. This is a separate concern tracked in the checkpoint format specification and deferred from this ADR.

## Consequences

- `AgentSignatureEnricher`, `AgentCryptographicVerifier`, `LedgerMerklePublisher`, and `LedgerPemUtil` no longer hardcode `"Ed25519"` — they derive the algorithm from the key material or detect it via trial-load.
- ML-DSA support activates automatically when BouncyCastle ≥ 1.79 is on the classpath.
- Operators adopt ML-DSA by generating new keys and recording a key rotation — no code changes required at adoption time.
- Historical entries with Ed25519 signatures remain verifiable indefinitely — the stored public key bytes carry the algorithm OID.
- Checkpoint PQC is deferred pending a format revision.

## Related

- ADR 0011 — per-actorId signing key model
- ADR 0012 — key rotation design (the transition mechanism)
- #81 — agent DID/VC identity binding
- #85 — external key distribution (HSM/TUF/PKI)
- NIST FIPS 204 (ML-DSA), finalized August 2024
- NIST SP 800-57 Rev.6
