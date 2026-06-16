package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.AgentIdentityValidatedEvent;
import io.casehub.platform.api.identity.AgentIdentityViolationEvent;
import io.casehub.platform.api.identity.CredentialValidationResult;
import io.casehub.platform.api.identity.IdentityBindingStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

/**
 * Persists {@link ActorIdentityBindingEntry} in response to async identity validation events.
 *
 * <p>Routes through {@link LedgerEntryRepository#save} — the full save pipeline runs:
 * sequence allocation, hash computation, Merkle frontier update, enricher pipeline, agent signing.
 *
 * <p>Uses {@code REQUIRES_NEW} so the binding entry commits in its own transaction,
 * independent of the parent entry's lifecycle. Failure is logged and swallowed.
 */
@ApplicationScoped
public class ActorIdentityBindingObserver {

    private static final Logger LOG = Logger.getLogger(ActorIdentityBindingObserver.class);

    @Inject
    LedgerEntryRepository ledgerRepo;

    void onValidated(@ObservesAsync final AgentIdentityValidatedEvent event) {
        persistBinding(
            event.tenancyId(), event.actorId(), event.actorDid(), event.status(),
            event.alsoKnownAsVerified(), event.keyMatchVerified(),
            event.verifiedKeyRef(), event.credentialResult(), event.didMethod());
    }

    void onViolation(@ObservesAsync final AgentIdentityViolationEvent event) {
        persistBinding(
            event.tenancyId(), event.actorId(), event.actorDid(), event.status(),
            false, false, null, null, extractDidMethod(event.actorDid()));
    }

    @Transactional(REQUIRES_NEW)
    void persistBinding(
            final String tenancyId,
            final String actorId,
            final String actorDid,
            final IdentityBindingStatus status,
            final boolean alsoKnownAsVerified,
            final boolean keyMatchVerified,
            final String verifiedKeyRef,
            final CredentialValidationResult credentialResult,
            final String didMethod) {
        try {
            final ActorIdentityBindingEntry entry = new ActorIdentityBindingEntry();
            entry.id = UUID.randomUUID();
            entry.subjectId = UUID.nameUUIDFromBytes(actorId.getBytes(StandardCharsets.UTF_8));
            entry.actorId = actorId;
            entry.actorType = io.casehub.platform.api.identity.ActorType.AGENT;
            entry.actorRole = "identity-binding";
            entry.entryType = LedgerEntryType.EVENT;
            entry.occurredAt = Instant.now();
            entry.boundDid = actorDid;
            entry.validationResult = status;
            entry.alsoKnownAsVerified = alsoKnownAsVerified;
            entry.keyMatchVerified = keyMatchVerified;
            entry.verifiedKeyRef = verifiedKeyRef;
            entry.credentialResult = credentialResult;
            entry.didMethod = didMethod;
            // tenancyId is passed to ledgerRepo.save() which stamps it onto the entry; do not pre-assign here
            ledgerRepo.save(entry, tenancyId);
        } catch (final Exception e) {
            LOG.warnf("ActorIdentityBindingObserver failed to persist binding for %s: %s",
                actorId, e.getMessage());
        }
    }

    private String extractDidMethod(final String did) {
        if (did == null || !did.startsWith("did:")) return null;
        final String[] parts = did.split(":", 3);
        return parts.length > 1 ? parts[1] : null;
    }
}
