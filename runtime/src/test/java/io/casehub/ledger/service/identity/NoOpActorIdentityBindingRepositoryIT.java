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
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
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
 * Verifies that {@code NoOpActorIdentityBindingRepository} is the active CDI bean
 * under the {@code noop-test} profile and that it prevents any DB writes when
 * {@code ActorIdentityBindingObserver} fires.
 *
 * <p>The {@code noop-test} profile selects {@code JpaLedgerEntryRepository} and
 * {@code JpaLedgerMerkleFrontierRepository} but deliberately omits
 * {@code JpaActorIdentityBindingRepository}, leaving the {@code @DefaultBean} no-op
 * as the active binding.
 *
 * <p>Tests do NOT use {@code @Transactional} — Awaitility polls from its own thread
 * which has no transaction context. Saves and DB reads use programmatic transactions
 * via {@link QuarkusTransaction}.
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
     * Saves a {@link TestEntry} with a non-null {@code actorDid}.
     *
     * <p>{@code InjectableTestDIDResolver @Alternative @Priority(1)} auto-activates with an
     * empty registry. {@code resolve()} returns {@code Optional.empty()} for any DID, so
     * {@code ActorIdentityValidationEnricher} returns {@code DID_UNRESOLVABLE}, fires
     * {@code AgentIdentityViolationEvent}, and {@code ActorIdentityBindingObserver.onViolation()}
     * calls {@code repository.save()} — which resolves to the no-op, preventing any persist.
     *
     * <p>The assertion reads the live H2 database (not the no-op), proving the distinction
     * between "observer ran, no-op blocked the write" and "observer never ran, trivially empty
     * table."
     */
    @Test
    void observerFiresButNoRowsWrittenWhenNoOpIsActive() {
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

        // Verify the enricher pipeline ran and the enricher set pendingIdentityStatus.
        // InjectableTestDIDResolver has an empty registry, so resolve() returns empty →
        // ActorIdentityValidationEnricher sets DID_UNRESOLVABLE and fires AgentIdentityViolationEvent.
        // Without this check, the DB-count assertion could pass vacuously if the enricher never ran.
        assertThat(saved[0].pendingIdentityStatus).isEqualTo(IdentityBindingStatus.DID_UNRESOLVABLE);

        // during(500ms) gives the async observer time to fire; the DB count must stay 0
        // because NoOpActorIdentityBindingRepository.save() never calls em.persist().
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> {
                final Long count = QuarkusTransaction.requiringNew().call(() ->
                    (Long) em.createNativeQuery(
                        "SELECT COUNT(*) FROM actor_identity_binding aib " +
                        "JOIN ledger_entry le ON le.id = aib.id " +
                        "WHERE le.actor_id = :id")
                        .setParameter("id", actorId)
                        .getSingleResult()
                );
                assertThat(count).isZero();
            });
    }
}
