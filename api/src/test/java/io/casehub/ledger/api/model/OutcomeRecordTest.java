package io.casehub.ledger.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.casehub.ledger.api.model.AttestationVerdict.SOUND;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;

class OutcomeRecordTest {

    private final UUID subjectId = UUID.randomUUID();

    @Test
    void nullActorId_throwsNPE() {
        assertThatThrownBy(() -> OutcomeRecord.of(null, subjectId, "strategy", SOUND, 0.7))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("actorId");
    }

    @Test
    void nullSubjectId_throwsNPE() {
        assertThatThrownBy(() -> OutcomeRecord.of("actor", null, "strategy", SOUND, 0.7))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("subjectId");
    }

    @Test
    void nullCapabilityTag_throwsNPE() {
        assertThatThrownBy(() -> OutcomeRecord.of("actor", subjectId, null, SOUND, 0.7))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("capabilityTag");
    }

    @Test
    void nullVerdict_throwsNPE() {
        assertThatThrownBy(() -> OutcomeRecord.of("actor", subjectId, "strategy", null, 0.7))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("verdict");
    }

    @Test
    void confidenceZero_throwsIAE() {
        assertThatThrownBy(() -> OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence must be in (0.0, 1.0]");
    }

    @Test
    void confidenceAboveOne_throwsIAE() {
        assertThatThrownBy(() -> OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence must be in (0.0, 1.0]");
    }

    @Test
    void confidenceOne_accepted() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 1.0);
        assertThat(r.confidence()).isEqualTo(1.0);
    }

    @Test
    void confidencePointSeven_accepted() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7);
        assertThat(r.confidence()).isEqualTo(0.7);
    }

    @Test
    void nullActorType_defaultsToAgent() {
        OutcomeRecord r = new OutcomeRecord("actor", subjectId, SOUND, 0.7,
                "strategy", null, null, null, null, null);
        assertThat(r.actorType()).isEqualTo(ActorType.AGENT);
    }

    @Test
    void attestorIdSetTypeNull_throwsIAE() {
        assertThatThrownBy(() -> new OutcomeRecord("actor", subjectId, SOUND, 0.7,
                "strategy", ActorType.AGENT, null, null, "some-attestor", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attestorId and attestorType must be provided together");
    }

    @Test
    void attestorTypeSetIdNull_throwsIAE() {
        assertThatThrownBy(() -> new OutcomeRecord("actor", subjectId, SOUND, 0.7,
                "strategy", ActorType.AGENT, null, null, null, ActorType.SYSTEM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attestorId and attestorType must be provided together");
    }

    @Test
    void bothAttestorFieldsNull_accepted() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7);
        assertThat(r.attestorId()).isNull();
        assertThat(r.attestorType()).isNull();
    }

    @Test
    void of_setsCapabilityTag() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7);
        assertThat(r.capabilityTag()).isEqualTo("strategy");
        assertThat(r.actorType()).isEqualTo(ActorType.AGENT);
        assertThat(r.actorRole()).isNull();
        assertThat(r.occurredAt()).isNull();
        assertThat(r.attestorId()).isNull();
    }

    @Test
    void ofGlobal_setsGlobalCapabilityTag() {
        OutcomeRecord r = OutcomeRecord.ofGlobal("actor", subjectId, SOUND, 0.7);
        assertThat(r.capabilityTag()).isEqualTo(CapabilityTag.GLOBAL);
    }

    @Test
    void withActorType_null_throwsNPE() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7);
        assertThatThrownBy(() -> r.withActorType(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withActorType_setsType_preservesOtherFields() {
        OutcomeRecord orig = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7);
        OutcomeRecord updated = orig.withActorType(ActorType.HUMAN);
        assertThat(updated.actorType()).isEqualTo(ActorType.HUMAN);
        assertThat(updated.actorId()).isEqualTo("actor");
        assertThat(updated.capabilityTag()).isEqualTo("strategy");
        assertThat(updated.confidence()).isEqualTo(0.7);
    }

    @Test
    void withActorRole_null_throwsNPE() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7);
        assertThatThrownBy(() -> r.withActorRole(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withActorRole_setsRole() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7)
                .withActorRole("strategist");
        assertThat(r.actorRole()).isEqualTo("strategist");
    }

    @Test
    void withOccurredAt_null_throwsNPE() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7);
        assertThatThrownBy(() -> r.withOccurredAt(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withOccurredAt_setsTimestamp() {
        Instant ts = Instant.parse("2026-06-02T10:00:00Z");
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7)
                .withOccurredAt(ts);
        assertThat(r.occurredAt()).isEqualTo(ts);
    }

    @Test
    void withAttestor_null_id_throwsNPE() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7);
        assertThatThrownBy(() -> r.withAttestor(null, ActorType.SYSTEM))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withAttestor_null_type_throwsNPE() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7);
        assertThatThrownBy(() -> r.withAttestor("attestor-id", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withAttestor_setsBothFields() {
        OutcomeRecord r = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7)
                .withAttestor("quarkmind:game-engine@v1", ActorType.SYSTEM);
        assertThat(r.attestorId()).isEqualTo("quarkmind:game-engine@v1");
        assertThat(r.attestorType()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void withMethods_returnNewInstances() {
        OutcomeRecord orig = OutcomeRecord.of("actor", subjectId, "strategy", SOUND, 0.7);
        OutcomeRecord updated = orig.withActorType(ActorType.HUMAN);
        assertThat(updated).isNotSameAs(orig);
        assertThat(orig.actorType()).isEqualTo(ActorType.AGENT);
    }
}
