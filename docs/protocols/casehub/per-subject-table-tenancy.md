---
id: PP-20260616-05dc6a
title: "Per-subject storage tables must include tenancy_id in their key"
type: rule
scope: platform
applies_to: "Any table in casehub-ledger that stores per-subject state: ledger_merkle_frontier, ledger_subject_sequence, or any future per-subject cache or index"
severity: critical
refs:
  - docs/DESIGN.md
violation_hint: "A table keyed by (subject_id) alone in a multi-tenant deployment — look for CREATE TABLE with subject_id as the sole PK or unique key component"
created: 2026-06-16
---

`KeyRotationEntry` and `ActorIdentityBindingEntry` derive `subjectId = UUID.nameUUIDFromBytes(actorId.getBytes(UTF_8))`, producing identical UUIDs for any two tenants sharing the same `actorId`. Any per-subject storage table keyed by `subjectId` alone silently merges state from different tenants: concurrent writes from tenant A and tenant B overwrite each other's frontier nodes and sequence counters, corrupting Merkle chains and making `LedgerVerificationService.inclusionProof()` cryptographically incorrect for affected entries (issue #139). Every table that tracks per-subject state must include `tenancy_id` as part of its primary key, with `DEFAULT TenancyConstants.DEFAULT_TENANT_ID` for single-tenant deployments.
