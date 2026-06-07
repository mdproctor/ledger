package io.casehub.ledger.runtime.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * Pure trust score computation — no persistence, no CDI events.
 *
 * <p>Runs the four-pass algorithm (capability → dimension → capability×dimension → global)
 * and returns the results as a {@link ComputedScores} record. Both {@code PerActorTrustComputer}
 * (batch/incremental write path) and {@code ComputedTrustScoreSource} (on-read path) delegate
 * here for the actual computation.
 */
@ApplicationScoped
public class TrustScoreCalculator {

    private final DecayFunction decayFunction;
    private final AttestationAggregator attestationAggregator;
    private final GlobalScoreStrategy globalScoreStrategy;
    private final AttestationAggregator.Strategy aggregationStrategy;

    @Inject
    public TrustScoreCalculator(final DecayFunction decayFunction,
                                final AttestationAggregator attestationAggregator,
                                final GlobalScoreStrategy globalScoreStrategy,
                                final LedgerConfig config) {
        this(decayFunction, attestationAggregator, globalScoreStrategy,
                config.trustScore().aggregationStrategy());
    }

    public TrustScoreCalculator(final DecayFunction decayFunction,
                                final AttestationAggregator attestationAggregator,
                                final GlobalScoreStrategy globalScoreStrategy,
                                final AttestationAggregator.Strategy aggregationStrategy) {
        this.decayFunction = decayFunction;
        this.attestationAggregator = attestationAggregator;
        this.globalScoreStrategy = globalScoreStrategy;
        this.aggregationStrategy = aggregationStrategy;
    }

    public record ComputedScores(
            Map<String, TrustScoreComputer.ActorScore> capabilityScores,
            Map<String, Double> dimensionScores,
            Map<String, Map<String, Double>> capabilityDimensionScores,
            TrustScoreComputer.ActorScore globalScore) {
    }

    public ComputedScores computeAll(final List<LedgerEntry> decisions,
                                     final Map<UUID, List<LedgerAttestation>> attestationsByEntry,
                                     final Instant now) {

        final TrustScoreComputer computer = new TrustScoreComputer(decayFunction);

        final List<LedgerAttestation> rawAttestations = new ArrayList<>();
        for (final LedgerEntry decision : decisions) {
            rawAttestations.addAll(attestationsByEntry.getOrDefault(decision.id, List.of()));
        }

        final List<LedgerAttestation> effectiveAttestations =
                buildEffectiveAttestations(decisions, attestationsByEntry);

        // ── Capability pass ──────────────────────────────────────────────────
        final Map<String, Map<UUID, List<LedgerAttestation>>> byCapabilityAndEntry =
                effectiveAttestations.stream()
                        .filter(a -> !CapabilityTag.GLOBAL.equals(a.capabilityTag))
                        .collect(Collectors.groupingBy(
                                a -> a.capabilityTag,
                                Collectors.groupingBy(a -> a.ledgerEntryId)));

        final Map<String, TrustScoreComputer.ActorScore> capabilityScores = new LinkedHashMap<>();
        for (final Map.Entry<String, Map<UUID, List<LedgerAttestation>>> capEntry :
                byCapabilityAndEntry.entrySet()) {
            capabilityScores.put(capEntry.getKey(),
                    computer.compute(decisions, capEntry.getValue(), now));
        }

        // ── Dimension pass ───────────────────────────────────────────────────
        final Map<String, List<LedgerAttestation>> byDimension = rawAttestations.stream()
                .filter(a -> a.trustDimension != null && a.dimensionScore != null)
                .collect(Collectors.groupingBy(a -> a.trustDimension));

        final Map<String, Double> dimensionScores = new LinkedHashMap<>();
        for (final Map.Entry<String, List<LedgerAttestation>> dimEntry : byDimension.entrySet()) {
            computer.computeDimensionScore(dimEntry.getValue(), now)
                    .ifPresent(score -> dimensionScores.put(dimEntry.getKey(), score));
        }

        // ── Capability×Dimension pass ────────────────────────────────────────
        final Map<String, Map<String, List<LedgerAttestation>>> byCapAndDim =
                rawAttestations.stream()
                        .filter(a -> a.trustDimension != null
                                && a.dimensionScore != null
                                && a.capabilityTag != null
                                && !CapabilityTag.GLOBAL.equals(a.capabilityTag))
                        .collect(Collectors.groupingBy(
                                a -> a.capabilityTag,
                                Collectors.groupingBy(a -> a.trustDimension)));

        final Map<String, Map<String, Double>> capDimScores = new LinkedHashMap<>();
        for (final Map.Entry<String, Map<String, List<LedgerAttestation>>> capEntry :
                byCapAndDim.entrySet()) {
            final Map<String, Double> dims = new LinkedHashMap<>();
            for (final Map.Entry<String, List<LedgerAttestation>> dimEntry :
                    capEntry.getValue().entrySet()) {
                computer.computeDimensionScore(dimEntry.getValue(), now)
                        .ifPresent(score -> dims.put(dimEntry.getKey(), score));
            }
            if (!dims.isEmpty()) {
                capDimScores.put(capEntry.getKey(), dims);
            }
        }

        // ── Global pass ──────────────────────────────────────────────────────
        // selectAttestations receives EFFECTIVE (aggregated) attestations
        final List<LedgerAttestation> selectedEffective =
                globalScoreStrategy.selectAttestations(effectiveAttestations);
        final Map<UUID, List<LedgerAttestation>> selectedByEntry = selectedEffective.stream()
                .collect(Collectors.groupingBy(a -> a.ledgerEntryId));

        final TrustScoreComputer.ActorScore betaGlobal = computer.compute(decisions, selectedByEntry, now);
        // derive() receives RAW attestations — frequency counts stay accurate
        final TrustScoreComputer.ActorScore globalScore =
                globalScoreStrategy.derive(capabilityScores, rawAttestations)
                        .orElse(betaGlobal);

        return new ComputedScores(capabilityScores, dimensionScores, capDimScores, globalScore);
    }

    private List<LedgerAttestation> buildEffectiveAttestations(
            final List<LedgerEntry> decisions,
            final Map<UUID, List<LedgerAttestation>> attestationsByEntry) {
        final List<LedgerAttestation> result = new ArrayList<>();
        for (final LedgerEntry decision : decisions) {
            final List<LedgerAttestation> entryAttestations =
                    attestationsByEntry.getOrDefault(decision.id, List.of());
            if (entryAttestations.isEmpty()) {
                continue;
            }
            final Map<String, List<LedgerAttestation>> byCapTag = entryAttestations.stream()
                    .collect(Collectors.groupingBy(
                            a -> a.capabilityTag != null ? a.capabilityTag : CapabilityTag.GLOBAL));
            for (final List<LedgerAttestation> group : byCapTag.values()) {
                attestationAggregator.aggregate(group, aggregationStrategy)
                        .map(agg -> toSynthetic(agg, group.get(0)))
                        .ifPresent(result::add);
            }
        }
        return result;
    }

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
