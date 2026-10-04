---
layout: post
title: "The NoOp That Ate Everything"
date: 2026-10-04
entry_type: note
subtype: diary
projects: [casehubio/ledger]
tags: [cdi, quarkus, defaultbean, alternative, silent-failure]
---

# The NoOp That Ate Everything

Every JPA repository in casehub-ledger was annotated `@Alternative`. Every one of them was dormant. The `@DefaultBean` NoOp fallbacks — designed as safety nets for datasource-free deployments — were silently winning across every consumer. Saves succeeded and persisted nothing. Queries returned empty. No error, no warning, no exception.

Seven classes. All doing the same wrong thing the same way: `@Alternative @ApplicationScoped`, requiring explicit `quarkus.arc.selected-alternatives` config that no consumer provided. The `@DefaultBean` producer methods in `LedgerCoreProducer` filled the gap with implementations that smiled, nodded, and threw your data away.

The fix is `@Priority(1)` — replace `@Alternative` with `jakarta.annotation.Priority` at value 1, and the JPA repo auto-displaces the `@DefaultBean` NoOp without any consumer configuration:

```java
// Before — dormant unless consumer explicitly activates
@ApplicationScoped
@Alternative
public class JpaLedgerEntryRepository implements LedgerEntryRepository { ... }

// After — auto-displaces @DefaultBean without consumer config
@ApplicationScoped
@Priority(1)
public class JpaLedgerEntryRepository implements LedgerEntryRepository { ... }
```

One import changes. One annotation changes. The semantics flip from "opt-in, default off" to "always active, displaceable by higher priority."

The interesting failure mode is the silence. CDI doesn't consider an unactivated `@Alternative` to be an error — it's by design. And `@DefaultBean` producers exist precisely to fill the gap when no real implementation is present. Each mechanism is doing exactly what it should. The combination is what kills you: the real implementation is present, compiled, tested, on the classpath — and completely inert. The NoOp catches the ball and quietly drops it.

The test cleanup was telling. The test properties had `quarkus.arc.selected-alternatives` lists that spanned seven lines, repeated in six profiles. All of that configuration existed to work around the wrong annotation choice. Removing it cut eighty lines from `application.properties` — and every test still passes, because the JPA repos now activate themselves.

One test — `NoOpActorIdentityBindingRepositoryIT` — had been *asserting the buggy behaviour*. It verified that `latestBindingFor()` returned empty when `JpaActorIdentityBindingRepository` wasn't in `selected-alternatives`. That was the test working correctly against a system that was broken by design. We updated it to verify that reads return actual data, which is what a user would expect.

The pattern generalises beyond this project. Any Quarkus extension that provides `@Alternative` implementations alongside `@DefaultBean` NoOps has this failure mode. The intent is usually "provide a default that works without a datasource." The effect is "require every consumer to add configuration they don't know about, or silently lose all their data." `@Priority(1)` solves it cleanly — the real implementation wins when present, the `@DefaultBean` NoOp catches the edge case when it's genuinely absent.
