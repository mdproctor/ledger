package io.casehub.ledger.runtime.privacy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.ActorIdentity;
import io.casehub.ledger.runtime.model.ErasureReceiptLedgerEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;

/**
 * CDI bean for processing GDPR Art.17 erasure requests.
 *
 * <p>
 * Severs the token→identity mapping for the given actor. Ledger entries retaining
 * the token become permanently anonymous — the hash chain is intact; the personal
 * data link is gone. Returns an {@link ErasureResult} with diagnostic information.
 *
 * <p>
 * When {@code casehub.ledger.erasure-receipt.enabled=true}, a tamper-evident
 * {@link ErasureReceiptLedgerEntry} is written to the Merkle chain in the same
 * transaction. This makes the act of forgetting provable and auditable.
 */
@ApplicationScoped
public class LedgerErasureService {

    @Inject
    ActorIdentityProvider actorIdentityProvider;

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Inject
    LedgerEntryRepository ledger;

    @Inject
    LedgerConfig config;

    /**
     * The outcome of an erasure request.
     *
     * @param rawActorId the identity that was requested for erasure
     * @param mappingFound {@code true} if a token→identity mapping existed and was severed
     * @param affectedEntryCount number of ledger entries whose {@code actorId} was the severed token;
     *        informational only — entries are not deleted
     * @param receiptEntryId UUID of the written {@link ErasureReceiptLedgerEntry}, when
     *        {@code casehub.ledger.erasure-receipt.enabled=true}; empty otherwise
     */
    public record ErasureResult(
            String rawActorId,
            boolean mappingFound,
            long affectedEntryCount,
            Optional<UUID> receiptEntryId) {
    }

    /**
     * Process an erasure request for the given actor identity.
     *
     * <p>
     * If no mapping exists (tokenisation was never enabled for this actor, or the identity
     * was already erased), returns {@code mappingFound=false} with count 0.
     *
     * <p>
     * When {@code casehub.ledger.erasure-receipt.enabled=true}, an
     * {@link ErasureReceiptLedgerEntry} is written regardless of whether a mapping was found —
     * the receipt records that an erasure was attempted.
     *
     * @param rawActorId the real actor identity to erase
     * @param reason the legal basis or trigger for this erasure
     * @return the erasure result
     */
    @Transactional
    public ErasureResult erase(final String rawActorId, final ErasureReason reason) {
        final List<ActorIdentity> existing = em
                .createNamedQuery("ActorIdentity.findByActorId", ActorIdentity.class)
                .setParameter("actorId", rawActorId)
                .getResultList();

        final boolean mappingFound = !existing.isEmpty();

        final long count;
        if (mappingFound) {
            final String token = existing.get(0).token;
            count = em
                    .createQuery("SELECT COUNT(e) FROM LedgerEntry e WHERE e.actorId = :token",
                            Long.class)
                    .setParameter("token", token)
                    .getSingleResult();
            actorIdentityProvider.erase(rawActorId);
        } else {
            count = 0L;
        }

        final Optional<UUID> receiptEntryId;
        if (config.erasureReceipt().enabled()) {
            final ErasureReceiptLedgerEntry receipt = buildReceipt(
                    rawActorId, reason, mappingFound, count);
            ledger.save(receipt, TenancyConstants.DEFAULT_TENANT_ID);
            receiptEntryId = Optional.of(receipt.id);
        } else {
            receiptEntryId = Optional.empty();
        }

        return new ErasureResult(rawActorId, mappingFound, count, receiptEntryId);
    }

    private ErasureReceiptLedgerEntry buildReceipt(
            final String rawActorId,
            final ErasureReason reason,
            final boolean mappingFound,
            final long affectedEntryCount) {
        final ErasureReceiptLedgerEntry receipt = new ErasureReceiptLedgerEntry();
        receipt.subjectId = UUID.nameUUIDFromBytes(rawActorId.getBytes(StandardCharsets.UTF_8));
        receipt.entryType = LedgerEntryType.EVENT;
        receipt.actorId = "system:ledger-erasure";
        receipt.actorType = ActorType.SYSTEM;
        receipt.actorRole = "ErasureService";
        receipt.occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        receipt.erasedActorId = rawActorId;
        receipt.erasureReason = reason;
        receipt.mappingFound = mappingFound;
        receipt.affectedEntryCount = affectedEntryCount;
        return receipt;
    }
}
