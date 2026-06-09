package io.casehub.ledger.service;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.PlainLedgerEntry;
import io.casehub.ledger.runtime.repository.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying tenant isolation in the ledger SPI layer.
 *
 * <p>Entries saved under one tenant must be invisible to queries scoped to a different
 * tenant. Cross-tenant read operations must span all tenants.
 */
@QuarkusTest
class TenancyIsolationIT {

    static final String TENANT_A = "tenant-a";
    static final String TENANT_B = "tenant-b";

    @Inject
    LedgerEntryRepository repo;

    @Inject
    CrossTenantLedgerEntryRepository crossTenantRepo;

    @Test
    @Transactional
    void tenant_a_entries_invisible_to_tenant_b() {
        final UUID subjectId = UUID.randomUUID();
        final PlainLedgerEntry entry = new PlainLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = subjectId;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-1";

        repo.save(entry, TENANT_A);

        assertEquals(1, repo.findBySubjectId(subjectId, TENANT_A).size());
        assertEquals(0, repo.findBySubjectId(subjectId, TENANT_B).size());

        assertTrue(crossTenantRepo.listAll().stream()
                .anyMatch(e -> e.id.equals(entry.id)));
    }

    @Test
    @Transactional
    void findEntryById_wrong_tenant_returns_empty() {
        final PlainLedgerEntry entry = new PlainLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = UUID.randomUUID();
        entry.entryType = LedgerEntryType.COMMAND;
        entry.actorId = "actor-1";

        repo.save(entry, TENANT_A);

        assertTrue(repo.findEntryById(entry.id, TENANT_A).isPresent());
        assertTrue(repo.findEntryById(entry.id, TENANT_B).isEmpty());
    }

    @Test
    @Transactional
    void attestation_save_rejects_cross_tenant_entry() {
        final PlainLedgerEntry entry = new PlainLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = UUID.randomUUID();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-1";

        repo.save(entry, TENANT_A);

        final LedgerAttestation attestation = new LedgerAttestation();
        attestation.id = UUID.randomUUID();
        attestation.ledgerEntryId = entry.id;
        attestation.attestorId = "attestor-1";
        attestation.verdict = AttestationVerdict.SOUND;
        attestation.capabilityTag = "*";

        assertThrows(IllegalArgumentException.class,
                () -> repo.saveAttestation(attestation, TENANT_B));
    }

    @Test
    @Transactional
    void cross_tenant_findAllEvents_sees_all_tenants() {
        final PlainLedgerEntry entryA = new PlainLedgerEntry();
        entryA.id = UUID.randomUUID();
        entryA.subjectId = UUID.randomUUID();
        entryA.entryType = LedgerEntryType.EVENT;
        entryA.actorId = "actor-a";
        repo.save(entryA, TENANT_A);

        final PlainLedgerEntry entryB = new PlainLedgerEntry();
        entryB.id = UUID.randomUUID();
        entryB.subjectId = UUID.randomUUID();
        entryB.entryType = LedgerEntryType.EVENT;
        entryB.actorId = "actor-b";
        repo.save(entryB, TENANT_B);

        final var allEvents = crossTenantRepo.findAllEvents();
        assertTrue(allEvents.stream().anyMatch(e -> e.id.equals(entryA.id)));
        assertTrue(allEvents.stream().anyMatch(e -> e.id.equals(entryB.id)));
    }
}
