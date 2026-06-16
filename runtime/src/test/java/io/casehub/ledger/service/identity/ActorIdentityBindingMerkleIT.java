package io.casehub.ledger.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.casehub.ledger.runtime.repository.ActorIdentityBindingRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.identity.ActorIdentityValidationEnricher;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.VerificationMethod;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Regression guard for #144: verifies that saving an {@link ActorIdentityBindingEntry}
 * correctly updates the Merkle frontier so that {@link LedgerVerificationService#verify}
 * returns {@code true}.
 *
 * <p>Before the fix: {@code JpaActorIdentityBindingRepository.save()} never called
 * {@code frontierRepo.replace()}, leaving the frontier empty. {@code verify()} threw
 * {@code IllegalStateException} from the internal {@code treeRoot()} call.
 *
 * <p>After the fix: saves route through {@code JpaLedgerEntryRepository.save()}, which
 * updates the frontier. {@code verify()} returns {@code true}.
 */
@QuarkusTest
@TestProfile(ActorIdentityBindingMerkleIT.MerkleProfile.class)
class ActorIdentityBindingMerkleIT {

    public static class MerkleProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "identity-binding-merkle-test";
        }
    }

    @Inject LedgerEntryRepository ledgerRepo;
    @Inject ActorIdentityBindingRepository bindingRepo;
    @Inject LedgerVerificationService verificationService;
    @Inject ActorIdentityValidationEnricher identityEnricher;
    @Inject InjectableTestDIDResolver testResolver;

    @InjectMock
    AgentSigner agentSigner;

    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        identityEnricher.invalidateAll();
        testResolver.clear();
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        when(agentSigner.sign(anyString(), any())).thenReturn(Optional.empty());
    }

    @Test
    void bindingEntryMaintainsMerkleFrontier() throws Exception {
        final String actorId = "claude:merkle-binding-" + UUID.randomUUID();
        final String did = "did:web:merkle-test.example.com";

        // Configure a resolvable DID so ActorIdentityValidationEnricher fires AgentIdentityValidatedEvent
        final byte[] pubKey = keyPair.getPublic().getEncoded();
        final var vm = new VerificationMethod(did + "#key-1", "Ed25519", pubKey);
        final var doc = new DIDDocument(did, List.of(vm), List.of(actorId));
        testResolver.register(did, doc);
        when(agentSigner.sign(eq(actorId), any()))
            .thenAnswer(inv -> Optional.of(
                AgentSignature.signWith(keyPair, inv.getArgument(1))));

        // Save a normal entry with actorDid set — this triggers the identity validation pipeline
        QuarkusTransaction.requiringNew().run(() -> {
            final TestEntry e = new TestEntry();
            e.subjectId = UUID.randomUUID();
            e.entryType = LedgerEntryType.EVENT;
            e.actorId = actorId;
            e.actorType = ActorType.AGENT;
            e.actorRole = "merkle-it";
            e.actorDid = did;
            ledgerRepo.save(e, DEFAULT_TENANT_ID);
        });

        // Binding entries have subjectId = nameUUIDFromBytes(actorId) — distinct from the TestEntry's subjectId
        final UUID bindingSubjectId = UUID.nameUUIDFromBytes(
            actorId.getBytes(StandardCharsets.UTF_8));

        // Wait for the async observer to commit the binding entry
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            final Optional<ActorIdentityBindingEntry> binding = QuarkusTransaction.requiringNew()
                .call(() -> bindingRepo.latestBindingFor(actorId, DEFAULT_TENANT_ID));
            assertThat(binding).isPresent();
            assertThat(binding.get().sequenceNumber).isPositive();
            assertThat(binding.get().digest).isNotNull();
        });

        // verify() recomputes all leaf hashes for bindingSubjectId and compares against
        // the stored Merkle frontier. Returns true only when the frontier was correctly
        // updated during save(). Throws IllegalStateException if the frontier is empty.
        final boolean verified = QuarkusTransaction.requiringNew()
            .call(() -> verificationService.verify(bindingSubjectId, DEFAULT_TENANT_ID));
        assertThat(verified).isTrue();
    }
}
