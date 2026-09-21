---
slug: AUTH
status: closed
updated: 2026-09-21
ids: [AUTH-001, AUTH-002, AUTH-003, AUTH-004, AUTH-005, AUTH-006, AUTH-018]
---

# Creating an account

When an account appears, what a device is allowed to create, the nickname it is
given, and what happens when the device cannot be attested at all.

### AUTH-001 — An account appears when an action needs one

When a person without an account performs an action that requires one — writing
a recipe, forking a recipe, rating a recipe, or choosing to carry favourites
beyond this device — the system shall offer to create an account, and shall
create it only where the device verdict reports a free slot.

**Acceptance criteria**
- Given a person with no account, when they browse, search, open a recipe, cook
  from it, run its timers, keep favourites on the device or report something,
  then no account is created and none is asked for.
- Given a person with no account, when they use a control that needs one, then
  account creation is offered, and the action they asked for proceeds once the
  account exists.
- Given account creation, when it succeeds, then one slot on the device is
  occupied with the month it was occupied in.
- Given a device with no free slot, when creation is attempted, then no account
  is created and AUTH-003 governs what the person is told.
- Given a verdict that cannot be obtained while the server can be reached, when
  creation is attempted, then AUTH-004 governs what is created.
- Given no network connection at all, when creation is attempted, then nothing is
  created and FLOW-003 states that a connection is needed.
- Given a person who declines the offer, when they return to what they were
  doing, then everything that works without an account still works.

**Verification:** walk every account-less path on a clean install; then trigger
creation from each of the four actions that require it.
**Source:** user decision, 2026-09-20. An account exists for the people who
publish and for the reader who wants favourites to outlive the device; a person
who only cooks mints nothing, consumes no nickname and occupies no slot. See
[ADR — an account is created on demand](../decisions/20260920-account-on-demand.md).

### AUTH-018 — The nickname is generated and may be rerolled once, before the account exists

When account creation is offered, the system shall show a generated three-word
nickname, shall allow the person to replace it with another generated one any
number of times before the account is created, and shall fix it permanently once
it is.

**Acceptance criteria**
- Given the creation screen, when it opens, then a generated nickname is already
  shown and nothing is typed by the person (CON-001).
- Given a shown nickname, when the person asks for another, then a different
  generated nickname replaces it.
- Given a nickname the person passed over, when the account is created, then only
  the accepted nickname is consumed and the others stay available to others.
- Given a created account, when its nickname is looked for in any interface, then
  no control changes it (DOM-001).
- Given two accounts created at the same moment, when their nicknames are
  compared, then they differ.

**Verification:** reroll several times, create the account, and confirm both the
permanence of the accepted name and the availability of the rejected ones.
**Source:** user decision, 2026-09-20. The nickname is the only public
representation of a user, so the person is given a say in it at the one moment
that costs nothing; after creation it is permanent, because other people's
recipes name it as a parent's author.

### AUTH-002 — A device has three slots and a slot frees after six months

The system shall treat a device as holding three account slots, and shall treat
an occupied slot as free again once six or more calendar months have passed
since the month in which it was occupied.

**Acceptance criteria**
- Given a device on which no account has ever been created, when its slots are
  read, then three are free.
- Given a slot occupied in March 2026, when the month is September 2026 or
  later, then that slot is free.
- Given a slot occupied in March 2026, when the month is August 2026, then that
  slot is not free.
- Given three slots occupied in the same month, when six months have passed,
  then all three are free at once.
- Given a slot that frees, when an account is created into it, then the account
  that occupied it before is neither deleted nor affected in any way.

**Verification:** occupy all three slots, move the device clock forward month by
month, and confirm the month each slot frees.
**Source:** user decision, 2026-09-20. Six months rather than never, because a
device is resold and handed on, and because a person who loses their key must be
able to start again on the phone they own. The slot is about the rate at which
one device can mint accounts, not about how many accounts a person may have.

### AUTH-003 — A device with no free slot says when it will have one

If no slot on the device is free, then the system shall state that this device
has created its accounts recently, shall state the month in which the next slot
frees, and shall offer to sign in with an existing account key.

