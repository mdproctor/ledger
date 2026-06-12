package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentEntrySigner;
import io.casehub.ledger.service.supplement.TestEntry;

class AgentEntrySignerTest {

    private KeyPair testKeyPair;
    private AgentEntrySigner signer;

    @BeforeEach
    void setUp() throws Exception {
        testKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private TestEntry entry(final String actorId) {
        final TestEntry e = new TestEntry();
        e.id = UUID.randomUUID();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.actorRole = "Reviewer";
        e.occurredAt = Instant.now();
        return e;
    }

    @Test
    void populatesSignatureAndPublicKey_whenActorHasKey() {
        final KeyPair kp = testKeyPair;
        signer = new AgentEntrySigner(
                (actorId, data) -> Optional.of(AgentSignature.signWith(kp, data)));

        final TestEntry e = entry("claude:reviewer@v1");
        signer.sign(e);

        assertThat(e.agentSignature).isNotNull().hasSizeGreaterThan(0);
        assertThat(e.agentPublicKey).isNotNull().isEqualTo(kp.getPublic().getEncoded());
    }

    @Test
    void signatureVerifiesAgainstStoredPublicKey() throws Exception {
        signer = new AgentEntrySigner(
                (actorId, data) -> Optional.of(AgentSignature.signWith(testKeyPair, data)));

        final TestEntry e = entry("claude:reviewer@v1");
        signer.sign(e);

        final Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(testKeyPair.getPublic());
        sig.update(e.canonicalBytes());
        assertThat(sig.verify(e.agentSignature)).isTrue();
    }

    @Test
    void leavesFieldsNull_whenActorHasNoKey() {
        signer = new AgentEntrySigner((actorId, data) -> Optional.empty());

        final TestEntry e = entry("unknown-actor");
        signer.sign(e);

        assertThat(e.agentSignature).isNull();
        assertThat(e.agentPublicKey).isNull();
    }

    @Test
    void leavesFieldsNull_whenActorIdIsNull() {
        signer = new AgentEntrySigner(
                (actorId, data) -> Optional.of(AgentSignature.signWith(testKeyPair, data)));

        final TestEntry e = entry(null);
        signer.sign(e);

        assertThat(e.agentSignature).isNull();
        assertThat(e.agentPublicKey).isNull();
    }

    @Test
    void isIdempotent_secondCallIsNoOp() throws Exception {
        signer = new AgentEntrySigner(
                (actorId, data) -> Optional.of(AgentSignature.signWith(testKeyPair, data)));

        final TestEntry e = entry("claude:reviewer@v1");
        signer.sign(e);
        final byte[] firstSig = e.agentSignature.clone();
        final byte[] firstKey = e.agentPublicKey.clone();

        final KeyPair otherPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        signer = new AgentEntrySigner(
                (actorId, data) -> Optional.of(AgentSignature.signWith(otherPair, data)));
        signer.sign(e);

        assertThat(e.agentSignature).isEqualTo(firstSig);
        assertThat(e.agentPublicKey).isEqualTo(firstKey);
    }

    @Test
    void doesNotThrow_whenSignerThrows() {
        signer = new AgentEntrySigner((actorId, data) -> {
            throw new RuntimeException("key store unavailable");
        });

        final TestEntry e = entry("claude:reviewer@v1");
        assertThatCode(() -> signer.sign(e)).doesNotThrowAnyException();
        assertThat(e.agentSignature).isNull();
    }

    @Test
    void populatesAgentKeyRef_fromSignerResult() {
        signer = new AgentEntrySigner(
                (actorId, data) -> Optional.of(AgentSignature.signWith(testKeyPair, data)));

        final TestEntry e = entry("claude:reviewer@v1");
        signer.sign(e);

        assertThat(e.agentKeyRef).isNotNull().hasSize(43);
        assertThat(e.agentKeyRef).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void agentKeyRef_isNullWhenUnsigned() {
        signer = new AgentEntrySigner((actorId, data) -> Optional.empty());

        final TestEntry e = entry("unknown-actor");
        signer.sign(e);

        assertThat(e.agentKeyRef).isNull();
    }

    @Test
    void prepareKey_populatesPublicKeyAndKeyRef_butNotSignature() {
        signer = new AgentEntrySigner(
                (actorId, data) -> Optional.of(AgentSignature.signWith(testKeyPair, data)));

        final TestEntry e = entry("claude:reviewer@v1");
        signer.prepareKey(e);

        assertThat(e.agentPublicKey).isNotNull().isEqualTo(testKeyPair.getPublic().getEncoded());
        assertThat(e.agentKeyRef).isNotNull();
        assertThat(e.agentSignature).isNull();
    }

    @Test
    void prepareKey_thenSign_producesValidSignature() throws Exception {
        signer = new AgentEntrySigner(
                (actorId, data) -> Optional.of(AgentSignature.signWith(testKeyPair, data)));

        final TestEntry e = entry("claude:reviewer@v1");
        signer.prepareKey(e);
        signer.sign(e);

        assertThat(e.agentSignature).isNotNull();
        final Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(testKeyPair.getPublic());
        sig.update(e.canonicalBytes());
        assertThat(sig.verify(e.agentSignature)).isTrue();
    }

    @Test
    void prepareKey_isNoOp_whenActorIdIsNull() {
        signer = new AgentEntrySigner(
                (actorId, data) -> Optional.of(AgentSignature.signWith(testKeyPair, data)));

        final TestEntry e = entry(null);
        signer.prepareKey(e);

        assertThat(e.agentPublicKey).isNull();
        assertThat(e.agentKeyRef).isNull();
    }

    @Test
    void prepareKey_isNoOp_whenActorHasNoKey() {
        signer = new AgentEntrySigner((actorId, data) -> Optional.empty());

        final TestEntry e = entry("unknown-actor");
        signer.prepareKey(e);

        assertThat(e.agentPublicKey).isNull();
        assertThat(e.agentKeyRef).isNull();
    }
}
