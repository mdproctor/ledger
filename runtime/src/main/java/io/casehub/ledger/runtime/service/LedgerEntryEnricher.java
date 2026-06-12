package io.casehub.ledger.runtime.service;

import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * SPI for auto-populating fields on {@link LedgerEntry} at persist time.
 *
 * <p><strong>Pipeline position:</strong> Content enrichers run BEFORE hashing
 * and signing. The full save pipeline is:
 * enrichment, digest (leafHash), agent signature, persist.
 *
 * <p><strong>Ordering:</strong> Enrichers are invoked in ascending
 * {@code @Priority} order. All implementations MUST carry a
 * {@code @jakarta.annotation.Priority} annotation:
 * <ul>
 *   <li>10 — TraceIdEnricher</li>
 *   <li>30 — ProvenanceCaptureEnricher</li>
 *   <li>40 — ActorDIDEnricher</li>
 *   <li>50 — ActorIdentityValidationEnricher</li>
 * </ul>
 *
 * <p><strong>Contract:</strong>
 * <ul>
 *   <li>Do NOT overwrite fields stamped by the save pipeline:
 *       {@code subjectId}, {@code sequenceNumber}, {@code tenancyId},
 *       {@code occurredAt}.</li>
 *   <li>Enrichers MAY attach supplements — that is the point of
 *       {@link io.casehub.ledger.runtime.service.intercept.ProvenanceCaptureEnricher}.</li>
 *   <li>Enrichers that modify supplement fields in-place MUST call
 *       {@link LedgerEntry#refreshSupplementJson()} or
 *       {@link LedgerEntry#attach(io.casehub.ledger.runtime.model.supplement.LedgerSupplement)}
 *       — direct field mutation leaves {@code supplementJson} stale, and the hash
 *       will cover the stale value.</li>
 * </ul>
 *
 * <p>Implementations are CDI beans discovered via
 * {@code @Inject @Any Instance<LedgerEntryEnricher>} and invoked by
 * {@link LedgerEnricherPipeline}. Must be idempotent and non-fatal — a thrown
 * exception is logged and swallowed; the persist is never blocked.
 */
public interface LedgerEntryEnricher {

    /**
     * Enrich the given entry before it is hashed and signed.
     * Called once per save. Must not throw. Must be idempotent.
     *
     * <p><strong>Contract:</strong> Do not overwrite fields stamped by the save
     * pipeline ({@code subjectId}, {@code sequenceNumber}, {@code tenancyId},
     * {@code occurredAt}). Enrichers that modify supplements must use
     * {@link LedgerEntry#attach} or {@link LedgerEntry#refreshSupplementJson()}.
     */
    void enrich(LedgerEntry entry);
}
