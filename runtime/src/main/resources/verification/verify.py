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


def append_to_frontier(digest: str, frontier: dict) -> dict:
    carry = digest
    level = 0
    while level in frontier:
        carry = internal_hash(frontier.pop(level), carry)
        level += 1
    frontier[level] = carry
    return frontier


def tree_root(frontier: dict) -> str:
    if not frontier:
        raise ValueError("empty frontier")
    sorted_levels = sorted(frontier.keys())
    current = frontier[sorted_levels[0]]
    for lvl in sorted_levels[1:]:
        current = internal_hash(frontier[lvl], current)
    return current


def verify_chain(chain: dict):
    subject_id = chain["subjectId"]
    entries = sorted(chain["entries"], key=lambda e: e["sequenceNumber"])
    stored_root = chain.get("storedRoot")
    if not entries:
        return True, "{}: SKIP (no entries)".format(subject_id)
    if stored_root is None:
        return False, "{}: FAIL (no stored root)".format(subject_id)
    frontier = {}
    for entry in entries:
        frontier = append_to_frontier(entry["digest"], frontier)
    computed = tree_root(frontier)
    if computed == stored_root:
        return True, "{}: PASS ({} entries)".format(subject_id, len(entries))
    return False, "{}: FAIL (root mismatch: computed={}... stored={}...)".format(
        subject_id, computed[:16], stored_root[:16])


def main():
    if len(sys.argv) != 2:
        print("Usage: {} <bundle.json>".format(sys.argv[0]))
        sys.exit(1)
    with open(sys.argv[1]) as f:
        bundle = json.load(f)
    print("Verifying tenancy: {}".format(bundle["tenancyId"]))
    print("Generated: {}".format(bundle["generatedAt"]))
    print("Subjects: {}".format(len(bundle["chains"])))
    print()
    passed = 0
    failed = 0
    for chain in bundle["chains"]:
        ok, msg = verify_chain(chain)
        print("  {}".format(msg))
        if ok:
            passed += 1
        else:
            failed += 1
    print()
    print("Result: {} passed, {} failed".format(passed, failed))
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
