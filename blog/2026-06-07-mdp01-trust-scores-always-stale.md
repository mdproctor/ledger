---
layout: post
title: "Trust scores were always stale — now they don't have to be"
date: 2026-06-07
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-ledger]
tags: [trust-scoring, spi, caching, staleness]
---

Trust scores in casehub-ledger are a materialized view. The nightly batch job computes Bayesian Beta scores from attestation history, writes them to `actor_trust_score`, and fires CDI events so consumers can refresh their caches. The engine-side `TrustScoreCache` maintains an in-memory copy, keyed by `actorId:capabilityKey`, refreshed on those events.

The problem: the cache only observes `TrustScoreFullPayload` from the batch job. The incremental observer — which recomputes per-actor scores within milliseconds of each attestation — fires a different event (`TrustScoreActorUpdatedEvent`). The cache never sees it. So even with incremental updates writing fresh scores to the database, the routing cache is stale until the next batch run.

For QuarkMind — four actors, bounded games, trust scores changing every few seconds — this is structurally wrong. The scores are cheap to compute. The materialization adds latency for zero benefit.

## The SPI

I wanted three options behind one abstraction: read from the DB (default), read from an in-memory cache (high-throughput), or compute on demand from raw attestation history (zero staleness). `TrustScoreSource` in `api/spi/` is the interface — eight methods returning `OptionalDouble`, `int`, and `Map<String, Double>`. No entity types cross the boundary.

`MaterializedTrustScoreSource` reads `ActorTrustScoreRepository` per call. `CachedTrustScoreSource` holds four `ConcurrentHashMap` instances (one per score type) and observes both batch and incremental events — fixing the staleness bug. `ComputedTrustScoreSource` loads the actor's attestation history and runs the Bayesian Beta computation on every call.

## The computation cache problem

The computed source hit a structural issue immediately. The engine's `TrustCandidateClassifier` calls `capabilityScore()`, `decisionCount()`, and up to N `capabilityDimensionScore()` calls per candidate. Three candidates with two quality dimensions: twelve full attestation loads per routing decision, all for the same actors.

The fix is a per-actor computation cache. On first query for an actor, load everything and compute all score types at once. Cache the bundle. Invalidate on `AttestationRecordedEvent` — the CDI event that fires after each attestation commits. Between attestations the cache is correct because no new data exists. Zero staleness with O(1) subsequent lookups.

## TrustScoreCalculator

Extracting the pure computation from `PerActorTrustComputer` turned out to be the cleanest structural improvement. The old class interleaved the four-pass algorithm (capability → dimension → capability×dimension → global) with `trustRepo.upsert()` calls and CDI event firing. `TrustScoreCalculator` takes decisions and attestations in, returns a `ComputedScores` record out. No persistence, no events.

Both paths now share the same computation: `PerActorTrustComputer` does load → calculate → persist → fire events. `ComputedTrustScoreSource` does load → calculate → return. The derive() path for `FrequencyWeightedGlobalStrategy` — which receives raw attestations for frequency counting, not the aggregated synthetics that `selectAttestations()` uses — is handled correctly because `computeAll()` runs the full four-pass flow exactly as the batch job does.

## Two gotchas

The cache key separator was `:` — natural and readable. But the actorId format is `{model-family}:{persona}@{major}`, so `claude:tarkus-reviewer@v1` with a `:` separator creates false `startsWith()` matches. Switched to `\0`.

`ConcurrentHashMap.computeIfAbsent()` with a mapping function that returns `null` doesn't throw — it returns null and stores nothing. Correct in OpenJDK, but the contract doesn't guarantee it, and the "stores nothing" part means every call for an unknown actor re-runs the database query. Sentinel pattern fixes both.
