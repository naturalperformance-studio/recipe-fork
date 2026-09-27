# Synchronisation: the server is authoritative, the device keeps an outbox

**Status:** accepted
**Date:** 2026-09-27
**Requirements:** FLOW-002, FLOW-003, FLOW-004, FLOW-005, FLOW-006, FLOW-007, FLOW-008, ST-009, AUTH-012, AUTH-013

**Context:** The owner edits offline and changes reach the server later (FLOW-002). Some acts are refused offline and queue nothing (FLOW-003). Each recipe shows whether the server has accepted its changes (FLOW-005). A fork is never sent before its parent (FLOW-007). Two devices that diverge produce a fork, never an overwrite (FLOW-008). The server orders every transition (ST-009).

**Decision:**
- The server is the authority for every recipe's state and content.
- The device holds its own copy in Room and an outbox of pending operations, drained in order by WorkManager whenever a connection exists. An operation that depends on another (a fork on its parent, media on its recipe) is not sent before it.
- Every recipe carries a server revision number. The device sends the revision its change was based on; if the server's revision differs, the server keeps its version and stores the device's version as a private fork of it owned by the same account, and says so in the response.
- "Accepted by the server" for a recipe means its outbox holds nothing for it.
- Acts that FLOW-003 refuses are never written to the outbox.

**Consequences:**
- The per-recipe marking of FLOW-005 and the list of unsent recipes of AUTH-013 are both a query over the outbox.
- No merge logic exists anywhere.

**Alternatives considered:**
- *CRDTs or field-level merge.* Rejected: the specification requires divergence to produce a fork (FLOW-008).
- *Last write wins.* Rejected: it discards an edit, which FLOW-008 forbids.
