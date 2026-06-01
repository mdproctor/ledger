package io.casehub.ledger.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.IdentityBindingStatus;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.VerificationMethod;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.casehub.ledger.runtime.service.identity.ActorIdentityValidationEnricher;
import io.casehub.ledger.runtime.service.identity.LedgerIdentityViolationException;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for {@code LedgerIdentityEnforcementListener} in ENFORCE mode.
 *
 * <p>Verifies that writes with non-VALID identity binding status are rejected with
 * {@link LedgerIdentityViolationException} when the validation mode is ENFORCE.
 *
 * <p>Uses an isolated DB and ENFORCE mode profile to avoid affecting other test classes.
 */
@QuarkusTest
@TestProfile(LedgerIdentityEnforcementIT.EnforceProfile.class)
class LedgerIdentityEnforcementIT {

    public static class EnforceProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "identity-enforce-test";
        }
    }

    @Inject LedgerEntryRepository ledgerRepo;
    @Inject ActorIdentityValidationEnricher identityEnricher;
    @Inject InjectableTestDIDResolver testResolver;

    @InjectMock
    AgentSigner agentSigner;

    static final String ACTOR_ID = "claude:enforce-it@v1";
    static final String DID = "did:web:enforce-test.example.com";

    private KeyPair keyPair;
    private byte[] pubKeyEncoded;

    @BeforeEach
    void setUp() throws Exception {
        identityEnricher.invalidateAll();
        testResolver.clear();

        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        pubKeyEncoded = keyPair.getPublic().getEncoded();

        // By default, sign entries for ACTOR_ID; no signing for other actors
        when(agentSigner.sign(anyString(), any())).thenReturn(Optional.empty());
        when(agentSigner.sign(eq(ACTOR_ID), any()))
                .thenAnswer(inv -> Optional.of(
                        AgentSignature.signWith(keyPair, inv.getArgument(1))));
    }

    @Test
    @Transactional
    void validBindingAllowsWrite() {
        // Register a DID document whose key matches the AgentSigner's key pair
        final var vm = new VerificationMethod(DID + "#key-1", "Ed25519", pubKeyEncoded);
        final var doc = new DIDDocument(DID, List.of(vm), List.of(ACTOR_ID));
        testResolver.register(DID, doc);

        final TestEntry entry = buildEntry();
        entry.actorDid = DID;
        // AgentSignatureEnricher will populate agentSignature + agentPublicKey from AgentSigner

        ledgerRepo.save(entry);
        assertThat(entry.id).isNotNull();
        assertThat(entry.pendingIdentityStatus).isEqualTo(IdentityBindingStatus.VALID);
    }

    @Test
    @Transactional
    void unresolvableDidBlocksWrite() {
        // No DID document registered — resolver returns empty
        final TestEntry entry = buildEntry();
        entry.actorDid = DID;

        assertThatThrownBy(() -> ledgerRepo.save(entry))
            .isInstanceOf(LedgerIdentityViolationException.class)
            .hasMessageContaining(ACTOR_ID)
            .extracting(ex -> ((LedgerIdentityViolationException) ex).status)
            .isEqualTo(IdentityBindingStatus.DID_UNRESOLVABLE);
    }

    @Test
    @Transactional
    void identityMismatchBlocksWrite() {
        // DID document has a different actor in alsoKnownAs
        final var vm = new VerificationMethod(DID + "#key-1", "Ed25519", pubKeyEncoded);
        final var doc = new DIDDocument(DID, List.of(vm), List.of("other:actor@v1"));
        testResolver.register(DID, doc);

        final TestEntry entry = buildEntry();
        entry.actorDid = DID;

        assertThatThrownBy(() -> ledgerRepo.save(entry))
            .isInstanceOf(LedgerIdentityViolationException.class)
            .extracting(ex -> ((LedgerIdentityViolationException) ex).status)
            .isEqualTo(IdentityBindingStatus.IDENTITY_MISMATCH);
    }

    @Test
    @Transactional
    void keyMismatchBlocksWrite() {
        // DID document has a different key than the agent's signing key
        final var vm = new VerificationMethod(DID + "#key-1", "Ed25519", new byte[]{99, 99, 99});
        final var doc = new DIDDocument(DID, List.of(vm), List.of(ACTOR_ID));
        testResolver.register(DID, doc);

        final TestEntry entry = buildEntry();
        entry.actorDid = DID;

        assertThatThrownBy(() -> ledgerRepo.save(entry))
            .isInstanceOf(LedgerIdentityViolationException.class)
            .extracting(ex -> ((LedgerIdentityViolationException) ex).status)
            .isEqualTo(IdentityBindingStatus.KEY_MISMATCH);
    }

    @Test
    @Transactional
    void noDidConfiguredAllowsWrite() {
        // Entry without actorDid — enricher skips, enforcement skips
        final TestEntry entry = buildEntry();
        // actorDid is null — no signing needed for this path

        ledgerRepo.save(entry);
        assertThat(entry.id).isNotNull();
        assertThat(entry.pendingIdentityStatus).isNull();
    }

    private TestEntry buildEntry() {
        final TestEntry e = new TestEntry();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = ACTOR_ID;
        e.actorType = ActorType.AGENT;
        e.actorRole = "enforce-it";
        return e;
    }
}
