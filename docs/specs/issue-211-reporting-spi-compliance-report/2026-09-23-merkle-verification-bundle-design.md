# Design Spec — Merkle Verification Bundle: Offline Tamper-Evidence Package

**Date:** 2026-09-23
**Issue:** #212 — feat: Merkle verification bundle — offline tamper-evidence package
**Status:** Draft

---

## Problem

Auditors need to independently verify the integrity of a tenancy's ledger
without access to the running system. Currently, `LedgerVerificationService`
performs online verification — it queries the database directly. There is no
mechanism to export a self-contained verification package that an auditor
can run offline.

---

## Design

A new service in `runtime` generates a `VerificationBundle` record containing
all entry digests, MMR frontier nodes, and stored roots per subject, along
with a standalone Python script and human-readable instructions. The auditor
downloads the bundle, runs the script, and gets a pass/fail per subject chain.

**Verification scope:** chain-only. The script takes precomputed digests as
given and verifies the MMR structure (binary-carry propagation → root
comparison). This catches chain manipulation (reordering, deletion, insertion).
Full field-level verification (recomputing `canonicalBytes()` → leaf hash)
is deferred — `domainContentBytes()` varies by consumer subclass.

---

## Part 1 — Models (`ledger-core`)

All records in `io.casehub.ledger.core.compliance`:

```java
public record VerificationBundle(
        String tenancyId,
        Instant generatedAt,
        List<SubjectChain> chains,
        String verificationScript,
        String verificationInstructions) {
}

public record SubjectChain(
        UUID subjectId,
        List<ChainEntry> entries,
        List<FrontierNode> frontier,
        String storedRoot) {
}

public record ChainEntry(
        int sequenceNumber,
        String digest) {
}

public record FrontierNode(
        int level,
        String hash) {
}
```

`ChainEntry.digest` is the precomputed leaf hash (`SHA-256(0x00 | canonicalBytes)`),
stored as the `digest` field on `LedgerEntry`. Entries are ordered by `sequenceNumber`.

`FrontierNode` mirrors `LedgerMerkleFrontier` — the stored MMR frontier for the subject.

`storedRoot` is the tree root computed from the frontier via `LedgerMerkleTree.treeRoot()`.

---

## Part 2 — Service (`runtime`)

```java
@ApplicationScoped
public class MerkleVerificationBundleService {

    @Inject LedgerEntryRepository repo;
    @Inject LedgerMerkleFrontierRepository frontierRepo;
    @Inject LedgerVerificationService verificationService;

    @Transactional
    public VerificationBundle generateBundle(final String tenancyId) {
        final List<UUID> subjectIds = repo.findDistinctSubjectIds(tenancyId);
        final List<SubjectChain> chains = subjectIds.stream()
                .map(sid -> buildChain(sid, tenancyId))
                .toList();
        final String script = loadResource("verify.py");
        final String instructions = loadResource("VERIFY-README.md");
        return new VerificationBundle(tenancyId, Instant.now(), chains, script, instructions);
    }

    private SubjectChain buildChain(final UUID subjectId, final String tenancyId) {
        final List<LedgerEntry> entries = repo.findBySubjectId(subjectId, tenancyId);
        final List<ChainEntry> chainEntries = entries.stream()
                .map(e -> new ChainEntry(e.sequenceNumber, e.digest))
                .toList();
        final List<LedgerMerkleFrontier> frontier =
                frontierRepo.findBySubjectId(subjectId, tenancyId);
        final List<FrontierNode> frontierNodes = frontier.stream()
                .map(f -> new FrontierNode(f.level, f.hash))
                .toList();
        String storedRoot;
        try {
            storedRoot = verificationService.treeRoot(subjectId, tenancyId);
        } catch (final Exception e) {
            storedRoot = null;
        }
        return new SubjectChain(subjectId, chainEntries, frontierNodes, storedRoot);
    }

    private String loadResource(final String name) {
        try (var is = getClass().getClassLoader()
                .getResourceAsStream("verification/" + name)) {
            if (is == null) return "";
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (final java.io.IOException e) {
            return "";
        }
    }
}
```

---

## Part 3 — Python Verification Script

Classpath resource at `runtime/src/main/resources/verification/verify.py`.

The script:
1. Reads a JSON bundle file (passed as CLI argument)
2. For each subject chain:
   a. Takes the list of digests in sequence order
   b. Runs binary-carry propagation: for each digest, appends to an MMR
      frontier using `internal_hash(left, right) = SHA-256(0x01 | bytes(left) | bytes(right))`
   c. Computes tree root from the final frontier (fold ASC by level:
      `internal_hash(higher_level, current)`)
   d. Compares computed root with `storedRoot`
3. Reports pass/fail per subject, overall summary

