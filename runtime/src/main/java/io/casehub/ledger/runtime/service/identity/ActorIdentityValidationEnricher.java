package io.casehub.ledger.runtime.service.identity;

import io.casehub.platform.api.identity.AgentCredentialValidator;
import io.casehub.platform.api.identity.AgentIdentityValidatedEvent;
import io.casehub.platform.api.identity.AgentIdentityViolationEvent;
import io.casehub.platform.api.identity.CredentialValidationResult;
import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.identity.AbstractCachingIdentityProvider;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.identity.IdentityBindingStatus;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;
import io.casehub.ledger.runtime.service.LedgerEntryEnricher;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Validates the actorId→DID binding at write time.
 *
 * <p>Cache: per-actorId IdentityBindingStatus. Populated on first miss via computeStatus().
 * Invalidated on key rotation via {@link AgentKeyRotatedEvent} CDI event observer.
 *
 * <p>Sets {@link LedgerEntry#pendingIdentityStatus} (transient) for
 * {@link LedgerIdentityEnforcementListener}. Fires async CDI events for the binding observer.
 *
 * <p>Must not throw — non-fatal enricher contract.
 */
@ApplicationScoped
@Priority(50)
public class ActorIdentityValidationEnricher implements LedgerEntryEnricher {

    private static final Logger LOG = Logger.getLogger(ActorIdentityValidationEnricher.class);

    private final DIDResolver resolver;
    private final AgentCredentialValidator credValidator;
    private final Event<Object> event;
    private final AbstractCachingIdentityProvider<IdentityBindingStatus> statusCache;

    @Inject
    public ActorIdentityValidationEnricher(
            final DIDResolver resolver,
            final AgentCredentialValidator credValidator,
            final Event<Object> event,
            final LedgerConfig config) {
        this(resolver, credValidator, event,
            Duration.ofMinutes(config.agentIdentity().didResolverCacheTtlMinutes()));
    }

    // Visible for tests — allows injecting a specific TTL without a LedgerConfig mock
    public ActorIdentityValidationEnricher(
            final DIDResolver resolver,
            final AgentCredentialValidator credValidator,
            final Event<Object> event,
            final Duration ttl) {
        this.resolver = resolver;
        this.credValidator = credValidator;
        this.event = event;
        this.statusCache = new AbstractCachingIdentityProvider<>(ttl) {
            @Override
            protected Optional<IdentityBindingStatus> loadContext(final String key) {
                // Never called externally. The cache is driven by put() from enrich().
                return Optional.empty();
            }
        };
    }

    @Override
    public void enrich(final LedgerEntry entry) {
        if (entry.actorDid == null) return;
        try {
            Optional<IdentityBindingStatus> cached = statusCache.get(entry.actorId);
            IdentityBindingStatus status;
            if (cached.isPresent()) {
                status = cached.get();
            } else {
                status = computeStatus(entry);
                statusCache.put(entry.actorId, Optional.of(status));
                fireEvent(entry, status);
            }
            entry.pendingIdentityStatus = status;
        } catch (final Exception e) {
            LOG.warnf("ActorIdentityValidationEnricher failed for actor %s: %s",
                entry.actorId, e.getMessage());
        }
    }

    public void invalidate(final String actorId) {
        statusCache.invalidate(actorId);
    }

    public void invalidateAll() {
        statusCache.invalidateAll();
    }

    void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        statusCache.invalidate(event.actorId());
    }

    private IdentityBindingStatus computeStatus(final LedgerEntry entry) {
        final Optional<DIDDocument> docOpt = resolver.resolve(entry.actorDid);
        if (docOpt.isEmpty()) return IdentityBindingStatus.DID_UNRESOLVABLE;

        final DIDDocument doc = docOpt.get();
        if (!doc.alsoKnownAs().contains(entry.actorId)) return IdentityBindingStatus.IDENTITY_MISMATCH;
        if (entry.agentPublicKey == null) return IdentityBindingStatus.UNSIGNED;

        final boolean keyMatch = doc.verificationMethods().stream()
            .anyMatch(vm -> Arrays.equals(vm.publicKeyBytes(), entry.agentPublicKey));
        if (!keyMatch) return IdentityBindingStatus.KEY_MISMATCH;

        final Optional<CredentialValidationResult> vcResult =
            credValidator.validate(entry.actorId, entry.actorDid);
        if (vcResult.isPresent()) {
            return switch (vcResult.get()) {
                case VALID -> IdentityBindingStatus.VALID;
                case EXPIRED -> IdentityBindingStatus.CREDENTIAL_EXPIRED;
                default -> IdentityBindingStatus.CREDENTIAL_INVALID;
            };
        }
        return IdentityBindingStatus.VALID;
    }

    private void fireEvent(final LedgerEntry entry, final IdentityBindingStatus status) {
        final String didMethod = extractDidMethod(entry.actorDid);
        if (status == IdentityBindingStatus.VALID) {
            event.fireAsync(new AgentIdentityValidatedEvent(
                entry.actorId, entry.tenancyId, entry.actorDid, status,
                true, true, entry.agentKeyRef, null, didMethod));
        } else {
            event.fireAsync(new AgentIdentityViolationEvent(
                entry.actorId, entry.tenancyId, entry.actorDid, status));
        }
    }

    private String extractDidMethod(final String did) {
        if (did == null || !did.startsWith("did:")) return null;
        final String[] parts = did.split(":", 3);
        return parts.length > 1 ? parts[1] : null;
    }
}
