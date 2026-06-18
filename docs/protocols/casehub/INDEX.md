# casehub-ledger Platform Protocols

Rules specific to the casehub-ledger extension architecture.

| File | Rule Summary | Applies To |
|------|-------------|------------|
| [per-subject-table-tenancy.md](per-subject-table-tenancy.md) | Per-subject storage tables must include tenancy_id in their key | ledger_merkle_frontier, ledger_subject_sequence, any future per-subject table |
| [ledger-subclass-repo-readonly.md](ledger-subclass-repo-readonly.md) | LedgerEntry subclass repositories must be read-only — save() routes through LedgerEntryRepository | KeyRotationRepository, ActorIdentityBindingRepository, any future subclass repos |
| [ledger-entry-named-query.md](ledger-entry-named-query.md) | All JPQL against LedgerEntry must use @NamedQuery — never em.createQuery() | JpaCrossTenantLedgerEntryRepository, JpaLedgerEntryRepository, any EntityManager injection for ledger queries |
