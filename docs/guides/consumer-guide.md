# casehub-ledger -- Consumer Guide

> Domain-agnostic, immutable, cryptographically tamper-evident audit ledger for any Quarkus application.

**GitHub:** [casehubio/casehub-ledger](https://github.com/casehubio/casehub-ledger)
**Tier:** Foundation

---

## Purpose

Zero knowledge of business domain. Consumers extend it; it never extends them. Any Quarkus app adds `io.casehub:casehub-ledger` as a dependency and immediately gets:

- Immutable append-only audit log (`LedgerEntry` base entity with JPA JOINED inheritance)
- Merkle Mountain Range tamper evidence (RFC 9162 stored frontier -- O(log N) inclusion proofs, Ed25519 signed checkpoints)
- Peer attestation (`LedgerAttestation` -- verdicts SOUND / FLAGGED / ENDORSED / CHALLENGED, confidence scores, capability-scoped and dimension-scoped)
- EigenTrust reputation (`TrustScoreComputer` -- nightly batch, exponential decay weighting, asymmetric valence for negative attestations)
- Provenance tracking (`ProvenanceSupplement` -- sourceEntityId / sourceEntityType / sourceEntitySystem + optional agentConfigHash)
- Decision context snapshots (GDPR Article 22 / EU AI Act Article 12 compliance via `ComplianceSupplement`)
- Actor pseudonymisation (token-based GDPR Art.17 erasure with tamper-evident receipts)
- Consumer-provided metadata (freeform JSON, tamper-evident, configurable size limit)
- Cloud KMS agent signing (AWS KMS, GCP Cloud KMS, Azure Key Vault, HashiCorp Vault Transit)

---

## Modules to Depend On

| Module | Artifact ID | When to use |
|--------|-------------|-------------|
| `api/` | `casehub-ledger-api` | Pure-Java SPIs and model types -- no JPA, no Quarkus framework deps. Two-tier `LedgerEntry` model: `@MappedSuperclass` base in `api/`, `JpaLedgerEntry` entity in `runtime/`. Contains `LedgerAppender`, `OutcomeRecorder`, `LedgerEntryRepository`, `TrustScoreSource`, `ActorIdentityProvider`, `LedgerTraceIdProvider` SPIs and the `AuditRecord` / `OutcomeRecord` value types. |
| `runtime/` | `casehub-ledger` | Full Quarkus extension: JPA entities, services, Flyway migrations, CDI beans, enricher pipeline, trust computation, privacy/erasure. |
| `deployment/` | `casehub-ledger-deployment` | Quarkus build-time augmentation. Includes `LedgerProcessor` which enforces `domainContentBytes()` override on subclasses with persistent join-table fields. |
| `persistence-memory/` | `casehub-ledger-memory` | Zero-datasource in-memory `@Alternative @Priority(1)` implementations of all persistence SPIs -- for `@QuarkusTest` isolation and ephemeral installs. Add as `compile`-scope dependency to activate. Includes: `InMemoryLedgerEntryRepository`, `InMemoryCrossTenantLedgerEntryRepository`, `InMemoryActorTrustScoreRepository`, `InMemoryLedgerMerkleFrontierRepository`, `InMemoryKeyRotationRepository`, `InMemoryErasureReceiptRepository`, `InMemoryActorIdentityBindingRepository`, `InMemoryAgentSigner`, `NoOpCurrentPrincipal`. |
| `rest/` | `casehub-ledger-rest` | JAX-RS REST API for ledger queries, attestations, Merkle verification, and trust scores -- opt-in via explicit dependency. Base path: `/api/v1/ledger/`. OpenAPI-annotated. |
| `testing/` | `casehub-ledger-testing` | `NoOpLedgerEntryRepository` -- `@Alternative @Priority(1)` implementation that returns empty results for all queries and passes through saves unchanged. For consumer `@QuarkusTest` tests that do not need real persistence. Activate via `quarkus.arc.selected-alternatives`. |
| `signing/` | (reactor POM) | Cloud-managed Ed25519 signing adapters. Each cloud provider has a pure Java module (framework-free) and a Quarkus CDI adapter module. See Cloud KMS Signers section below. |
| `annotations/` | `casehub-ledger-annotations` / `casehub-ledger-annotations-deployment` | Annotation-driven audit: `@Audited`, `@Attested`, `@ComplianceSupplement`. Quarkus extension with build-time validation. See Annotation-Driven Audit section below. |
| `graphql/` | `casehub-ledger-graphql` | GraphQL resolvers and MCP domain provider. Query/mutation resolvers for ledger entries, attestations, trust scores, and Merkle verification. Opt-in via explicit dependency. |
| `reporting/` | `casehub-ledger-reporting` | Opt-in compliance reporting: Qute HTML templates, PDF rendering via platform `PdfGenerator` SPI, content negotiation (`OutputFormat`, `ReportMediaType`). Renders `ComplianceReport` and `AuditTrailExport` to JSON/CSV/HTML/PDF. Add `platform-pdf` to classpath for PDF output. |
| `examples/` | (reactor POM) | Runnable example applications demonstrating each ledger capability. Not deployed. `maven.deploy.skip=true`. |
| `consumer-compat-test/` | `casehub-ledger-consumer-compat-test` | Boot guard for CDI graph integrity. Standalone POM (not a child of ledger parent). Single `@QuarkusTest` with empty body -- if CDI boots with no persistence infrastructure and no `quarkus.arc.exclude-types`, every injection point is satisfied by `@DefaultBean` no-ops. `maven.deploy.skip=true`. |

---

## Key Abstractions

### Core Model

| Concept | Class/Interface | Role |
|---|---|---|
| Ledger entry | `LedgerEntry` (api, `@MappedSuperclass`) | Abstract base for all tamper-evident audit records. Core fields: `id`, `subjectId`, `tenancyId`, `sequenceNumber`, `entryType`, `actorId`, `actorType`, `actorRole`, `occurredAt`, `digest`, `traceId`, `causedByEntryId`, `agentSignature`, `agentPublicKey`, `agentKeyRef`, `actorDid`, `metadata`, `supplementJson`. |
| JPA ledger entry | `JpaLedgerEntry` (runtime) | JPA `@Entity` subclass with `@Inheritance(JOINED)`, `@DiscriminatorColumn`, `@NamedQuery` annotations, and `@EntityListeners`. Consumers extend this class. |
| Plain entry | `PlainLedgerEntry` (runtime) | Concrete `JpaLedgerEntry` subclass for domain-agnostic EVENT writes via `OutcomeRecorder`. No domain-specific fields. Discriminator: `PLAIN`. Table: `plain_ledger_entry`. |
| Attestation | `LedgerAttestation` (api, `@MappedSuperclass`) | Peer verdict record: `attestorId`, `attestorType`, `attestorRole`, `verdict` (SOUND / FLAGGED / ENDORSED / CHALLENGED), `confidence`, `capabilityTag`, `trustDimension`, `dimensionScore`, `evidence`. |
| Trust score | `ActorTrustScore` (runtime, `@Entity`) | Per-actor trust score keyed by `(actorId, capabilityKey, dimensionKey)` triple. Four `ScoreType` values: `GLOBAL`, `CAPABILITY`, `DIMENSION`, `CAPABILITY_DIMENSION`. Carries Bayesian Beta parameters: `alpha`, `beta`, `trustScore`, `decisionCount`, `overturnedCount`, `attestationPositive`, `attestationNegative`, `globalTrustScore` (EigenTrust), `lastComputedAt`. |
| Merkle frontier | `LedgerMerkleFrontier` (runtime, `@Entity`) | Stored MMR frontier enabling incremental Merkle tree operations per (subject, tenant). |
| Actor identity | `ActorIdentity` (runtime, `@Entity`) | Token-to-identity mapping for pseudonymisation. |
| Identity binding | `ActorIdentityBindingEntry` (runtime, `@Entity`) | First-class ledger entry recording DID binding validation events. Discriminator: `IDENTITY_BINDING`. Table: `actor_identity_binding_entry`. |
| Key rotation | `KeyRotationEntry` (runtime, `@Entity`) | First-class ledger entry recording signing key rotation/revocation. Discriminator: `KEY_ROTATION`. Table: `key_rotation_entry`. |
| Erasure receipt | `ErasureReceiptLedgerEntry` (runtime, `@Entity`) | Tamper-evident GDPR Art.17 erasure record. Opt-in via `casehub.ledger.erasure-receipt.enabled=true`. Discriminator: `ERASURE_RECEIPT`. Table: `erasure_receipt_entry`. |
| Archive record | `LedgerEntryArchiveRecord` (runtime, `@Entity`) | Self-contained archive of entries removed by retention enforcement. Table: `ledger_entry_archive`. |
| Entry type | `LedgerEntryType` (api, enum) | `COMMAND` (intent), `EVENT` (fact), `ATTESTATION` (peer review). |
| Verdict | `AttestationVerdict` (api, enum) | `SOUND` (positive), `FLAGGED` (negative/needs review), `ENDORSED` (explicit positive), `CHALLENGED` (formal dispute). |
| Score type | `ScoreType` (api, enum) | `GLOBAL`, `CAPABILITY`, `DIMENSION`, `CAPABILITY_DIMENSION`. |
| Capability tag | `CapabilityTag` (api) | Constants including `CapabilityTag.GLOBAL` for non-capability-scoped attestations. |
| Erasure reason | `ErasureReason` (api, enum) | `GDPR_ART_17_REQUEST`, `RETENTION_EXPIRED`, `ACCOUNT_DELETION`. |
| Key rotation reason | `KeyRotationReason` (api, enum) | Rotation/revocation reason for `KeyRotationEntry`. |
| Trust score snapshot | `TrustScoreSnapshot` (runtime, `@Entity`) | Point-in-time trust score record for trajectory visibility. Four `ScoreType` values, keyed by `(actorId, scoreType, capabilityTag, dimensionKey)`. Captured by `PerActorTrustComputer` on every score upsert. Query via `TrustScoreSnapshotRepository`. |
| Attestation summary | `AttestationSummary` (api, record) | Verdict counts and confidence statistics for aggregate attestation queries. Returned by `LedgerEntryRepository.summariseAttestationsByActor()`. |

### Supplements (Optional Attachments)

Attached via `entry.attach(supplement)`. Zero overhead when not used -- supplement tables are only written when a supplement is actually attached.

| Supplement | Class | Purpose |
|---|---|---|
| `ComplianceSupplement` | `ComplianceSupplement` (api, `@MappedSuperclass`); `JpaComplianceSupplement` (runtime, `@Entity`) | GDPR Art.22 / EU AI Act Art.12 decision fields: `planRef`, `rationale`, `evidence`, `detail`, `decisionContext` (JSON snapshot), `algorithmRef`, `confidenceScore`, `contestationUri`, `humanOverrideAvailable`. |
| `ProvenanceSupplement` | `ProvenanceSupplement` (api, `@MappedSuperclass`); `JpaProvenanceSupplement` (runtime, `@Entity`) | Data lineage: `sourceEntityId`, `sourceEntityType`, `sourceEntitySystem`. LLM agent config drift detection: `agentConfigHash` (SHA-256 hex). |

### Write Path SPIs (in `casehub-ledger-api`)

These are the primary entry points for consumers writing to the ledger.

| SPI | Purpose | Usage |
|---|---|---|
| `LedgerAppender` | Value-type write SPI accepting `AuditRecord`. Returns UUID of persisted entry. Does not support ATTESTATION entries. | `appender.append(AuditRecord.event(actorId, subjectId), tenancyId)` |
| `OutcomeRecorder` | Combined write: creates both a `LedgerEntry` (EVENT) and a `LedgerAttestation` atomically in one transaction. | `recorder.record(OutcomeRecord.of(actorId, subjectId, capabilityTag, verdict, confidence))` |

### Value Types for Writes

| Type | Purpose | Factory Methods |
|---|---|---|
| `AuditRecord` (api, record) | Input for `LedgerAppender`. Immutable. Requires `actorId` and `subjectId`. Rejects `ATTESTATION` entry type at construction. Defaults to `ActorType.AGENT` and `LedgerEntryType.EVENT`. | `AuditRecord.event(actorId, subjectId)` then `.withActorRole()`, `.withCausedBy()`, `.withOccurredAt()`, `.withMetadata()` |
| `OutcomeRecord` (api, record) | Input for `OutcomeRecorder`. Immutable. Requires `actorId`, `subjectId`, `verdict`, `confidence` (0,1], `capabilityTag`. | `OutcomeRecord.of(actorId, subjectId, capabilityTag, verdict, confidence)` or `.ofGlobal(...)`. Then `.withActorRole()`, `.withActorType()`, `.withOccurredAt()`, `.withAttestor()`, `.withMetadata()`. |

### Persistence SPIs

| SPI | Location | Default | Built-in Alternatives | Purpose |
|---|---|---|---|---|
| `LedgerEntryRepository` | api | `NoOpLedgerEntryRepository` (`@DefaultBean`) | JPA: `JpaLedgerEntryRepository`; memory: `InMemoryLedgerEntryRepository` (`@Alternative @Priority(1)`) | Persist and query ledger entries and attestations. Tenant-scoped (every method takes `tenancyId`). Includes streaming (`streamBySubjectId`, `streamByActorId`), cursor-based pagination (`findBySubjectIdPaged`), and aggregate queries (`countByActorAndVerdict`, `countBySubjectAndVerdict`, `summariseAttestationsByActor`). |
| `CrossTenantLedgerEntryRepository` | runtime | — | JPA: `JpaCrossTenantLedgerEntryRepository` (`@CrossTenant` qualifier); memory: `InMemoryCrossTenantLedgerEntryRepository` | Cross-tenant reads for trust computation, health checks, retention, and compliance export. Must use `@CrossTenant` CDI qualifier for injection. |
| `LedgerMerkleFrontierRepository` | runtime | `NoOpLedgerMerkleFrontierRepository` (`@DefaultBean`) | JPA: `JpaLedgerMerkleFrontierRepository` (`@Alternative`); memory: `InMemoryLedgerMerkleFrontierRepository` | Read/replace the per-(subject, tenant) Merkle MMR frontier. |
| `ActorTrustScoreRepository` | runtime | `NoOpActorTrustScoreRepository` (`@DefaultBean`) | JPA: `JpaActorTrustScoreRepository` (`@Alternative`); memory: `InMemoryActorTrustScoreRepository` | Persist and query trust scores. Includes batch methods. |
| `ErasureReceiptRepository` | runtime | `NoOpErasureReceiptRepository` (`@DefaultBean`) | JPA: `JpaErasureReceiptRepository` (`@Alternative`); memory: `InMemoryErasureReceiptRepository` | Query erasure receipt entries by actor/tenant. `countByTenant(tenancyId)`. |
| `KeyRotationRepository` | runtime | — | JPA: `JpaKeyRotationRepository`; memory: `InMemoryKeyRotationRepository` | Query key rotation entries by actor. `findCompromisedByActorIdAndKeyRef()` is cross-tenant. |
| `ActorIdentityBindingRepository` | runtime | `NoOpActorIdentityBindingRepository` (`@DefaultBean`) | JPA: `JpaActorIdentityBindingRepository`; memory: `InMemoryActorIdentityBindingRepository` | Query DID identity binding entries by actor/tenant. |
| `TrustScoreSnapshotRepository` | runtime | `NoOpTrustScoreSnapshotRepository` (`@DefaultBean`) | JPA: `JpaTrustScoreSnapshotRepository`; memory: `InMemoryTrustScoreSnapshotRepository` | Save and query trust score snapshots. `findGlobalSnapshots`, `findCapabilitySnapshots`, `findDimensionSnapshots`, `findByActorAndTimeRange`, `deleteOlderThan`. |

### Other SPIs

| SPI | Location | Default | Purpose |
|---|---|---|---|
| `TrustScoreSource` | api | `MaterializedTrustScoreSource` (`@DefaultBean`) | On-read trust score retrieval. Three implementations: `MaterializedTrustScoreSource` (reads from `ActorTrustScoreRepository`), `CachedTrustScoreSource` (wraps materialized with TTL cache), `ComputedTrustScoreSource` (computes on demand from raw attestation history). Batch methods: `scoresFor()`, `decisionCountsFor()`. |
| `ActorIdentityProvider` | api | pass-through (`@DefaultBean`) | Tokenise / resolve / erase actor identities (GDPR). Only `ActorType.HUMAN` actors are tokenised; SYSTEM and AGENT actors pass through unchanged. Built-in `InternalActorIdentityProvider` activates when `casehub.ledger.identity.tokenisation.enabled=true`. |
| `DecisionContextSanitiser` | runtime | `PassThroughDecisionContextSanitiser` (no-op) | Sanitise PII from `ComplianceSupplement.decisionContext` JSON before storage. |
| `LedgerTraceIdProvider` | api | `OtelTraceIdProvider` (reads from active OpenTelemetry span) | Override OTel trace ID extraction. |
| `AgentSigner` | runtime | `ConfiguredAgentSigner` (reads PEM keys from config) | Ed25519 signing of entry canonical bytes. Return `Optional.empty()` for actors not configured for signing. Cloud KMS adapters implement this. |
| `LedgerEntryEnricher` | runtime | (multiple built-in enrichers) | Auto-populate fields on `LedgerEntry` at persist time, before hashing and signing. Priority-ordered. Consumer enrichers are CDI-discovered. See Enricher Pipeline section. |
| `TrustImportService` | runtime | `NoOpTrustImportService` | Import trust scores from an external `TrustExportPayload`. Built-in alternative: `JpaTrustImportService` (seed-if-absent). |
| `TrustBootstrapSource` | runtime | `NoOpTrustBootstrapSource` (returns empty -- Beta(1,1) prior) | Fetch prior trust data for first-time actors from an external source. |
| `AttestorCredibilityPolicy` | api | `DefaultAttestorCredibilityPolicy` (all attestors equally credible) | Weight attestor credibility in trust computation. Override to discount low-trust attestors. |

### Metadata Field

`LedgerEntry.metadata` -- consumer-provided freeform JSON context (`TEXT` column). Included in `canonicalBytes()` for tamper evidence. Must be valid JSON, must NOT contain PII. Propagated through the write path via `AuditRecord.withMetadata(String)` and `OutcomeRecord.withMetadata(String)`. Config: `casehub.ledger.metadata.max-size` (default 65536).

---

## Consumer Pattern

How to extend the ledger for your domain:

1. **Extend `JpaLedgerEntry`** (not `LedgerEntry`) as a JPA `@Entity` with `@DiscriminatorValue` and `@Table`
2. **Add a Flyway migration** (V1012+ range) for the subclass join table
3. **Wire a CDI observer** to capture domain events as ledger entries
4. **Optionally attach** `JpaComplianceSupplement` or `JpaProvenanceSupplement`
5. **Optionally provide metadata** via `.withMetadata()` on `AuditRecord` or `OutcomeRecord`

### Leaf Hash Requirement

Subclasses with persistent join-table fields MUST override `domainContentBytes()` -- build-time enforcement via `LedgerProcessor` produces a deployment error if they do not. The leaf hash is `SHA-256(0x00 | canonicalBytes)` per RFC 9162.

### Existing Consumers

| Consumer | Subclass | Subclass table | subject_id maps to |
|---|---|---|---|
| `casehub-work` | `WorkItemLedgerEntry` | `work_item_ledger_entry` | WorkItem UUID |
| `casehub-qhorus` | `MessageLedgerEntry` | `message_ledger_entry` | Channel UUID |
| `casehub-engine` | `CaseLedgerEntry` | (engine-specific) | Case UUID |

---

## Enricher Pipeline

The `LedgerEnricherPipeline` runs all `LedgerEntryEnricher` implementations in `@Priority` order before hashing and signing. The full save pipeline is: **enrichment -> digest (leafHash) -> agent signature -> persist**.

Built-in enrichers (ascending priority):

| Priority | Enricher | Purpose |
|---|---|---|
| 10 | `TraceIdEnricher` | Populates `traceId` from `LedgerTraceIdProvider` (OTel by default) |
| 30 | `ProvenanceCaptureEnricher` | Attaches `ProvenanceSupplement` from CDI context when available |
| 35 | `ComplianceSupplementEnricher` | Attaches `ComplianceSupplement` from ThreadLocal context (standalone `@ComplianceSupplement` or `@Audited`) |
| 40 | `ActorDIDEnricher` | Populates `actorDid` from the platform `ActorDIDProvider` SPI |
| 50 | `ActorIdentityValidationEnricher` | Fires DID/VC identity validation and records binding events |

Consumer enrichers: implement `LedgerEntryEnricher`, annotate with `@ApplicationScoped` and `@Priority`. Must be idempotent and non-fatal (exceptions are logged and swallowed). Must not overwrite fields set by the save pipeline (`subjectId`, `sequenceNumber`, `tenancyId`, `occurredAt`, `metadata`).

---

## REST API (casehub-ledger-rest)

Opt-in module. Add `casehub-ledger-rest` dependency to expose these JAX-RS endpoints. All endpoints are OpenAPI-annotated.

### Ledger Entries -- `/api/v1/ledger/entries`

| Method | Path | Purpose | Parameters |
|---|---|---|---|
| `GET` | `/api/v1/ledger/entries` | Query entries by subject or actor | `subjectId` (UUID), `actorId` (string), `tenancyId` (required), `from`/`to` (Instant) |
| `GET` | `/api/v1/ledger/entries/{id}` | Get single entry by ID | `tenancyId` (required) |
| `GET` | `/api/v1/ledger/entries/{id}/caused-by` | Get entries causally triggered by this entry | `tenancyId` (required) |

### Attestations -- `/api/v1/ledger/entries/{entryId}/attestations`

| Method | Path | Purpose | Parameters |
|---|---|---|---|
| `GET` | `/api/v1/ledger/entries/{entryId}/attestations` | List attestations for an entry | `tenancyId` (required), `capabilityTag` (optional filter) |
| `POST` | `/api/v1/ledger/entries/{entryId}/attestations` | Create attestation on an entry | `tenancyId` (required), JSON body: `attestorId`, `attestorType`, `verdict`, `confidence`, `capabilityTag`, `evidence`, `attestorRole`, `trustDimension`, `dimensionScore` |

### Merkle Verification -- `/api/v1/ledger/verify`

| Method | Path | Purpose | Parameters |
|---|---|---|---|
| `GET` | `/api/v1/ledger/verify` | Verify integrity of all entries for a subject | `subjectId` (required), `tenancyId` (required) |
| `GET` | `/api/v1/ledger/verify/entries/{entryId}/proof` | Get Merkle inclusion proof for a single entry | `tenancyId` (required) |

### Trust Scores -- `/api/v1/ledger/trust`

| Method | Path | Purpose | Parameters |
|---|---|---|---|
| `GET` | `/api/v1/ledger/trust/{actorId}` | Get all trust scores for an actor (global, capabilities, dimensions) | — |
| `GET` | `/api/v1/ledger/trust/{actorId}/capability/{capabilityTag}` | Get capability-specific score, decision count, and quality dimensions | — |

---

## Cloud KMS Signers

Cloud-managed Ed25519 signing lives in the `signing/` reactor. Each provider has a **pure Java module** (no framework deps -- usable from `main()`) and a **Quarkus CDI adapter** module that implements `AgentSigner`.

| Pure Java Module | Quarkus Adapter | Cloud Provider | Key Classes |
|---|---|---|---|
| `signing/vault-transit` | `signing/vault-transit-quarkus` | HashiCorp Vault Transit | `VaultTransitSigningClient`, `VaultTokenSource` SPI, `AppRoleVaultTokenSource`, `JwtVaultTokenSource`, `StaticVaultTokenSource`, `LoginBasedVaultTokenSource` |
| `signing/aws-kms` | `signing/aws-kms-quarkus` | AWS KMS | `AwsKmsSigningClient`, `AwsKmsContext`, `AwsKmsSigningConfig`, `AwsKmsAgentSigner` |
| `signing/gcp-kms` | `signing/gcp-kms-quarkus` | GCP Cloud KMS | `GcpKmsSigningClient`, `GcpKmsClientWrapper`, `GcpKmsContext`, `GcpKmsAgentSigner` |
| `signing/azure-keyvault` | `signing/azure-keyvault-quarkus` | Azure Key Vault | `AzureKeyVaultSigningClient`, `AzureKeyVaultClientWrapper`, `AzureKeyVaultContext`, `EcSignatureConverter`, `AzureKeyVaultAgentSigner` |

**`VaultTokenSource` SPI** (`io.casehub.ledger.signing.vault`): `token()` and `invalidate()`. Three implementations extend `LoginBasedVaultTokenSource` (abstract -- lazy login with lease-aware TTL, 30s buffer before expiry): `AppRoleVaultTokenSource`, `JwtVaultTokenSource` (consolidates Kubernetes auth -- accepts any JWT source including OIDC, federated identity), `StaticVaultTokenSource` (constant token, no-op invalidate).

**403-retry:** `VaultTransitAgentSigner` (Quarkus adapter) catches `VaultAuthenticationException` on both `fetchPublicKey()` and `sign()`, calls `tokenSource.invalidate()`, obtains a fresh token, and retries once.

---

## Annotation-Driven Audit (casehub-ledger-annotations)

Declarative audit logging via CDI interceptors. Add `casehub-ledger-annotations` as a dependency to use.

### Annotations

| Annotation | Target | Purpose |
|---|---|---|
| `@Audited` | Method | Records a `LedgerEntry` from the method's return value. Auto-populates `domainData` from the return value. |
| `@Attested` | Method | Composes with `@Audited` -- creates both an entry and an attestation atomically via `OutcomeRecorder`. |
| `@ComplianceSupplement` | Method | Attaches EU AI Act / GDPR metadata. Works standalone or combined with `@Audited`. |
| `@SubjectId` | Parameter | Required -- marks the UUID parameter as the aggregate key. |
| `@ActorId` | Parameter | Marks the actor identity parameter. |
| `@TenancyId` | Parameter | Marks the tenant identity parameter. |
| `@Verdict` | Parameter | Marks the `AttestationVerdict` parameter (for `@Attested`). |
| `@ConfidenceScore` | Parameter | Marks the confidence score parameter (for `@Attested`). |
| `@DecisionContext` | Parameter | Marks the decision context JSON parameter (for `@ComplianceSupplement`). |

### Build-Time Validation

`LedgerAnnotationsProcessor` validates at build time:
- `@SubjectId` is required on `@Audited` methods
- `@SubjectId` parameter must be `UUID`
- `@Attested` requires `@Audited` on the same method
- `@ComplianceSupplement` can be standalone or combined with `@Audited`

### Interceptor Priorities

| Priority | Interceptor | Purpose |
|---|---|---|
| `APPLICATION` | `ComplianceSupplementInterceptor` | Pushes/pops compliance context (runs first) |
| `APPLICATION + 1` | `AuditedInterceptor` | Handles `@Audited` and `@Attested` (runs after compliance context is set) |

---

## Examples

The `examples/` directory contains 14 runnable applications demonstrating individual ledger capabilities. Each is self-contained with its own POM. Not deployed (`maven.deploy.skip=true`).

| Example | Demonstrates |
|---|---|
| `order-processing` | Basic ledger extension -- custom `LedgerEntry` subclass for order events |
| `eigentrust-mesh` | EigenTrust power iteration for transitive global trust scores |
| `trust-score-routing` | Trust-based routing using `TrustGateService` batch capability scores |
| `merkle-verification` | Merkle Mountain Range inclusion proofs and integrity verification |
| `art22-decision-snapshot` | GDPR Art.22 `ComplianceSupplement` with decision context snapshots |
| `art12-compliance` | EU AI Act Art.12 technical logging and reconstructability |
| `privacy-pseudonymisation` | Actor identity tokenisation and GDPR Art.17 erasure |
| `prov-dm-export` | PROV-DM export of ledger provenance chains |
| `otel-trace-wiring` | OpenTelemetry trace ID auto-wiring via `LedgerTraceIdProvider` |
| `vault-transit-signing` | HashiCorp Vault Transit agent signing |
| `aws-kms-signing` | AWS KMS agent signing |
| `gcp-kms-signing` | GCP Cloud KMS agent signing |
| `audit-trail-annotated` | Annotation-driven audit with `@Audited`, `@Attested`, `@ComplianceSupplement` |
| `azure-keyvault-signing` | Azure Key Vault agent signing |

---

## Flyway Conventions

Path: `classpath:db/ledger/migration` (moved from `classpath:db/migration` in ledger#95).
Consumers must add this path to their `quarkus.flyway.locations` config.

All base schema is consolidated into a single migration file:

| Version | Contents |
|---|---|
| V1000 | All ledger tables: `ledger_entry`, `ledger_attestation`, `actor_trust_score`, `compliance_supplement`, `provenance_supplement`, `ledger_entry_archive`, `actor_identity`, `key_rotation_entry`, `actor_identity_binding_entry`, `plain_ledger_entry`, `erasure_receipt_entry`, `trust_score_snapshot`, `ledger_merkle_frontier`, `ledger_subject_sequence`. No production database exists -- schema is maintained as a single clean-slate migration. |

**Consumers** own V1012+ for their own subclass join tables.

---

## Agent Identity Convention

Format: `{model-family}:{persona}@{major}` -- e.g. `"claude:tarkus-reviewer@v1"`.
Major version bump resets trust baseline to Beta(1,1) = 0.5 prior.
Bump criteria: model family change, persona behaviour change, scope change. Do NOT bump for: bug fixes, tuning, CLAUDE.md changes that do not alter behaviour.

---

## Configuration

All configuration is under the `casehub.ledger` prefix via Quarkus `@ConfigMapping`.

### Master Switch

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.enabled` | `true` | Master switch. When false, no ledger entries are written. |
| `casehub.ledger.datasource` | (empty) | Named datasource / persistence unit. Empty uses default. |

### Hash Chain

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.hash-chain.enabled` | `true` | Merkle leaf hash computation and frontier updates. |

### Decision Context

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.decision-context.enabled` | `true` | JSON snapshot of observable state for GDPR Art.22. |

### Evidence

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.evidence.enabled` | `false` | Structured evidence capture in `ComplianceSupplement.evidence`. |

### Attestations

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.attestations.enabled` | `true` | Peer attestation API. |

### Trust Score

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.trust-score.enabled` | `false` | Nightly Bayesian Beta trust score computation. |
| `casehub.ledger.trust-score.schedule` | `24h` | Recomputation interval (Quarkus duration). |
| `casehub.ledger.trust-score.decay-half-life-days` | `90` | Exponential decay half-life for attestation recency. |
| `casehub.ledger.trust-score.routing-enabled` | `false` | Trust scores influence routing suggestions via CDI events. |
| `casehub.ledger.trust-score.routing-delta-threshold` | `0.01` | Minimum score delta to publish. |
| `casehub.ledger.trust-score.aggregation-strategy` | `WEIGHTED_MAJORITY` | Attestation aggregation: `WEIGHTED_MAJORITY`, `UNANIMOUS_REQUIRED`, `FIRST_ATTESTOR`. |
| `casehub.ledger.trust-score.incremental.enabled` | `false` | Immediate per-actor recomputation on attestation. |
| `casehub.ledger.trust-score.materialization.enabled` | `true` | Persist computed scores. When false, `ComputedTrustScoreSource` still works. |
| `casehub.ledger.trust-score.eigentrust.enabled` | `false` | EigenTrust power iteration for transitive global trust. |
| `casehub.ledger.trust-score.eigentrust.alpha` | `0.15` | Dampening constant (0.0-1.0). |
| `casehub.ledger.trust-score.eigentrust.pre-trusted-actors` | (empty) | Actor IDs that seed the eigenvector. |
| `casehub.ledger.trust-score.export.deployment-id` | (empty) | Opaque deployment identifier for trust export payloads. |
| `casehub.ledger.trust-score.bootstrap.enabled` | `false` | Seed trust from external source for first-time actors. |
| `casehub.ledger.trust-score.snapshot.retention-days` | `365` | Max age in days for trust score snapshots. Set to 0 to disable trimming. |

### Decay

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.decay.flagged-persistence-multiplier` | `0.5` | FLAGGED/CHALLENGED attestations decay slower (0.1-1.0). |

### Identity / Pseudonymisation

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.identity.tokenisation.enabled` | `false` | Built-in token-based pseudonymisation. |

### Agent Identity / DID

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.agent-identity.validation-mode` | `WARN` | `WARN` or `ENFORCE` for DID/VC validation failures. |
| `casehub.ledger.agent-identity.dids."<actorId>"` | — | Map actorId to DID URI. |
| `casehub.ledger.agent-identity.did-resolver-cache-ttl-minutes` | `5` | DID document resolution cache TTL. |
| `casehub.ledger.agent-identity.credential-cache-ttl-minutes` | `60` | VC validation result cache TTL. EXPIRED results not cached. |
| `casehub.ledger.agent-identity.web-resolver-timeout-ms` | `5000` | HTTP timeout for DID resolver. |
| `casehub.ledger.agent-identity.web-resolver-max-response-bytes` | `1048576` | Max DID document size (SSRF/DoS protection). |

### Agent Identity / SCIM

Config prefix: `casehub.ledger.agent-identity.scim.*`

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.agent-identity.scim.endpoint` | (optional) | SCIM2 Agent endpoint URL. Must be HTTPS. |
| `casehub.ledger.agent-identity.scim.auth-token` | (optional) | Bearer token for the SCIM2 endpoint. |
| `casehub.ledger.agent-identity.scim.timeout-ms` | `5000` | HTTP request timeout. |
| `casehub.ledger.agent-identity.scim.cache-ttl-minutes` | `5` | Per-actorId DID cache TTL. |
| `casehub.ledger.agent-identity.scim.require-https` | `true` | Reject non-HTTPS endpoint URLs. |

### Agent Signing

| Key | Purpose |
|-----|---------|
| `casehub.ledger.agent-signing.keys."<actorId>".private-key` | Path to PKCS#8 PEM Ed25519 private key. |
| `casehub.ledger.agent-signing.keys."<actorId>".public-key` | Path to X.509 PEM Ed25519 public key. |

### Retention

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.retention.enabled` | `false` | Nightly archive-and-delete of old entries. |
| `casehub.ledger.retention.operational-days` | `180` | Minimum retention window (EU AI Act Art.12). |
| `casehub.ledger.retention.archive-before-delete` | `true` | Write to `ledger_entry_archive` before deleting. |

### Merkle Publishing

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.merkle.publish.url` | (empty) | POST endpoint for signed tlog-checkpoint. |
| `casehub.ledger.merkle.publish.private-key` | (empty) | Ed25519 private key PEM path. |
| `casehub.ledger.merkle.publish.key-id` | `default` | Key identifier for checkpoint receivers. |

### Health Check

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.health.enabled` | `true` | Periodic sequence gap detection. |
| `casehub.ledger.health.check-interval` | `1h` | Interval between health check runs. |

### Outcome

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.outcome.default-attestor-id` | (optional) | Default attestor identity when `OutcomeRecord.attestorId()` is null. |
| `casehub.ledger.outcome.default-attestor-type` | `SYSTEM` | Default attestor type. |

### Erasure

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.erasure-receipt.enabled` | `false` | Opt-in tamper-evident GDPR Art.17 erasure receipts. |

### Metadata

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.metadata.max-size` | `65536` | Max size (characters) for consumer-provided JSON metadata. |

---

## Multi-Tenancy

All queries, sequences, and hash chains are scoped per `(subjectId, tenancyId)`. The `tenancyId` parameter is required on every tenant-scoped SPI method; filtering is unconditional. `CrossTenantLedgerEntryRepository` exists for system-level operations (trust computation, health checks, retention) and requires the `@CrossTenant` CDI qualifier for injection. Build-time enforcement: `LedgerProcessor` rejects `@RequestScoped` beans injecting `@CrossTenant`.

---

## What This Repo Does NOT Do

- Provide REST endpoints by default -- `casehub-ledger-rest` is opt-in via explicit dependency
- Provide MCP tools (consumers define their own)
- Capture domain events (consumers wire their own CDI observers)
- Replay events or project CQRS views
- Know anything about WorkItems, Cases, or agent channels

---

## Boundary Rules

- `casehub-ledger` provides model, SPI, services, and JPA implementations only
- Domain-specific subclasses, REST endpoints, and MCP tools live in consumers
- `subjectId` is the generic aggregate identifier -- consumers set it to their own aggregate UUID (WorkItem UUID, Channel UUID, etc.)
- All queries, sequences, and hash chains are scoped per `(subjectId, tenancyId)`
- Multi-tenancy uses explicit `tenancyId` parameter on every tenant-scoped SPI method; filtering is unconditional
- `ActorDIDProvider` is a platform API (`io.casehub.platform.api.identity`), not a ledger SPI -- but ledger uses it via `ActorDIDEnricher`

---

## Dependencies

### Depends On

Nothing in the casehubio ecosystem beyond platform libraries. Quarkus + Hibernate ORM + `casehub-platform-api` (for `ActorType`, `ActorDIDProvider`, `TenancyConstants`) + `casehub-platform-identity` (for `DIDResolver`, `AgentCredentialValidator`, `ActorDIDProvider` implementations, no-op defaults).

### Depended On By

| Repo | How |
|---|---|
| `casehub-work` | Optional ledger module -- extends `LedgerEntry` to record work item events |
| `casehub-qhorus` | Mandatory -- extends `LedgerEntry` to record agent messages; provides ledger write integration |
| `casehub-engine` | Optional ledger module -- extends `LedgerEntry` to record case events |
| `claudony` | Transitively via Qhorus and casehub-ledger |
