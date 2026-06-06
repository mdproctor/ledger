package io.casehub.ledger.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.platform.api.identity.ActorType;

/**
 * Plain JUnit 5 unit tests for {@link PerActorTrustComputer}.
 *
 * <p>
 * No Quarkus runtime needed — uses the test constructor with a {@link CapturingTrustScoreRepo}
 * that stores upserted scores in memory. Verifies that the four-pass algorithm (capability,
 * dimension, capability-dimension, global) produces the expected score types and values.
 */
class PerActorTrustComputerTest {

    private CapturingTrustScoreRepo trustRepo;
    private PerActorTrustComputer computer;
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        trustRepo = new CapturingTrustScoreRepo();
        // Simple decay: 90-day half-life, no valence asymmetry
        computer = new PerActorTrustComputer(
                (ageInDays, verdict) -> Math.pow(2.0, -(double) ageInDays / 90),
                trustRepo,
                new AllAttestationsGlobalStrategy(),
                new AttestationAggregator(),
                AttestationAggregator.Strategy.WEIGHTED_MAJORITY);
    }

    // ── Test 1: No attestations → neutral GLOBAL score ───────────────────────

    @Test
    void noAttestations_producesNeutralGlobalScore() {
        final String actorId = "agent-no-att";
        final LedgerEntry entry = makeEntry(actorId, now.minus(1, ChronoUnit.DAYS));

        computer.computeForActor(actorId, List.of(entry), Map.of(), now);

        final ActorTrustScore global = trustRepo.findByActorId(actorId).orElse(null);
        assertThat(global).isNotNull();
        assertThat(global.scoreType).isEqualTo(ActorTrustScore.ScoreType.GLOBAL);
        assertThat(global.trustScore).isCloseTo(0.5, within(0.01));
        assertThat(global.alpha).isCloseTo(1.0, within(0.01));
        assertThat(global.beta).isCloseTo(1.0, within(0.01));
        // Only GLOBAL row produced — no capability/dimension attestations
        assertThat(trustRepo.allScores()).hasSize(1);
    }

    // ── Test 2: Positive attestation → high GLOBAL score ─────────────────────

    @Test
    void positiveAttestation_producesHighGlobalScore() {
        final String actorId = "agent-positive";
        final LedgerEntry entry = makeEntry(actorId, now.minus(1, ChronoUnit.DAYS));
        final LedgerAttestation att = makeAttestation(entry, AttestationVerdict.SOUND,
                CapabilityTag.GLOBAL, null, null);

        computer.computeForActor(actorId, List.of(entry),
                Map.of(entry.id, List.of(att)), now);

        final ActorTrustScore global = trustRepo.findByActorId(actorId).orElse(null);
        assertThat(global).isNotNull();
        assertThat(global.trustScore).isGreaterThan(0.6);
        assertThat(global.alpha).isGreaterThan(global.beta);
        assertThat(global.attestationPositive).isEqualTo(1);
        assertThat(global.attestationNegative).isEqualTo(0);
    }

    // ── Test 3: Capability-tagged attestation → CAPABILITY score ─────────────

    @Test
    void capabilityTaggedAttestation_producesCapabilityScore() {
        final String actorId = "agent-capability";
        final LedgerEntry entry = makeEntry(actorId, now.minus(1, ChronoUnit.DAYS));
        final LedgerAttestation att = makeAttestation(entry, AttestationVerdict.SOUND,
                "security-review", null, null);

        computer.computeForActor(actorId, List.of(entry),
                Map.of(entry.id, List.of(att)), now);

        // Should have both CAPABILITY and GLOBAL rows
        final Optional<ActorTrustScore> capScore = trustRepo.findCapabilityScore(actorId, "security-review");
        assertThat(capScore).isPresent();
        assertThat(capScore.get().scoreType).isEqualTo(ActorTrustScore.ScoreType.CAPABILITY);
        assertThat(capScore.get().capabilityKey).isEqualTo("security-review");
        assertThat(capScore.get().trustScore).isGreaterThan(0.6);

        // GLOBAL row should also exist
        assertThat(trustRepo.findByActorId(actorId)).isPresent();
    }

    // ── Test 4: Dimension attestation → DIMENSION score ──────────────────────

    @Test
    void dimensionAttestation_producesDimensionScore() {
        final String actorId = "agent-dimension";
        final LedgerEntry entry = makeEntry(actorId, now.minus(1, ChronoUnit.DAYS));
        final LedgerAttestation att = makeAttestation(entry, AttestationVerdict.SOUND,
                CapabilityTag.GLOBAL, "review-thoroughness", 0.8);

        computer.computeForActor(actorId, List.of(entry),
                Map.of(entry.id, List.of(att)), now);

        final Optional<ActorTrustScore> dimScore = trustRepo.findDimensionScore(actorId, "review-thoroughness");
        assertThat(dimScore).isPresent();
        assertThat(dimScore.get().scoreType).isEqualTo(ActorTrustScore.ScoreType.DIMENSION);
        assertThat(dimScore.get().dimensionKey).isEqualTo("review-thoroughness");
        assertThat(dimScore.get().trustScore).isCloseTo(0.8, within(0.05));
    }

    // ── Test 5: Capability+dimension attestation → CAPABILITY_DIMENSION score ─

    @Test
    void capabilityDimensionAttestation_producesCapabilityDimensionScore() {
        final String actorId = "agent-cap-dim";
        final LedgerEntry entry = makeEntry(actorId, now.minus(1, ChronoUnit.DAYS));
        final LedgerAttestation att = makeAttestation(entry, AttestationVerdict.SOUND,
                "code-review", "review-thoroughness", 0.9);

        computer.computeForActor(actorId, List.of(entry),
                Map.of(entry.id, List.of(att)), now);

        final Optional<ActorTrustScore> cdScore =
                trustRepo.findCapabilityDimension(actorId, "code-review", "review-thoroughness");
        assertThat(cdScore).isPresent();
        assertThat(cdScore.get().scoreType).isEqualTo(ActorTrustScore.ScoreType.CAPABILITY_DIMENSION);
        assertThat(cdScore.get().capabilityKey).isEqualTo("code-review");
        assertThat(cdScore.get().dimensionKey).isEqualTo("review-thoroughness");
        assertThat(cdScore.get().trustScore).isCloseTo(0.9, within(0.05));

        // Should also produce CAPABILITY, DIMENSION, and GLOBAL rows
        assertThat(trustRepo.findCapabilityScore(actorId, "code-review")).isPresent();
        assertThat(trustRepo.findDimensionScore(actorId, "review-thoroughness")).isPresent();
        assertThat(trustRepo.findByActorId(actorId)).isPresent();
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private LedgerEntry makeEntry(final String actorId, final Instant occurredAt) {
        final LedgerEntry entry = new LedgerEntry() {};
        entry.id = UUID.randomUUID();
        entry.subjectId = UUID.randomUUID();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = actorId;
        entry.actorType = ActorType.AGENT;
        entry.actorRole = "Classifier";
        entry.occurredAt = occurredAt;
        entry.sequenceNumber = 1;
        return entry;
    }

    private LedgerAttestation makeAttestation(final LedgerEntry entry,
                                              final AttestationVerdict verdict,
                                              final String capabilityTag,
                                              final String trustDimension,
                                              final Double dimensionScore) {
        final LedgerAttestation att = new LedgerAttestation();
        att.id = UUID.randomUUID();
        att.ledgerEntryId = entry.id;
        att.subjectId = entry.subjectId;
        att.attestorId = "compliance-bot";
        att.attestorType = ActorType.AGENT;
        att.verdict = verdict;
        att.confidence = 1.0;
        att.capabilityTag = capabilityTag;
        att.trustDimension = trustDimension;
        att.dimensionScore = dimensionScore;
        att.occurredAt = entry.occurredAt.plusSeconds(60);
        return att;
    }

    // ── In-memory repository capturing upserted scores ───────────────────────

    /**
     * Minimal {@link ActorTrustScoreRepository} implementation for unit testing.
     * Stores upserted scores in memory keyed by {@code actorId|scoreType|capabilityKey|dimensionKey}.
     */
    static class CapturingTrustScoreRepo implements ActorTrustScoreRepository {

        private final java.util.concurrent.ConcurrentHashMap<String, ActorTrustScore> store =
                new java.util.concurrent.ConcurrentHashMap<>();

        private static String key(String actorId, ActorTrustScore.ScoreType type, String cap, String dim) {
            return actorId + "|" + type + "|" + nvl(cap) + "|" + nvl(dim);
        }

        private static String nvl(String s) {
            return s != null ? s : "";
        }

        List<ActorTrustScore> allScores() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Optional<ActorTrustScore> findByActorId(String actorId) {
            return Optional.ofNullable(store.get(key(actorId, ActorTrustScore.ScoreType.GLOBAL, null, null)));
        }

        @Override
        public Optional<ActorTrustScore> findCapabilityScore(String actorId, String capabilityTag) {
            return Optional.ofNullable(store.get(key(actorId, ActorTrustScore.ScoreType.CAPABILITY, capabilityTag, null)));
        }

        @Override
        public Optional<ActorTrustScore> findDimensionScore(String actorId, String dimension) {
            return Optional.ofNullable(store.get(key(actorId, ActorTrustScore.ScoreType.DIMENSION, null, dimension)));
        }

        @Override
        public Optional<ActorTrustScore> findCapabilityDimension(String actorId, String capabilityTag, String dimension) {
            return Optional.ofNullable(store.get(key(actorId, ActorTrustScore.ScoreType.CAPABILITY_DIMENSION, capabilityTag, dimension)));
        }

        @Override
        public List<ActorTrustScore> findCapabilityDimensions(String actorId, String capabilityTag) {
            return store.values().stream()
                    .filter(s -> actorId.equals(s.actorId))
                    .filter(s -> ActorTrustScore.ScoreType.CAPABILITY_DIMENSION.equals(s.scoreType))
                    .filter(s -> capabilityTag.equals(s.capabilityKey))
                    .toList();
        }

        @Override
        public List<ActorTrustScore> findByActorIdAndScoreType(String actorId, ActorTrustScore.ScoreType scoreType) {
            return store.values().stream()
                    .filter(s -> actorId.equals(s.actorId))
                    .filter(s -> scoreType.equals(s.scoreType))
                    .toList();
        }

        @Override
        public void upsert(String actorId, ActorTrustScore.ScoreType scoreType,
                           String capabilityKey, String dimensionKey,
                           ActorType actorType, double trustScore,
                           int decisionCount, int overturnedCount,
                           double alpha, double beta,
                           int attestationPositive, int attestationNegative,
                           Instant lastComputedAt) {
            final String k = key(actorId, scoreType, capabilityKey, dimensionKey);
            store.compute(k, (key, existing) -> {
                final ActorTrustScore score = existing != null ? existing : new ActorTrustScore();
                if (existing == null) {
                    score.id = UUID.randomUUID();
                    score.actorId = actorId;
                    score.scoreType = scoreType;
                    score.capabilityKey = capabilityKey;
                    score.dimensionKey = dimensionKey;
                }
                score.actorType = actorType;
                score.trustScore = trustScore;
                score.alpha = alpha;
                score.beta = beta;
                score.decisionCount = decisionCount;
                score.overturnedCount = overturnedCount;
                score.attestationPositive = attestationPositive;
                score.attestationNegative = attestationNegative;
                score.lastComputedAt = lastComputedAt;
                return score;
            });
        }

        @Override
        public void updateGlobalTrustScore(String actorId, double globalTrustScore) {
            store.computeIfPresent(key(actorId, ActorTrustScore.ScoreType.GLOBAL, null, null),
                    (k, score) -> { score.globalTrustScore = globalTrustScore; return score; });
        }

        @Override
        public List<ActorTrustScore> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<ActorTrustScore> findAllByLastComputedAtAfter(Instant since) {
            return store.values().stream()
                    .filter(s -> s.lastComputedAt != null && s.lastComputedAt.isAfter(since))
                    .toList();
        }
    }
}
