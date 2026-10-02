# A recipe the server no longer holds is sent as new

**Status:** accepted — widens FLOW-059 (statement); adds FLOW-059 (13); qualifies ST-010 (2).

**Context:** two paths bring the server a sending, naming a revision it once gave,
for a recipe it no longer holds: a restore from the hourly copy taken before the
recipe was first sent (QUAL-008), and a deletion on another device of the account
before this device learned of it (ST-010 (2)). FLOW-059 covered a first sending and
a replacement, and this is neither (SD-045, 2026-09-30).

**Decision:** the server creates the recipe again in `private`, with the same
identity, owned by the sending account, as on a first sending.

**Consequences:** nothing is stuck on a device after a restore the owner had no part
in, and the server keeps no record of deleted recipes. The cost: a recipe deleted on
device A comes back when device B, not yet told of the deletion, sends it. Both acts
are the owner's, and the later one wins.

**Alternatives considered:**
- *Refuse the sending.* Rejected: after a restore the recipe could never be sent and
  so never submitted (FLOW-006); the only way out would be forking one's own recipe.
- *Keep the identities of deleted recipes: refuse a deleted one and remove it from
  the device, create a lost one again.* Rejected: a new stored record against
  ST-010's promise that nothing of a deleted recipe remains, for a case both acts of
  which are the owner's.
