package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CredibilityFlag;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;
import io.casehub.ledger.api.model.LedgerAttestation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Computes Bayesian Beta trust scores from ledger attestation history.
 *
 * <p>
 * Pure Java — no CDI, no database. Suitable for unit tests without a Quarkus runtime.
 *
 * <p>
 * Algorithm: start with prior Beta(1, 1). For each attestation across all of an actor's
 * decisions, compute a decay weight via the injected {@link DecayFunction} using the
 * attestation's own {@code occurredAt}. SOUND/ENDORSED increments α by
 * {@code decayWeight × confidence}; FLAGGED/CHALLENGED increments β by
 * {@code decayWeight × confidence}. Score = α/(α+β), clamped to [0.0, 1.0].
 *
 * <p>
 * The default {@link ExponentialDecayFunction} applies exponential decay with an
 * asymmetric valence multiplier — FLAGGED attestations decay slower, persisting longer
 * as negative evidence.
 *
 * <p>
 * Properties: no history → 0.5 (maximum uncertainty). Unattested decisions contribute
 * nothing — they do not inflate the score.
 */
public final class TrustScoreComputer {

    private final DecayFunction decayFunction;

    /**
     * CDI/production constructor — delegates decay to the supplied {@link DecayFunction}.
     *
     * @param decayFunction the decay strategy to apply
     */
    public TrustScoreComputer(final DecayFunction decayFunction) {
        this.decayFunction = decayFunction;
    }

    /**
     * Convenience constructor for unit tests — uses simple exponential decay
     * ({@code 2^(-ageInDays / halfLifeDays)}) with no valence asymmetry.
     *
     * @param halfLifeDays recency decay half-life in days; values ≤ 0 default to 90
     */
    public TrustScoreComputer(final int halfLifeDays) {
        final int effective = halfLifeDays > 0 ? halfLifeDays : 90;
        this.decayFunction = (ageInDays, verdict) -> Math.pow(2.0, -(double) ageInDays / effective);
    }

    /**
     * The computed score and metrics for one actor.
     *
     * @param trustScore computed trust score in [0.0, 1.0]
     * @param alpha final α value (prior 1.0 + positive recency-weighted contributions)
     * @param beta final β value (prior 1.0 + negative recency-weighted contributions)
     * @param decisionCount number of EVENT entries evaluated
     * @param overturnedCount number of decisions with at least one positive-weight negative attestation
     * @param attestationPositive total positive attestation count (raw, regardless of weight)
     * @param attestationNegative total negative attestation count (raw, regardless of weight)
     */
    public record ActorScore(
            double trustScore,
            double alpha,
            double beta,
            int decisionCount,
            int overturnedCount,
            int attestationPositive,
            int attestationNegative,
            double credibilityRetention) {
    }

    /**
     * Compute a Bayesian Beta trust score for one actor.
     *
     * @param decisions EVENT ledger entries where this actor was the decision-maker
     * @param attestationsByEntryId map from entry id to its attestations
     * @param now reference timestamp for age calculation
     * @return the computed score and metrics
     */
    public ActorScore compute(
            final List<LedgerEntry> decisions,
            final Map<UUID, List<LedgerAttestation>> attestationsByEntryId,
            final Instant now) {
        return compute(decisions, attestationsByEntryId, now, Map.of());
    }

