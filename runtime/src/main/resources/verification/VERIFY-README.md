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

- Individual entry content (field values to digest recomputation)
- Agent cryptographic signatures
- Supplement or domain-specific data integrity
