# CONVENTIONS.md — commons-manager

Module notes for `commons-manager`. Build/architecture context lives in the repository root `CLAUDE.md`.

## By-design registry — do not "fix"

Rejected findings are registered here; full reasoning and verification evidence live in `.claude/audit/commons-manager-audit-2026-11-21.md` (section 4). Do not re-litigate what is registered here; if a rejection is revisited, start from the report note for its ID.

- **`AbstractManager.doGetIfPresent()`: optimistic `entry` capture before the lock (CM-20).** The comment `// Optimistically get entry without lock.` is the contract: the captured `entry` is a hint, and a `null` from an entry removed and cleared in the capture→lock window is accepted snapshot semantics — `getIfPresent`/`containsKey` are snapshots, not guarantees. The asymmetry with `doGet()` (which re-reads under the read lock) is deliberate: `get` is the usage-accounting path with `ManagerException` on absent key, a different accuracy stake. Do not re-read under the lock.
- **`AbstractManager.doRemoveEntry()`: `objectAndAdapter != null` guard (CM-19).** The guard is reachable and load-bearing: in `UsageCountingManager.release()` another thread can remove and clear the entry between `unlockRead` and `lockWrite` inside `upgradeLock()`, while `canRemoveUnused` rechecks the counter, not the entry — `doRemoveEntry` then runs on the stale entry and the guard handles the race. Replacing the guard with `requireNonNull` introduces an NPE regression.
- **`AbstractManager#values`: lazy view cache without `volatile` (CM-22).** Benign data race by construction: `ValuesCollection` is immutable and stateless, racing instances are indistinguishable to the caller; the field mirrors the `ConcurrentHashMap` view-cache pattern exactly (CHM's own view fields are plain `transient`). The rationale is pinned in the field's Javadoc with an explicit ban on adding `volatile`.
