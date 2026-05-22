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
import io.casehub.ledger.runtime.service.AgentKeyProvider;
import io.casehub.ledger.runtime.service.AgentSignatureEnricher;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;
import io.casehub.ledger.runtime.service.SigningKey;
import io.casehub.ledger.service.supplement.TestEntry;

class AgentSignatureEnricherTest {

    private KeyPair testKeyPair;
    private AgentSignatureEnricher enricher;

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
        enricher = new AgentSignatureEnricher(actorId -> Optional.of(SigningKey.of(kp)));

        final TestEntry e = entry("claude:reviewer@v1");
        enricher.enrich(e);

        assertThat(e.agentSignature).isNotNull().hasSizeGreaterThan(0);
        assertThat(e.agentPublicKey).isNotNull()
            .isEqualTo(kp.getPublic().getEncoded());
    }

    @Test
    void signatureVerifiesAgainstStoredPublicKey() throws Exception {
        enricher = new AgentSignatureEnricher(actorId -> Optional.of(SigningKey.of(testKeyPair)));

        final TestEntry e = entry("claude:reviewer@v1");
        enricher.enrich(e);

        final Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(testKeyPair.getPublic());
        sig.update(LedgerMerkleTree.canonicalBytes(e));
        assertThat(sig.verify(e.agentSignature)).isTrue();
    }

    @Test
    void leavesFieldsNull_whenActorHasNoKey() {
        enricher = new AgentSignatureEnricher(actorId -> Optional.empty());

        final TestEntry e = entry("unknown-actor");
        enricher.enrich(e);

        assertThat(e.agentSignature).isNull();
        assertThat(e.agentPublicKey).isNull();
    }

    @Test
    void leavesFieldsNull_whenActorIdIsNull() {
        enricher = new AgentSignatureEnricher(actorId -> Optional.of(SigningKey.of(testKeyPair)));

        final TestEntry e = entry(null);
        enricher.enrich(e);

        assertThat(e.agentSignature).isNull();
        assertThat(e.agentPublicKey).isNull();
    }

    @Test
    void isIdempotent_secondCallIsNoOp() {
        enricher = new AgentSignatureEnricher(actorId -> Optional.of(SigningKey.of(testKeyPair)));

        final TestEntry e = entry("claude:reviewer@v1");
        enricher.enrich(e);
        final byte[] firstSig = e.agentSignature.clone();
        final byte[] firstKey = e.agentPublicKey.clone();

        // Replace key provider with a different key — if re-signing occurred, the signature would differ
        final KeyPair otherPair;
        try {
            otherPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        enricher = new AgentSignatureEnricher(actorId -> Optional.of(SigningKey.of(otherPair)));
        enricher.enrich(e);

        assertThat(e.agentSignature).isEqualTo(firstSig);
        assertThat(e.agentPublicKey).isEqualTo(firstKey);
    }

    @Test
    void doesNotThrow_whenKeyProviderThrows() {
        enricher = new AgentSignatureEnricher(actorId -> {
            throw new RuntimeException("key store unavailable");
        });

        final TestEntry e = entry("claude:reviewer@v1");
        assertThatCode(() -> enricher.enrich(e)).doesNotThrowAnyException();
        assertThat(e.agentSignature).isNull();
    }

    @Test
    void populatesAgentKeyRef_matchingSigningKeyRef() {
        final SigningKey sk = SigningKey.of(testKeyPair);
        enricher = new AgentSignatureEnricher(actorId -> Optional.of(sk));

        final TestEntry e = entry("claude:reviewer@v1");
        enricher.enrich(e);

        assertThat(e.agentKeyRef).isEqualTo(sk.keyRef());
    }

    @Test
    void agentKeyRef_isNullWhenUnsigned() {
        enricher = new AgentSignatureEnricher(actorId -> Optional.empty());

        final TestEntry e = entry("unknown-actor");
        enricher.enrich(e);

        assertThat(e.agentKeyRef).isNull();
    }
}
