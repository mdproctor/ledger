package io.casehub.ledger.runtime.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;

/**
 * Computes all trust score types (CAPABILITY, DIMENSION, CAPABILITY_DIMENSION, GLOBAL)
 * for a single actor from their decisions and associated attestations.
 *
 * <p>
 * Extracted from {@link TrustScoreJob} to enable both batch (nightly job) and incremental
 * (per-attestation observer) recomputation via the same logic. The four-pass algorithm is
 * identical to the original {@code TrustScoreJob} loop body:
 * <ol>
 *   <li><b>Capability pass</b> — group aggregated attestations by capabilityTag, Bayesian Beta
 *       via {@link TrustScoreComputer#compute}, upsert CAPABILITY rows</li>
 *   <li><b>Dimension pass</b> — group raw attestations by trustDimension, decay-weighted average
 *       via {@link TrustScoreComputer#computeDimensionScore}, upsert DIMENSION rows</li>
 *   <li><b>Capability&times;Dimension pass</b> — group by (capabilityTag, trustDimension),
 *       upsert CAPABILITY_DIMENSION rows</li>
 *   <li><b>Global pass</b> — {@link GlobalScoreStrategy#selectAttestations} +
 *       {@link GlobalScoreStrategy#derive}, upsert GLOBAL row</li>
 * </ol>
 *
 * <p>
 * This bean is package-private — only {@code TrustScoreJob} and the incremental observer
 * in this package need access.
 */
@ApplicationScoped
class PerActorTrustComputer {

    private final DecayFunction decayFunction;
    private final ActorTrustScoreRepository trustRepo;
    private final GlobalScoreStrategy globalScoreStrategy;
    private final AttestationAggregator attestationAggregator;
    private final AttestationAggregator.Strategy aggregationStrategy;

    /**
     * CDI constructor — reads the aggregation strategy from config.
     */
    @Inject
    PerActorTrustComputer(final DecayFunction decayFunction,
                          final ActorTrustScoreRepository trustRepo,
                          final GlobalScoreStrategy globalScoreStrategy,
                          final AttestationAggregator attestationAggregator,
                          final LedgerConfig config) {
        this.decayFunction = decayFunction;
        this.trustRepo = trustRepo;
        this.globalScoreStrategy = globalScoreStrategy;
        this.attestationAggregator = attestationAggregator;
        this.aggregationStrategy = config.trustScore().aggregationStrategy();
    }

    /**
     * Test constructor — no CDI, no config. Strategy is passed directly.
     */
    PerActorTrustComputer(final DecayFunction decayFunction,
                          final ActorTrustScoreRepository trustRepo,
                          final GlobalScoreStrategy globalScoreStrategy,
                          final AttestationAggregator attestationAggregator,
                          final AttestationAggregator.Strategy aggregationStrategy) {
        this.decayFunction = decayFunction;
        this.trustRepo = trustRepo;
        this.globalScoreStrategy = globalScoreStrategy;
        this.attestationAggregator = attestationAggregator;
        this.aggregationStrategy = aggregationStrategy;
    }