**Acceptance criteria**
- Given all three slots occupied, when creation is attempted, then the message
  names the month the earliest slot frees and offers to sign in instead.
- Given that screen, when the person signs in with a key they hold, then they are
  signed in and no slot is consumed.
- Given that screen, when the person does nothing, then everything that works
  without an account still works.
- Given that screen, when the stated month arrives, then the same action creates
  an account without any further obstacle.

**Verification:** fill the slots, attempt creation, read the message, then sign
in from that screen.
**Source:** user decision, 2026-09-20. The month is stated rather than withheld
because CON-004 requires automation to explain itself; a wall with no date reads
as a defect.

### AUTH-004 — Without a verdict the account is created muted

If the server can be reached but a device verdict cannot be obtained, then the
system shall create a muted account: one that carries favourites beyond the
device and does nothing else that an account is for. A muted account shall not
write, fork, edit, submit or rate.

**Acceptance criteria**
- Given no verdict, when an account is created, then favourites carried by that
  account work exactly as they do for a full account.
- Given no network connection at all, when an account is asked for, then nothing
  is created, not even a muted account, and FLOW-003 states that a connection is
  needed: a muted account is still an account, with a unique nickname (DOM-001)
  and three keys the server issues (AUTH-014).
- Given a muted account, when the recipe card of a published recipe is opened,
  then the fork control and the rating controls are absent.
- Given a muted account, when the editor would be reached, then it is not
  offered and no recipe can be created.
- Given a muted account, when it was created because the person asked to fork or
  to rate, then AUTH-006 states at that moment that the action cannot proceed
  and why.
- Given a muted account, when it is created, then no slot on the device is
  occupied.
- Given any person with or without an account, when they report a recipe or an
  author, then the report is recorded and delivered as `abuse` requires.

**Verification:** attempt each of the four triggering actions with attestation
blocked, and walk every control named here.
**Source:** user decision, 2026-09-20. Refusing to create anything was rejected:
an outage at the attestation service would then block the one thing a reader
might legitimately want — favourites that survive the phone. Creating a full
account was rejected: the gate would be worth nothing, since a sideloaded build
obtains no verdict by construction.

### AUTH-005 — A muted account becomes full when a verdict and a slot allow it

When a muted account attempts an action that AUTH-004 withholds, the system shall
obtain a device verdict, and shall make the account full and occupy a slot if the
verdict reports one free.

**Acceptance criteria**
- Given a muted account created while the attestation service was unreachable,
  when the service can be reached again and the person taps the fork control,
  then a verdict is obtained, a slot is occupied, the account becomes full and
  the fork proceeds.
- Given a muted account on a device with no free slot, when it attempts such an
  action, then it stays muted and AUTH-003 states the month a slot frees.
- Given a muted account whose verdict still cannot be obtained, when it attempts
  such an action, then it stays muted and AUTH-006 states why.
- Given an account that has become full, when it is used afterwards, then no
  further verdict is requested for it.

**Verification:** create a muted account offline, restore the connection and
attempt a fork; repeat on a device with its slots full.
**Source:** user decision, 2026-09-20. The verdict is asked for at the moment it
is needed rather than on every launch, which keeps the application independent of
an external service and keeps requests within the free quota it offers.

### AUTH-006 — A muted account is told what it is and what lifts it

While an account is muted, the system shall state that it is limited, shall state
which actions are withheld, and shall state what would lift the limit.

**Acceptance criteria**
- Given a muted account, when a withheld control would have been shown, then a
  statement takes its place rather than the control failing silently.
- Given that statement, when it is read, then it says the device could not be
  checked and that a connection and an installation from the store lift the
  limit.
- Given a muted account on a device with no free slot, when the statement is
  shown, then it names the month a slot frees instead (AUTH-003).
- Given a full account, when any screen is shown, then no such statement appears
  anywhere.

**Verification:** read every statement a muted account can reach, in both the no
verdict and the no free slot case.
**Source:** CON-004 — automation that changes what a user may do explains itself.
