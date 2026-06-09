package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.ReactiveKeyRotationService;
import io.casehub.ledger.runtime.service.model.CompromisedWindow;
import io.quarkus.test.junit.QuarkusTest;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Integration tests for {@link ReactiveKeyRotationService}.
 *
 * <p>
 * {@code @Transactional} on each test method is required for the <em>setup</em>
 * operations that use the blocking {@link KeyRotationService} and
 * {@link io.casehub.ledger.runtime.repository.LedgerEntryRepository} — those
 * blocking saves need a JTA transaction context. The reactive calls under test
 * ({@code await().atMost(...)}) go through the
 * {@link BlockingReactiveKeyRotationRepository} test shim, which delegates to the
 * blocking repo on the same thread and therefore participates in the same JTA
 * transaction. In production the reactive path runs on an event loop thread
 * outside any JTA context — callers are responsible for reactive transaction
 * management.
 */
@QuarkusTest
class ReactiveKeyRotationServiceIT {

    @Inject
    ReactiveKeyRotationService reactiveRotationService;

    @Inject
    KeyRotationService rotationService;

    @Inject
    AgentKeyRotatedEventCapture eventCapture;

    private String newKeyRef() throws Exception {
        return AgentSignature.signWith(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), new byte[0]).keyRef();
    }

    @Test
    @Transactional
    void compromisedWindowsAsync_emptyWhenNoCompromiseRecord() throws Exception {
        final String actorId = "claude:reviewer@v10-" + UUID.randomUUID();
        final String keyRef = newKeyRef();

        final List<CompromisedWindow> windows = reactiveRotationService
                .compromisedWindowsAsync(actorId, keyRef)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(windows).isEmpty();
    }

    @Test
    @Transactional
    void compromisedWindowsAsync_onlyReturnsCompromisedReason() throws Exception {
        final String actorId = "claude:reviewer@v11-" + UUID.randomUUID();
        final String oldKeyRef = newKeyRef();
        final String newKeyRef = newKeyRef();
        final Instant compromisedSince = Instant.now().minusSeconds(3600);

        rotationService.recordRotation(actorId, oldKeyRef, newKeyRef,
                KeyRotationReason.SCHEDULED, Instant.now(), DEFAULT_TENANT_ID);
        rotationService.recordRotation(actorId, oldKeyRef, null,
                KeyRotationReason.COMPROMISED, compromisedSince, DEFAULT_TENANT_ID);

        final List<CompromisedWindow> windows = reactiveRotationService
                .compromisedWindowsAsync(actorId, oldKeyRef)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(windows).hasSize(1);
        assertThat(windows.get(0).keyRef()).isEqualTo(oldKeyRef);
        assertThat(windows.get(0).effectiveSince()).isEqualTo(compromisedSince);
    }

    @Test
    @Transactional
    void rotationHistoryAsync_returnsAllEventsOrdered() throws Exception {
        final String actorId = "claude:reviewer@v12-" + UUID.randomUUID();
        final String keyRef1 = newKeyRef();
        final String keyRef2 = newKeyRef();

        rotationService.recordRotation(actorId, keyRef1, keyRef2,
                KeyRotationReason.SCHEDULED, Instant.now().minusSeconds(60), DEFAULT_TENANT_ID);
        rotationService.recordRotation(actorId, keyRef2, null,
                KeyRotationReason.COMPROMISED, Instant.now(), DEFAULT_TENANT_ID);

        final List<KeyRotationEntry> history = reactiveRotationService
                .rotationHistoryAsync(actorId)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(history).hasSize(2);
        assertThat(history.get(0).reason).isEqualTo(KeyRotationReason.SCHEDULED);
        assertThat(history.get(1).reason).isEqualTo(KeyRotationReason.COMPROMISED);
    }

    @Test
    @Transactional
    void recordRotationAsync_persistsEntry() throws Exception {
        final String actorId = "claude:reviewer@v13-" + UUID.randomUUID();
        final String oldKeyRef = newKeyRef();
        final String newKeyRef = newKeyRef();

        final KeyRotationEntry entry = reactiveRotationService.recordRotationAsync(
                actorId, oldKeyRef, newKeyRef,
                KeyRotationReason.SCHEDULED, Instant.now(), DEFAULT_TENANT_ID)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(entry.id).isNotNull();
        assertThat(entry.actorId).isEqualTo(actorId);
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.COMMAND);
    }

    @Test
    @Transactional
    void recordRotationAsync_subjectIdIsDeterministicFromActorId() throws Exception {
        final String actorId = "claude:auditor@v2-" + UUID.randomUUID();
        final String oldKeyRef = newKeyRef();

        final KeyRotationEntry entry = reactiveRotationService.recordRotationAsync(
                actorId, oldKeyRef, null,
                KeyRotationReason.COMPROMISED, Instant.now(), DEFAULT_TENANT_ID)
                .await().atMost(Duration.ofSeconds(5));

        final UUID expectedSubjectId = UUID.nameUUIDFromBytes(
                actorId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(entry.subjectId).isEqualTo(expectedSubjectId);
    }

    @Test
    @Transactional
    void recordRotationAsync_firesAgentKeyRotatedEvent() throws Exception {
        eventCapture.reset();
        final String actorId = "claude:async-event-test-" + UUID.randomUUID();
        final String oldRef = newKeyRef();
        final String newRef = newKeyRef();

        reactiveRotationService.recordRotationAsync(
                actorId, oldRef, newRef,
                KeyRotationReason.SCHEDULED, Instant.now(), DEFAULT_TENANT_ID)
                .await().atMost(Duration.ofSeconds(5));

        // fireAsync is fire-and-forget; give it a moment to complete
        Thread.sleep(200);
        assertThat(eventCapture.events()).hasSize(1);
        assertThat(eventCapture.events().get(0).actorId()).isEqualTo(actorId);
    }
}
