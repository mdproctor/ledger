package io.casehub.ledger.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.PlainLedgerEntry;
import io.casehub.ledger.runtime.repository.ReactiveLedgerEntryRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Verifies that the reactive delegation path for attestation persistence works end-to-end
 * via {@link InMemoryReactiveLedgerEntryRepository} — the ledger side of casehubio/ledger#105.
 */
@QuarkusTest
@TestProfile(ReactiveProfile.class)
class InMemoryReactiveAttestationTest {

    @Inject
    ReactiveLedgerEntryRepository reactiveRepo;

    @Inject
    InMemoryLedgerEntryRepository blockingRepo;

    @BeforeEach
    void setUp() {
        blockingRepo.clear();
    }

    @Test
    void saveAttestation_persistsAndIsRetrievable() {
        final PlainLedgerEntry entry = savedEntry();

        final LedgerAttestation attestation = attestation(entry.id, "claude:reviewer@v1",
                AttestationVerdict.SOUND, CapabilityTag.GLOBAL);

        final LedgerAttestation saved = reactiveRepo.saveAttestation(attestation, DEFAULT_TENANT_ID)
                .await().indefinitely();

        assertThat(saved.id).isNotNull();
        assertThat(reactiveRepo.findAttestationsByEntryId(entry.id, DEFAULT_TENANT_ID).await().indefinitely())
                .hasSize(1)
                .first()
                .satisfies(a -> {
                    assertThat(a.attestorId).isEqualTo("claude:reviewer@v1");
                    assertThat(a.verdict).isEqualTo(AttestationVerdict.SOUND);
                });
    }

    @Test
    void saveAttestation_multipleForSameEntry_allRetrievable() {
        final PlainLedgerEntry entry = savedEntry();

        reactiveRepo.saveAttestation(
                attestation(entry.id, "actor-a", AttestationVerdict.SOUND, "code-review"), DEFAULT_TENANT_ID)
                .await().indefinitely();
        reactiveRepo.saveAttestation(
                attestation(entry.id, "actor-b", AttestationVerdict.FLAGGED, "code-review"), DEFAULT_TENANT_ID)
                .await().indefinitely();

        assertThat(reactiveRepo.findAttestationsByEntryId(entry.id, DEFAULT_TENANT_ID).await().indefinitely())
                .hasSize(2);
    }

    private PlainLedgerEntry savedEntry() {
        final PlainLedgerEntry entry = new PlainLedgerEntry();
        entry.subjectId = UUID.randomUUID();
        entry.actorId = "test-actor";
        entry.actorRole = "reviewer";
        entry.entryType = LedgerEntryType.EVENT;
        entry.occurredAt = Instant.now();
        return (PlainLedgerEntry) blockingRepo.save(entry, DEFAULT_TENANT_ID);
    }

    private LedgerAttestation attestation(final UUID entryId, final String attestorId,
            final AttestationVerdict verdict, final String capabilityTag) {
        final LedgerAttestation a = new LedgerAttestation();
        a.ledgerEntryId = entryId;
        a.attestorId = attestorId;
        a.verdict = verdict;
        a.capabilityTag = capabilityTag;
        a.confidence = 0.9;
        a.occurredAt = Instant.now();
        return a;
    }
}
