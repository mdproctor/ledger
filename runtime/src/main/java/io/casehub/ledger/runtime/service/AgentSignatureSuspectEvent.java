package io.casehub.ledger.runtime.service;

import java.time.Instant;
import java.util.UUID;

/**
 * CDI event fired when {@link LedgerVerificationService#verifyAgentSignature(UUID)} or
 * {@link LedgerVerificationService#verifyAgentSignatureAsync(UUID)} returns
 * {@link io.casehub.ledger.runtime.service.model.VerificationResult#SUSPECT}.
 *
 * <p>
 * SUSPECT means the signature is cryptographically valid but was produced by a key
 * subsequently reported {@link io.casehub.ledger.api.model.KeyRotationReason#COMPROMISED}
 * within the applicable time window.
 *
 * <p>
 * Consumers:
 * <pre>{@code
 * // Synchronous consumer
 * void onSuspect(@Observes AgentSignatureSuspectEvent e) { ... }
 *
 * // Asynchronous consumer (non-blocking)
 * CompletionStage<Void> onSuspect(@ObservesAsync AgentSignatureSuspectEvent e) { ... }
 * }</pre>
 *
 * @param entryId        the entry whose signature is SUSPECT
 * @param actorId        the actor who signed the entry
 * @param keyRef         the key generation reported COMPROMISED
 * @param occurredAt     when the entry was signed
 * @param effectiveSince the earliest matching compromise window — "compromised since when"
 */
public record AgentSignatureSuspectEvent(
        UUID entryId,
        String actorId,
        String keyRef,
        Instant occurredAt,
        Instant effectiveSince) {}