    /**
     * Compute and upsert all trust score types for a single actor.
     *
     * @param actorId              the actor whose scores are being computed
     * @param decisions            EVENT ledger entries where this actor was the decision-maker
     * @param attestationsByEntry  map from entry ID to its attestations (only entries for this actor)
     * @param now                  reference timestamp for decay weighting
     * @return the computed scores as detached value objects (useful for routing events)
     */
    List<ActorTrustScore> computeForActor(final String actorId,
                                          final List<LedgerEntry> decisions,
                                          final Map<UUID, List<LedgerAttestation>> attestationsByEntry,
                                          final Instant now) {

        final List<ActorTrustScore> results = new ArrayList<>();

        final ActorType actorType = decisions.stream()
                .map(e -> e.actorType)
                .filter(t -> t != null)
                .findFirst()
                .orElse(ActorType.HUMAN);

        final TrustScoreComputer computer = new TrustScoreComputer(decayFunction);

        // Collect all attestations for this actor's decisions (used by the dimension pass and derive())
        final List<LedgerAttestation> actorAttestations = new ArrayList<>();
        for (final LedgerEntry decision : decisions) {
            actorAttestations.addAll(attestationsByEntry.getOrDefault(decision.id, List.of()));
        }

        // Build aggregated view for capability and global passes.
        // Dimension pass uses the original actorAttestations (dimensionScore is continuous, not verdict-based).
        final List<LedgerAttestation> effectiveAttestations =
                buildEffectiveAttestations(decisions, attestationsByEntry);

        // ── Capability pass ────────────────────────────────────────────────────────
        // Group aggregated attestations by (capabilityTag → entryId) in one pass
        final Map<String, Map<UUID, List<LedgerAttestation>>> byCapabilityAndEntry = effectiveAttestations.stream()
                .filter(a -> !CapabilityTag.GLOBAL.equals(a.capabilityTag))
                .collect(Collectors.groupingBy(
                        a -> a.capabilityTag,
                        Collectors.groupingBy(a -> a.ledgerEntryId)));

        final Map<String, TrustScoreComputer.ActorScore> capabilityScores = new LinkedHashMap<>();

        for (final Map.Entry<String, Map<UUID, List<LedgerAttestation>>> capEntry : byCapabilityAndEntry.entrySet()) {
            final String capabilityTag = capEntry.getKey();
            final Map<UUID, List<LedgerAttestation>> capByEntry = capEntry.getValue();

            final TrustScoreComputer.ActorScore capScore = computer.compute(decisions, capByEntry, now);
            trustRepo.upsert(actorId, ActorTrustScore.ScoreType.CAPABILITY, capabilityTag, null,
                    actorType, capScore.trustScore(),
                    capScore.decisionCount(), capScore.overturnedCount(),
                    capScore.alpha(), capScore.beta(),
                    capScore.attestationPositive(), capScore.attestationNegative(), now);
            capabilityScores.put(capabilityTag, capScore);
            results.add(buildScore(actorId, ActorTrustScore.ScoreType.CAPABILITY,
                    capabilityTag, null, actorType, capScore, now));
        }

        // ── Dimension pass ─────────────────────────────────────────────────────────
        // Group actor's dimension-tagged attestations by dimension in one pass.
        // Excludes attestations with null dimensionScore — they carry no quality signal.
        final Map<String, List<LedgerAttestation>> byDimension = actorAttestations.stream()
                .filter(a -> a.trustDimension != null && a.dimensionScore != null)
                .collect(Collectors.groupingBy(a -> a.trustDimension));

        for (final Map.Entry<String, List<LedgerAttestation>> dimEntry : byDimension.entrySet()) {
            final String dimension = dimEntry.getKey();
            final List<LedgerAttestation> dimAttestations = dimEntry.getValue();

            computer.computeDimensionScore(dimAttestations, now).ifPresent(dimScore -> {
                // scores >= 0.5 count as positive; < 0.5 count as negative; 0.5 (neutral) maps to positive
                final int dimPositive = (int) dimAttestations.stream()
                        .filter(a -> a.dimensionScore >= 0.5).count();
                final int dimNegative = (int) dimAttestations.stream()
                        .filter(a -> a.dimensionScore < 0.5).count();
                // distinct entries where this actor was decision-maker, assessed on this dimension
                final int dimDecisionCount = (int) dimAttestations.stream()
                        .map(a -> a.ledgerEntryId).distinct().count();

                trustRepo.upsert(actorId, ActorTrustScore.ScoreType.DIMENSION, null, dimension,
                        actorType, dimScore,
                        dimDecisionCount, 0,
                        0.0, 0.0,
                        dimPositive, dimNegative, now);
                results.add(buildDimensionScore(actorId, null, dimension, actorType,
                        dimScore, dimDecisionCount, dimPositive, dimNegative, now));
            });
        }

        // ── CAPABILITY_DIMENSION pass ─────────────────────────────────────────────
        // Attestations tagged with both a non-GLOBAL capabilityTag and a trustDimension.
        // Uses raw actorAttestations (not aggregated synthetics) — same as dimension pass.
        final Map<String, Map<String, List<LedgerAttestation>>> byCapabilityAndDimension =
                actorAttestations.stream()
                        .filter(a -> a.trustDimension != null
                                && a.dimensionScore != null
                                && a.capabilityTag != null
                                && !CapabilityTag.GLOBAL.equals(a.capabilityTag))
                        .collect(Collectors.groupingBy(
                                a -> a.capabilityTag,
                                Collectors.groupingBy(a -> a.trustDimension)));

        for (final Map.Entry<String, Map<String, List<LedgerAttestation>>> capEntry :
                byCapabilityAndDimension.entrySet()) {
            final String capabilityTag = capEntry.getKey();
            for (final Map.Entry<String, List<LedgerAttestation>> dimEntry :
                    capEntry.getValue().entrySet()) {
                final String dimension = dimEntry.getKey();
                final List<LedgerAttestation> compositeAttestations = dimEntry.getValue();

                computer.computeDimensionScore(compositeAttestations, now).ifPresent(score -> {
                    final int cdPositive = (int) compositeAttestations.stream()
                            .filter(a -> a.dimensionScore >= 0.5).count();
                    final int cdNegative = (int) compositeAttestations.stream()
                            .filter(a -> a.dimensionScore < 0.5).count();
                    final int cdDecisionCount = (int) compositeAttestations.stream()
                            .map(a -> a.ledgerEntryId).distinct().count();

                    trustRepo.upsert(actorId, ActorTrustScore.ScoreType.CAPABILITY_DIMENSION,
                            capabilityTag, dimension, actorType, score,
                            cdDecisionCount, 0, 0.0, 0.0, cdPositive, cdNegative, now);
                    results.add(buildDimensionScore(actorId, capabilityTag, dimension, actorType,
                            score, cdDecisionCount, cdPositive, cdNegative, now));
                });
            }
        }

        // ── Global pass ────────────────────────────────────────────────────────────
        // selectAttestations filters by capabilityTag/etc. — synthetics preserve all fields.
        // Group directly by ledgerEntryId rather than using reference-equality set,
        // since effectiveAttestations are synthetic instances not in attestationsByEntry.
        final List<LedgerAttestation> selectedEffective =
                globalScoreStrategy.selectAttestations(effectiveAttestations);
        final Map<UUID, List<LedgerAttestation>> selectedByEntry = selectedEffective.stream()
                .collect(Collectors.groupingBy(a -> a.ledgerEntryId));

        final TrustScoreComputer.ActorScore globalScore = computer.compute(decisions, selectedByEntry, now);
        // derive() receives original actorAttestations — capability frequency counts stay accurate
        final TrustScoreComputer.ActorScore finalScore =
                globalScoreStrategy.derive(capabilityScores, actorAttestations)
                        .orElse(globalScore);

        trustRepo.upsert(actorId, ActorTrustScore.ScoreType.GLOBAL, null, null,
                actorType, finalScore.trustScore(),
                finalScore.decisionCount(), finalScore.overturnedCount(),
                finalScore.alpha(), finalScore.beta(),
                finalScore.attestationPositive(), finalScore.attestationNegative(), now);
        results.add(buildScore(actorId, ActorTrustScore.ScoreType.GLOBAL, null, null,
                actorType, finalScore, now));

        return results;
    }

