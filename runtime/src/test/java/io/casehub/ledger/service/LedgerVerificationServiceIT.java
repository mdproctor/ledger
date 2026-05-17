package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.SigningKey;
import io.casehub.ledger.runtime.service.model.InclusionProof;
import io.casehub.ledger.runtime.service.model.VerificationResult;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class LedgerVerificationServiceIT {

    @Inject
    LedgerVerificationService verificationService;
    @Inject
    LedgerEntryRepository repo;
    @Inject
    EntityManager em;
    @Inject
    KeyRotationService rotationService;

    private TestEntry seedEntry(UUID subjectId, int seq, String actorId) {
        TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.sequenceNumber = seq;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.SYSTEM;
        e.actorRole = "Tester";
        return (TestEntry) repo.save(e);
    }

    @Test
    @Transactional
    void treeRoot_afterOneEntry_matchesFrontier() {
        UUID sub = UUID.randomUUID();
        seedEntry(sub, 1, "actor-a");

        String root = verificationService.treeRoot(sub);
        List<LedgerMerkleFrontier> frontier = em
                .createNamedQuery("LedgerMerkleFrontier.findBySubjectId", LedgerMerkleFrontier.class)
                .setParameter("subjectId", sub)
                .getResultList();

        assertThat(root).isNotNull().matches("[0-9a-f]{64}");
        assertThat(root).isEqualTo(LedgerMerkleTree.treeRoot(frontier));
    }

    @Test
    @Transactional
    void treeRoot_fiveEntries_frontierHasBitCount5Nodes() {
        UUID sub = UUID.randomUUID();
        for (int i = 1; i <= 5; i++)
            seedEntry(sub, i, "actor-" + i);

        verificationService.treeRoot(sub);

        List<LedgerMerkleFrontier> frontier = em
                .createNamedQuery("LedgerMerkleFrontier.findBySubjectId", LedgerMerkleFrontier.class)
                .setParameter("subjectId", sub)
                .getResultList();
        assertThat(frontier).hasSize(Integer.bitCount(5));
    }

    @Test
    @Transactional
    void inclusionProof_singleEntry_verifies() {
        UUID sub = UUID.randomUUID();
        TestEntry e = seedEntry(sub, 1, "actor-a");

        InclusionProof proof = verificationService.inclusionProof(e.id);

        assertThat(proof).isNotNull();
        assertThat(proof.entryId()).isEqualTo(e.id);
        assertThat(proof.entryIndex()).isEqualTo(0);
        assertThat(proof.treeSize()).isEqualTo(1);
        assertThat(proof.siblings()).isEmpty();
        assertThat(LedgerMerkleTree.verifyProof(proof, proof.treeRoot())).isTrue();
    }

    @Test
    @Transactional
    void inclusionProof_fourEntries_allVerify() {
        UUID sub = UUID.randomUUID();
        TestEntry[] entries = new TestEntry[4];
        for (int i = 0; i < 4; i++)
            entries[i] = seedEntry(sub, i + 1, "actor-" + i);

        String root = verificationService.treeRoot(sub);
        for (TestEntry e : entries) {
            InclusionProof proof = verificationService.inclusionProof(e.id);
            assertThat(LedgerMerkleTree.verifyProof(proof, root))
                    .as("entry %s should verify", e.id).isTrue();
        }
    }

    @Test
    @Transactional
    void inclusionProof_lastOfSevenEntries_verifies() {
        UUID sub = UUID.randomUUID();
        TestEntry last = null;
        for (int i = 1; i <= 7; i++)
            last = seedEntry(sub, i, "actor-" + i);

        String root = verificationService.treeRoot(sub);
        InclusionProof proof = verificationService.inclusionProof(last.id);
        assertThat(LedgerMerkleTree.verifyProof(proof, root)).isTrue();
    }

    @Test
    @Transactional
    void verify_untamperedChain_returnsTrue() {
        UUID sub = UUID.randomUUID();
        for (int i = 1; i <= 5; i++)
            seedEntry(sub, i, "actor-" + i);
        assertThat(verificationService.verify(sub)).isTrue();
    }

    @Test
    @Transactional
    void verify_afterDigestTampering_returnsFalse() {
        UUID sub = UUID.randomUUID();
        TestEntry e = seedEntry(sub, 1, "actor-a");

        LedgerEntry stored = repo.findEntryById(e.id).orElseThrow();
        stored.digest = "0000000000000000000000000000000000000000000000000000000000000000";
        repo.save(stored);

        assertThat(verificationService.verify(sub)).isFalse();
    }

    // ── Agent signature verification ──────────────────────────────────────────

    @Test
    @Transactional
    void verifyAgentSignature_unsignedEntry_returnsUnsigned() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "unsigned-actor");

        assertThat(verificationService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.UNSIGNED);
    }

    @Test
    @Transactional
    void verifyAgentSignature_validSignature_returnsValid() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "signed-actor");

        final byte[] canonical = LedgerMerkleTree.canonicalBytes(e);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(canonical);
        e.agentSignature = sig.sign();
        e.agentPublicKey = kp.getPublic().getEncoded();
        e.agentKeyRef = SigningKey.of(kp).keyRef();
        repo.save(e);

        assertThat(verificationService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.VALID);
    }

    @Test
    @Transactional
    void verifyAgentSignature_tamperedSignature_returnsInvalid() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "signed-actor");

        final byte[] canonical = LedgerMerkleTree.canonicalBytes(e);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(canonical);
        final byte[] signature = sig.sign();
        signature[0] ^= 0xFF;
        e.agentSignature = signature;
        e.agentPublicKey = kp.getPublic().getEncoded();
        e.agentKeyRef = SigningKey.of(kp).keyRef();
        repo.save(e);

        assertThat(verificationService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.INVALID);
    }

    @Test
    @Transactional
    void verifyAgentSignature_unknownEntry_throwsIllegalArgument() {
        final UUID nonexistent = UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> verificationService.verifyAgentSignature(nonexistent));
    }

    @Test
    @Transactional
    void verifyAgentSignature_mutatedActorId_returnsInvalid() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "original-actor");

        final byte[] canonical = LedgerMerkleTree.canonicalBytes(e);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(canonical);
        e.agentSignature = sig.sign();
        e.agentPublicKey = kp.getPublic().getEncoded();
        e.agentKeyRef = SigningKey.of(kp).keyRef();
        e.actorId = "impersonator-actor";
        repo.save(e);

        assertThat(verificationService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.INVALID);
    }

    @Test
    @Transactional
    void verifyAgentSignature_compromisedKey_afterEffectiveSince_returnsSuspect() throws Exception {
        final java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("Ed25519");
        final SigningKey sk = SigningKey.of(gen.generateKeyPair());
        final UUID sub = UUID.randomUUID();

        final TestEntry e = seedEntry(sub, 1, "claude:reviewer@v1");
        final byte[] canonical = LedgerMerkleTree.canonicalBytes(e);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(sk.keyPair().getPrivate());
        sig.update(canonical);
        e.agentSignature = sig.sign();
        e.agentPublicKey = sk.keyPair().getPublic().getEncoded();
        e.agentKeyRef = sk.keyRef();
        repo.save(e);

        // effectiveSince BEFORE the entry's occurredAt → entry is SUSPECT
        final java.time.Instant compromisedSince = e.occurredAt.minusSeconds(60);
        rotationService.recordRotation("claude:reviewer@v1", sk.keyRef(), null,
                KeyRotationReason.COMPROMISED, compromisedSince);

        assertThat(verificationService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.SUSPECT);
    }

    @Test
    @Transactional
    void verifyAgentSignature_compromisedKey_beforeEffectiveSince_returnsValid() throws Exception {
        final java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("Ed25519");
        final SigningKey sk = SigningKey.of(gen.generateKeyPair());
        final UUID sub = UUID.randomUUID();

        final TestEntry e = seedEntry(sub, 1, "claude:reviewer@v1");
        final byte[] canonical = LedgerMerkleTree.canonicalBytes(e);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(sk.keyPair().getPrivate());
        sig.update(canonical);
        e.agentSignature = sig.sign();
        e.agentPublicKey = sk.keyPair().getPublic().getEncoded();
        e.agentKeyRef = sk.keyRef();
        repo.save(e);

        // effectiveSince AFTER the entry's occurredAt → entry is still VALID
        final java.time.Instant compromisedSince = e.occurredAt.plusSeconds(3600);
        rotationService.recordRotation("claude:reviewer@v1", sk.keyRef(), null,
                KeyRotationReason.COMPROMISED, compromisedSince);

        assertThat(verificationService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.VALID);
    }

    @Test
    @Transactional
    void verifyAgentSignature_scheduledRotation_doesNotProduceSuspect() throws Exception {
        final java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("Ed25519");
        final SigningKey sk = SigningKey.of(gen.generateKeyPair());
        final UUID sub = UUID.randomUUID();

        final TestEntry e = seedEntry(sub, 1, "claude:reviewer@v1");
        final byte[] canonical = LedgerMerkleTree.canonicalBytes(e);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(sk.keyPair().getPrivate());
        sig.update(canonical);
        e.agentSignature = sig.sign();
        e.agentPublicKey = sk.keyPair().getPublic().getEncoded();
        e.agentKeyRef = sk.keyRef();
        repo.save(e);

        // SCHEDULED rotation — never produces SUSPECT regardless of effectiveSince
        rotationService.recordRotation("claude:reviewer@v1", sk.keyRef(),
                SigningKey.of(gen.generateKeyPair()).keyRef(),
                KeyRotationReason.SCHEDULED, e.occurredAt.minusSeconds(60));

        assertThat(verificationService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.VALID);
    }
}
