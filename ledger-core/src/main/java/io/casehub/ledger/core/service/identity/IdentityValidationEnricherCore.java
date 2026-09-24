package io.casehub.ledger.core.service.identity;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.core.enricher.LedgerEntryEnricher;
import io.casehub.platform.api.identity.AgentCredentialValidator;
import io.casehub.platform.api.identity.CredentialValidationResult;
import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.identity.IdentityBindingStatus;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IdentityValidationEnricherCore implements LedgerEntryEnricher {

    private static final Logger log = Logger.getLogger(IdentityValidationEnricherCore.class.getName());

    private final DIDResolver resolver;
    private final AgentCredentialValidator credValidator;
    private final ConcurrentHashMap<String, IdentityBindingStatus> statusCache = new ConcurrentHashMap<>();
    private final BiConsumer<LedgerEntry, IdentityBindingStatus> statusCallback;

    public IdentityValidationEnricherCore(DIDResolver resolver,
                                          AgentCredentialValidator credValidator,
                                          BiConsumer<LedgerEntry, IdentityBindingStatus> statusCallback) {
        this.resolver = resolver;
        this.credValidator = credValidator;
        this.statusCallback = statusCallback;
    }

    @Override
    public void enrich(LedgerEntry entry) {
        if (entry.actorDid == null) return;
        try {
            IdentityBindingStatus status = statusCache.computeIfAbsent(
                    entry.actorId, k -> computeStatus(entry));
            statusCallback.accept(entry, status);
        } catch (Exception e) {
            log.log(Level.WARNING, "IdentityValidationEnricherCore failed for actor "
                    + entry.actorId + ": " + e.getMessage());
        }
    }

    public void invalidate(String actorId) {
        statusCache.remove(actorId);
    }

    public void invalidateAll() {
        statusCache.clear();
    }

    @Override
    public int priority() {
        return 50;
    }

    private IdentityBindingStatus computeStatus(LedgerEntry entry) {
        Optional<DIDDocument> docOpt = resolver.resolve(entry.actorId, entry.actorDid);
        if (docOpt.isEmpty()) return IdentityBindingStatus.DID_UNRESOLVABLE;

        DIDDocument doc = docOpt.get();
        if (!doc.alsoKnownAs().contains(entry.actorId)) return IdentityBindingStatus.IDENTITY_MISMATCH;
        if (entry.agentPublicKey == null) return IdentityBindingStatus.UNSIGNED;

        boolean keyMatch = doc.verificationMethods().stream()
                .anyMatch(vm -> Arrays.equals(vm.publicKeyBytes(), entry.agentPublicKey));
        if (!keyMatch) return IdentityBindingStatus.KEY_MISMATCH;

        Optional<CredentialValidationResult> vcResult =
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
}
