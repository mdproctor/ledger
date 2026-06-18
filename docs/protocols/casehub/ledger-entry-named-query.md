---
id: PP-20260618-51c673
title: "All JPQL queries against LedgerEntry must use @NamedQuery — never em.createQuery()"
type: rule
scope: repo
applies_to: "JpaCrossTenantLedgerEntryRepository, JpaLedgerEntryRepository, and any class injecting EntityManager for LedgerEntry queries"
severity: important
refs:
  - CLAUDE.md
violation_hint: "em.createQuery(\"SELECT ... FROM LedgerEntry ...\") in any repository or service class"
garden_ref: "GE-20260618-d244e2"
created: 2026-06-18
---

All JPQL that targets `LedgerEntry` (or any subclass) must be declared as a `@NamedQuery`
annotation on the entity class and called via `em.createNamedQuery()`. Inline
`em.createQuery("SELECT ...")` bypasses Hibernate's startup validation — JPQL errors
(including Hibernate 6 type-checker regressions that are dialect-specific) are caught only
at query execution time, which for scheduled health or trust jobs can be hours after
deployment. `@NamedQuery` on the entity converts these from delayed runtime failures into
immediate boot failures, visible in every environment from the first startup.
