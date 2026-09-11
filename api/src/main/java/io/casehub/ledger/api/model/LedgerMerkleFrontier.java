package io.casehub.ledger.api.model;

import java.util.UUID;

/**
 * One node in the Merkle Mountain Range frontier for a subject.
 *
 * <p>
 * A subject with N entries has exactly {@code Integer.bitCount(N)} nodes at any time.
 * Pure Java — JPA mappings live in the runtime entity subclass.
 */
public class LedgerMerkleFrontier {

    public UUID id;

    public UUID subjectId;

    public String tenancyId;

    public int level;

    public String hash;
}
