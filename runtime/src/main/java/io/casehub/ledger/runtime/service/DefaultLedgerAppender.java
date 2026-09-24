package io.casehub.ledger.runtime.service;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;

import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.jpa.PlainLedgerEntry;

/**
 * Default blocking implementation of {@link LedgerAppender}.
 *
 * <p>Creates a {@link PlainLedgerEntry} from the {@link AuditRecord} and delegates
 * to {@link LedgerEntryRepository#save} — the full save pipeline runs (sequence
 * assignment, enrichment, hash chain, agent signing).
 *
 * <p>{@code @DefaultBean} — replaced by any {@code @ApplicationScoped} bean implementing
 * {@link LedgerAppender} that the consuming application provides.
 *
 * <p>Transaction boundary: this method is NOT {@code @Transactional}. The repository's
 * {@code save()} is the transactional boundary — consistent with the {@link DefaultOutcomeRecorder}
 * pattern for single-write paths.
 */
@DefaultBean
@ApplicationScoped
public class DefaultLedgerAppender implements LedgerAppender {

    @Inject
    LedgerEntryRepository repo;

    @Inject
    LedgerConfig config;

    @Override
    public UUID append(final AuditRecord record, final String tenancyId) {
        if (record.metadata() != null && record.metadata().length() > config.metadata().maxSize()) {
            throw new IllegalArgumentException(
                    "metadata exceeds maximum size of " + config.metadata().maxSize()
                            + " bytes — got " + record.metadata().length());
        }
        final PlainLedgerEntry entry = new PlainLedgerEntry();
        entry.actorId = record.actorId();
        entry.actorType = record.actorType();
        entry.actorRole = record.actorRole();
        entry.subjectId = record.subjectId();
        entry.entryType = record.entryType();
        entry.occurredAt = record.occurredAt();
        entry.causedByEntryId = record.causedByEntryId();
        entry.metadata = record.metadata();
        entry.domainData = record.domainData();

        final LedgerEntry saved = repo.save(entry, tenancyId);
        return saved.id;
    }
}
