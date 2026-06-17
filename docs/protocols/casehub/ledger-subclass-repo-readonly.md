---
id: PP-20260616-7d4171
title: "LedgerEntry subclass repositories must be read-only — save() routes through LedgerEntryRepository"
type: rule
scope: platform
applies_to: "Any repository SPI for a LedgerEntry subclass: KeyRotationRepository, ActorIdentityBindingRepository, and any future subclass-specific repos"
severity: important
refs:
  - docs/DESIGN.md
violation_hint: "A repository interface for a LedgerEntry subclass that declares save() — look for classes implementing a subclass-specific repository with em.persist() or sequenceAllocator calls"
created: 2026-06-16
---

Any dedicated repository SPI for a `LedgerEntry` subclass must be read-only: no `save()` method, no sequence allocation, no digest computation, no Merkle frontier update. Saves must go through `LedgerEntryRepository.save(entry, tenancyId)`, which runs the full pipeline (sequence allocation, enrichers, leaf hash, agent signing, Merkle frontier update). In-memory implementations delegate reads to `InMemoryLedgerEntryRepository.allEntries()` filtered by `instanceof` and `tenancyId`. History/audit read methods must include a `tenancyId` parameter — each tenant sees only its own records. **Exception:** security-integrity queries that must inspect across all tenants deliberately omit `tenancyId`. The canonical example is `KeyRotationRepository.findCompromisedByActorIdAndKeyRef` — a compromised signing key is a global security fact; if any tenant reports a key as COMPROMISED, all tenants must treat signatures from that key as SUSPECT. The canonical examples are `KeyRotationRepository` and `ActorIdentityBindingRepository` (fixed in #144/#145/#146). Violating this rule duplicates the save pipeline incompletely — missing Merkle frontier updates (#144) or tenancy isolation (#145/#146) without any compile-time signal.
