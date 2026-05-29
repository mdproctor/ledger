# Example: Vault Transit Remote Signing

Demonstrates `VaultTransitAgentSigner` — an `AgentSigner` implementation where the private
key never leaves HashiCorp Vault. Signing happens via the Vault Transit Secrets Engine REST API.

## Pattern

`VaultTransitAgentSigner` extends `AbstractCachingAgentSigner<VaultTransitContext>`:

- `loadContext(actorId)` — `GET /v1/transit/keys/<key-name>` — fetches and caches public key
- `performSign(actorId, context, data)` — `POST /v1/transit/sign/<key-name>` — remote sign

The private key **never leaves Vault**. Only the public key is cached locally for storage
on `LedgerEntry.agentPublicKey` (needed for self-contained verification via `AgentCryptographicVerifier`).

## Signature format

Vault Transit ed25519: 64 raw bytes after stripping `vault:v1:` prefix and base64-decoding.
Vault Transit ECDSA (ecdsa-p256): ASN.1 DER — same format JCA expects, no conversion needed.

## Auth

This example uses a static token (`casehub.ledger.vault-transit.token`).
Production deployments should use AppRole or OIDC — see issue #101.

## PKCS#11 HSMs via JCA

For HSMs exposing a JCA Provider, you do NOT need `VaultTransitAgentSigner`. Use
`KeyStore.getInstance("PKCS11")` to load a `KeyPair` where the `PrivateKey` is an HSM-backed
handle. JCA routes signing into hardware without exporting key material. Extend
`AbstractCachingAgentSigner<KeyPair>` and call `AgentSignature.signWith(keyPair, data)`.

## Running the tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl examples/vault-transit-signing
```

The tests use WireMock to simulate the Vault Transit API — no real Vault instance required.
