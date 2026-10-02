# A session is ended by the reader

**Status:** accepted, adds FLOW-058; completes CAT-001, FLOW-017, FLOW-033 and FLOW-034

**Context:** CAT-001, FLOW-033 and FLOW-034 keep a reader's choice "within the same
session", and FLOW-034 (6) opens the favourites section on the marked view in a
session with no choice yet. The word was defined nowhere, and the specification
already treats leaving the application, the operating system ending it and the
reader closing it as three different events (SD-028).

**Decision:** a session lasts from a start of the application that has nothing to
restore until the next such start. Only the reader removing the application from
the recent applications or a restart of the device ends one. The operating
system ending the process while the application is away does not (FLOW-058).

**Consequences:** the line agrees with what the specification already decided.
FLOW-012 restores the recipe after the system ends the process, and FLOW-017
already reset the card's count on a "fresh start". A reader never sees a choice
vanish because of something they did not do. The device holds the chosen
ordering, filter and view for as long as a restored process might need them, the
same kind of state RET-010 already allows for the card.

**Alternatives considered:**
- *A session is one life of the process.* Rejected: the card would come back after
  the system ended the process while the favourites view reset, and the reader
  cannot see when that happens, so the reset would look random.
- *No sessions; every choice is kept for good.* Rejected: it undoes the decision
  of SD-004 that the section opens on the marked recipes, and it keeps more state
  on the device than any requirement needs.
