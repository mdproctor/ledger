package io.casehub.ledger.runtime.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;

/**
 * On-read {@link TrustScoreSource}: computes trust scores from raw attestation history
 * on each query. No materialized store, zero staleness.
 *
 * <p>A per-actor computation cache eliminates multiplicative query cost when the engine's
 * {@code TrustCandidateClassifier} calls multiple SPI methods for the same actor within
 * a single routing decision. The cache is invalidated on {@link AttestationRecordedEvent}
 * for the affected decision-maker — zero-staleness is preserved because invalidation
 * happens exactly when the underlying data changes.
 *
 * <p>No size bound on the cache — acceptable for lightweight deployments with bounded
 * actor counts (the target use case). If future use cases require unbounded actor sets,
 * add LRU eviction.
 *
 * <p>Activate via
 * {@code quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.service.ComputedTrustScoreSource}.
 */
@ApplicationScoped
@Alternative
public class ComputedTrustScoreSource implements TrustScoreSource {

    private final LedgerEntryRepository ledgerRepo;
    private final TrustScoreCalculator calculator;
    private static final TrustScoreCalculator.ComputedScores EMPTY_SENTINEL =
            new TrustScoreCalculator.ComputedScores(Map.of(), Map.of(), Map.of(),
                    new TrustScoreComputer.ActorScore(0, 0, 0, 0, 0, 0, 0));

    private final ConcurrentHashMap<String, TrustScoreCalculator.ComputedScores> cache =
            new ConcurrentHashMap<>();

    @Inject
    public ComputedTrustScoreSource(final LedgerEntryRepository ledgerRepo,
                                    final TrustScoreCalculator calculator) {
        this.ledgerRepo = ledgerRepo;
        this.calculator = calculator;
    }

    public void invalidateActor(final String actorId) {
        cache.remove(actorId);
    }

    void onAttestationRecorded(
            @Observes(during = TransactionPhase.AFTER_SUCCESS)
            final AttestationRecordedEvent event) {
        invalidateActor(event.actorId());
    }

    @Override
    public OptionalDouble globalScore(final String actorId) {
        final TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        return scores != null
                ? OptionalDouble.of(scores.globalScore().trustScore())
                : OptionalDouble.empty();
    }

    @Override
    public OptionalDouble capabilityScore(final String actorId, final String capabilityTag) {
        final TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return OptionalDouble.empty();
        }
        final TrustScoreComputer.ActorScore cap = scores.capabilityScores().get(capabilityTag);
        return cap != null ? OptionalDouble.of(cap.trustScore()) : OptionalDouble.empty();
    }

    @Override
    public OptionalDouble dimensionScore(final String actorId, final String dimensionKey) {
        final TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return OptionalDouble.empty();
        }
        final Double dim = scores.dimensionScores().get(dimensionKey);
        return dim != null ? OptionalDouble.of(dim) : OptionalDouble.empty();
    }

    @Override
    public OptionalDouble capabilityDimensionScore(final String actorId, final String capabilityTag,
            final String dimensionKey) {
        final TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return OptionalDouble.empty();
        }
        final Map<String, Double> dims = scores.capabilityDimensionScores().get(capabilityTag);
        if (dims == null) {
            return OptionalDouble.empty();
        }
        final Double val = dims.get(dimensionKey);
        return val != null ? OptionalDouble.of(val) : OptionalDouble.empty();
    }

    @Override
    public int decisionCount(final String actorId, final String capabilityTag) {
        final TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return 0;
        }
        final TrustScoreComputer.ActorScore cap = scores.capabilityScores().get(capabilityTag);
        return cap != null ? cap.decisionCount() : 0;
    }

    @Override
    public Map<String, Double> allCapabilityScores(final String actorId) {
        final TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return Map.of();
        }
        return scores.capabilityScores().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().trustScore()));
    }

    @Override
    public Map<String, Double> allDimensionScores(final String actorId) {
        final TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        return scores != null ? Collections.unmodifiableMap(scores.dimensionScores()) : Map.of();
    }

    @Override
    public Map<String, Double> qualityScores(final String actorId, final String capabilityTag) {
        final TrustScoreCalculator.ComputedScores scores = computeIfAbsent(actorId);
        if (scores == null) {
            return Map.of();
        }
        final Map<String, Double> dims = scores.capabilityDimensionScores().get(capabilityTag);
        return dims != null ? Collections.unmodifiableMap(dims) : Map.of();
    }

    private TrustScoreCalculator.ComputedScores computeIfAbsent(final String actorId) {
        final TrustScoreCalculator.ComputedScores scores =
                cache.computeIfAbsent(actorId, this::computeFresh);
        return scores == EMPTY_SENTINEL ? null : scores;
    }

    private TrustScoreCalculator.ComputedScores computeFresh(final String actorId) {
        final List<LedgerEntry> decisions = ledgerRepo.findEventsByActorId(actorId);
        if (decisions.isEmpty()) {
            return EMPTY_SENTINEL;
        }
        final Set<UUID> entryIds = decisions.stream()
                .map(e -> e.id)
                .collect(Collectors.toSet());
        final Map<UUID, List<LedgerAttestation>> attestationsByEntry =
                ledgerRepo.findAttestationsForEntries(entryIds);
        return calculator.computeAll(decisions, attestationsByEntry, Instant.now());
    }
}
