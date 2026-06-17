package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.runtime.service.TrustGateService;

/**
 * Pure unit tests for {@link TrustGateService} — no Quarkus runtime, no CDI.
 * Tests the policy layer (thresholds, fallbacks) on top of {@link TrustScoreSource}.
 */
class TrustGateServiceTest {

    private static TrustScoreSource sourceWith(final String actorId, final double globalScore) {
        return new StubTrustScoreSource() {
            @Override
            public OptionalDouble globalScore(final String id) {
                return actorId.equals(id) ? OptionalDouble.of(globalScore) : OptionalDouble.empty();
            }
        };
    }

    private static TrustScoreSource emptySource() {
        return new StubTrustScoreSource();
    }

    private static TrustScoreSource sourceWith(
            final String actorId, final double globalScore,
            final String capabilityTag, final double capabilityScore) {
        return new StubTrustScoreSource() {
            @Override
            public OptionalDouble globalScore(final String id) {
                return actorId.equals(id) ? OptionalDouble.of(globalScore) : OptionalDouble.empty();
            }

            @Override
            public OptionalDouble capabilityScore(final String id, final String tag) {
                return actorId.equals(id) && capabilityTag.equals(tag)
                        ? OptionalDouble.of(capabilityScore)
                        : OptionalDouble.empty();
            }
        };
    }

    // ── meetsThresholdAsync (global) ─────────────────────────────────────────

    @Test
    void meetsThresholdAsync_true_whenScoreAboveMin() {
        final TrustGateService gate = new TrustGateService(sourceWith("actor-a", 0.8));
        assertThat(gate.meetsThresholdAsync("actor-a", 0.7).await().indefinitely()).isTrue();
    }

    @Test
    void meetsThresholdAsync_false_whenScoreBelowMin() {
        final TrustGateService gate = new TrustGateService(sourceWith("actor-a", 0.6));
        assertThat(gate.meetsThresholdAsync("actor-a", 0.7).await().indefinitely()).isFalse();
    }

