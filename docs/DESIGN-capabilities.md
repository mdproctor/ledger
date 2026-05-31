# CaseHub Ledger — Capabilities Design

> Part of the casehub-ledger design documentation. See [`DESIGN.md`](DESIGN.md) for
> entity model, architecture, SPI contracts, and configuration.

## Merkle Mountain Range

Replaces the linear hash chain. Per-subject stored frontier gives O(log N) inclusion proofs.

**Hash functions (RFC 9162 domain separation):**
- Leaf: `SHA-256(0x00 | subjectId|seqNum|entryType|actorId|actorRole|occurredAt)`
- Internal node: `SHA-256(0x01 | left_bytes | right_bytes)` — raw 32-byte values, not hex

**Frontier:** `ledger_merkle_frontier` table stores at most `Integer.bitCount(N)` rows per subject after N entries. The tree root = fold frontier ASC by level.

**`LedgerMerkleTree`** (pure static utility) — `leafHash()`, `internalHash()`, `append()`, `treeRoot()`, `inclusionProof()`, `verifyProof()`. No CDI, no side effects.

**`LedgerVerificationService`** (`@ApplicationScoped`) — `treeRoot(UUID)`, `inclusionProof(UUID)`, `verify(UUID)`. Auto-activated.

**External publishing** (opt-in) — `LedgerMerklePublisher` posts Ed25519-signed tlog-checkpoints to `casehub.ledger.merkle.publish.url` on each frontier update. Disabled by default.

## W3C PROV-DM JSON-LD Export

Exports a subject's complete audit trail as a W3C PROV-DM JSON-LD document for
interoperability with ML pipeline auditing tools, RDF stores, and regulatory systems.

**Mapping:**
- `LedgerEntry` → `prov:Entity` (`ledger:entry/<uuid>`)
- `actorId` → `prov:Agent` (`ledger:actor/<actorId>`, deduplicated per export)
- Entry action → `prov:Activity` (`ledger:activity/<uuid>`)

**Relations:** `wasGeneratedBy` (every entry), `wasAssociatedWith` (when actorId set),
`wasDerivedFrom` (sequential chain + `causedByEntryId` cross-subject causality),
`hadPrimarySource` (when `ProvenanceSupplement` attached).

**`LedgerProvSerializer`** (pure static) — `toProvJsonLd(UUID subjectId, List<LedgerEntry> entries)`.
No CDI, no DB access. Must be called within a `@Transactional` boundary so supplements lazy-load.

**`LedgerProvExportService`** (`@ApplicationScoped`) — `exportSubject(UUID subjectId)`.
Fetches entries, initialises supplements, delegates to serialiser. Auto-activated.

Consumers can auto-attach `ProvenanceSupplement` via the `@ProvenanceCapture` CDI interceptor binding — see `docs/DESIGN.md` (Supplements section) for usage.

See `docs/prov-dm-mapping.md` for the full field-by-field mapping including all supplement fields.

## `@ConfigRoot` alongside `@ConfigMapping`

`LedgerConfig` carries both annotations. `@ConfigMapping` provides the SmallRye nested
interface API; `@ConfigRoot(phase = ConfigPhase.RUN_TIME)` tells the
`quarkus-extension-processor` to emit the `quarkus.ledger` prefix into the extension
descriptor. Without `@ConfigRoot`, consuming apps see "Unrecognized configuration key"
warnings and cannot override defaults via `application.properties`.

---

## Privacy and Pseudonymisation

Actor identities (`actorId`, `attestorId`) and decision context blobs are intercepted on
every write by two SPIs in `io.casehub.ledger.runtime.privacy`:

| SPI | Default | Purpose |
|---|---|---|
| `ActorIdentityProvider` | Pass-through | Tokenise actor identities; resolve tokens back to real identities; sever mappings on erasure |
| `DecisionContextSanitiser` | Pass-through | Strip PII from `ComplianceSupplement.decisionContext` before persist |

Both defaults produce zero behaviour change. Supply a custom CDI bean to replace either.

**Built-in tokenisation** (`InternalActorIdentityProvider`) activates when
`casehub.ledger.identity.tokenisation.enabled=true`. Tokens are UUID strings stored in the
`actor_identity` table (V1004). Erasure deletes the mapping row — the token in existing
entries becomes permanently unresolvable but the Merkle hash chain is intact.

**`LedgerErasureService`** processes GDPR Art.17 erasure requests. Returns `ErasureResult`
with the actor identity, whether a mapping was found, and how many ledger entries were
affected (entries are not deleted).

**Config:**

| Key | Default | Description |
|---|---|---|
| `casehub.ledger.identity.tokenisation.enabled` | `false` | Activate built-in UUID token pseudonymisation (see `docs/PRIVACY.md`) |

---

## Trust Scoring — Capability Tags

