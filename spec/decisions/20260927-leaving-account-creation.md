# Leaving account creation, and where the recovery key stands in it

**Status:** accepted, completes AUTH-007 and AUTH-020

**Context:** The creation screen could not be left "by an ordinary back action",
while an abandoned creation was meant to cost nothing, and no way out was stated
(SD-009). The recovery key had a presumed offer to be saved and no stated place
(SD-007).

**Decision:**
- The creation flow has its own cancel control; the system back action asks the
  person to confirm leaving without an account. Either leaves on confirmation,
  back to where the person was, with nothing created (AUTH-007 (10)).
- The recovery key is shown on its own step after the account key is confirmed;
  back from it returns to the account key step; it can be copied and is never
  offered as a file (AUTH-020).

**Consequences:** A person who opened creation by mistake leaves in two taps. The
account key step is revisitable until creation completes, which costs nothing
because nothing yet exists. The recovery key cannot land beside the account key
file by the product's doing; it can still land in the same password manager, and
the screen's statement is all that argues against it. Taking it back in (AUTH-021)
is by text only.

**Alternatives considered:**
- *Only the system back action with a question, no own control.* Rejected: a
  person who does not know the gesture has no visible way out.
- *Only an own control, back swallowed.* Rejected: a gesture that silently does
  nothing reads as a hang.
- *The recovery key as display only.* Rejected: some fifty characters copied by
  hand are copied wrong.
- *A file of its own.* Rejected: it would likely land in the same folder as the
  account key file.
- *The recovery key on the account key's screen, or a step with no way back.*
  Rejected in favour of a separate step with an ordinary back.
