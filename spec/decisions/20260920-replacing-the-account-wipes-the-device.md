# Replacing the account wipes what the device held

**Status:** accepted

**Context:** the device holds every recipe the account owns, in every state,
including drafts and changes the server has not accepted (FLOW-038, FLOW-005).
Nothing said what becomes of that when the account on the device changes. The
product owner found the gap on 2026-09-20 while the device gate was being
decided, which makes the case ordinary rather than exotic: a person who loses
their key file creates a new account on the same phone.

Two claims were in tension. Draft section 2.3 promises that unpublished recipes
are lost for good when the key file is lost — but on that very phone they are
still sitting in local storage. If the next account can read them, the promise
is false and a resold phone leaks its previous owner's drafts. If it cannot, the
promise is true and the data occupies the device forever.

**Decision:** a device holds exactly one active account (AUTH-011). Importing a
key file replaces the active account and deletes everything the previous one held
on the device (AUTH-012), after a statement that names each recipe holding
changes the server has not accepted and that requires explicit confirmation
(AUTH-013). Recipes the server had accepted come back if the original key file is
imported again; unsent changes and queued media do not.

**Consequences:**
- The product's own promise becomes true: what was never sent is genuinely gone.
- A resold or handed-on device carries nothing of its previous holder.
- Importing the wrong key file destroys unsent drafts. FLOW-005 already knows
  which recipes those are, so the statement names them instead of warning in the
  abstract — that naming is the whole protection, and it is why the statement
  cannot be switched off.
- Multi-account use is not possible at all: a household sharing one tablet must
  re-import a key file each time, and each re-import wipes the other person's
  unsent work. Accepted for the MVP and deferred as OQ-028.

**Alternatives considered:**
- *Keep the previous account's data, unreadable to the new account, and restore it
  if that key file returns.* Rejected: the storage is occupied indefinitely, and
  the spec would have to state honestly that lost recipes are not lost but sealed
  — which invites exactly the recovery request NGOAL-003 refuses.
- *Keep it for a number of days, then wipe.* Rejected as a third state to build and
  explain, for a mistake — importing the wrong file — that the naming statement of
  AUTH-013 already makes hard to commit.
- *Several accounts live on one device with a switch.* Rejected for the MVP by the
  product owner: favourites, held media and the storage setting of FLOW-011 all
  multiply by the number of accounts, and the first release is the fork mechanic,
  not account management.