`LedgerAttestation.capabilityTag` scopes each verdict to a capability domain. The sentinel
`CapabilityTag.GLOBAL = "*"` (never null) means the verdict applies to all capabilities.
A specific tag (e.g. `"security-review"`) restricts it to that capability only.

Three SPI query methods feed capability-aware trust computation:

| Method | Purpose |
|---|---|
| `findAttestationsByEntryIdAndCapabilityTag(entryId, tag)` | Capability-specific verdicts on one entry |
| `findAttestationsByEntryIdGlobal(entryId)` | Global (`"*"`) verdicts on one entry |
| `findAttestationsByAttestorIdAndCapabilityTag(attestorId, tag)` | All verdicts by one actor for one capability (feeds B2 `TrustScoreJob`) |

See `docs/DESIGN.md` (Roadmap → Trust scoring) for the `ActorTrustScore` discriminator model
that consumes these signals in B2 (✅ #61) and B3 (✅ #62).

---

## Trust Scoring — Capability Beta Scores

`TrustScoreJob` computes per-capability Beta models alongside the global score (✅ #61).
For each distinct `capabilityTag ≠ "*"` in the actor's attestations, a separate `CAPABILITY`
row is written to `actor_trust_score` with `scope_key = capabilityTag`.

The global score aggregation strategy is pluggable via `GlobalScoreStrategy` (see ADR 0008):

| Implementation | CDI | Behaviour | Backed by |
|---|---|---|---|
| `AllAttestationsGlobalStrategy` | `@DefaultBean` | All attestations → global Beta | Wang & Vassileva (2003) |
| `ExplicitGlobalAttestationsStrategy` | `@Alternative` | Only `"*"` attestations → global Beta | Semantic separation |
| `FrequencyWeightedGlobalStrategy` | `@Alternative` | Global = Σ(count_i/total × capScore_i) | Fan et al. (2015) |

`TrustGateService.meetsThreshold(actorId, capabilityTag, minTrust)` queries the CAPABILITY
score first and falls back to GLOBAL when none exists.

**Attestation aggregation** — before per-(entryId, capabilityTag) Beta computation, `AttestationAggregator` collapses multiple attestors' verdicts for the same entry into a single consensus verdict. Configurable via `casehub.ledger.trust-score.aggregation-strategy` (`WEIGHTED_MAJORITY` default, `UNANIMOUS_REQUIRED`, `FIRST_ATTESTOR`). This prevents a low-confidence minority verdict from disproportionately affecting actor scores.

---

## Trust Scoring — Dimension Scores (✅ #62)

Dimension scores capture continuous quality axes — orthogonal to capability scores. Where capability scores answer "can this actor do this task?", dimension scores answer "how well does this actor perform along a specific quality dimension?".

`LedgerAttestation` carries two nullable dimension fields:
- `trustDimension` — quality axis label (e.g. `"review-thoroughness"`, `"false-positive-rate"`)
- `dimensionScore` — continuous score in [0.0, 1.0]

Both are nullable. Ordinary attestations omit them; capability and global scoring are unaffected.

For each `(actorId, trustDimension)` pair, `TrustScoreJob` runs a dimension pass (after capability, before global) computing:

```
score = Σ(weight_i × confidence_i × dimensionScore_i) / Σ(weight_i × confidence_i)
weight_i = 2^(-ageInDays_i / halfLifeDays)
```

`TrustScoreComputer.computeDimensionScore()` performs this calculation. Pure time-based decay — no valence asymmetry. Result is stored as a `DIMENSION` row in `actor_trust_score` (`scope_key = dimensionName`). The `alpha_value`/`beta_value` columns are not meaningful for DIMENSION rows (stored as 0.0).

Query surface: `TrustGateService.dimensionScores(actorId)` → `Map<String, Double>`; `TrustGateService.dimensionScore(actorId, dimension)` → `Optional<Double>`.

---

## Agent Identity Model

LLM agents are stateless — each session starts fresh. For trust scores to accumulate and
audit trails to remain coherent, `actorId` must be stable across sessions. See ADR 0004.

### `actorId` format for LLM agents

```
{model-family}:{persona}@{major}
```

Examples: `"claude:tarkus-reviewer@v1"`, `"claude:message-router@v1"`.

| Segment | Description |
|---|---|
| `model-family` | LLM family: `claude`, `gpt`, `gemini`, … |
| `persona` | Stable role name from the agent's system instructions |
| `@{major}` | Major version; bumped when behaviour changes enough to warrant a new trust baseline |

### Versioning semantics

| Change type | Version impact | Trust |
|---|---|---|
| Behaviour break / major system instruction rework | Bump major (`v1` → `v2`) | New baseline |
| Feature add or tuning within same persona | No bump | Full inheritance |
| Bug fix or internal refactor | No bump | Full inheritance |

Versioning is intentional and human-controlled — it is not automatic. The question is:
"does this change warrant resetting the trust baseline?" If yes, bump. If no, don't.

**Concrete criteria for CLAUDE.md / system instruction changes:**

| Change | Bump? |
|---|---|
| Complete role redefinition | Yes |
| New decision authority (e.g. now approves financial actions) | Yes |
| Significant tightening or loosening of behavioural constraints | Yes |
| Prompt tuning, worked examples, clarifications | No |
| Memory file updates (accumulated knowledge) | No |
| Bug fix in instructions | No |
| Model family upgrade with same instructions | Consumer discretion |

**Score inheritance:** there is no inheritance API. When a consumer bumps from `@v1` to
`@v2`, v2 starts at Beta(1,1) = 0.5 (prior). A clean break is the safe default — the
new configuration earns trust independently. Consumers who want to pre-seed v2 trust
can write synthetic attestations before go-live, which leaves an explicit auditable trail.
See ADR 0004 for the full rationale.

### Three-layer model

| Layer | Field | Description |
|---|---|---|
| Persistent identity | `actorId` | Stable trust key — `"{model-family}:{persona}@{major}"` |
| Configuration binding | `ProvenanceSupplement.agentConfigHash` | SHA-256 hex of CLAUDE.md + system prompts; forensic only; nullable |
| Session correlation | `correlationId` | Ephemeral session/trace ID; not the actor ID |

### Consumer conventions

```java
entry.actorId   = "claude:tarkus-reviewer@v1";
entry.actorType = ActorType.AGENT;
entry.actorRole = "code-reviewer";  // broader functional classification

// Optional — populate for forensic config drift detection
ProvenanceSupplement ps = new ProvenanceSupplement();
ps.agentConfigHash = sha256HexOf(claudeMd + systemPrompts);
entry.attach(ps);
```

The `actorId` string is also valid as a `prov:Agent` URI in W3C PROV-DM exports
(`ledger:actor/claude:tarkus-reviewer@v1`). See `docs/prov-dm-mapping.md`.

### Trust score behaviour in agent mesh deployments

**Sparse sessions (1 decision, 1 attestation):** The Beta model handles this via the
prior. Beta(1,1) = 0.5 with no history; one positive attestation yields Beta(2,1) = 0.67.
The contribution is real but carries low confidence weight — exactly the right behaviour.
No special handling is needed for short-lived sessions.

**Concurrent sessions:** Multiple sessions with the same `actorId` running in parallel
produce concurrent appends to `ledger_attestation` — plain inserts with no conflict.
`TrustScoreJob` is a single serialized batch (Quarkus prevents concurrent executions of
the same job identity). Attestations written after a batch starts are picked up on the
next run. This is expected batch semantics, not a data hazard.

**Scheduling for high-interaction meshes:** The default `trust-score.schedule=24h` is
appropriate for most deployments. For dense agent meshes where an actor makes hundreds of
decisions per hour, reduce the interval so trust scores reflect recent behaviour:

```properties
casehub.ledger.trust-score.schedule=1h   # high-interaction mesh
casehub.ledger.trust-score.schedule=6h   # moderate interaction
casehub.ledger.trust-score.schedule=24h  # default (nightly)
```

There is no benefit to scheduling below the typical inter-attestation interval — scores
cannot change faster than attestations arrive.

---

## Agent DID/VC Identity Binding

`actorId` strings are cryptographically bound to W3C DIDs via a three-SPI pipeline. See ADR 0015.

### Three-SPI strategy model

| SPI | Default | Purpose |
|---|---|---|
| `ActorDIDProvider` | `NoOpActorDIDProvider` | Maps actorId → DID URI at write time |
| `DIDResolver` | `NoOpDIDResolver` | Resolves DID URI → DIDDocument (verificationMethods, alsoKnownAs) |
| `AgentCredentialValidator` | `NoOpCredentialValidator` | Validates VC binding claim (opt-in VC layer) |

Alternative implementations: `ConfiguredActorDIDProvider` (config-based), `KeyDIDResolver` (did:key, no HTTP), `WebDIDResolver` (did:web, HTTPS with SSRF protection), `ScimActorDIDProvider` (SCIM2 enterprise IdP — `@Alternative`, activate via `quarkus.arc.selected-alternatives`; queries `GET /scim/v2/Agents?filter=externalId eq "{actorId}"` and caches with TTL; see `docs/integration/scim2-agent-identity.md` in casehubio/parent).

**Cache invalidation:** `AgentKeyRotatedEvent` (CDI event fired by `KeyRotationService` after persist) is observed by `ActorIdentityValidationEnricher` and `ScimActorDIDProvider` to evict their per-actorId caches. `ReactiveKeyRotationService` fires via `fireAsync()` (fire-and-forget). `AbstractCachingAgentSigner` exposes `onKeyRotated(AgentKeyRotatedEvent)` as a plain method for concrete CDI subclasses to wire as `@Observes`.

### Write path

`ActorDIDEnricher` (@Priority 40) populates `LedgerEntry.actorDid`. `ActorIdentityValidationEnricher` (@Priority 50) validates in five steps: DID resolution → `alsoKnownAs` check → null-key check → key match → VC validation. Sets `entry.pendingIdentityStatus` (transient). Fires `AgentIdentityValidatedEvent` (VALID) or `AgentIdentityViolationEvent` (non-VALID) asynchronously.

`LedgerIdentityEnforcementListener` (`@EntityListeners` on `LedgerEntry`) reads `pendingIdentityStatus` and throws `LedgerIdentityViolationException` when `casehub.ledger.agent-identity.validation-mode=ENFORCE` and result is non-VALID. ENFORCE is JPA-only.

`ActorIdentityBindingObserver` observes async events and persists `ActorIdentityBindingEntry` in a `REQUIRES_NEW` transaction.

### Read path

`AgentIdentityVerificationService.verifyIdentityBinding(LedgerEntry)` → `IdentityVerificationResult`. Checks actorDid presence → agentPublicKey presence → DID resolution → alsoKnownAs → key match. Does NOT re-run VC validation — write-time results are in `ActorIdentityBindingRepository`.

`ReactiveAgentIdentityVerificationService.verifyIdentityBindingAsync(LedgerEntry)` → `Uni<IdentityVerificationResult>`. `@DefaultBean @Unremovable` bridge wrapping the blocking service on the Vert.x worker pool. Always active (no Hibernate Reactive dep, no `@IfBuildProperty` gate).

### `ActorIdentityBindingEntry`

`LedgerEntry` subclass (JOINED inheritance, table `actor_identity_binding`). `subjectId = UUID.nameUUIDFromBytes(actorId.getBytes(UTF_8))` — same derivation as `KeyRotationEntry`. Forms a unified actor lifecycle sequence with key rotation events. `entryType = EVENT`.

### Config

`casehub.ledger.agent-identity.*`: `validation-mode` (WARN | ENFORCE), `dids.<actorId>`, `did-resolver-cache-ttl-minutes` (default 5), `credential-cache-ttl-minutes` (default 60), `web-resolver-timeout-ms` (default 5000), `web-resolver-max-response-bytes` (default 1MiB).

`casehub.ledger.agent-identity.scim.*` (for `ScimActorDIDProvider @Alternative`): `endpoint` (`Optional<String>`), `auth-token` (`Optional<String>`), `timeout-ms` (default 5000), `cache-ttl-minutes` (default 5), `require-https` (default `true`). Activate the provider via `quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.service.identity.ScimActorDIDProvider`. When active, `dids.*` config-based mappings are superseded.

---

## Agent Mesh Topology

Three topologies are possible for deploying `casehub-ledger` across a mesh of LLM agents.

| Topology | Description | When appropriate |
|---|---|---|
| **Centralized** (current) | All agents write to one ledger instance | ✅ Correct for current ecosystem |
| **Hierarchical** | Each node has a local ledger; a root orchestrator aggregates | When Claudony itself becomes distributed |
| **Gossip-based** | Agents exchange attestations peer-to-peer and converge on a shared view | Only if adversarial agents are a real threat; significant complexity cost |

### Recommendation: centralized

For the current Tarkus / Qhorus / Claudony ecosystem, **centralized is correct**.
Claudony is the natural orchestrator and the natural ledger owner — all agent decisions
flow through it, so a single ledger gives complete visibility with no synchronisation
complexity. The Merkle Mountain Range provides tamper evidence; the EigenTrust pass
provides transitive reputation — both work best with a full global view of attestations.

### When hierarchical becomes relevant

If Claudony itself is distributed (multiple Claudony instances coordinating across
datacentres), a hierarchical topology becomes appropriate:

- Each Claudony instance maintains a local ledger for its agents.
- A root aggregator periodically merges frontier hashes and runs the EigenTrust pass
  over the combined attestation graph.
- Merkle roots from each shard can be cross-attested for tamper evidence.

No extension changes are required to support this — the current `LedgerEntry` model and
trust engine are topology-agnostic. The aggregation layer is a consumer responsibility.

### Why gossip is out of scope

Gossip-based convergence requires conflict resolution, eventual consistency guarantees,
and Byzantine-fault-tolerant attestation handling. This complexity is only warranted when
agents are genuinely adversarial and cannot trust the orchestrator. That is not the threat
model for the current ecosystem. If it becomes one, the right answer is a purpose-built
consensus layer, not an extension to `casehub-ledger`.

---

