# The favourites section opens on the marked recipes, and a device with no account has only them

**Status:** accepted, completes FLOW-034

**Context:** The section has two views, and nothing said which one it opens on
before the reader chooses, or whether a device with no account — which owns
nothing — sees the own-recipes view and the switch (SD-004).

**Decision:** Before the reader has chosen in a session, the section opens on the
marked recipes. A device with no account shows that view alone, with no switch;
the own-recipes view and the switch appear with an account (FLOW-034, ACT
permissions table).

**Consequences:** The first screen never depends on what the account holds. An
author reaches their own recipes with one switch. A person with no account is not
shown an empty view whose only purpose would be to offer an account.

**Alternatives considered:**
- *Own recipes first when there are any.* Rejected: the first screen would change
  with the account's contents.
- *Own recipes always.* Rejected: most people who open favourites are readers.
- *Both views for a device with no account.* Rejected: an empty view that exists
  to sell an account.
