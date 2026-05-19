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
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.SigningKey;
import io.casehub.ledger.runtime.service.model.CompromisedWindow;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class KeyRotationServiceIT {

    @Inject
    KeyRotationService rotationService;

    private SigningKey newKey() throws Exception {
        return SigningKey.of(KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
    }

    @Test
    @Transactional
    void recordRotation_persistsEntry() throws Exception {
        final String actorId = "claude:reviewer@v1";
        final SigningKey oldKey = newKey();
        final SigningKey newKey = newKey();

        final KeyRotationEntry entry = rotationService.recordRotation(
                actorId, oldKey.keyRef(), newKey.keyRef(),
                KeyRotationReason.SCHEDULED, Instant.now());

        assertThat(entry.id).isNotNull();
        assertThat(entry.actorId).isEqualTo(actorId);
        assertThat(entry.previousKeyRef).isEqualTo(oldKey.keyRef());
        assertThat(entry.newKeyRef).isEqualTo(newKey.keyRef());
        assertThat(entry.reason).isEqualTo(KeyRotationReason.SCHEDULED);
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.COMMAND);
    }

    @Test
    @Transactional
    void recordRotation_subjectIdIsDeterministicFromActorId() throws Exception {
        final String actorId = "claude:auditor@v1";
        final SigningKey oldKey = newKey();

        final KeyRotationEntry entry = rotationService.recordRotation(
                actorId, oldKey.keyRef(), null,
                KeyRotationReason.COMPROMISED, Instant.now());

        final UUID expectedSubjectId = UUID.nameUUIDFromBytes(
                actorId.getBytes(StandardCharsets.UTF_8));
        assertThat(entry.subjectId).isEqualTo(expectedSubjectId);
    }

    @Test
    @Transactional
    void rotationHistory_returnsAllEventsOrdered() throws Exception {
        final String actorId = "claude:reviewer@v2-" + UUID.randomUUID();
        final SigningKey k1 = newKey();
        final SigningKey k2 = newKey();

        rotationService.recordRotation(actorId, k1.keyRef(), k2.keyRef(),
                KeyRotationReason.SCHEDULED, Instant.now().minusSeconds(60));
        rotationService.recordRotation(actorId, k2.keyRef(), null,
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
        final SigningKey oldKey = newKey();
        final SigningKey newKey = newKey();
        final Instant compromisedSince = Instant.now().minusSeconds(3600);

        rotationService.recordRotation(actorId, oldKey.keyRef(), newKey.keyRef(),
                KeyRotationReason.SCHEDULED, Instant.now());
        rotationService.recordRotation(actorId, oldKey.keyRef(), null,
                KeyRotationReason.COMPROMISED, compromisedSince);

        final List<CompromisedWindow> windows =
                rotationService.compromisedWindows(actorId, oldKey.keyRef());

        assertThat(windows).hasSize(1);
        assertThat(windows.get(0).keyRef()).isEqualTo(oldKey.keyRef());
        assertThat(windows.get(0).effectiveSince()).isEqualTo(compromisedSince);
    }

    @Test
    @Transactional
    void compromisedWindows_emptyWhenNoCompromiseRecord() throws Exception {
        final String actorId = "claude:reviewer@v4-" + UUID.randomUUID();
        final SigningKey key = newKey();

        final List<CompromisedWindow> windows =
                rotationService.compromisedWindows(actorId, key.keyRef());

        assertThat(windows).isEmpty();
    }

}
