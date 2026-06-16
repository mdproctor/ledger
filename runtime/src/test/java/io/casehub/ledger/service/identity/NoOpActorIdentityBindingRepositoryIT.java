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
import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.ActorIdentityBindingRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.IdentityBindingStatus;
import io.casehub.ledger.runtime.service.AgentSigner;
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
 * Verifies the read/write split introduced by routing binding entry saves through
 * {@code LedgerEntryRepository} rather than {@code ActorIdentityBindingRepository}.
 *
 * <p>The {@code noop-test} profile selects {@code JpaLedgerEntryRepository} and
 * {@code JpaLedgerMerkleFrontierRepository} but deliberately omits
 * {@code JpaActorIdentityBindingRepository}.
 *
 * <p>Write path: {@code ActorIdentityBindingObserver} calls {@code ledgerRepo.save()} →
 * {@code JpaLedgerEntryRepository} → binding entry IS written to {@code actor_identity_binding}.
 * Writes do NOT require {@code JpaActorIdentityBindingRepository} in selected-alternatives.
 *
 * <p>Read path: {@code ActorIdentityBindingRepository} resolves to {@code @DefaultBean}
 * no-op → {@code latestBindingFor()} returns empty. Reads require {@code JpaActorIdentityBindingRepository}
 * to be in selected-alternatives.
 */
@QuarkusTest
@TestProfile(NoOpActorIdentityBindingRepositoryIT.Profile.class)
class NoOpActorIdentityBindingRepositoryIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "noop-test";
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

    /**
     * Saves an entry with {@code actorDid} set. The enricher fires
     * {@code AgentIdentityViolationEvent} (DID unresolvable — no resolver registered).
     * The observer calls {@code ledgerRepo.save(bindingEntry)} — the JPA ledger repo
     * writes the row, even though {@code JpaActorIdentityBindingRepository} is absent.
     *
     * <p>Assert 1: row is written (write path does not need {@code JpaActorIdentityBindingRepository}).
     * Assert 2: {@code bindingRepo.latestBindingFor()} returns empty (no-op read is active).
     */
    @Test
    void writePathDoesNotNeedJpaBindingRepo_readPathRequiresIt() {
        final String actorId = "claude:noop-test-" + UUID.randomUUID();

        final LedgerEntry[] saved = new LedgerEntry[1];
        QuarkusTransaction.requiringNew().run(() -> {
            final TestEntry e = new TestEntry();
            e.subjectId = UUID.randomUUID();
            e.entryType = LedgerEntryType.EVENT;
            e.actorId = actorId;
            e.actorType = ActorType.AGENT;
            e.actorRole = "noop-it";
            e.actorDid = "did:web:noop-test.example.com";
            saved[0] = ledgerRepo.save(e, DEFAULT_TENANT_ID);
        });

        assertThat(saved[0].pendingIdentityStatus).isEqualTo(IdentityBindingStatus.DID_UNRESOLVABLE);

        // Assert 1: binding entry IS written via JpaLedgerEntryRepository
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

        // Assert 2: SPI read via @DefaultBean no-op returns empty
        final Optional<ActorIdentityBindingEntry> viaRepo = QuarkusTransaction.requiringNew()
            .call(() -> bindingRepo.latestBindingFor(actorId, DEFAULT_TENANT_ID));
        assertThat(viaRepo).isEmpty();
    }
}