```python
#!/usr/bin/env python3
"""Offline Merkle Mountain Range verification for casehub-ledger bundles."""
import hashlib
import json
import sys

def internal_hash(left_hex: str, right_hex: str) -> str:
    left = bytes.fromhex(left_hex)
    right = bytes.fromhex(right_hex)
    data = b'\x01' + left + right
    return hashlib.sha256(data).hexdigest()

def append_to_frontier(digest: str, frontier: dict[int, str]) -> dict[int, str]:
    carry = digest
    level = 0
    while level in frontier:
        carry = internal_hash(frontier.pop(level), carry)
        level += 1
    frontier[level] = carry
    return frontier

def tree_root(frontier: dict[int, str]) -> str:
    if not frontier:
        raise ValueError("empty frontier")
    sorted_levels = sorted(frontier.keys())
    current = frontier[sorted_levels[0]]
    for lvl in sorted_levels[1:]:
        current = internal_hash(frontier[lvl], current)
    return current

def verify_chain(chain: dict) -> tuple[bool, str]:
    subject_id = chain["subjectId"]
    entries = sorted(chain["entries"], key=lambda e: e["sequenceNumber"])
    stored_root = chain.get("storedRoot")
    if not entries:
        return True, f"{subject_id}: SKIP (no entries)"
    if stored_root is None:
        return False, f"{subject_id}: FAIL (no stored root)"
    frontier = {}
    for entry in entries:
        frontier = append_to_frontier(entry["digest"], frontier)
    computed = tree_root(frontier)
    if computed == stored_root:
        return True, f"{subject_id}: PASS ({len(entries)} entries)"
    return False, f"{subject_id}: FAIL (root mismatch: computed={computed[:16]}... stored={stored_root[:16]}...)"

def main():
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <bundle.json>")
        sys.exit(1)
    with open(sys.argv[1]) as f:
        bundle = json.load(f)
    print(f"Verifying tenancy: {bundle['tenancyId']}")
    print(f"Generated: {bundle['generatedAt']}")
    print(f"Subjects: {len(bundle['chains'])}")
    print()
    passed = 0
    failed = 0
    for chain in bundle["chains"]:
        ok, msg = verify_chain(chain)
        print(f"  {msg}")
        if ok:
            passed += 1
        else:
            failed += 1
    print()
    print(f"Result: {passed} passed, {failed} failed")
    sys.exit(0 if failed == 0 else 1)

if __name__ == "__main__":
    main()
```

---

## Part 4 — Verification Instructions

Classpath resource at `runtime/src/main/resources/verification/VERIFY-README.md`.

```markdown
# Ledger Verification Bundle

## Prerequisites

- Python 3.8+ (no external packages required)

## Usage

1. Save the bundle JSON to a file (e.g., `bundle.json`)
2. Save `verify.py` to the same directory
3. Run: `python3 verify.py bundle.json`

## What This Verifies

The script verifies the Merkle Mountain Range (MMR) integrity for each
subject's chain of ledger entries:

- Entry digests are arranged in sequence order
- The MMR tree is reconstructed using binary-carry propagation
- Internal node hashes use SHA-256 with 0x01 domain separator (RFC 9162)
- The computed tree root is compared against the stored root

A PASS means the chain structure is intact — no entries have been
reordered, deleted, or inserted after the original hash computation.

## What This Does NOT Verify

- Individual entry content (field values → digest recomputation)
- Agent cryptographic signatures
- Supplement or domain-specific data integrity
```

---

## Part 5 — Testing

| Test | Scope |
|---|---|
| `MerkleVerificationBundleServiceIT` | Generate bundle for tenancy with 2 subjects. Verify: chains list has 2 entries, each has digests matching stored entries, storedRoot is non-null, script and instructions are non-empty strings. |
| `VerifyPyScriptTest` | Pure Java test: construct a known chain (3 entries), compute expected root via `LedgerMerkleTree`, serialize to JSON, invoke `python3 verify.py bundle.json` via `ProcessBuilder`, assert exit code 0 and stdout contains "PASS". Also test tampered chain (alter one digest) → exit code 1, stdout contains "FAIL". |

---

## File Map

| File | Action |
|---|---|
| `ledger-core/.../compliance/VerificationBundle.java` | Create |
| `ledger-core/.../compliance/SubjectChain.java` | Create |
| `ledger-core/.../compliance/ChainEntry.java` | Create |
| `ledger-core/.../compliance/FrontierNode.java` | Create |
| `runtime/.../service/MerkleVerificationBundleService.java` | Create |
| `runtime/src/main/resources/verification/verify.py` | Create |
| `runtime/src/main/resources/verification/VERIFY-README.md` | Create |
| `runtime/src/test/java/.../MerkleVerificationBundleServiceIT.java` | Create |
| `runtime/src/test/java/.../VerifyPyScriptTest.java` | Create |
| `CLAUDE.md` | Modify — add MerkleVerificationBundleService to project structure |

---

## References

- `ledger-core/.../merkle/LedgerMerkleTree.java` — MMR algorithm (leafHash, internalHash, append, treeRoot)
- `api/.../model/LedgerEntry.java:327` — canonicalBytes() canonical form
- `runtime/.../service/LedgerVerificationService.java` — online verification (verify, treeRoot, inclusionProof)
- `runtime/.../repository/LedgerMerkleFrontierRepository.java` — frontier storage
- `api/.../spi/LedgerEntryRepository.java` — findDistinctSubjectIds (from #211), findBySubjectId
- Issue #211 — parent issue (reporting SPI)
- Issue #212 — this issue
- RFC 9162 — Certificate Transparency (domain separation: 0x00 leaf, 0x01 internal)
