# ledger Workspace
**Name:** casehub-ledger
**Project repo:** /Users/mdproctor/claude/casehub/ledger
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/ledger` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` (workspace staging) |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| java-update-design / update-primary-doc | `design/JOURNAL.md` (created by `epic`) |
| adr | `adr/` (workspace staging) |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (staging; promoted to project `docs/specs/` at epic close)
- `plans/` — implementation plans (ephemeral; stay in workspace only)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records (staging; promoted to project `docs/adr/` at epic close)
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Git Discipline

Two git repositories are active in every session: a **workspace** (staging area for specs and ADRs; permanent home for blog, handover, plans, snapshots) and the **project repo** (source code + promoted specs and ADRs).

Before any git operation, run `git rev-parse --show-toplevel` to confirm which repo is currently active. Do not assume — the session may have opened in either. cd to the correct repo before staging:
- Source code commits → project repo
- Specs and ADRs → workspace first, then promote to project repo at epic close

## Rules

- **Specs and ADRs are project knowledge** — final home is the project repo under `docs/specs/` and `docs/adr/`
- The workspace `specs/` and `adr/` directories are staging areas only — skills write there first
- **Promotion at epic close**: copy spec/ADR files to project repo, commit there; leave workspace copies in place
- Plans (`plans/`) are ephemeral — workspace only, never promoted
- Blog, handover, snapshots, design journal — workspace only, never promoted

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `docs/adr/` — promoted at epic close |
| specs      | project     | lands in `docs/specs/` — promoted at epic close |
| blog       | project     | lands in `docs/blog/` — promoted at work end |
| plans      | workspace   | stay in workspace permanently |
| design     | project     | journal file lives in workspace design/; ARC42STORIES.MD is the primary architecture record at project root |
| snapshots  | workspace   | stay in workspace permanently |
| handover   | workspace   | |

---

# CaseHub Ledger — Claude Code Project Guide

