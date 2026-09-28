# The recipe that created the account is the account's

**Status:** accepted — widens ACT-002, corrects AUTH-026 (3) and FLOW-059 (1).

**Context:** a person with no account active sends a recipe from the pool. Account
creation is offered, the account is created, and AUTH-026 then offers to move the
whole pool into it. FLOW-059 (1) had the recipe sent and made the account's; on a
declined offer AUTH-026 (3) and ACT-002 left it in the pool, owned by nobody and
unsendable by that account. The two meet only on this path, and T-026 could not be
built without deciding which holds (SD-040, 2026-09-28).

**Decision:** the recipe whose sending created the account becomes that account's
and is sent, whatever the answer to the pool offer. The offer covers the rest of
the pool and says that this recipe is already in the account. ACT-002 gains this
as a second, narrow way for a pool recipe to get its owner; there is still no
action anywhere that changes an owner.

**Consequences:** the person's explicit act — send this recipe — is carried out
as asked, and a question about the rest of the pool cannot undo it. The cost is
that "a pool recipe gets its owner only when the whole pool moves" stops being the
single rule, and the offer's text has one more thing to say.

**Alternatives considered:**
- *Show the pool offer before sending; declining it cancels the sending.* Rejected:
  it undoes what the person asked for because of their answer to a question about
  something else.
- *No offer on this path; the whole pool moves into the new account.* Rejected: it
  silently takes what the person did not ask to move, such as recipes others wrote
  on a shared device.