    private static ActorTrustScore buildScore(final String actorId,
            final ActorTrustScore.ScoreType scoreType,
            final String capabilityKey, final String dimensionKey,
            final ActorType actorType,
            final TrustScoreComputer.ActorScore score, final Instant now) {
        final ActorTrustScore s = new ActorTrustScore();
        s.actorId = actorId;
        s.scoreType = scoreType;
        s.capabilityKey = capabilityKey;
        s.dimensionKey = dimensionKey;
        s.actorType = actorType;
        s.trustScore = score.trustScore();
        s.decisionCount = score.decisionCount();
        s.overturnedCount = score.overturnedCount();
        s.alpha = score.alpha();
        s.beta = score.beta();
        s.attestationPositive = score.attestationPositive();
        s.attestationNegative = score.attestationNegative();
        s.lastComputedAt = now;
        return s;
    }

    private static ActorTrustScore buildDimensionScore(final String actorId,
            final String capabilityKey, final String dimensionKey,
            final ActorType actorType,
            final double score, final int decisionCount,
            final int positive, final int negative, final Instant now) {
        final ActorTrustScore s = new ActorTrustScore();
        s.actorId = actorId;
        s.scoreType = capabilityKey != null
                ? ActorTrustScore.ScoreType.CAPABILITY_DIMENSION
                : ActorTrustScore.ScoreType.DIMENSION;
        s.capabilityKey = capabilityKey;
        s.dimensionKey = dimensionKey;
        s.actorType = actorType;
        s.trustScore = score;
        s.decisionCount = decisionCount;
        s.overturnedCount = 0;
        s.alpha = 0.0;
        s.beta = 0.0;
        s.attestationPositive = positive;
        s.attestationNegative = negative;
        s.lastComputedAt = now;
        return s;
    }