## Platform Docs
- [Platform Index](https://raw.githubusercontent.com/casehubio/parent/main/docs/INDEX.md) — discovery index (start here)
- [Building Platform](https://raw.githubusercontent.com/casehubio/parent/main/docs/guides/building-platform.md) — platform contributor guide

## Repo Guide

This repo owns its own documentation, synced to parent via CI:
- `docs/guides/consumer-guide.md` — for app builders: modules, APIs, quick start
- `docs/guides/contributor-guide.md` — for platform builders: architecture, SPIs, internals

Update the relevant guide in the same session when implementation changes modules, SPIs, or public APIs. Do not defer — drift compounds.

Read `docs/guides/consumer-guide.md` for app-level work. Only read `docs/guides/contributor-guide.md` when modifying this repo's internals or extension points.

## Reference Documents (casehub-parent)

| Document | What it covers |
|----------|---------------|
| `../garden/docs/protocols/casehub/FOUNDATION-INDEX.md` | CaseHub foundation protocols |

---

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, GraalVM 25 (native image)

---

## What This Project Is

`casehub-ledger` is a CaseHub extension providing a domain-agnostic immutable
audit ledger for any Quarkus application. Any Quarkus app adds `io.casehub:casehub-ledger`
as a dependency and immediately gets:

- **Immutable append-only audit log** (`LedgerEntry` base entity with JPA JOINED inheritance)
- **Merkle Mountain Range tamper evidence** (RFC 9162 stored frontier — O(log N) inclusion proofs, Ed25519 signed checkpoints)
- **Peer attestation** (`LedgerAttestation` — verdicts, confidence scores)
- **EigenTrust reputation** (`TrustScoreComputer` — nightly batch, exponential decay weighting)
- **Provenance tracking** (`sourceEntityId / sourceEntityType / sourceEntitySystem`)
- **Decision context snapshots** (GDPR Article 22 / EU AI Act Article 12 compliance)

### Domain-Specific Subclasses

Domain logic is NOT in this extension — it lives in consumers via JPA JOINED subclasses:

| Consumer | Subclass | Subclass table | subject_id maps to |
|---|---|---|---|
| `casehub-work` | `WorkItemLedgerEntry` | `work_item_ledger_entry` | WorkItem UUID |
| `casehub-qhorus` | `MessageLedgerEntry` | `message_ledger_entry` | Channel UUID |

Each consumer defines its own subclass and its own Flyway migration for the subclass table.
The base tables (`ledger_entry`, `ledger_attestation`, `actor_trust_score`) are defined here
in V1000–V1008 and always present when `casehub-ledger` is on the classpath.

**Design documentation:** `ARC42STORIES.MD` at the project root is the primary architecture record — covers entity model, architecture, SPI contracts, Merkle MMR, trust scoring, agent identity, and delivery history. `docs/DESIGN.md` and `docs/DESIGN-capabilities.md` redirect to it.

---

## Maven Coordinates

| Element | Value |
|---|---|
| GitHub repo | `casehubio/ledger` |
| groupId | `io.casehub` |
| Parent artifactId | `casehub-ledger-parent` |
| Runtime artifactId | `casehub-ledger` |
| Deployment artifactId | `casehub-ledger-deployment` |
| persistence-memory artifactId | `casehub-ledger-persistence-memory` |
| Testing artifactId | `casehub-ledger-testing` |
| REST artifactId | `casehub-ledger-rest` |
| GraphQL artifactId | `casehub-ledger-graphql` |
| Annotations artifactId | `casehub-ledger-annotations` / `casehub-ledger-annotations-deployment` |
| Vault Transit artifactId | `casehub-ledger-vault-transit` / `casehub-ledger-vault-transit-quarkus` |
| AWS KMS artifactId | `casehub-ledger-aws-kms` / `casehub-ledger-aws-kms-quarkus` |
| GCP Cloud KMS artifactId | `casehub-ledger-gcp-kms` / `casehub-ledger-gcp-kms-quarkus` |
| Azure Key Vault artifactId | `casehub-ledger-azure-keyvault` / `casehub-ledger-azure-keyvault-quarkus` |
| Root Java package | `io.casehub.ledger.runtime` |
| Signing packages | `io.casehub.ledger.signing.{vault,aws,gcp,azure}[.quarkus]` |
| Deployment subpackage | `io.casehub.ledger.deployment` |
| Annotations package | `io.casehub.ledger.annotations` |
| Annotations runtime subpackage | `io.casehub.ledger.annotations.runtime` |
| Annotations deployment subpackage | `io.casehub.ledger.annotations.deployment` |
| Config prefix | `casehub.ledger` |
| Signing config prefixes | `casehub.ledger.{vault-transit,aws-kms,gcp-kms,azure-keyvault}` |
| Feature name | `ledger` |

---

## Key Design Decisions

**`subject_id` — the generic aggregate identifier**
All queries, sequences, and hash chains are scoped per `subject_id`. This field replaces
the domain-specific `work_item_id` that was in the original Tarkus ledger. Consumers set
`subjectId` to their own aggregate UUID (WorkItem UUID, Channel UUID, etc.).

**JPA JOINED inheritance**
`LedgerEntry` is abstract with `@Inheritance(strategy = JOINED)`. Hibernate joins to all
registered subclass tables on query. `LedgerAttestation` holds a FK to the base table —
attestations work regardless of which subclass produced the entry.

**Merkle leaf hash canonical form**
The leaf hash covers all tamper-critical fields: structural metadata, `supplementJson`,
and subclass domain content via `domainContentBytes()`. `canonicalBytes()` is a `public final`
instance method on `LedgerEntry`. Subclasses override `domainContentBytes()` to include
join-table fields; a build-time guard enforces this for `@Entity` subclasses with persistent fields.
The leaf hash is `SHA-256(0x00 | canonicalBytes)` per RFC 9162.
The Merkle Mountain Range (stored frontier) replaces the old linear chain.

**`traceId` and `causedByEntryId` are core fields**
Both OTel trace linking and causal relationships are structural — present on every entry where
relevant. They live on `LedgerEntry` directly (not in supplements). `traceId` is auto-populated
from the active OTel span at persist time via the `LedgerEntryEnricher` pipeline (`LedgerTraceListener`). `findCausedBy(UUID entryId)`
traverses causal chains one hop at a time. The test for core vs supplement: is the field
relevant to every consumer, every entry, every time? If yes → core. If no → supplement.

**Three data channels on `LedgerEntry`**
Core fields (universal, typed, fixed — actorId, subjectId, entryType, etc.), supplements
(optional cross-cutting concerns with fixed schemas — `ComplianceSupplement`, `ProvenanceSupplement`),
and metadata (consumer-provided freeform JSON audit context — routing rationale, candidate lists,
decision explanations). Metadata is opaque to the ledger, stored verbatim, included in
`canonicalBytes()` as a positional field, and size-limited via `casehub.ledger.metadata.max-size`
(default 64KB). Must not contain PII (GDPR erasure does not scan field contents). See #172.

**All entities are plain `@Entity` — no Panache active-record base**
No entity in the runtime module extends `PanacheEntityBase`. This allows reactive
subclassing by consumers (e.g. Qhorus's `MessageLedgerEntry`) and removes the
forced `quarkus-hibernate-orm-panache` dep. Repositories use `EntityManager` + JPQL.
Queries are declared as `@NamedQuery` on entity classes — Hibernate validates them at
startup, so typos fail at boot not at query time.
`LedgerEntryRepository.findById(UUID)` was renamed to `findEntryById(UUID)` to avoid
a Java return-type conflict with `PanacheRepositoryBase.findById()`.

**REST endpoints are domain-specific**
`casehub-ledger` provides model, SPI, services, and JPA implementations only. Tarkus and
Qhorus each define their own REST/MCP endpoints on top.

**`actorId` format for LLM agents**
LLM agents are stateless; use versioned persona names so trust accumulates correctly
across sessions: `"{model-family}:{persona}@{major}"` — e.g. `"claude:tarkus-reviewer@v1"`.
Major version bump resets the trust baseline; tuning/bug-fix does not. See ADR 0004 and
`ARC42STORIES.MD` §9.4 Layer L6 (Agent Identity) for concrete bump criteria and the no-inheritance
rationale.

**Multi-tenancy — explicit `tenancyId` parameter, unconditional filtering**
`tenancyId` is an explicit `String` parameter on every tenant-scoped SPI method. Cross-tenant
methods (trust computation, health checks, retention) live in separate `CrossTenant*Repository`
interfaces, injected via `@CrossTenant` CDI qualifier and guarded by
`LedgerSystemCurrentPrincipal.isCrossTenantAdmin()`. Per PP-20260520-439daf, filtering is
unconditional — single-tenant deployments use `TenancyConstants.DEFAULT_TENANT_ID` as a sentinel
that always matches. Per PP-20260520-e6a5f0, `CurrentPrincipal.tenancyId()` is never called
inside repositories or services — callers pass tenancyId from the HTTP boundary. Same pattern
as casehub-engine (#299, #405, #406).

---

## Identity Infrastructure — casehub-platform-identity

Agent identity SPIs, resolvers, and no-op implementations have been extracted to the
`casehub-platform-identity` library. The runtime pom declares this as a dependency.

**What lives in `casehub-platform-identity` (`io.casehub.platform.api.identity` / `io.casehub.platform.identity`):**

| Category | Classes |
|---|---|
| SPIs | `ActorDIDProvider`, `DIDResolver`, `AgentCredentialValidator` |
| Model | `DIDDocument`, `VerificationMethod`, `IdentityVerificationResult`, `CredentialValidationResult`, `IdentityBindingStatus` |
| Events | `AgentIdentityValidatedEvent`, `AgentIdentityViolationEvent` |
| Cache base | `AbstractCachingIdentityProvider` |
| No-Op defaults | `NoOpActorDIDProvider`, `NoOpDIDResolver`, `NoOpCredentialValidator` |
| Implementations | `KeyDIDResolver`, `WebDIDResolver`, `ConfiguredActorDIDProvider`, `ScimActorDIDProvider`, `ScimAgentResource` |

**What stays in ledger (`runtime/.../service/identity/`):**
- `ActorDIDEnricher`, `ActorIdentityValidationEnricher`, `ActorIdentityBindingObserver` — enrichment pipeline
- `AgentIdentityVerificationService`, `ReactiveAgentIdentityVerificationService` — read-path
- `IdentityCacheInvalidator` — bridges `AgentKeyRotatedEvent` → platform provider cache invalidation
- `LedgerIdentityEnforcementListener`, `LedgerIdentityViolationException` — ENFORCE mode gate

**Package migration:**

| Old (ledger api) | New (platform) |
|---|---|
| `io.casehub.ledger.api.spi.identity.*` | `io.casehub.platform.api.identity.*` |
| `io.casehub.ledger.api.spi.resolve.DIDResolver` | `io.casehub.platform.api.identity.DIDResolver` |
| `io.casehub.ledger.api.model.IdentityVerificationResult` | `io.casehub.platform.api.identity.IdentityVerificationResult` |
| `io.casehub.ledger.api.model.CredentialValidationResult` | `io.casehub.platform.api.identity.CredentialValidationResult` |
| `io.casehub.ledger.api.model.IdentityBindingStatus` | `io.casehub.platform.api.identity.IdentityBindingStatus` |

**Config key migration:**

| Old key | New key | Owned by |
|---|---|---|
| `casehub.ledger.agent-identity.dids.*` | `casehub.identity.dids.*` | platform |
| `casehub.ledger.agent-identity.scim.*` | `casehub.identity.scim.*` | platform |
| `casehub.ledger.agent-identity.web-resolver-*` | `casehub.identity.web-resolver-*` | platform |
| `casehub.ledger.agent-identity.validation-mode` | (unchanged) | ledger |

**Activating SCIM provider** (updated class name):
```properties
quarkus.arc.selected-alternatives=io.casehub.platform.identity.ScimActorDIDProvider
```

---

## Project Structure

```
casehub-ledger/  (local folder: ~/claude/casehub/ledger)
├── api/
│   └── src/main/java/io/casehub/ledger/api/
│       ├── model/
│       │   ├── LedgerEntry.java             — abstract base (all persistent fields, canonicalBytes(), domainData: Map<String,Object>); JPA annotations stripped — mappings in runtime/META-INF/orm.xml; consumers extend this or JpaLedgerEntry
│       │   ├── LedgerEntryType.java         — COMMAND | EVENT | ATTESTATION enum
│       │   ├── ActorType.java               — HUMAN | AGENT | SYSTEM enum
│       │   ├── KeyRotationReason.java       — SCHEDULED | COMPROMISED enum (NIST SP 800-57 lifecycle distinction)
│       │   ├── ErasureReason.java           — GDPR_ART_17_REQUEST | RETENTION_EXPIRED | ACCOUNT_DELETION enum (legal basis for erasure events)
│       │   ├── AttestationVerdict.java      — SOUND | FLAGGED | ENDORSED | CHALLENGED enum
│       │   ├── CapabilityTag.java           — sentinel constants: GLOBAL = "*" for cross-capability attestations
│       │   ├── ScoreType.java               — GLOBAL | CAPABILITY | DIMENSION | CAPABILITY_DIMENSION enum (trust score type; see ADR 0010)
│       │   ├── AuditRecord.java             — immutable record: domain-agnostic audit event fields for LedgerAppender write path; metadata component for consumer-provided JSON context (#172)
│       │   ├── AttestationSummary.java      — immutable record: verdict counts + confidence stats for aggregate queries (#201)
│       │   └── supplement/
│       │       ├── LedgerSupplement.java        — abstract base for supplements (JPA annotations stripped — mappings in runtime/META-INF/orm.xml)
│       │       ├── CompensationSupplement.java  — saga compensation record: originalEntryId, reason, regulatory basis, mode (JPA-free)
│       │       ├── ComplianceSupplement.java    — GDPR Art.22, governance fields (JPA-free)
│       │       └── ProvenanceSupplement.java    — workflow source entity; agentConfigHash (JPA-free)
│       └── spi/
│           ├── LedgerEntryRepository.java        — tenant-scoped SPI (all methods take tenancyId); findEntryById (not findById — no Panache conflict); streaming (streamBySubjectId, streamByActorId), cursor (findBySubjectIdPaged), aggregate (countByActorAndVerdict, countBySubjectAndVerdict, summariseAttestationsByActor)
│           ├── ReactiveLedgerEntryRepository.java — tenant-scoped reactive SPI (Uni<T> return types; all methods take tenancyId)
│           ├── LedgerAppender.java              — write-path SPI: append(AuditRecord) → LedgerEntry (higher-level facade over repository.save())
│           ├── ReactiveLedgerAppender.java      — reactive write-path SPI: appendAsync(AuditRecord) → Uni<LedgerEntry>
│           ├── ActorIdentityProvider.java       — SPI: tokenise/resolve/erase actor identities (moved from runtime.privacy; see #142)
│           ├── OutcomeRecorder.java             — write-path SPI: record(outcome, actorId, subjectId, tenancyId) → UUID entryId
│           ├── ReactiveOutcomeRecorder.java     — reactive write-path SPI: recordAsync(outcome, actorId, subjectId, tenancyId) → Uni<UUID>
│           ├── TrustScoreSource.java            — read-path SPI: globalScore(actorId), capabilityScore(actorId, capabilityTag), dimensionScore(actorId, dimension), capabilityDimensionScore(actorId, capability, dimension)
│           └── LedgerTraceIdProvider.java       — SPI: readCurrentTraceId() → Optional<String> (OTel or custom trace context)
├── runtime/
│   └── src/main/resources/META-INF/
│       └── orm.xml                          — JPA mapped-superclass declarations for api/ model classes (LedgerEntry, LedgerAttestation, LedgerSupplement, ComplianceSupplement, ProvenanceSupplement)
│   └── src/main/java/io/casehub/ledger/runtime/
│       ├── config/LedgerConfig.java         — @ConfigMapping(prefix = "casehub.ledger")
│       ├── model/
│       │   ├── converter/
│       │   │   └── DomainDataConverter.java     — JPA AttributeConverter: Map<String,Object> ↔ JSON TEXT; USE_BIG_DECIMAL_FOR_FLOATS + USE_LONG_FOR_INTS for deterministic round-trip
│       │   ├── jpa/
│       │   │   └── JpaLedgerEntry.java          — @Entity: runtime JPA persistence layer extending api LedgerEntry; JOINED inheritance root for runtime; consumers can extend this instead of api LedgerEntry when they need JPA features in the base class
│       │   ├── LedgerAttestation.java       — @Entity: peer attestation entity
│       │   ├── ActorTrustScore.java         — @Entity: trust score entity; four ScoreType values (GLOBAL|CAPABILITY|DIMENSION|CAPABILITY_DIMENSION) × two-column key (capability_key, dimension_key); see ADR 0010
│       │   ├── LedgerMerkleFrontier.java    — @Entity: Merkle frontier node (log₂(N) rows per subject per tenant); tenancyId column added in #139
│       │   ├── LedgerEntryArchiveRecord.java — archive snapshot record for retention-deleted entries (V1003)
│       │   ├── KeyRotationEntry.java         — @Entity: LedgerEntry subclass: key rotation/revocation event; subjectId=UUID.nameUUIDFromBytes(actorId); see ADR 0012
│       │   ├── ActorIdentityBindingEntry.java — @Entity: LedgerEntry subclass: DID/VC binding validation event; subjectId=nameUUIDFromBytes(actorId); entryType=EVENT; see ADR 0015
│       │   ├── PlainLedgerEntry.java         — @Entity: LedgerEntry subclass for domain-agnostic event writes via OutcomeRecorder (V1009)
│       │   ├── ErasureReceiptLedgerEntry.java — @Entity: LedgerEntry subclass: tamper-evident GDPR Art.17 erasure record; subjectId=nameUUIDFromBytes(erasedActorId); entryType=EVENT; opt-in via casehub.ledger.erasure-receipt.enabled (V1010)
│       │   ├── TrustScoreSnapshot.java       — @Entity: trust score point-in-time snapshot for trend visibility; (actorId, scoreType, capabilityTag, dimensionKey, score, previousScore, occurredAt); captured by PerActorTrustComputer on every score upsert for all four score types (V1000)
│       │   ├── ActorIdentity.java           — @Entity: token↔identity mapping for pseudonymisation
│       │   └── supplement/
│       │       ├── JpaLedgerSupplement.java      — @Entity: runtime JPA base extending api LedgerSupplement; JOINED inheritance
│       │       ├── JpaCompensationSupplement.java — @Entity: runtime JPA layer extending api CompensationSupplement
│       │       ├── JpaComplianceSupplement.java  — @Entity: runtime JPA layer extending api ComplianceSupplement
│       │       ├── JpaProvenanceSupplement.java  — @Entity: runtime JPA layer extending api ProvenanceSupplement
│       │       └── LedgerSupplementSerializer.java — JSON serialiser for supplementJson
│       ├── repository/
│       │   ├── NoOpLedgerEntryRepository.java    — @DefaultBean: CDI-satisfaction no-op; all reads return empty, save/saveAttestation return argument unchanged; active when neither JPA nor in-memory alternative is selected (see #138)
│       │   ├── NoOpActorTrustScoreRepository.java — @DefaultBean: CDI-satisfaction no-op; all reads return empty/empty-list, upsert/updateGlobalTrustScore are no-ops; active when neither JPA nor in-memory alternative is selected (see #143)
│       │   ├── NoOpActorIdentityBindingRepository.java — @DefaultBean: CDI-satisfaction no-op for ActorIdentityBindingRepository read methods; write path uses LedgerEntryRepository (observer no longer injects this bean)
│       │   ├── NoOpLedgerMerkleFrontierRepository.java — @DefaultBean: CDI-satisfaction no-op; findBySubjectId() returns empty, replace() is a no-op
│       │   ├── NoOpErasureReceiptRepository.java — @DefaultBean: CDI-satisfaction no-op; returns empty list
│       │   ├── NoOpTrustScoreSnapshotRepository.java — @DefaultBean: CDI-satisfaction no-op; save() discards, find methods return empty
│       │   ├── TrustScoreSnapshotRepository.java — SPI: save(TrustScoreSnapshot), findGlobalSnapshots(actorId), findCapabilitySnapshots(actorId, capabilityTag), findDimensionSnapshots(actorId, dimensionKey), findByActorAndTimeRange(actorId, from, to), deleteOlderThan(cutoff)
│       │   └── jpa/                              — JPA implementations (EntityManager-based)
│       │       ├── JpaLedgerEntryRepository.java     — @Alternative: JPA implementation of LedgerEntryRepository; activate via quarkus.arc.selected-alternatives
│       │       ├── JpaActorIdentityBindingRepository.java — @Alternative: read-only JPA implementation (latestBindingFor, bindingHistoryFor with tenancyId); no save() — saves go through JpaLedgerEntryRepository; activate via quarkus.arc.selected-alternatives
│       │       ├── JpaActorTrustScoreRepository.java — @Alternative @ApplicationScoped: activate via quarkus.arc.selected-alternatives; was plain @ApplicationScoped before #143 — @Alternative required so NoOpActorTrustScoreRepository @DefaultBean can fill the default slot
│       │       ├── JpaCrossTenantLedgerEntryRepository.java
│       │       ├── JpaErasureReceiptRepository.java — @Alternative: findByErasedActorId NamedQuery; activate via quarkus.arc.selected-alternatives
│       │       ├── JpaTrustScoreSnapshotRepository.java — @Alternative: JPA implementation; persist + named query retrieval; activate via quarkus.arc.selected-alternatives
│       │       └── LedgerSequenceAllocator.java     — CDI bean: atomic per-(subject, tenant) sequence allocation; dialect detected lazily via INFORMATION_SCHEMA.SETTINGS on H2 (getDatabaseProductName() returns "H2" for all modes; getMetaData().getURL() drops connection properties via Agroal — URL not reliable for mode detection); three-way Dialect enum (POSTGRESQL / H2_PG_MODE / H2_STANDARD); PostgreSQL: single-statement INSERT ON CONFLICT DO UPDATE (atomic upsert, DO UPDATE row lock serialises full save pipeline per tenant); H2+MODE=PostgreSQL: INSERT ON CONFLICT DO NOTHING + UPDATE (H2 2.4.240 rejects ON CONFLICT (col) DO UPDATE); H2 standard: full SQL-standard MERGE WHEN MATCHED UPDATE WHEN NOT MATCHED INSERT (single statement, not concurrent-safe for first inserts)
│       ├── qualifier/
│       │   └── CrossTenant.java              — CDI qualifier: disambiguates CrossTenantLedgerEntryRepository from LedgerEntryRepository (Category 1 only; build-time scope validation)
│       ├── service/
│       │   ├── DefaultLedgerAppender.java       — CDI bean: default LedgerAppender implementation — constructs PlainLedgerEntry from AuditRecord, delegates to LedgerEntryRepository
│       │   ├── DefaultReactiveLedgerAppender.java — CDI bean: default ReactiveLedgerAppender implementation — delegates to ReactiveLedgerEntryRepository
│       │   ├── TraceIdEnricher.java             — auto-populates traceId from active OTel span
│       │   ├── OtelTraceIdProvider.java         — OTel span reader for TraceIdEnricher
│       │   ├── LedgerTraceListener.java         — @EntityListeners runner: iterates LedgerEntryEnricher pipeline, non-fatal
│       │   ├── LedgerMerkleTree.java            — Merkle Mountain Range algorithm (pure static); canonicalBytes() public static — shared by Merkle and agent signing
│       │   ├── LedgerVerificationService.java   — treeRoot / inclusionProof / verify (Merkle-only; no reactive imports)
│       │   ├── AgentCryptographicVerifier.java  — package-private static utility: verifyCryptographic(LedgerEntry); shared by blocking and reactive tiers; mirrors LedgerMerkleTree pattern
│       │   ├── AgentSignatureVerificationService.java — verifyAgentSignature (blocking only; delegates crypto to AgentCryptographicVerifier, compromise check to KeyRotationService)
│       │   ├── ReactiveAgentSignatureVerificationService.java — verifyAgentSignatureAsync (Uni<VerificationResult>); excluded via LedgerProcessor ExcludedTypeBuildItem when casehub.ledger.reactive.enabled=false
│       │   ├── AgentSignatureSuspectEvent.java  — CDI event record fired when verifyAgentSignature[Async] returns SUSPECT; consumers use @Observes or @ObservesAsync
│       │   ├── LedgerMerklePublisher.java       — Ed25519 signed tlog-checkpoint (opt-in CDI bean)
│       │   ├── SigningKey.java                  — record: keyRef (Base64URL SHA-256 of public key) + KeyPair; self-derived, zero operator config
│       │   ├── AgentSigner.java                 — SPI: sign(actorId, data) → Optional<AgentSignature>; keyMaterial(actorId) → Optional<AgentKeyMaterial> (default method, avoids wasted KMS sign calls); algorithm-transparent; see PP-20260523-e7b577
│       │   ├── AgentKeyMaterial.java            — record: publicKey + keyRef; returned by AgentSigner.keyMaterial()
│       │   ├── SignatureAlgorithms.java          — package-private: signatureAlgorithm(Key) maps EC curves to JCA Signature names (P-256→SHA256withECDSA, P-384→SHA384withECDSA, P-521→SHA512withECDSA); Ed25519/ML-DSA pass through
│       │   ├── ConfiguredAgentSigner.java       — @DefaultBean: loads PKCS#8 private + X.509 public PEM per actorId from casehub.ledger.agent-signing.keys.*
│       │   ├── AgentEntrySigner.java             — CDI bean: signs entry.canonicalBytes() in save pipeline (after hash, before persist), stores agentSignature + agentPublicKey + agentKeyRef
│       │   ├── AgentKeyRotatedEvent.java        — CDI event record fired by KeyRotationService/ReactiveKeyRotationService after rotation is persisted; observers (ActorIdentityValidationEnricher, IdentityCacheInvalidator) invalidate their caches
│       │   ├── KeyRotationService.java          — CDI bean: recordRotation fires AgentKeyRotatedEvent after persist; rotationHistory(actorId, tenancyId) tenant-scoped; compromisedWindows cross-tenant
│       │   ├── ReactiveKeyRotationService.java  — compromisedWindowsAsync / rotationHistoryAsync / recordRotationAsync (Uni<T>); fires AgentKeyRotatedEvent via fireAsync (fire-and-forget); excluded when casehub.ledger.reactive.enabled=false
│       │   ├── LedgerProvExportService.java      — W3C PROV-DM JSON-LD export (CDI bean)
│       │   ├── LedgerProvSerializer.java         — PROV-DM serialisation utility
│       │   ├── LedgerEntryArchiver.java          — archive record JSON serialisation for retention
│       │   ├── model/
│       │   │   ├── InclusionProof.java       — Merkle inclusion proof value type
│       │   │   ├── ProofStep.java            — single sibling node in a proof path
│       │   │   ├── VerificationResult.java  — UNSIGNED | VALID | INVALID | SUSPECT (agent signature verification result)
│       │   │   ├── CompromisedWindow.java   — record: keyRef + effectiveSince (time window for SUSPECT detection)
│       │   │   └── SubjectSequenceStats.java — record: (UUID subjectId, String tenancyId, long count, int min, int max); sequence aggregate projection returned by CrossTenantLedgerEntryRepository.findSequenceStats(); min/max are int to match LedgerEntry.sequenceNumber exactly
│       │   ├── RetentionEligibilityChecker.java — pure utility: checks retention window eligibility per entry
│       │   ├── LedgerRetentionJob.java      — @Scheduled daily retention sweep (EU AI Act Art.12)
│       │   ├── DecayFunction.java           — SPI: attestation decay weight (ageInDays, verdict) → weight
│       │   ├── ExponentialDecayFunction.java — @DefaultBean: 2^(-age/halfLife) × valence multiplier (FLAGGED slower decay)
│       │   ├── TrustScoreComputer.java      — Bayesian Beta trust scoring (compute) + decay-weighted dimension average (computeDimensionScore); delegates decay to DecayFunction (pure Java)
│       │   ├── TrustScoreCalculator.java    — @ApplicationScoped: pure four-pass computation (capability → dimension → cap×dim → global); no persistence, no CDI events; used by PerActorTrustComputer (write path) and ComputedTrustScoreSource (read path)
│       │   ├── MaterializedTrustScoreSource.java — @DefaultBean TrustScoreSource: reads ActorTrustScoreRepository per call
│       │   ├── CachedTrustScoreSource.java  — @Alternative TrustScoreSource: in-memory ConcurrentHashMap cache; observes TrustScoreFullPayload + TrustScoreActorUpdatedEvent for refresh
│       │   ├── ComputedTrustScoreSource.java — @Alternative TrustScoreSource: on-read computation from raw attestation history; per-actor computation cache with event-driven invalidation
│       │   ├── TrustGateService.java        — CDI bean: trust threshold enforcement; injects TrustScoreSource (not repo directly); returns OptionalDouble; methods: meetsThreshold, currentScore, allDimensionScores, dimensionScore, qualityScore, qualityScores, meetsQualityThreshold, allCapabilityScores, decisionCount; batch: scoresFor(List<String> candidateIds, String capabilityTag) + decisionCountsFor() + Uni<> async variants (ledger#136)
│       │   ├── GlobalScoreStrategy.java          — SPI: select attestations / derive global trust score (ADR 0008)
│       │   ├── AllAttestationsGlobalStrategy.java — @DefaultBean: all attestations → global Beta (Option B)
│       │   ├── ExplicitGlobalAttestationsStrategy.java — @Alternative: only "*" attestations (Option A)
│       │   ├── FrequencyWeightedGlobalStrategy.java — @Alternative: frequency-weighted from capability scores (Option C)
│       │   ├── AttestationAggregator.java   — CDI bean: collapses (entryId, capabilityTag) attestation groups into consensus verdict (WEIGHTED_MAJORITY | UNANIMOUS_REQUIRED | FIRST_ATTESTOR)
│       │   ├── EigenTrustComputer.java      — EigenTrust power iteration, transitive global trust scores (pure Java)
│       │   ├── AttestationRecordedEvent.java — CDI event record fired from saveAttestation(); carries actorId (decision-maker), ledgerEntryId, attestationId
│       │   ├── PerActorTrustComputer.java    — package-private CDI bean: delegates computation to TrustScoreCalculator, persists results, fires events; used by TrustScoreJob and IncrementalTrustUpdateObserver
│       │   ├── IncrementalTrustUpdateObserver.java — CDI observer: @Observes(AFTER_SUCCESS) AttestationRecordedEvent → per-actor trust recomputation in REQUIRES_NEW; gated by casehub.ledger.trust-score.incremental.enabled
│       │   ├── TrustScoreJob.java           — @Scheduled nightly recomputation; delegates per-actor work to PerActorTrustComputer
│       │   ├── LedgerHealthJob.java         — @Scheduled gap detection + reconciliation (configurable interval, default 1h); delegates to CrossTenantLedgerEntryRepository.findSequenceStats() — no direct EntityManager; fires LedgerAnomalyDetected sealed events
│       │   ├── LedgerReconciliationSource.java — SPI: consumers implement to compare domain entity counts vs ledger counts
│       │   ├── LedgerAnomalyDetected.java   — sealed interface: base type for all health-job anomaly CDI events (see #139)
│       │   ├── LedgerSequenceGapDetected.java — record: (UUID subjectId, String tenancyId, long expectedCount, long actualCount) implements LedgerAnomalyDetected; fired on per-(subject,tenant) sequence gap
│       │   ├── LedgerReconciliationMismatchDetected.java — record: (String entityType, long domainCount, long ledgerCount) implements LedgerAnomalyDetected; fired on reconciliation source count discrepancy
│       │   ├── LedgerComplianceReportService.java — CDI bean: reportForActor / reportForSubject → ComplianceReport
│       │   ├── ComplianceReport.java        — value type: DecisionRecord list + Merkle anchor + format(ReportFormat)
│       │   ├── DecisionRecord.java          — single automated decision entry in a compliance report
│       │   ├── ReportFormat.java            — PLAIN_JSON | JSON_LD | CSV
│       │   ├── routing/
│       │   │   ├── TrustScoreRoutingPublisher.java — CDI event dispatch after trust score computation
│       │   │   ├── TrustScoreFullPayload.java      — all current scores (strategy: rebuild ranked list)
│       │   │   ├── TrustScoreDeltaPayload.java     — changed actors only (strategy: incremental cache)
│       │   │   ├── TrustScoreComputedAt.java       — lightweight notification (strategy: signal only)
│       │   │   ├── TrustScoreDelta.java            — single actor score change value type
│       │   │   └── TrustScoreActorUpdatedEvent.java — CDI event: per-actor score update notification (incremental path only); carries actorId, all score types, computedAt
│       │   ├── federation/
│       │   │   ├── TrustExportPayload.java         — record: exportedAt, exportingDeployment, actors
│       │   │   ├── ActorExport.java                — record: actorId, actorType, globalScore, capabilityScores, dimensionScores, capabilityDimensionScores
│       │   │   ├── GlobalScoreExport.java          — record: Bayesian Beta global trust score fields
│       │   │   ├── CapabilityScoreExport.java      — record: capability-scoped Bayesian Beta score fields
│       │   │   ├── DimensionScoreExport.java       — record: continuous quality dimension score (score, sampleCount)
│       │   │   ├── CapabilityDimensionScoreExport.java — record: per-capability quality dimension score (capabilityTag, dimension, score, sampleCount)
│       │   │   ├── TrustExportService.java         — CDI bean: exportAll / exportActor / exportDelta read-model
│       │   │   ├── TrustImportService.java         — SPI: importTrust(TrustExportPayload); implementation is the merge strategy
│       │   │   ├── NoOpTrustImportService.java     — @DefaultBean no-op (trust import is opt-in)
│       │   │   ├── JpaTrustImportService.java      — @Alternative: seed-if-absent for all score types
│       │   │   ├── TrustBootstrapSource.java       — SPI: fetchPriorTrust(actorId) → Optional<TrustExportPayload>
│       │   │   ├── NoOpTrustBootstrapSource.java   — @DefaultBean no-op (bootstrapping is opt-in)
│       │   │   └── TrustBootstrapService.java      — CDI bean: bootstrapIfNew(Set<actorId>) — wired into TrustScoreJob pre-pass
│       │   └── intercept/
│       │       ├── AuditedInterceptor.java          — CDI interceptor at Priority APPLICATION+1: handles @Audited, @Attested; auto-populates domainData from return value (#197)
│       │       ├── ComplianceSupplementInterceptor.java — CDI interceptor at Priority APPLICATION: standalone @ComplianceSupplement context push/pop (#198)
│       │       ├── ComplianceSupplementContext.java  — ThreadLocal context (same pattern as ProvenanceContext; moved from annotations/runtime in #199)
│       │       ├── ComplianceSupplementEnricher.java — LedgerEntryEnricher at Priority 35 (moved from annotations/runtime in #199)
│       │       ├── ProvenanceCapture.java           — CDI interceptor binding (@InterceptorBinding); attributes sourceEntityType, sourceEntitySystem
│       │       ├── ProvenanceCaptureInterceptor.java — CDI interceptor: pushes ProvenanceContext before proceed, pops in finally
│       │       ├── ProvenanceCaptureEnricher.java   — LedgerEntryEnricher: attaches ProvenanceSupplement from active context
│       │       ├── ProvenanceContext.java           — @ApplicationScoped ThreadLocal stack; supports nested @ProvenanceCapture scopes
│       │       └── SourceEntityId.java              — parameter annotation: marks the UUID to use as sourceEntityId
│       │   └── identity/
│       │       │   (SPIs, resolvers, and No-Op impls live in casehub-platform-identity — see below)
│       │       ├── ActorDIDEnricher.java                 — @Priority(40) enricher: populates LedgerEntry.actorDid from ActorDIDProvider; skips ActorIdentityBindingEntry (instanceof guard — prevents event loop and boundDid/actorDid discrepancy)
│       │       ├── ActorIdentityBindingObserver.java     — @ObservesAsync → @Transactional(REQUIRES_NEW) persistence of ActorIdentityBindingEntry; injects LedgerEntryRepository (not ActorIdentityBindingRepository) — full save pipeline runs including Merkle frontier update
│       │       ├── ActorIdentityValidationEnricher.java  — @Priority(50) enricher: full DID/key/VC validation pipeline; sets pendingIdentityStatus
│       │       ├── AgentIdentityVerificationService.java — read-path: verifyIdentityBinding(LedgerEntry) → IdentityVerificationResult
│       │       ├── ReactiveAgentIdentityVerificationService.java — @DefaultBean @Unremovable: Uni<IdentityVerificationResult> bridge wrapping blocking service on worker pool; no Hibernate Reactive dep, always active
│       │       ├── IdentityCacheInvalidator.java         — bridges AgentKeyRotatedEvent → platform cache invalidation; @Observes AgentKeyRotatedEvent, calls actorDIDProvider.invalidate(actorId) if provider is AbstractCachingIdentityProvider
│       │       ├── LedgerIdentityEnforcementListener.java — @EntityListeners @PrePersist: ENFORCE mode gate (JPA-only)
│       │       └── LedgerIdentityViolationException.java — thrown by enforcement listener in ENFORCE mode
│       └── privacy/
│           ├── ActorIdentityProvider.java   — moved to api/spi/ (ledger#142); SPI: tokenise/resolve/erase actor identities; tokenise takes ActorType — only HUMAN actors are pseudonymised; tokeniseForQuery() returns Optional<String> (empty=null input; present=always query by token or raw actorId)
│           ├── DecisionContextSanitiser.java — SPI: sanitise decisionContext JSON before persist
│           ├── InternalActorIdentityProvider.java — built-in UUID token impl (config-gated)
│           ├── LedgerErasureService.java    — GDPR Art.17 erasure (CDI bean); erase(rawActorId, ErasureReason) severs token→identity mapping; when casehub.ledger.erasure-receipt.enabled=true (default false) writes ErasureReceiptLedgerEntry in same TX; ErasureResult carries Optional<UUID> receiptEntryId
│           └── LedgerPrivacyProducer.java   — CDI producer for both SPIs (@DefaultBean); injects Instance<EntityManager> (not EntityManager directly) so datasource-free deployments don't fail CDI augmentation (#149)
│   └── src/main/resources/db/ledger/migration/
│       ├── V1000__ledger_base_schema.sql    — ledger_entry + ledger_attestation tables; ledger_merkle_frontier (tenancy_id, UNIQUE(subject_id, tenancy_id, level)); ledger_subject_sequence (composite PK (subject_id, tenancy_id)); ledger_entry UNIQUE index (subject_id, tenancy_id, sequence_number)
│       ├── V1001__actor_trust_score.sql     — actor_trust_score two-column key model (UUID PK, score_type GLOBAL|CAPABILITY|DIMENSION|CAPABILITY_DIMENSION, capability_key + dimension_key, CHECK constraint, NULLS NOT DISTINCT)
│       ├── V1002__ledger_supplement.sql     — supplement tables + drops moved columns
│       ├── V1003__ledger_entry_archive.sql  — ledger_entry_archive table
│       ├── V1004__actor_identity.sql        — actor_identity pseudonymisation table
│       ├── V1005__agent_signature.sql       — agent_signature + agent_public_key BYTEA nullable on ledger_entry; CHECK constraint enforces pair nullability
│       ├── V1006__agent_key_ref.sql         — agent_key_ref TEXT on ledger_entry; CHECK enforces null iff agent_signature null
│       ├── V1007__key_rotation_entry.sql    — key_rotation_entry table (KeyRotationEntry subclass: previous_key_ref, new_key_ref, reason, effective_since)
│       ├── V1008__actor_identity_binding.sql        — actor_did TEXT nullable on ledger_entry; actor_identity_binding join table
│       ├── V1009__plain_ledger_entry.sql            — plain_ledger_entry join table (PlainLedgerEntry subclass for domain-agnostic event writes via OutcomeRecorder)
│       ├── V1010__erasure_receipt_entry.sql         — erasure_receipt_entry join table (ErasureReceiptLedgerEntry; opt-in via casehub.ledger.erasure-receipt.enabled)
│       ├── V1011__ledger_entry_metadata.sql         — metadata TEXT column on ledger_entry for consumer-provided audit context (#172)
│       └── (trust_score_snapshot defined in V1000 — consolidated initial schema)
└── deployment/
│   └── src/main/java/io/casehub/ledger/deployment/
│       ├── LedgerBuildTimeConfig.java       — @ConfigRoot(BUILD_TIME): casehub.ledger.reactive.enabled (default false)
│       └── LedgerProcessor.java             — @BuildStep: FeatureBuildItem + excludeReactiveBeans (ExcludedTypeBuildItem when reactive.enabled=false) + validateFlywayMigrationLocation (WARN if db/ledger/migration absent from Flyway locations)
└── persistence-memory/
    └── src/main/java/io/casehub/ledger/memory/
        ├── InMemoryLedgerEntryRepository.java        — @Alternative @Priority(1); save pipeline mirrors JPA; sequenceCounters + subjectLocks keyed by SubjectKey(UUID subjectId, String tenancyId) for per-tenant isolation (#139); allEntries() method for delegates
        ├── InMemoryLedgerMerkleFrontierRepository.java — @Alternative @Priority(1); ConcurrentHashMap keyed by FrontierKey(UUID subjectId, String tenancyId) for per-tenant frontier isolation (#139)
        ├── InMemoryActorTrustScoreRepository.java    — @Alternative @Priority(1); composite key: actorId|scoreType|cap|dim
        ├── InMemoryKeyRotationRepository.java        — @Alternative @Priority(1); reads via blocking.allEntries()
        ├── InMemoryActorIdentityBindingRepository.java — @Alternative @Priority(1); read-only delegate — reads via blocking.allEntries() filtered by instanceof ActorIdentityBindingEntry + tenancyId; mirrors InMemoryKeyRotationRepository
        ├── InMemoryErasureReceiptRepository.java — @Alternative @Priority(1); read-only delegate — reads via blocking.allEntries() filtered by instanceof ErasureReceiptLedgerEntry + tenancyId
        ├── InMemoryTrustScoreSnapshotRepository.java — @Alternative @Priority(1); CopyOnWriteArrayList store; sorted DESC retrieval
        ├── InMemoryAgentSigner.java              — @Alternative @Priority(1); ConcurrentHashMap<String,KeyPair>; register(actorId,keyPair) + clear() for session-boundary reset; see #104
        ├── InMemoryReactiveLedgerEntryRepository.java — @IfBuildProperty(reactive.enabled=true); delegates to blocking
        ├── InMemoryReactiveKeyRotationRepository.java — @IfBuildProperty(reactive.enabled=true); delegates to blocking
        ├── InMemoryCrossTenantLedgerEntryRepository.java — @Alternative @Priority(1); cross-tenant delegate
        └── InMemoryCrossTenantReactiveLedgerEntryRepository.java — @IfBuildProperty(reactive.enabled=true); delegates to blocking
└── testing/
    └── src/main/java/io/casehub/ledger/testing/
        ├── NoOpLedgerEntryRepository.java           — @Alternative @Priority(1); no-op LedgerEntryRepository for consumer test profiles
        └── NoOpReactiveLedgerEntryRepository.java   — @Alternative @Priority(1); no-op ReactiveLedgerEntryRepository for consumer test profiles
└── graphql/                              — opt-in GraphQL resolvers + MCP domain provider (plain JAR, not a Quarkus extension)
    └── src/main/java/io/casehub/ledger/graphql/
        ├── LedgerQueryResolver.java             — @GraphQLApi @McpDomain("ledger"): ledgerEntries, ledgerEntry, ledgerAttestations, trustScore, trustCapabilityScore, trustRoutingProfile (composite), merkleVerification
        ├── LedgerMutationResolver.java          — @GraphQLApi @McpDomain("ledger"): appendLedgerEntry (with domainData), createAttestation
        ├── LedgerModelEnricher.java             — @McpDomain("ledger") ModelEnricher: summary + state for MCP hierarchical model
        └── dto/                                 — GraphQL input/output records (decoupled from JPA entities)
            ├── LedgerEntryType.java, LedgerAttestationType.java, TrustScoreType.java
            ├── TrustCapabilityScoreType.java, TrustRoutingProfileType.java
            ├── MerkleVerificationType.java, LedgerEntryPage.java
            └── LedgerEntryFilterInput.java, AppendLedgerEntryInput.java, CreateAttestationInput.java
└── rest/                                 — opt-in JAX-RS REST endpoints (plain JAR, not a Quarkus extension)
    └── src/main/java/io/casehub/ledger/rest/
        ├── LedgerEntryResource.java             — GET /api/v1/ledger/entries — query by subject or actor; GET /{id}; GET /{id}/caused-by
        ├── MerkleVerificationResource.java      — GET /api/v1/ledger/verify — integrity check; GET /entries/{id}/proof — inclusion proof
        ├── TrustScoreResource.java              — GET /api/v1/ledger/trust/{actorId} — global, capability, dimension scores
        ├── AttestationResource.java             — GET/POST /api/v1/ledger/entries/{id}/attestations — list and create
        ├── LedgerExceptionMapper.java           — @Provider: maps domain exceptions to HTTP 400/404/409/500
        ├── LedgerNotFoundException.java         — 404 signal
        ├── LedgerRestUtil.java                  — tenancy ID defaulting
        └── dto/                                 — request/response records (decoupled from JPA entities)
            ├── LedgerEntryResponse.java
            ├── AttestationResponse.java
            ├── CreateAttestationRequest.java
            ├── InclusionProofResponse.java
            ├── TrustScoreResponse.java
            ├── VerificationResponse.java
            └── LedgerDtoMapper.java             — entity → DTO conversion
└── annotations/                          — annotation-driven audit, compliance, and attestation (Quarkus extension)
    ├── pom.xml                           — aggregator POM
    ├── runtime/                          → io.casehub:casehub-ledger-annotations
    │   └── src/main/java/io/casehub/ledger/annotations/
    │       ├── Audited.java              — @InterceptorBinding: marks methods for ledger entry recording
    │       ├── Attested.java             — composes with @Audited for entry + attestation via OutcomeRecorder
    │       ├── ComplianceSupplement.java — @InterceptorBinding: attaches EU AI Act / GDPR metadata (standalone or with @Audited)
    │       ├── SubjectId.java            — @Target(PARAMETER): required aggregate key
    │       └── ActorId.java, TenancyId.java, DecisionContext.java, ConfidenceScore.java, Verdict.java — parameter annotations
    └── deployment/                       → io.casehub:casehub-ledger-annotations-deployment
        └── src/main/java/.../deployment/
            └── LedgerAnnotationsProcessor.java — Jandex build-time validation (@SubjectId required, type checks, @Attested requires @Audited, @ComplianceSupplement standalone OK)
└── signing/                              — first-class signing adapter modules (profile: with-signing)
    ├── pom.xml                           — aggregator POM
    ├── vault-transit/                    → io.casehub:casehub-ledger-vault-transit (pure Java; HttpClient + Jackson)
    ├── vault-transit-quarkus/            → io.casehub:casehub-ledger-vault-transit-quarkus (CDI adapter)
    ├── aws-kms/                          → io.casehub:casehub-ledger-aws-kms (pure Java; AWS SDK v2)
    ├── aws-kms-quarkus/                  → io.casehub:casehub-ledger-aws-kms-quarkus (CDI adapter)
    ├── gcp-kms/                          → io.casehub:casehub-ledger-gcp-kms (pure Java; Google Cloud KMS)
    ├── gcp-kms-quarkus/                  → io.casehub:casehub-ledger-gcp-kms-quarkus (CDI adapter)
    ├── azure-keyvault/                   → io.casehub:casehub-ledger-azure-keyvault (pure Java; Azure Key Vault + EcSignatureConverter)
    └── azure-keyvault-quarkus/           → io.casehub:casehub-ledger-azure-keyvault-quarkus (CDI adapter)
```

**Signing module architecture:** Two-layer per provider: pure Java client (zero framework deps, usable from Spring/Micronaut/plain Java) + Quarkus CDI adapter (extends `AbstractCachingAgentSigner`, `@Alternative @Priority(1)`). EC keys only — RSA out of scope. Consumer activation: `quarkus.arc.selected-alternatives=<adapter class>`.

**Vault Transit auth methods:** `VaultTransitConfig.AuthConfig` supports `AuthMethod.TOKEN` (static token), `AuthMethod.APPROLE` (AppRole auth with role-id + secret-id), `AuthMethod.KUBERNETES` (Kubernetes auth with service account token), and `AuthMethod.JWT` (JWT/OIDC auth with `role` + `jwt` from file/env). `JwtVaultTokenSource` handles JWT auth via `/v1/auth/jwt/login` endpoint. Browser-based OIDC flow (two-step auth URL + callback) deferred to #171.

---

## Build and Test

```bash
# Build all modules
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install

# Run all tests (all modules)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test

# Run tests for a specific module
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl runtime   # QuarkusTest + IT
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl api        # pure JUnit 5, no Quarkus runtime

# If you edited api/ and want to run only runtime tests, install api first:
# (mvn test -pl runtime resolves api from .m2 cache — source changes in api are invisible otherwise)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn install -pl api -q && \
  JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl runtime

# Run signing module tests (requires with-signing profile)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -Pwith-signing

# Run a specific signing module
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl signing/aws-kms -Pwith-signing

# Native image build (requires GraalVM)
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home \
  mvn package -Pnative -DskipTests
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

**Docker required for PostgreSQL tests.** Two test classes (`JpaSequenceNumberPgIT`, `LedgerHealthJobPgIT`) use Testcontainers to start a PostgreSQL 17 container. Docker must be running for these tests. All other tests use H2 in-memory and do not require Docker.

---

## Java and GraalVM on This Machine

```bash
# Java 26 (Oracle, system default) — use for dev and tests
JAVA_HOME=$(/usr/libexec/java_home -v 26)

# GraalVM 25 — use for native image builds only
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home
```

---

## Ecosystem Context

```
casehub-ledger       (audit/provenance — this project)
    ↑         ↑
 casehub-work    casehub-qhorus    (each adds its own LedgerEntry subclass)
    ↑         ↑
          claudony
```

casehub-work and casehub-qhorus are siblings — neither depends on the other. Both depend on
`casehub-ledger`. Claudony composes them.

---

## Schema Convention

**No existing installations** — there are no deployed instances of `casehub-ledger` in production.
All schema changes go directly into the base migration files (V1000–V1011) or into a new base
migration file. Do NOT create incremental migration scripts to evolve the schema. Rewrite the
relevant migration file in place. Treat every schema change as a clean-slate design decision.

Migrations live at `runtime/src/main/resources/db/ledger/migration/`. Consumers must add
`classpath:db/ledger/migration` to `quarkus.flyway.locations`. Omitting it triggers a
build-time warning from `LedgerProcessor.validateFlywayMigrationLocation`.

---

## Project Artifacts

Paths that are project content (not workspace noise). Skills use this to avoid
filtering or dropping commits that touch these paths.

| Path | What it is |
|------|------------|
| `docs/adr/` | Architecture decision records |
| `CLAUDE.md` | Project conventions (build, test, naming) |
| `ARC42STORIES.MD` | Architecture and delivery documentation (arc42stories format) |

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/ledger
**Changelog:** GitHub Releases (run `gh release create --generate-notes` at milestones)

**Automatic behaviours (Claude follows these at all times in this project):**
- **Before implementation begins** — when the user says "implement", "start coding", "execute the plan", "let's build", or similar: check if an active issue or epic exists. If not, run issue-workflow Phase 1 to create one **before writing any code**.
- **Before writing any code** — check if an issue exists for what's about to be implemented. If not, draft one and assess epic placement (issue-workflow Phase 2) before starting. Also check if the work spans multiple concerns.
- **Before any commit** — run issue-workflow Phase 3 (via git-commit) to confirm issue linkage and check for split candidates. This is a fallback — the issue should already exist from before implementation began.
- **All commits should reference an issue** — `Refs #N` (ongoing) or `Closes #N` (done). If the user explicitly says to skip ("commit as is", "no issue"), ask once to confirm before proceeding — it must be a deliberate choice, not a default.

---

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.


## Ecosystem Conventions

All casehubio projects align on these conventions:

**Quarkus version:** All projects use `3.32.2`. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:** Add to `pom.xml` `<repositories>`:
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```
CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

**Cross-project SNAPSHOT versions:** `casehub-ledger` and `casehub-work` modules are `0.2-SNAPSHOT` resolved from GitHub Packages. Declare in `pom.xml` properties and `<dependencyManagement>` — no hardcoded versions in submodule poms.
