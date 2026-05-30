package io.casehub.ledger.runtime.service;

import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * SPI for auto-populating fields on {@link LedgerEntry} at persist time.
 *
 * <p>
 * <strong>Ordering:</strong> Enrichers are invoked in ascending {@code @Priority} order
 * by {@link LedgerEnricherPipeline}. All implementations MUST carry a
 * {@code @jakarta.annotation.Priority} annotation. An enricher without {@code @Priority}
 * sorts last ({@code Integer.MAX_VALUE}). Assigned priorities in casehub-ledger:
 * <ul>
 *   <li>10 — TraceIdEnricher</li>
 *   <li>20 — AgentSignatureEnricher</li>
 *   <li>30 — ProvenanceCaptureEnricher</li>
 *   <li>40 — ActorDIDEnricher (added in #81)</li>
 *   <li>50 — ActorIdentityValidationEnricher (added in #81)</li>
 * </ul>
 *
 * <p>
 * Implementations are CDI beans discovered via {@code @Inject @Any Instance<LedgerEntryEnricher>}
 * and invoked in the {@code @PrePersist} pipeline. Implementations must be idempotent and
 * non-fatal — a thrown exception is logged and swallowed; the persist is never blocked.
 *
 * <p>
 * Register an implementation by creating an {@code @ApplicationScoped} CDI bean that
 * implements this interface. No registration step is required.
 */
public interface LedgerEntryEnricher {

    /**
     * Enrich the given entry before it is persisted.
     * Called once per {@code @PrePersist} event. Must not throw. Implementations must be
     * idempotent — this method may be called more than once on the same entry under retried transactions.
     *
     * <p>
     * <strong>Contract:</strong> Enrichers must not modify the canonical fields used in
     * the Merkle leaf hash: {@code subjectId}, {@code sequenceNumber}, {@code entryType},
     * {@code actorId}, {@code actorRole}, {@code occurredAt}. These fields are hashed
     * before enrichment in some execution paths. Modifying them in an enricher produces
     * a mismatch between the leaf hash and the stored entry state.
     */
    void enrich(LedgerEntry entry);
}
