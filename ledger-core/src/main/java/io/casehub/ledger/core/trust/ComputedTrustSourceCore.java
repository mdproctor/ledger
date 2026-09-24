package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.api.spi.TrustScoreSource;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ComputedTrustSourceCore implements TrustScoreSource {

    private final CrossTenantLedgerEntryRepository ledgerRepo;
    private final TrustScoreCalculator calculator;
    private static final TrustScoreCalculator.ComputedScores EMPTY_SENTINEL =
            new TrustScoreCalculator.ComputedScores(Map.of(), Map.of(), Map.of(),
                    new TrustScoreComputer.ActorScore(0, 0, 0, 0, 0, 0, 0, 1.0));

    private final ConcurrentHashMap<String, TrustScoreCalculator.ComputedScores> cache =
            new ConcurrentHashMap<>();

    public ComputedTrustSourceCore(CrossTenantLedgerEntryRepository ledgerRepo,
                                   TrustScoreCalculator calculator) {
        this.ledgerRepo = ledgerRepo;
        this.calculator = calculator;
    }

    public void invalidateActor(String actorId) {
        cache.remove(actorId);
    }

    @Override
    public OptionalDouble globalScore(String actorId) {
        TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        return scores != null
                ? OptionalDouble.of(scores.globalScore().trustScore())
                : OptionalDouble.empty();
    }

    @Override
    public OptionalDouble capabilityScore(String actorId, String capabilityTag) {
        TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return OptionalDouble.empty();
        }
        TrustScoreComputer.ActorScore cap = scores.capabilityScores().get(capabilityTag);
        return cap != null ? OptionalDouble.of(cap.trustScore()) : OptionalDouble.empty();
    }

    @Override
    public OptionalDouble dimensionScore(String actorId, String dimensionKey) {
        TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return OptionalDouble.empty();
        }
        Double dim = scores.dimensionScores().get(dimensionKey);
        return dim != null ? OptionalDouble.of(dim) : OptionalDouble.empty();
    }

    @Override
    public OptionalDouble capabilityDimensionScore(String actorId, String capabilityTag,
                                                   String dimensionKey) {
        TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return OptionalDouble.empty();
        }
        Map<String, Double> dims = scores.capabilityDimensionScores().get(capabilityTag);
        if (dims == null) {
            return OptionalDouble.empty();
        }
        Double val = dims.get(dimensionKey);
        return val != null ? OptionalDouble.of(val) : OptionalDouble.empty();
    }

    @Override
    public int decisionCount(String actorId, String capabilityTag) {
        TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return 0;
        }
        TrustScoreComputer.ActorScore cap = scores.capabilityScores().get(capabilityTag);
        return cap != null ? cap.decisionCount() : 0;
    }

    @Override
    public Map<String, Double> allCapabilityScores(String actorId) {
        TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return Map.of();
        }
        return scores.capabilityScores().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().trustScore()));
    }

    @Override
    public Map<String, Double> allDimensionScores(String actorId) {
        TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        return scores != null ? Collections.unmodifiableMap(scores.dimensionScores()) : Map.of();
    }

    @Override
    public Map<String, Double> qualityScores(String actorId, String capabilityTag) {
        TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return Map.of();
        }
        Map<String, Double> dims = scores.capabilityDimensionScores().get(capabilityTag);
        return dims != null ? Collections.unmodifiableMap(dims) : Map.of();
    }

    private TrustScoreCalculator.ComputedScores computeIfAbsent(String actorId) {
        TrustScoreCalculator.ComputedScores scores =
                cache.computeIfAbsent(actorId, this::computeFresh);
        return scores == EMPTY_SENTINEL ? null : scores;
    }

    @SuppressWarnings("unchecked")
    private TrustScoreCalculator.ComputedScores computeFresh(String actorId) {
        List<LedgerEntry> decisions = ledgerRepo.findEventsByActorId(actorId);
        if (decisions.isEmpty()) {
            return EMPTY_SENTINEL;
        }
        Map<UUID, List<LedgerAttestation>> attestationsByEntry =
                (Map<UUID, List<LedgerAttestation>>) (Map<?, ?>) ledgerRepo.findAttestationsByActorId(actorId);
        return calculator.computeAll(decisions, attestationsByEntry, Instant.now());
    }
}
