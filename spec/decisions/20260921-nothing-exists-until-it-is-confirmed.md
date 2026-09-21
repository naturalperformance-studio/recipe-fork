# Nothing exists until the person confirms they kept the keys

**Status:** accepted; completes AUTH-007

**Context:** Account creation is the one screen that hands over something
irreplaceable. AUTH-007 shows the account key and refuses to complete creation
until the person confirms they saved it; AUTH-020 does the same for the recovery
key as its own artefact; LEG-004 requires the terms to be accepted there too.
All three describe what must happen before creation completes, and none said
what exists if the person simply leaves — while the server must already have
generated a nickname unique across every account that ever existed (DOM-001) and
three keys (AUTH-014) in order to show them.

**Decision:** nothing. Until every confirmation the screen requires has been
given, no account exists: the nickname is not consumed, no device slot is
occupied, and the keys that were displayed match nothing afterwards.

**Consequences:**
- A person who abandons creation half-way costs the product nothing and costs
  themselves nothing. Since the slot frees only after six months (AUTH-002) and a
  nickname is never reissued (DOM-001), the opposite rule would have made an
  interrupted screen permanently expensive.
- Someone who copies the key and leaves before confirming holds a string that
  opens nothing. That is stated in the requirement rather than left to be
  discovered, because it is the one surprising consequence of this rule.
- The server generates a nickname and three keys that it may then discard. They
  are cheap to generate and the collision rule (DOM-001) is unaffected by
  discarding them.

**Alternatives considered:**
- *Create the account when the keys are issued.* Rejected: every abandoned screen
  would eat a nickname for ever and a device slot for six months, and a person
  who tapped twice and changed their mind would be locked out of their own phone
  for half a year.
- *Create it, and delete it if the screen is abandoned.* Rejected as the same
  thing with a timer: it needs a rule for how long to wait, a sweep that deletes
  accounts by age which RET-006 forbids, and a decision about a person who
  returns after the sweep.
