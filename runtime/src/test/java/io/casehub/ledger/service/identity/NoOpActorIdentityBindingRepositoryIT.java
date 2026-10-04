package io.casehub.ledger.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.jpa.ActorIdentityBindingEntry;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.jpa.JpaLedgerEntry;
import io.casehub.ledger.jpa.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.ActorIdentityBindingRepository;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.IdentityBindingStatus;
import io.casehub.ledger.core.signing.AgentSigner;
import io.casehub.ledger.runtime.service.identity.ActorIdentityValidationEnricher;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Verifies the read/write split: binding entries are written via
 * {@code LedgerEntryRepository.save()} and readable via
 * {@code ActorIdentityBindingRepository.latestBindingFor()}.
 */
@QuarkusTest
@TestProfile(NoOpActorIdentityBindingRepositoryIT.Profile.class)
class NoOpActorIdentityBindingRepositoryIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "binding-write-read-test";
        }
    }

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Inject
    ActorIdentityBindingRepository bindingRepo;

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Inject
    ActorIdentityValidationEnricher identityEnricher;

    @InjectMock
    AgentSigner agentSigner;

    @BeforeEach
    void setUp() {
        identityEnricher.invalidateAll();
        when(agentSigner.sign(anyString(), any())).thenReturn(Optional.empty());
    }

    @Test
    void writePathAndReadPathBothReturnBindingData() {
        final String actorId = "claude:binding-test-" + UUID.randomUUID();

        final LedgerEntry[] saved = new LedgerEntry[1];
        QuarkusTransaction.requiringNew().run(() -> {
            final TestEntry e = new TestEntry();
            e.subjectId = UUID.randomUUID();
            e.entryType = LedgerEntryType.EVENT;
            e.actorId = actorId;
            e.actorType = ActorType.AGENT;
            e.actorRole = "binding-it";
            e.actorDid = "did:web:binding-test.example.com";
            saved[0] = ledgerRepo.save(e, DEFAULT_TENANT_ID);
        });

        assertThat(((JpaLedgerEntry) saved[0]).pendingIdentityStatus).isEqualTo(IdentityBindingStatus.DID_UNRESOLVABLE);

        // Write path: binding entry written via JpaLedgerEntryRepository
        await().atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> {
                final Long count = QuarkusTransaction.requiringNew().call(() ->
                    (Long) em.createNativeQuery(
                        "SELECT COUNT(*) FROM actor_identity_binding aib " +
                        "JOIN ledger_entry le ON le.id = aib.id " +
                        "WHERE le.actor_id = :id")
                        .setParameter("id", actorId)
                        .getSingleResult()
                );
                assertThat(count).isPositive();
            });

        // Read path: JpaActorIdentityBindingRepository auto-active, returns the binding
        await().atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> {
                final Optional<ActorIdentityBindingEntry> viaRepo = QuarkusTransaction.requiringNew()
                    .call(() -> bindingRepo.latestBindingFor(actorId, DEFAULT_TENANT_ID));
                assertThat(viaRepo).isPresent();
                assertThat(viaRepo.get().boundDid).isEqualTo("did:web:binding-test.example.com");
            });
    }
}