    public ActorScore compute(
            final List<LedgerEntry> decisions,
            final Map<UUID, List<LedgerAttestation>> attestationsByEntryId,
            final Instant now,
            final Map<String, AttestorCredibilityPolicy.CredibilityAssessment> credibilityByAttestorId) {

        if (decisions.isEmpty()) {
            return new ActorScore(0.5, 1.0, 1.0, 0, 0, 0, 0, 1.0);
        }

        double alpha                    = 1.0;
        double beta                     = 1.0;
        int    overturnedCount          = 0;
        int    totalPositive            = 0;
        int    totalNegative            = 0;
        double totalRawWeight           = 0.0;
        double totalEffectiveWeight     = 0.0;
        int    assessedAttestationCount = 0;

        for (final LedgerEntry entry : decisions) {
            final List<LedgerAttestation> attestations = attestationsByEntryId.getOrDefault(entry.id, List.of());
            boolean                       hasNegative  = false;

            for (final LedgerAttestation attestation : attestations) {
                final Instant attestationTime = attestation.occurredAt != null ? attestation.occurredAt : now;
                final long    ageInDays       = Math.max(0, java.time.Duration.between(attestationTime, now).toDays());
                final double  decayWeight     = decayFunction.weight(ageInDays, attestation.verdict);
                final double  rawWeight       = decayWeight * Math.max(0.0, Math.min(1.0, attestation.confidence));

                final AttestorCredibilityPolicy.CredibilityAssessment assessment =
                        credibilityByAttestorId.getOrDefault(attestation.attestorId,
                                                             AttestorCredibilityPolicy.CredibilityAssessment.NEUTRAL);
                final double credibilityWeight = Math.max(0.0, Math.min(1.0, assessment.weight()));
                final double effectiveWeight   = rawWeight * credibilityWeight;

                final boolean hasInsufficientData = assessment.flags() != null
                                                    && assessment.flags().contains(CredibilityFlag.INSUFFICIENT_DATA);
                if (!hasInsufficientData) {
                    totalRawWeight += rawWeight;
                    totalEffectiveWeight += effectiveWeight;
                    assessedAttestationCount++;
                }

                if (attestation.verdict == AttestationVerdict.SOUND
                    || attestation.verdict == AttestationVerdict.ENDORSED) {
                    alpha += effectiveWeight;
                    totalPositive++;
                } else if (attestation.verdict == AttestationVerdict.FLAGGED
                           || attestation.verdict == AttestationVerdict.CHALLENGED) {
                    beta += effectiveWeight;
                    totalNegative++;
                    if (effectiveWeight > 0.0) {
                        hasNegative = true;
                    }
                }
            }
            if (hasNegative) {
                overturnedCount++;
            }
        }

        final double trustScore = Math.max(0.0, Math.min(1.0, alpha / (alpha + beta)));
        final double retention = assessedAttestationCount == 0
                                 ? Double.NaN
                                 : (totalRawWeight > 0.0 ? totalEffectiveWeight / totalRawWeight : 1.0);

        return new ActorScore(
                trustScore, alpha, beta,
                decisions.size(), overturnedCount,
                totalPositive, totalNegative, retention);
    }


    /**
     * Computes a decay-weighted average of continuous quality dimension scores.
     *
     * <p>
     * Unlike the Bayesian Beta model used by {@link #compute}, dimension scores are continuous
     * in [0.0, 1.0]. This method computes {@code Σ(weight_i × dimensionScore_i) / Σ(weight_i)}
     * where weight decays purely with age (using {@link AttestationVerdict#SOUND} to suppress
     * the FLAGGED/CHALLENGED valence asymmetry — continuous scores have no verdict polarity).
     * Each attestation's contribution is also weighted by its {@code confidence} in [0.0, 1.0],
     * consistent with {@link #compute}.
     *
     * <p>
     * Attestations with {@code null} {@code dimensionScore} are excluded.
     *
     * @param dimensionAttestations attestations for one (actor, trustDimension) pair
     * @param now reference timestamp for age calculation
     * @return weighted average in [0.0, 1.0], or empty if no valid attestations exist
     */
    public OptionalDouble computeDimensionScore(
            final List<LedgerAttestation> dimensionAttestations,
            final Instant now) {
        return computeDimensionScore(dimensionAttestations, now, Map.of());
    }

    public OptionalDouble computeDimensionScore(
            final List<LedgerAttestation> dimensionAttestations,
            final Instant now,
            final Map<String, AttestorCredibilityPolicy.CredibilityAssessment> credibilityByAttestorId) {
        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (final LedgerAttestation a : dimensionAttestations) {
            if (a.dimensionScore == null) {
                continue;
            }
            final Instant attestedAt = a.occurredAt != null ? a.occurredAt : now;
            final long    ageInDays  = Math.max(0, java.time.Duration.between(attestedAt, now).toDays());
            final double rawWeight = decayFunction.weight(ageInDays, AttestationVerdict.SOUND)
                                     * Math.max(0.0, Math.min(1.0, a.confidence));

            final AttestorCredibilityPolicy.CredibilityAssessment assessment =
                    credibilityByAttestorId.getOrDefault(a.attestorId,
                                                         AttestorCredibilityPolicy.CredibilityAssessment.NEUTRAL);
            final double credibilityWeight = Math.max(0.0, Math.min(1.0, assessment.weight()));
            final double weight            = rawWeight * credibilityWeight;

            weightedSum += weight * a.dimensionScore;
            totalWeight += weight;
        }

        if (totalWeight == 0.0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.max(0.0, Math.min(1.0, weightedSum / totalWeight)));
    }


}
