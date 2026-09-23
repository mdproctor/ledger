## D1: Verification scope

**Choice:** Chain-only verification (digest → MMR → root comparison)
**Alternatives:**
- Full field-level verification — recomputes canonicalBytes() from raw fields, but domainContentBytes() varies by consumer subclass, making a generic Python script impossible for domain entries
**Rationale:** Chain manipulation (reordering, deletion, insertion) is the primary tamper-evidence concern. Chain-only verification catches this without needing subclass-specific logic. Full field-level verification for PlainLedgerEntry can be added as a future enhancement.
**Trade-offs:** Cannot detect content tampering where someone modifies a field value AND recomputes the digest — but this requires access to the signing key
**Sources:** `ledger-core/.../merkle/LedgerMerkleTree.java`, `api/.../model/LedgerEntry.java:canonicalBytes()`
**Exploration:** quick
**Status:** captured

## D2: Module placement

**Choice:** Service in `runtime`, models in `ledger-core` — same pattern as AuditTrailExportService
**Alternatives:**
- In `reporting` module — wrong: reporting is presentation-only, this service needs DB access
**Rationale:** Follows the established pattern from #211. The service queries LedgerEntryRepository and LedgerMerkleFrontierRepository.
**Trade-offs:** None
**Sources:** `runtime/.../service/AuditTrailExportService.java` — same pattern
**Exploration:** quick
**Status:** captured

## D3: Python script packaging

**Choice:** Embedded as classpath resource (`verify.py`) read at bundle generation time
**Alternatives:**
- Generated dynamically from Java — fragile, harder to test independently
- External download URL — defeats offline purpose
**Rationale:** Classpath resource is versioned with the module, testable independently, and included verbatim in the bundle. Auditors can inspect the exact script that was bundled.
**Trade-offs:** Script updates require a new module release
**Sources:** Standard classpath resource pattern
**Exploration:** quick
**Status:** captured
