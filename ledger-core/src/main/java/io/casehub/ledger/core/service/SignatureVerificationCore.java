package io.casehub.ledger.core.service;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.model.CompromisedWindow;
import io.casehub.ledger.core.model.VerificationResult;
import io.casehub.ledger.core.signing.AgentCryptographicVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

public class SignatureVerificationCore {

    private final LedgerEntryRepository ledgerRepo;
    private final BiFunction<String, String, List<CompromisedWindow>> compromisedWindowsLookup;

    public SignatureVerificationCore(LedgerEntryRepository ledgerRepo,
                                     BiFunction<String, String, List<CompromisedWindow>> compromisedWindowsLookup) {
        this.ledgerRepo = ledgerRepo;
        this.compromisedWindowsLookup = compromisedWindowsLookup;
    }

    public VerificationResult verifyAgentSignature(UUID entryId, String tenancyId) {
        LedgerEntry entry = ledgerRepo.findEntryById(entryId, tenancyId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + entryId));

        if (entry.agentSignature == null) {
            return VerificationResult.UNSIGNED;
        }

        VerificationResult cryptoResult = AgentCryptographicVerifier.verifyCryptographic(entry);
        if (cryptoResult != VerificationResult.VALID) {
            return cryptoResult;
        }

        if (entry.agentKeyRef != null && entry.actorId != null) {
            Optional<Instant> effectiveSince =
                    compromisedEffectiveSince(entry.actorId, entry.agentKeyRef, entry.occurredAt);
            if (effectiveSince.isPresent()) {
                return VerificationResult.SUSPECT;
            }
        }

        return VerificationResult.VALID;
    }

    private Optional<Instant> compromisedEffectiveSince(String actorId, String keyRef, Instant occurredAt) {
        return compromisedWindowsLookup.apply(actorId, keyRef)
                .stream()
                .filter(w -> !occurredAt.isBefore(w.effectiveSince()))
                .map(CompromisedWindow::effectiveSince)
                .min(Instant::compareTo);
    }
}
