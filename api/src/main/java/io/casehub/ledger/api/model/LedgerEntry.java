package io.casehub.ledger.api.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


import io.casehub.platform.api.identity.ActorType;

import io.casehub.ledger.api.model.supplement.CompensationSupplement;
import io.casehub.ledger.api.model.supplement.ComplianceSupplement;
import io.casehub.ledger.api.model.supplement.LedgerSupplement;
import io.casehub.ledger.api.model.supplement.LedgerSupplementSerializer;
import io.casehub.ledger.api.model.supplement.ProvenanceSupplement;

/**
 * Abstract base for all ledger entries.
 *
 * <h2>Core fields</h2>
 * <p>
 * {@code LedgerEntry} holds exactly the fields that are relevant for every entry,
 * every consumer, every time: the subject aggregate, sequence position, actor identity,
 * timestamp, and the tamper-evident hash chain. Nothing else.
 *
 * <h2>Supplements</h2>
 * <p>
 * Optional cross-cutting concerns are handled by
 * {@link io.casehub.ledger.api.model.supplement.LedgerSupplement} subclasses
 * attached via {@link #attach(LedgerSupplement)}:
 * <ul>
 * <li>{@link ComplianceSupplement} — GDPR Art.22 decision snapshot, governance</li>
 * <li>{@link ProvenanceSupplement} — workflow source entity</li>
 * </ul>
 * If a consumer never calls {@code attach()}, no supplement tables are written
 * and the lazy {@code supplements} list is never initialised — zero overhead.
 *
 * <h2>JPA JOINED inheritance</h2>
 * <p>
 * Domain-specific subclasses (e.g. {@code WorkItemLedgerEntry} in Tarkus) extend
 * the JPA entity subclass and add a sibling table joined on {@code id}. Supplements
 * are orthogonal to subclasses — any subclass can attach any supplement.
 *
 * <h2>Hash chain</h2>
 * <p>
 * The {@code digest} field holds the RFC 9162 leaf hash — {@code SHA-256(0x00 | canonical fields)}.
 * Chain integrity is maintained by the Merkle Mountain Range in {@code LedgerMerkleFrontier}.
 *
 * <h2>Two-tier design</h2>
 * <p>
 * This class is {@code @MappedSuperclass} — it defines all persistent fields and the
 * canonical bytes computation. JPA-specific machinery ({@code @Entity}, {@code @Inheritance},
 * {@code @NamedQuery}, {@code @EntityListeners}) lives on the runtime
 * {@code JpaLedgerEntry} subclass. Non-JPA backends (in-memory, event-sourced) extend
 * this class directly.
 */
public abstract class LedgerEntry {

    private static final byte[] EMPTY_BYTES = new byte[0];

    private static final ObjectMapper DOMAIN_DATA_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    // ── Core identity ─────────────────────────────────────────────────────────

    /** Primary key — UUID assigned eagerly at construction time. */
    public UUID id = UUID.randomUUID();

    /**
     * The aggregate this entry belongs to — the domain object whose lifecycle
     * is being recorded. Scopes the sequence number and hash chain.
     */
    public UUID subjectId;

    /**
     * Tenant that owns this entry. Non-null, defaults to
     * {@link io.casehub.platform.api.identity.TenancyConstants#DEFAULT_TENANT_ID}.
     * Set at persist time by the repository — callers do not set this directly.
     */
    public String tenancyId;

    /** Position of this entry in the per-subject ledger sequence (1-based). */
    public int sequenceNumber;

    /** Whether this entry is a command (intent), event (fact), or attestation record. */
    public LedgerEntryType entryType;

    // ── Actor ─────────────────────────────────────────────────────────────────

    /** Identity of the actor who triggered this transition. */
    public String actorId;

    /** Whether the actor is a human, autonomous agent, or the system itself. */
    public ActorType actorType;

    /** The functional role of the actor in this transition — e.g. {@code "Resolver"}. */
    public String actorRole;

    // ── Timing ────────────────────────────────────────────────────────────────

    /** When this entry was recorded — set automatically on first persist. */
    public Instant occurredAt;

    // ── Hash chain ────────────────────────────────────────────────────────────

    /**
     * RFC 9162 leaf hash: {@code SHA-256(0x00 | canonicalFields)}.
     * Null when hash chain is disabled ({@code casehub.ledger.hash-chain.enabled=false}).
     */
    public String digest;

    // ── Observability & causality ─────────────────────────────────────────────

    /**
     * OpenTelemetry trace ID linking this entry to a distributed trace.
     * W3C trace context format (32-char hex string).
     */
    public String traceId;

    /**
     * FK to the ledger entry that causally produced this entry.
     * Null for entries with no known causal predecessor.
     *
     * <p>
     * Enables cross-system causal chain traversal via
     * {@code LedgerEntryRepository#findCausedBy(UUID)}.
     * When Claudony orchestrates Tarkus → Qhorus, each downstream entry's
     * {@code causedByEntryId} points to its upstream cause.
     */
    public UUID causedByEntryId;

