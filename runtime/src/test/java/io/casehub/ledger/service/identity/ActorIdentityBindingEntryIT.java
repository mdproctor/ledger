package io.casehub.ledger.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.IdentityBindingStatus;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.identity.DIDDocument;
import io.casehub.ledger.api.spi.identity.VerificationMethod;
import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.casehub.ledger.runtime.repository.ActorIdentityBindingRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.casehub.ledger.runtime.service.identity.ActorIdentityValidationEnricher;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for the async {@code ActorIdentityBindingEntry} persistence pipeline.
 *
 * <p>Verifies that saving a {@link io.casehub.ledger.runtime.model.LedgerEntry} with a DID
 * binding triggers the enricher → async CDI event → observer → binding entry persist flow
 * end-to-end inside a Quarkus container with H2.
 *
 * <p>Uses an isolated DB to avoid interference with other test classes. Each test method
 * uses a unique actorId to prevent cross-test contamination from REQUIRES_NEW commits
 * that survive test rollbacks.
 *
 * <p>Tests do NOT use {@code @Transactional} because Awaitility polls from its own thread
 * which has no transaction context. Instead, saves and reads use programmatic transactions
 * via {@link QuarkusTransaction}.
 */
@QuarkusTest
@TestProfile(ActorIdentityBindingEntryIT.IdentityBindingProfile.class)
class ActorIdentityBindingEntryIT {

    public static class IdentityBindingProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "identity-binding-test";
        }
    }

    @Inject LedgerEntryRepository ledgerRepo;
    @Inject ActorIdentityBindingRepository bindingRepo;
    @Inject ActorIdentityValidationEnricher identityEnricher;
    @Inject InjectableTestDIDResolver testResolver;

    @InjectMock
    AgentSigner agentSigner;

    private KeyPair keyPair;
    private byte[] pubKeyEncoded;

    @BeforeEach
    void setUp() throws Exception {
        identityEnricher.invalidateAll();
        testResolver.clear();

        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        pubKeyEncoded = keyPair.getPublic().getEncoded();

        // Default: no signing for any actor
        when(agentSigner.sign(anyString(), any())).thenReturn(Optional.empty());
    }

    /** Configures a DID document and AgentSigner mock for the given actorId. */
    private void configureDid(final String actorId, final String did) {
        final var vm = new VerificationMethod(did + "#key-1", "Ed25519", pubKeyEncoded);
        final var doc = new DIDDocument(did, List.of(vm), List.of(actorId));
        testResolver.register(did, doc);

        when(agentSigner.sign(eq(actorId), any()))
                .thenAnswer(inv -> Optional.of(
                        AgentSignature.signWith(keyPair, inv.getArgument(1))));
    }

    @Test
    void bindingEntryPersistedAsyncAfterSave() {
        final String actorId = "claude:binding-persist-" + UUID.randomUUID();
        final String did = "did:web:persist-test.example.com";
        configureDid(actorId, did);

        saveEntryWithDid(actorId, did, 1);

        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                final var binding = readLatestBinding(actorId);
                assertThat(binding).isPresent();
                assertThat(binding.get().validationResult).isEqualTo(IdentityBindingStatus.VALID);
                assertThat(binding.get().alsoKnownAsVerified).isTrue();
                assertThat(binding.get().keyMatchVerified).isTrue();
                assertThat(binding.get().boundDid).isEqualTo(did);
                assertThat(binding.get().didMethod).isEqualTo("web");
            });
    }

    @Test
    void cacheHitProducesNoDuplicateBindingEntry() {
        final String actorId = "claude:binding-cache-" + UUID.randomUUID();
        final String did = "did:web:cache-test.example.com";
        configureDid(actorId, did);

        saveEntryWithDid(actorId, did, 1);

        // Wait for the first binding entry to be committed
        await().atMost(5, TimeUnit.SECONDS)
            .until(() -> readLatestBinding(actorId).isPresent());

        // Save a second entry for the same actor — cache hit, no new binding entry
        saveEntryWithDid(actorId, did, 2);

        // Short wait to confirm no second binding entry appears
        await().during(500, TimeUnit.MILLISECONDS)
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(readBindingHistory(actorId)).hasSize(1));
    }

    @Test
    void invalidateAllCausesNewBindingEntryOnNextSave() {
        final String actorId = "claude:binding-invalidate-" + UUID.randomUUID();
        final String did = "did:web:invalidate-test.example.com";
        configureDid(actorId, did);

        saveEntryWithDid(actorId, did, 1);

        // Wait for the first binding entry
        await().atMost(5, TimeUnit.SECONDS)
            .until(() -> readLatestBinding(actorId).isPresent());

        // Invalidate all cached status entries
        identityEnricher.invalidateAll();

        // Save again — cache miss, should produce a second binding entry
        saveEntryWithDid(actorId, did, 3);

        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(readBindingHistory(actorId)).hasSize(2));
    }

    /**
     * Saves a TestEntry with actorDid set, in a committed transaction.
     * agentSignature and agentPublicKey are populated by AgentSignatureEnricher from the mocked signer.
     */
    private void saveEntryWithDid(final String actorId, final String did, final int seq) {
        QuarkusTransaction.requiringNew().run(() -> {
            final TestEntry e = new TestEntry();
            e.subjectId = UUID.randomUUID();
            e.sequenceNumber = seq;
            e.entryType = LedgerEntryType.EVENT;
            e.actorId = actorId;
            e.actorType = ActorType.AGENT;
            e.actorRole = "binding-it";
            e.actorDid = did;
            ledgerRepo.save(e);
        });
    }

    /** Reads latest binding in its own transaction. */
    private java.util.Optional<ActorIdentityBindingEntry> readLatestBinding(final String actorId) {
        return QuarkusTransaction.requiringNew()
            .call(() -> bindingRepo.latestBindingFor(actorId));
    }

    /** Reads binding history in its own transaction. */
    private List<ActorIdentityBindingEntry> readBindingHistory(final String actorId) {
        return QuarkusTransaction.requiringNew()
            .call(() -> bindingRepo.bindingHistoryFor(actorId));
    }
}
