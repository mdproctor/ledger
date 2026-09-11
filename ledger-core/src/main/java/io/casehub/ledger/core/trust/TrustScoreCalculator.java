package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;
import io.casehub.ledger.api.model.LedgerAttestation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure trust score computation — no persistence, no CDI events.
 *
 * <p>Runs the four-pass algorithm (capability → dimension → capability×dimension → global)
 * and returns the results as a {@link ComputedScores} record. Both {@code PerActorTrustComputer}
 * (batch/incremental write path) and {@code ComputedTrustScoreSource} (on-read path) delegate
 * here for the actual computation.
 */
public class TrustScoreCalculator {

    private final DecayFunction             decayFunction;
    private final GlobalScoreStrategy       globalScoreStrategy;
    private final AttestorCredibilityPolicy credibilityPolicy;

    public TrustScoreCalculator(final DecayFunction decayFunction,
                                final GlobalScoreStrategy globalScoreStrategy,
                                final AttestorCredibilityPolicy credibilityPolicy) {
        this.decayFunction = decayFunction;
        this.globalScoreStrategy = globalScoreStrategy;
        this.credibilityPolicy = credibilityPolicy;
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

        // ── Resolve credibility for all attestors (single batch call) ────────
        final java.util.Set<String> attestorIds = rawAttestations.stream()
                                                                 .map(a -> a.attestorId)
                                                                 .filter(java.util.Objects::nonNull)
                                                                 .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        final Map<String, AttestorCredibilityPolicy.CredibilityAssessment> credibilityMap =
                credibilityPolicy.assessBatch(attestorIds);

        // ── Capability pass ──────────────────────────────────────────────────
        final Map<String, Map<UUID, List<LedgerAttestation>>> byCapabilityAndEntry =
                rawAttestations.stream()
                               .filter(a -> a.capabilityTag != null
                                            && !CapabilityTag.GLOBAL.equals(a.capabilityTag))
                               .collect(Collectors.groupingBy(
                                       a -> a.capabilityTag,
                                       Collectors.groupingBy(a -> a.ledgerEntryId)));

        final Map<String, TrustScoreComputer.ActorScore> capabilityScores = new LinkedHashMap<>();
        for (final Map.Entry<String, Map<UUID, List<LedgerAttestation>>> capEntry :
                byCapabilityAndEntry.entrySet()) {
            capabilityScores.put(capEntry.getKey(),
                                 computer.compute(decisions, capEntry.getValue(), now, credibilityMap));
        }

        // ── Dimension pass ───────────────────────────────────────────────────
        final Map<String, List<LedgerAttestation>> byDimension = rawAttestations.stream()
                                                                                .filter(a -> a.trustDimension != null && a.dimensionScore != null)
                                                                                .collect(Collectors.groupingBy(a -> a.trustDimension));

        final Map<String, Double> dimensionScores = new LinkedHashMap<>();
        for (final Map.Entry<String, List<LedgerAttestation>> dimEntry : byDimension.entrySet()) {
            computer.computeDimensionScore(dimEntry.getValue(), now, credibilityMap)
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
                computer.computeDimensionScore(dimEntry.getValue(), now, credibilityMap)
                        .ifPresent(score -> dims.put(dimEntry.getKey(), score));
            }
            if (!dims.isEmpty()) {
                capDimScores.put(capEntry.getKey(), dims);
            }
        }

        // ── Global pass ──────────────────────────────────────────────────────
        final List<LedgerAttestation> selected =
                globalScoreStrategy.selectAttestations(rawAttestations);
        final Map<UUID, List<LedgerAttestation>> selectedByEntry = selected.stream()
                                                                           .collect(Collectors.groupingBy(a -> a.ledgerEntryId));

        final TrustScoreComputer.ActorScore betaGlobal = computer.compute(decisions, selectedByEntry, now, credibilityMap);
        final TrustScoreComputer.ActorScore globalScore =
                globalScoreStrategy.derive(capabilityScores, rawAttestations)
                                   .orElse(betaGlobal);

        return new ComputedScores(capabilityScores, dimensionScores, capDimScores, globalScore);
    }

}
