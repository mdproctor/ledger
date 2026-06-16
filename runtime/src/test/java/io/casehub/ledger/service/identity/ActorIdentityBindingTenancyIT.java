package io.casehub.ledger.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.casehub.ledger.runtime.repository.ActorIdentityBindingRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.IdentityBindingStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Verifies per-tenant isolation in {@link ActorIdentityBindingRepository} read methods.
 *
 * <p>Two tenants can share the same {@code actorId} — e.g. a shared LLM persona like
 * {@code claude:reviewer@v1}. Binding entries from tenant A must not appear in queries
 * scoped to tenant B, and vice versa.
 *
 * <p>Uses an isolated DB to prevent cross-test contamination from REQUIRES_NEW commits.
 */
@QuarkusTest
@TestProfile(ActorIdentityBindingTenancyIT.TenancyProfile.class)
class ActorIdentityBindingTenancyIT {

    public static class TenancyProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "identity-binding-tenancy-test";
        }
    }

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Inject
    ActorIdentityBindingRepository bindingRepo;

    @InjectMock
    AgentSigner agentSigner;

    @BeforeEach
    void setUp() {
        when(agentSigner.sign(anyString(), any())).thenReturn(Optional.empty());
    }

    @Test
    void latestBindingForIsIsolatedByTenant() {
        final String actorId = "claude:tenancy-isolation-" + UUID.randomUUID();
        final String tenantA = "tenant-a-" + UUID.randomUUID();
        final String tenantB = "tenant-b-" + UUID.randomUUID();

        final ActorIdentityBindingEntry entryA = buildBindingEntry(actorId,
                "did:web:test-a.example.com", IdentityBindingStatus.VALID);
        QuarkusTransaction.requiringNew().run(() -> ledgerRepo.save(entryA, tenantA));

        // tenantA can read their own entry
        final Optional<ActorIdentityBindingEntry> forA = QuarkusTransaction.requiringNew()
            .call(() -> bindingRepo.latestBindingFor(actorId, tenantA));
        assertThat(forA).isPresent();
        assertThat(forA.get().tenancyId).isEqualTo(tenantA);

        // tenantB sees nothing — same actorId, different tenant
        final Optional<ActorIdentityBindingEntry> forB = QuarkusTransaction.requiringNew()
            .call(() -> bindingRepo.latestBindingFor(actorId, tenantB));
        assertThat(forB).isEmpty();
    }

    @Test
    void bindingHistoryForIsIsolatedByTenant() {
        final String actorId = "claude:history-isolation-" + UUID.randomUUID();
        final String tenantA = "tenant-ha-" + UUID.randomUUID();
        final String tenantB = "tenant-hb-" + UUID.randomUUID();

        final ActorIdentityBindingEntry e1 = buildBindingEntry(actorId,
                "did:web:history-a.example.com", IdentityBindingStatus.VALID);
        final ActorIdentityBindingEntry e2 = buildBindingEntry(actorId,
                "did:web:history-b.example.com", IdentityBindingStatus.DID_UNRESOLVABLE);
        QuarkusTransaction.requiringNew().run(() -> ledgerRepo.save(e1, tenantA));
        QuarkusTransaction.requiringNew().run(() -> ledgerRepo.save(e2, tenantB));

        // tenantA sees exactly 1 entry
        assertThat(QuarkusTransaction.requiringNew()
            .call(() -> bindingRepo.bindingHistoryFor(actorId, tenantA))).hasSize(1);
        // tenantB sees exactly 1 entry
        assertThat(QuarkusTransaction.requiringNew()
            .call(() -> bindingRepo.bindingHistoryFor(actorId, tenantB))).hasSize(1);
    }

    private static ActorIdentityBindingEntry buildBindingEntry(
            final String actorId, final String did, final IdentityBindingStatus status) {
        final ActorIdentityBindingEntry e = new ActorIdentityBindingEntry();
        e.id = UUID.randomUUID();
        e.subjectId = UUID.nameUUIDFromBytes(actorId.getBytes(StandardCharsets.UTF_8));
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.actorRole = "tenancy-test";
        e.entryType = LedgerEntryType.EVENT;
        e.occurredAt = Instant.now();
        e.boundDid = did;
        e.validationResult = status;
        e.alsoKnownAsVerified = status == IdentityBindingStatus.VALID;
        e.keyMatchVerified = status == IdentityBindingStatus.VALID;
        return e;
    }
}
