package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.model.AgentSignatureSuspectEvent;
import io.casehub.ledger.core.model.CompromisedWindow;
import io.casehub.ledger.core.model.VerificationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class AgentSignatureVerificationService {

    private static final Logger log = Logger.getLogger(AgentSignatureVerificationService.class);

    private final io.casehub.ledger.core.service.SignatureVerificationCore core;

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Inject
    KeyRotationService keyRotationService;

    @Inject
    Event<AgentSignatureSuspectEvent> suspectEvent;

    @Inject
    AgentSignatureVerificationService(LedgerEntryRepository ledgerRepo, KeyRotationService keyRotationService) {
        this.core = new io.casehub.ledger.core.service.SignatureVerificationCore(
                ledgerRepo, keyRotationService::compromisedWindows);
    }

    @Transactional
    public VerificationResult verifyAgentSignature(UUID entryId, String tenancyId) {
        VerificationResult result = core.verifyAgentSignature(entryId, tenancyId);
        if (result == VerificationResult.SUSPECT) {
            LedgerEntry entry = ledgerRepo.findEntryById(entryId, tenancyId).orElse(null);
            if (entry != null) {
                var effectiveSince = keyRotationService.compromisedWindows(entry.actorId, entry.agentKeyRef)
                                                       .stream()
                                                       .filter(w -> !entry.occurredAt.isBefore(w.effectiveSince()))
                                                       .map(CompromisedWindow::effectiveSince)
                                                       .min(java.time.Instant::compareTo)
                                                       .orElse(null);
                if (effectiveSince != null) {
                    AgentSignatureSuspectEvent payload = new AgentSignatureSuspectEvent(
                            entryId, entry.actorId, entry.agentKeyRef, entry.occurredAt, effectiveSince);
                    suspectEvent.fire(payload);
                    suspectEvent.fireAsync(payload)
                                .exceptionally(ex -> {
                                    log.debugf(ex, "AgentSignatureSuspectEvent async observer failed");
                                    return null;
                                });
                }
            }
        }
        return result;
    }
}