    @Test
    void meetsThresholdAsync_false_whenNoScoreExists() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.meetsThresholdAsync("unknown-actor", 0.5).await().indefinitely()).isFalse();
    }

    // ── meetsThreshold (global) ───────────────────────────────────────────────

    @Test
    void meetsThreshold_true_whenScoreAboveMin() {
        final TrustGateService gate = new TrustGateService(sourceWith("actor-a", 0.8));
        assertThat(gate.meetsThreshold("actor-a", 0.7)).isTrue();
    }

    @Test
    void meetsThreshold_true_whenScoreEqualsMin() {
        final TrustGateService gate = new TrustGateService(sourceWith("actor-a", 0.7));
        assertThat(gate.meetsThreshold("actor-a", 0.7)).isTrue();
    }

    @Test
    void meetsThreshold_false_whenScoreBelowMin() {
        final TrustGateService gate = new TrustGateService(sourceWith("actor-a", 0.6));
        assertThat(gate.meetsThreshold("actor-a", 0.7)).isFalse();
    }

    @Test
    void meetsThreshold_false_whenNoScoreExists() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.meetsThreshold("unknown-actor", 0.0)).isFalse();
    }

    // ── meetsThreshold (capability — Phase 2) ────────────────────────────────

    @Test
    void meetsThreshold_withCapability_usesCapabilityScore_whenAvailable() {
        final TrustGateService gate = new TrustGateService(
                sourceWith("actor-x", 0.4, "security-review", 0.9));
        assertThat(gate.meetsThreshold("actor-x", "security-review", 0.8)).isTrue();
    }

    @Test
    void meetsThreshold_withCapability_capabilityScoreBelowThreshold() {
        final TrustGateService gate = new TrustGateService(
                sourceWith("actor-y", 0.9, "style-review", 0.3));
        assertThat(gate.meetsThreshold("actor-y", "style-review", 0.8)).isFalse();
    }

    @Test
    void meetsThreshold_withCapability_fallsBackToGlobal_whenNoCapabilityScore() {
        final TrustGateService gate = new TrustGateService(sourceWith("actor-z", 0.85));
        assertThat(gate.meetsThreshold("actor-z", "unknown-tag", 0.8)).isTrue();
        assertThat(gate.meetsThreshold("actor-z", "unknown-tag", 0.9)).isFalse();
    }

    @Test
    void meetsThreshold_withCapability_falseWhenNoScoreAtAll() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.meetsThreshold("ghost", "security-review", 0.0)).isFalse();
    }

    // ── currentScore (capability) ────────────────────────────────────────────

    @Test
    void currentScore_withCapability_returnsCapabilityScore() {
        final TrustGateService gate = new TrustGateService(
                sourceWith("actor-q", 0.5, "security-review", 0.9));
        assertThat(gate.currentScore("actor-q", "security-review")).hasValue(0.9);
    }

    @Test
    void currentScore_withCapability_emptyWhenNoCapabilityScore() {
        final TrustGateService gate = new TrustGateService(sourceWith("actor-r", 0.7));
        assertThat(gate.currentScore("actor-r", "unknown-tag")).isEmpty();
    }

    // ── currentScore (global) ────────────────────────────────────────────────

    @Test
    void currentScore_returnsScore_whenActorKnown() {
        final TrustGateService gate = new TrustGateService(sourceWith("actor-c", 0.75));
        assertThat(gate.currentScore("actor-c")).hasValue(0.75);
    }

    @Test
    void currentScore_returnsEmpty_whenActorUnknown() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.currentScore("ghost")).isEmpty();
    }

    // ── allDimensionScores ───────────────────────────────────────────────────

    @Test
    void allDimensionScores_returnsAllDimensionScores() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public Map<String, Double> allDimensionScores(final String id) {
                return "actor-d".equals(id)
                        ? Map.of("thoroughness", 0.8, "false-positive-rate", 0.2)
                        : Map.of();
            }
        });

        final Map<String, Double> scores = gate.allDimensionScores("actor-d");
        assertThat(scores).hasSize(2);
        assertThat(scores.get("thoroughness")).isEqualTo(0.8);
        assertThat(scores.get("false-positive-rate")).isEqualTo(0.2);
    }

    @Test
    void allDimensionScores_returnsEmptyMap_whenNoDimensionRows() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.allDimensionScores("ghost")).isEmpty();
    }

    // ── allCapabilityScores ──────────────────────────────────────────────────

    @Test
    void allCapabilityScores_returnsAllCapabilityScores() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public Map<String, Double> allCapabilityScores(final String id) {
                return "actor-cap".equals(id)
                        ? Map.of("sar-drafting", 0.79, "osint-screening", 0.85)
                        : Map.of();
            }
        });

        final Map<String, Double> scores = gate.allCapabilityScores("actor-cap");
        assertThat(scores).hasSize(2);
        assertThat(scores.get("sar-drafting")).isEqualTo(0.79);
        assertThat(scores.get("osint-screening")).isEqualTo(0.85);
    }

    @Test
    void allCapabilityScores_returnsEmptyMap_whenNoCapabilityRows() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.allCapabilityScores("ghost")).isEmpty();
    }

    // ── dimensionScore ───────────────────────────────────────────────────────

    @Test
    void dimensionScore_returnsScore_whenDimensionExists() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public OptionalDouble dimensionScore(final String id, final String dim) {
                return "actor-e".equals(id) && "thoroughness".equals(dim)
                        ? OptionalDouble.of(0.75) : OptionalDouble.empty();
            }
        });
        assertThat(gate.dimensionScore("actor-e", "thoroughness")).hasValue(0.75);
    }

    @Test
    void dimensionScore_returnsEmpty_whenDimensionNotFound() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.dimensionScore("actor-f", "false-positive-rate")).isEmpty();
    }

    @Test
    void dimensionScore_returnsEmpty_whenActorUnknown() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.dimensionScore("ghost", "thoroughness")).isEmpty();
    }

    // ── qualityScore ─────────────────────────────────────────────────────────

    @Test
    void qualityScore_returnsScore_whenCompositeExists() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public OptionalDouble capabilityDimensionScore(final String id, final String cap,
                    final String dim) {
                return "actor-cd".equals(id) && "security-review".equals(cap)
                        && "thoroughness".equals(dim)
                        ? OptionalDouble.of(0.92) : OptionalDouble.empty();
            }
        });
        assertThat(gate.qualityScore("actor-cd", "security-review", "thoroughness")).hasValue(0.92);
    }

    @Test
    void qualityScore_returnsEmpty_whenNotComputed() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.qualityScore("ghost", "security-review", "thoroughness")).isEmpty();
    }

    @Test
    void qualityScore_returnsEmpty_whenCapabilityMismatch() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public OptionalDouble capabilityDimensionScore(final String id, final String cap,
                    final String dim) {
                return "actor-cd2".equals(id) && "security-review".equals(cap)
                        && "thoroughness".equals(dim)
                        ? OptionalDouble.of(0.92) : OptionalDouble.empty();
            }
        });
        assertThat(gate.qualityScore("actor-cd2", "architecture-review", "thoroughness")).isEmpty();
    }

    // ── qualityScores ────────────────────────────────────────────────────────

    @Test
    void qualityScores_returnsAllDimensionsForCapability() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public Map<String, Double> qualityScores(final String id, final String cap) {
                return "actor-qs".equals(id) && "security-review".equals(cap)
                        ? Map.of("thoroughness", 0.9, "false-positive-rate", 0.1)
                        : Map.of();
            }
        });

        final Map<String, Double> scores = gate.qualityScores("actor-qs", "security-review");
        assertThat(scores).hasSize(2);
        assertThat(scores.get("thoroughness")).isEqualTo(0.9);
        assertThat(scores.get("false-positive-rate")).isEqualTo(0.1);
    }

    @Test
    void qualityScores_returnsEmptyMap_whenNoneComputed() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.qualityScores("ghost", "security-review")).isEmpty();
    }

    // ── meetsQualityThreshold ────────────────────────────────────────────────

    @Test
    void meetsQualityThreshold_true_whenScoreMeetsMin() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public OptionalDouble capabilityDimensionScore(final String id, final String cap,
                    final String dim) {
                return "actor-mqt".equals(id) && "security-review".equals(cap)
                        && "thoroughness".equals(dim)
                        ? OptionalDouble.of(0.8) : OptionalDouble.empty();
            }
        });
        assertThat(gate.meetsQualityThreshold("actor-mqt", "security-review", "thoroughness", 0.75))
                .isTrue();
    }

    @Test
    void meetsQualityThreshold_false_whenScoreBelowMin() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public OptionalDouble capabilityDimensionScore(final String id, final String cap,
                    final String dim) {
                return "actor-mqt2".equals(id) && "security-review".equals(cap)
                        && "thoroughness".equals(dim)
                        ? OptionalDouble.of(0.6) : OptionalDouble.empty();
            }
        });
        assertThat(gate.meetsQualityThreshold("actor-mqt2", "security-review", "thoroughness", 0.75))
                .isFalse();
    }

    @Test
    void meetsQualityThreshold_false_whenNoScoreExists() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.meetsQualityThreshold("ghost", "security-review", "thoroughness", 0.0))
                .isFalse();
    }

    // ── scoresFor ────────────────────────────────────────────────────────────

    @Test
    void scoresFor_returnsScoreForEachCandidate() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public OptionalDouble capabilityScore(final String id, final String cap) {
                if (!"review".equals(cap)) {
                    return OptionalDouble.empty();
                }
                return switch (id) {
                    case "a" -> OptionalDouble.of(0.8);
                    case "b" -> OptionalDouble.of(0.3);
                    default -> OptionalDouble.empty();
                };
            }
        });

        final Map<String, OptionalDouble> scores = gate.scoresFor(List.of("a", "b", "c"), "review");
        assertThat(scores).hasSize(3);
        assertThat(scores.get("a")).hasValue(0.8);
        assertThat(scores.get("b")).hasValue(0.3);
        assertThat(scores.get("c")).isEmpty();
    }

    @Test
    void scoresFor_emptyList_returnsEmptyMap() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.scoresFor(List.of(), "review")).isEmpty();
    }

    @Test
    void scoresForAsync_returnsEquivalentResult() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public OptionalDouble capabilityScore(final String id, final String cap) {
                return "x".equals(id) && "review".equals(cap)
                        ? OptionalDouble.of(0.7) : OptionalDouble.empty();
            }
        });

        final Map<String, OptionalDouble> scores =
                gate.scoresForAsync(List.of("x", "y"), "review").await().indefinitely();
        assertThat(scores.get("x")).hasValue(0.7);
        assertThat(scores.get("y")).isEmpty();
    }

    // ── decisionCountsFor ────────────────────────────────────────────────────

    @Test
    void decisionCountsFor_returnsCountForEachCandidate() {
        final TrustGateService gate = new TrustGateService(new StubTrustScoreSource() {
            @Override
            public int decisionCount(final String id, final String cap) {
                return "review".equals(cap) && "a".equals(id) ? 5 : 0;
            }
        });

        final Map<String, Integer> counts = gate.decisionCountsFor(List.of("a", "b"), "review");
        assertThat(counts).hasSize(2);
        assertThat(counts.get("a")).isEqualTo(5);
        assertThat(counts.get("b")).isZero();
    }

    @Test
    void decisionCountsFor_emptyList_returnsEmptyMap() {
        final TrustGateService gate = new TrustGateService(emptySource());
        assertThat(gate.decisionCountsFor(List.of(), "review")).isEmpty();
    }

    // ── Base stub ────────────────────────────────────────────────────────────

    private static class StubTrustScoreSource implements TrustScoreSource {
        @Override public OptionalDouble globalScore(final String id) { return OptionalDouble.empty(); }
        @Override public OptionalDouble capabilityScore(final String id, final String t) { return OptionalDouble.empty(); }
        @Override public OptionalDouble dimensionScore(final String id, final String d) { return OptionalDouble.empty(); }
        @Override public OptionalDouble capabilityDimensionScore(final String id, final String c, final String d) { return OptionalDouble.empty(); }
        @Override public int decisionCount(final String id, final String t) { return 0; }
        @Override public Map<String, Double> allCapabilityScores(final String id) { return Map.of(); }
        @Override public Map<String, Double> allDimensionScores(final String id) { return Map.of(); }
        @Override public Map<String, Double> qualityScores(final String id, final String t) { return Map.of(); }
    }
}