    // ── Agent signing ─────────────────────────────────────────────────────────

    /**
     * Ed25519 signature of {@link #canonicalBytes()}
     * by the agent identified in {@link #actorId}.
     * Null when the actor is not configured for bilateral signing.
     */
    public byte[] agentSignature;

    /**
     * X.509-encoded Ed25519 public key of the signing agent.
     * Stored alongside the signature for self-contained verification —
     * entries remain verifiable without any external key management system.
     * Null when {@link #agentSignature} is null.
     */
    public byte[] agentPublicKey;

    /**
     * Self-derived identifier for the key generation that produced {@link #agentSignature}.
     * Value: {@code Base64URL(SHA-256(agentPublicKey))} — computable from stored bytes.
     * Null when {@link #agentSignature} is null.
     */
    public String agentKeyRef;

    /** DID URI bound to this entry's actorId at write time. Null when no binding is configured. */
    public String actorDid;

    // ── Consumer metadata ─────────────────────────────────────────────────────

    /**
     * Consumer-provided freeform JSON context for this entry.
     *
     * <p>Carries domain-specific audit data (routing rationale, candidate lists,
     * decision explanations) that is opaque to the ledger. Stored verbatim,
     * included in {@link #canonicalBytes()} for tamper evidence, returned on reads.
     *
     * <p><strong>Contract:</strong> Must be valid JSON. Must NOT contain personally
     * identifiable information (PII) — the GDPR Art.17 erasure mechanism severs
     * the token→identity mapping but does not scan or modify field contents.
     *
     * @see io.casehub.ledger.api.model.OutcomeRecord#withMetadata(String)
     * @see io.casehub.ledger.api.model.AuditRecord#withMetadata(String)
     */
    public String metadata;

    // ── Domain data ───────────────────────────────────────────────────────────

    /**
     * Flexible key-value payload for domain-specific entry data.
     *
     * <p>Used in centralized mode where remote apps cannot provide typed JPA
     * subclasses. Stored as JSONB in PostgreSQL. Included in
     * {@link #canonicalBytes()} when non-null and non-empty — serialized with
     * sorted keys for deterministic hashing.
     *
     * <p>Complementary to {@link #domainContentBytes()}: typed subtypes use
     * domainContentBytes() for their typed fields; remote entries use domainData.
     */
    public Map<String, Object> domainData;

    // ── Supplements ───────────────────────────────────────────────────────────

    /**
     * In-memory supplements attached to this entry.
     * Marked {@code @Transient} — JPA supplement entities are persisted explicitly
     * by the save pipeline (each supplement type has its own self-contained table).
     * Use {@link #attach(LedgerSupplement)}, {@link #compliance()},
     * and {@link #provenance()} for type-safe access.
     */
    public List<LedgerSupplement> supplements = new ArrayList<>();

    /**
     * Denormalised JSON snapshot of all attached supplements.
     * Written automatically by {@link #attach(LedgerSupplement)}.
     * Enables fast single-entry reads without joining supplement tables.
     * Format: {@code {"COMPLIANCE":{...},"PROVENANCE":{...}}}.
     */
    public String supplementJson;

    // ── Supplement helpers ────────────────────────────────────────────────────

    /**
     * Attach a supplement to this entry, replacing any existing supplement of the
     * same type. Also refreshes {@link #supplementJson} to keep it in sync.
     *
     * <p>
     * <strong>Important:</strong> After attaching, do not mutate the supplement's fields
     * directly without calling {@link #refreshSupplementJson()} — direct field mutation
     * leaves {@code supplementJson} stale. Prefer re-attaching a new supplement instance
     * when fields need to change.
     *
     * @param supplement the supplement to attach; must not be null
     */
    public void attach(final LedgerSupplement supplement) {
        Objects.requireNonNull(supplement, "supplement must not be null");
        supplements.removeIf(s -> s.getClass() == supplement.getClass());
        supplements.add(supplement);
        supplementJson = LedgerSupplementSerializer.toJson(supplements);
    }

    /**
     * Refreshes {@link #supplementJson} from the current state of the
     * {@link #supplements} list.
     *
     * <p>
     * Call this after mutating a supplement's fields in-place (e.g. when adding
     * {@code rationale} to an already-attached {@link ComplianceSupplement}):
     *
     * <pre>{@code
     * entry.compliance().ifPresent(cs -> {
     *     cs.rationale = reason;
     *     entry.refreshSupplementJson();
     * });
     * }</pre>
     */
    public void refreshSupplementJson() {
        supplementJson = LedgerSupplementSerializer.toJson(supplements);
    }

