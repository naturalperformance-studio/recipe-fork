# The account's creation gives the owner, not the sending

**Status:** accepted — completes 20260929-the-recipe-that-created-the-account; corrects FLOW-059 (1), ACT-002 (3); adds FLOW-059 (12).

**Context:** a person with no account active sends a recipe from the pool; an
account is created for it. The ADR of 2026-09-29 made the recipe that account's
"and sent", reasoning that the person's act is carried out as asked. On two paths
nothing is sent: the account is created muted (AUTH-004), or the server refuses
the sending that follows creation (FLOW-003). FLOW-059 (1) and ACT-002 (3) tied
ownership to the sending, AUTH-026 (3, 4) and ACT-002's statement to the creation,
and T-026 could not be built until one was chosen (SD-042, 2026-09-29).

**Decision:** the recipe becomes the account's the moment the account created for
it exists, whether or not it is then sent. On the two paths above it sits in the
account's recipe book, marked never sent, and the send control is offered on it
again once the account can send — at once after a refusal, once a muted account
becomes full (AUTH-005 (5)).

**Consequences:** the pool offer's statement "this recipe is already in the
account" is true on every path, and the person finds the recipe where they went to
send it. The ADR of 2026-09-29 keeps its decision; its reason changes from "the act
is carried out" to "the account was made for this recipe", because the act is not
always carried out. Ownership is now fixed for a recipe the server never received,
as it already is for every recipe written while an account is active.

**Alternatives considered:**
- *The recipe becomes the account's only once the server stores it; otherwise it
  stays in the pool.* Rejected: the person lands in a new, empty account with their
  recipe back in the pool; the pool is shown only with no account active, and
  sending from there offers to create an account again — a second account and a
  second device slot for one recipe. The pool offer would also need a second text.