    /**
     * Aggregates attestations per (entryId, capabilityTag) group and returns the flattened result.
     * Each group is reduced to a single synthetic {@link LedgerAttestation} carrying the consensus
     * verdict and aggregated confidence. The dimension pass is excluded — it uses raw attestations.
     */
    private List<LedgerAttestation> buildEffectiveAttestations(
            final List<LedgerEntry> decisions,
            final Map<UUID, List<LedgerAttestation>> attestationsByEntry) {
        final List<LedgerAttestation> result = new ArrayList<>();
        for (final LedgerEntry decision : decisions) {
            final List<LedgerAttestation> entryAttestations = attestationsByEntry.getOrDefault(decision.id, List.of());
            if (entryAttestations.isEmpty()) {
                continue;
            }
            // Aggregate per capabilityTag — different capability scopes are independent signals
            final Map<String, List<LedgerAttestation>> byCapTag = entryAttestations.stream()
                    .collect(Collectors.groupingBy(a -> a.capabilityTag != null ? a.capabilityTag : CapabilityTag.GLOBAL));
            for (final List<LedgerAttestation> group : byCapTag.values()) {
                attestationAggregator.aggregate(group, aggregationStrategy)
                        .map(agg -> toSynthetic(agg, group.get(0)))
                        .ifPresent(result::add);
            }
        }
        return result;
    }

    /**
     * Builds a non-persisted synthetic {@link LedgerAttestation} from an aggregated result.
     * {@code id}, {@code attestorId}, and {@code attestorType} are intentionally left null —
     * the synthetic is never written to the database and is not attributed to a single attestor.
     * {@code trustDimension} and {@code dimensionScore} are copied for structural completeness
     * but are never read from synthetics; the dimension pass always uses raw attestations.
     */
    private static LedgerAttestation toSynthetic(
            final AttestationAggregator.AggregatedAttestation agg,
            final LedgerAttestation template) {
        final LedgerAttestation synthetic = new LedgerAttestation();
        synthetic.ledgerEntryId = template.ledgerEntryId;
        synthetic.subjectId = template.subjectId;
        synthetic.capabilityTag = template.capabilityTag;
        synthetic.trustDimension = template.trustDimension;
        synthetic.dimensionScore = template.dimensionScore;
        synthetic.verdict = agg.consensusVerdict();
        synthetic.confidence = agg.aggregatedConfidence();
        synthetic.occurredAt = template.occurredAt;
        synthetic.attestorRole = template.attestorRole;
        return synthetic;
    }
}
