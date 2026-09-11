package io.casehub.ledger.service;

import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.signing.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSignatureVerificationService;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that CDI event producers fire both {@code fire()} and {@code fireAsync()}
 * channels — the dual-channel normalisation from ledger#159.
 *
 * <p>Each test uses committed transactions so {@code @Observes(AFTER_SUCCESS)}
 * observers fire. Async delivery is synchronised via {@link java.util.concurrent.CountDownLatch}.
 */
@QuarkusTest
class DualChannelEventIT {

    @Inject
    LedgerEntryRepository repo;

    @Inject
    KeyRotationService rotationService;

    @Inject
    AgentSignatureVerificationService signatureService;

    @Inject
    AttestationRecordedEventCapture attestationCapture;

    @Inject
    AgentKeyRotatedEventCapture keyRotatedCapture;

    @Inject
    AgentSuspectEventCapture suspectCapture;

    @BeforeEach
    void reset() {
        attestationCapture.reset();
        keyRotatedCapture.reset();
        suspectCapture.reset();
    }

    @Test
    void saveAttestation_firesAsyncChannel() throws InterruptedException {
        final UUID entryId = seedEntry();

        QuarkusTransaction.requiringNew().run(() -> {
            final LedgerAttestation att = new LedgerAttestation();
            att.ledgerEntryId = entryId;
            att.subjectId = UUID.randomUUID();
            att.attestorId = "test-attestor";
            att.attestorType = ActorType.AGENT;
            att.verdict = AttestationVerdict.SOUND;
            att.confidence = 1.0;
            att.capabilityTag = CapabilityTag.GLOBAL;
            att.occurredAt = Instant.now();
            repo.saveAttestation(att, DEFAULT_TENANT_ID);
        });

        assertThat(attestationCapture.asyncLatch().await(5, TimeUnit.SECONDS))
                .as("fireAsync() should deliver to @ObservesAsync")
                .isTrue();
        assertThat(attestationCapture.asyncEvents()).hasSize(1);
        assertThat(attestationCapture.syncEvents()).isNotEmpty();
    }

    @Test
    void recordRotation_firesAsyncChannel() throws Exception {
        final String actorId = "dual-channel-rotate-" + UUID.randomUUID();
        final String oldRef = AgentSignature.signWith(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), new byte[0]).keyRef();
        final String newRef = AgentSignature.signWith(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), new byte[0]).keyRef();

        QuarkusTransaction.requiringNew().run(() ->
                rotationService.recordRotation(actorId, oldRef, newRef,
                        KeyRotationReason.SCHEDULED, Instant.now(), DEFAULT_TENANT_ID));

        assertThat(keyRotatedCapture.asyncLatch().await(5, TimeUnit.SECONDS))
                .as("fireAsync() should deliver to @ObservesAsync")
                .isTrue();
        assertThat(keyRotatedCapture.asyncEvents()).hasSize(1);
        assertThat(keyRotatedCapture.syncEvents()).hasSize(1);
    }

    private UUID seedEntry() {
        return QuarkusTransaction.requiringNew().call(() -> {
            final TestEntry entry = new TestEntry();
            entry.subjectId = UUID.randomUUID();
            entry.entryType = LedgerEntryType.EVENT;
            entry.actorId = "dual-channel-actor-" + UUID.randomUUID();
            entry.actorType = ActorType.AGENT;
            entry.actorRole = "Tester";
            entry.occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            return repo.save(entry, DEFAULT_TENANT_ID).id;
        });
    }
}
