package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.model.CompromisedWindow;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class KeyRotationServiceIT {

    @Inject
    KeyRotationService rotationService;

    private String newKeyRef() throws Exception {
        return AgentSignature.signWith(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), new byte[0]).keyRef();
    }

    @Test
    @Transactional
    void recordRotation_persistsEntry() throws Exception {
        final String actorId = "claude:reviewer@v1";
        final String oldKeyRef = newKeyRef();
        final String newKeyRef = newKeyRef();

        final KeyRotationEntry entry = rotationService.recordRotation(
                actorId, oldKeyRef, newKeyRef,
                KeyRotationReason.SCHEDULED, Instant.now());

        assertThat(entry.id).isNotNull();
        assertThat(entry.actorId).isEqualTo(actorId);
        assertThat(entry.previousKeyRef).isEqualTo(oldKeyRef);
        assertThat(entry.newKeyRef).isEqualTo(newKeyRef);
        assertThat(entry.reason).isEqualTo(KeyRotationReason.SCHEDULED);
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.COMMAND);
    }

    @Test
    @Transactional
    void recordRotation_subjectIdIsDeterministicFromActorId() throws Exception {
        final String actorId = "claude:auditor@v1";
        final String oldKeyRef = newKeyRef();

        final KeyRotationEntry entry = rotationService.recordRotation(
                actorId, oldKeyRef, null,
                KeyRotationReason.COMPROMISED, Instant.now());

        final UUID expectedSubjectId = UUID.nameUUIDFromBytes(
                actorId.getBytes(StandardCharsets.UTF_8));
        assertThat(entry.subjectId).isEqualTo(expectedSubjectId);
    }

    @Test
    @Transactional
    void rotationHistory_returnsAllEventsOrdered() throws Exception {
        final String actorId = "claude:reviewer@v2-" + UUID.randomUUID();
        final String keyRef1 = newKeyRef();
        final String keyRef2 = newKeyRef();

        rotationService.recordRotation(actorId, keyRef1, keyRef2,
                KeyRotationReason.SCHEDULED, Instant.now().minusSeconds(60));
        rotationService.recordRotation(actorId, keyRef2, null,
                KeyRotationReason.COMPROMISED, Instant.now());

        final List<KeyRotationEntry> history = rotationService.rotationHistory(actorId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).reason).isEqualTo(KeyRotationReason.SCHEDULED);
        assertThat(history.get(1).reason).isEqualTo(KeyRotationReason.COMPROMISED);
    }

    @Test
    @Transactional
    void compromisedWindows_onlyReturnsCompromisedReason() throws Exception {
        final String actorId = "claude:reviewer@v3-" + UUID.randomUUID();
        final String oldKeyRef = newKeyRef();
        final String newKeyRef = newKeyRef();
        final Instant compromisedSince = Instant.now().minusSeconds(3600);

        rotationService.recordRotation(actorId, oldKeyRef, newKeyRef,
                KeyRotationReason.SCHEDULED, Instant.now());
        rotationService.recordRotation(actorId, oldKeyRef, null,
                KeyRotationReason.COMPROMISED, compromisedSince);

        final List<CompromisedWindow> windows =
                rotationService.compromisedWindows(actorId, oldKeyRef);

        assertThat(windows).hasSize(1);
        assertThat(windows.get(0).keyRef()).isEqualTo(oldKeyRef);
        assertThat(windows.get(0).effectiveSince()).isEqualTo(compromisedSince);
    }

    @Test
    @Transactional
    void compromisedWindows_emptyWhenNoCompromiseRecord() throws Exception {
        final String actorId = "claude:reviewer@v4-" + UUID.randomUUID();
        final String keyRef = newKeyRef();

        final List<CompromisedWindow> windows =
                rotationService.compromisedWindows(actorId, keyRef);

        assertThat(windows).isEmpty();
    }

}