    /**
     * Returns the {@link ComplianceSupplement} attached to this entry, if any.
     *
     * @return the compliance supplement, or empty if none is attached
     */
    public Optional<ComplianceSupplement> compliance() {
        return supplements.stream()
                .filter(ComplianceSupplement.class::isInstance)
                .map(ComplianceSupplement.class::cast)
                .findFirst();
    }

    /**
     * Returns the {@link ProvenanceSupplement} attached to this entry, if any.
     *
     * @return the provenance supplement, or empty if none is attached
     */
    public Optional<ProvenanceSupplement> provenance() {
        return supplements.stream()
                .filter(ProvenanceSupplement.class::isInstance)
                .map(ProvenanceSupplement.class::cast)
                .findFirst();
    }

    /**
     * Returns the {@link CompensationSupplement} attached to this entry, if any.
     *
     * @return the compensation supplement, or empty if none is attached
     */
    public Optional<CompensationSupplement> compensation() {
        return supplements.stream()
                .filter(CompensationSupplement.class::isInstance)
                .map(CompensationSupplement.class::cast)
                .findFirst();
    }

    // ── Canonical bytes ───────────────────────────────────────────────────────

    /**
     * Compute the canonical byte representation of this entry for tamper-evident hashing.
     *
     * <p>
     * Includes all tamper-critical fields:
     * <ul>
     * <li>Base identity: {@code subjectId}, {@code sequenceNumber}, {@code entryType}</li>
     * <li>Actor context: {@code actorId}, {@code actorRole}, {@code actorType}</li>
     * <li>Timing: {@code occurredAt} (truncated to milliseconds)</li>
     * <li>Multi-tenancy: {@code tenancyId}</li>
     * <li>Causality: {@code causedByEntryId}</li>
     * <li>Consumer metadata: {@code metadata} (always present — empty string when null)</li>
     * <li>Supplements: {@code supplementJson} (if non-null)</li>
     * <li>Domain content: {@code domainContentBytes()} (if non-empty)</li>
     * </ul>
     *
     * <p>
     * Format: 10 pipe-delimited positional base fields, followed by optional supplement JSON
     * and domain content:
     * {@code subjectId|seqNum|entryType|actorId|actorRole|occurredAt|tenancyId|actorType|causedByEntryId|metadata[|supplementJson][|domainContent]}
     *
     * <p>
     * Null fields are rendered as empty strings. Deterministic — same entry produces same bytes.
     * {@code metadata} is positional (always present) to avoid ambiguity with the conditionally
     * appended {@code supplementJson}.
     *
     * @return canonical UTF-8 byte array for this entry
     */
    public final byte[] canonicalBytes() {
        final List<String> parts = new ArrayList<>(10);

        // Base fields (10 positional fields, all pipe-delimited)
        parts.add(subjectId != null ? subjectId.toString() : "");
        parts.add(String.valueOf(sequenceNumber));
        parts.add(entryType != null ? entryType.name() : "");
        parts.add(actorId != null ? actorId : "");
        parts.add(actorRole != null ? actorRole : "");
        parts.add(occurredAt != null
                ? occurredAt.truncatedTo(ChronoUnit.MILLIS).toString()
                : "");
        parts.add(tenancyId != null ? tenancyId : "");
        parts.add(actorType != null ? actorType.name() : "");
        parts.add(causedByEntryId != null ? causedByEntryId.toString() : "");
        parts.add(metadata != null ? metadata : "");

        // Build base canonical string
        final StringBuilder canonical = new StringBuilder(String.join("|", parts));

        // Append supplement JSON if present
        if (supplementJson != null && !supplementJson.isEmpty()) {
            canonical.append("|").append(supplementJson);
        }

        // Append domain data if present
        if (domainData != null && !domainData.isEmpty()) {
            canonical.append("|").append(canonicalDomainData(domainData));
        }

        // Append domain content if present
        final byte[] domainBytes = domainContentBytes();
        if (domainBytes.length > 0) {
            canonical.append("|");
            canonical.append(new String(domainBytes, StandardCharsets.UTF_8));
        }

        return canonical.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String canonicalDomainData(final Map<String, Object> data) {
        try {
            return DOMAIN_DATA_MAPPER.writeValueAsString(data);
        } catch (final Exception e) {
            throw new IllegalStateException("domainData serialization failed", e);
        }
    }

    /**
     * Returns domain-specific content bytes for hash protection.
     *
     * <p>Subclasses that declare persistent fields on join tables MUST override
     * this method to include those fields. The returned bytes are appended to the
     * canonical form used by both the Merkle leaf hash and the agent signature.
     *
     * <p>Build-time enforcement: {@code LedgerProcessor} produces a deployment error
     * if a {@code LedgerEntry} subclass declares persistent fields (non-{@code @Transient})
     * but does not override this method.
     *
     * @return domain content bytes; empty array if no domain fields exist
     */
    protected byte[] domainContentBytes() {
        return EMPTY_BYTES;
    }
}
