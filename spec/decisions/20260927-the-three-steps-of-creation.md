# The three steps of creation

**Status:** accepted, writes AUTH-024; completes AUTH-007, AUTH-018 and LEG-004

**Context:** Account creation had a recovery key step of its own (AUTH-020) and
otherwise spoke of one "creation screen". Where the nickname and its reroll
(AUTH-018) and the acceptance of the terms (LEG-004) stand was stated nowhere, nor
what the system back action does once there is a step before the account key's,
nor what leaving the application mid-creation does. The re-check of SD-009 found
the last point open; the others follow from it.

**Decision:**
- Step 1: the nickname with its reroll, and the terms with their acceptance; it is
  not left forward until the terms are accepted. Step 2: the account key and the
  deletion key. Step 3: the recovery key; confirming it completes creation.
- The system back action goes to the previous step with everything as it was left;
  a reroll on step 1 does not change the keys. Only back on step 1 asks before
  leaving creation. Every step carries the cancel control.
- Leaving the application while its process lives returns to the same step with
  the same content. If the operating system ends the process, creation is not
  resumed and nothing of it exists.

**Consequences:** The person agrees to the terms before being handed any secret,
and the nickname is chosen where no key stands beside it. The keys are stable
across the whole walk, so a key copied on step 2 stays valid if the person goes
back to change the nickname. A person who switches to a password manager keeps
their place unless the system ends the application meanwhile, in which case the
key they copied matches nothing (AUTH-007 (8)).

**Alternatives considered:**
- *Nickname with the account key on one step, terms last.* Rejected: the reroll
  would stand beside the keys, and the terms would be accepted after the secrets
  had been shown.
- *Back on step 2 asks to leave creation.* Rejected: changing the nickname would
  cost starting over, and back would behave differently on step 2 and step 3.
- *Any departure from the application abandons creation.* Rejected: switching to
  a password manager to store the key is the expected use, and it would lose the
  key the person had just copied.
