package io.casehub.ledger.runtime.service.identity;

/**
 * Cached result of a SCIM2 agent lookup.
 *
 * <p>Contains only the DID string. Public-key bytes are intentionally absent:
 * SCIM {@code x509Certificates[0].value} is a DER-encoded X.509 certificate,
 * not the {@code SubjectPublicKeyInfo} bytes that {@code LedgerEntry.agentPublicKey} requires.
 * Extraction requires {@code CertificateFactory}. Add when there is a concrete consumer.
 */
public record ScimAgentResource(String did) {}
