---
slug: AUTH
status: blocked
updated: 2026-09-20
ids: [AUTH-001, AUTH-002, AUTH-003, AUTH-004, AUTH-005, AUTH-006]
---

# The device gate

What first launch creates, what a device is allowed to create, and what the
product does when the device cannot be attested at all.

## Creating an account

### AUTH-001 — An account is created only where the device has a free slot

When the application is launched and no account is active on the device, the
system shall obtain a device verdict and shall create an account only if that
verdict reports a free slot on the device.

**Acceptance criteria**
- Given a device that has never created an account, when the application is
  first launched, then an account is created and the user is signed in to it
  without entering anything (ACT-001).
- Given that creation, when it succeeds, then one slot on the device is marked
  as occupied with the month it was occupied in.
- Given a device with no free slot, when the application is launched, then no
  account is created and AUTH-003 governs what the user is told.
- Given a verdict that cannot be obtained, when the application is launched,
  then AUTH-004 governs what is created.
- Given an account created on one device, when the same key file is imported on
  another device, then no slot is consumed there (AUTH-009).

**Verification:** first launch on a clean device; first launch on a device whose
slots are all occupied; first launch with the attestation service unreachable.
**Source:** user decision, 2026-09-20.

### AUTH-002 — A device has three slots and a slot frees after six months

The system shall treat a device as holding three account slots, and shall treat
an occupied slot as free again once six or more calendar months have passed
since the month in which it was occupied.

**Acceptance criteria**
- Given a device with no account ever created, when its slots are read, then
  three are free.
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
device is resold and handed on, and because a person who loses their key file
must be able to start again on the phone they own. The slot is about the rate at
which one device can mint accounts, not about how many accounts a person may
have.

### AUTH-003 — A device with no free slot says when it will have one

If no slot on the device is free, then the system shall state that this device
has created its accounts recently, shall state the month in which the next slot
frees, and shall offer to import an existing key file.

**Acceptance criteria**
- Given all three slots occupied, when the application is launched, then the
  message names the month the earliest slot frees and offers key file import.
- Given that screen, when the user imports a valid key file, then they are signed
  in and no slot is consumed.
- Given that screen, when the user does nothing, then no account exists and no
  part of the application that needs one is offered.
- Given that screen, when the stated month arrives, then launching the
  application creates an account without any further action.

**Verification:** fill the slots, relaunch, read the message, then import a key
file from that screen.
**Source:** user decision, 2026-09-20. The month is stated rather than withheld
because CON-004 requires automation to explain itself; a wall with no date reads
as a defect.

## When the device cannot be attested

### AUTH-004 — Without a verdict the account is created muted

If a device verdict cannot be obtained, then the system shall create a muted
account: one that may browse the catalogue, search, open and cook recipes, run
timers and keep favourites, and that may not create a recipe, fork a recipe, open
the editor, submit anything for moderation, or rate anything.

**Acceptance criteria**
- Given a first launch with no network connection, when the verdict cannot be
  obtained, then a muted account is created and the catalogue states it needs a
  connection (FLOW-003), while the application itself opens.
- Given a muted account, when the recipe card of a published recipe is opened,
  then the fork control and the rating controls are absent and the favourite
  control is present.
- Given a muted account, when the own-recipes view would be reached, then no
  editor is offered and no recipe can be created.
- Given a muted account, when it reports a recipe or an author, then the report
  is recorded and delivered exactly as ABUSE-002 and ABUSE-004 require.
- Given a muted account, when it is created, then no slot on the device is
  occupied.

**Verification:** launch on a device with no attestation available and walk every
control named here; repeat in aeroplane mode on first launch.
**Source:** user decision, 2026-09-20. Refusing to start at all was rejected: an
outage at the attestation service would then be a dead application for every new
user. Creating a full account was rejected: the gate would be worth nothing,
since a sideloaded build obtains no verdict by construction. Reporting stays
available because CON-006 obliges the product to offer it, and ABUSE-003 already
prevents a report from an account that has published nothing from hiding
anything by itself.

### AUTH-005 — A muted account becomes full when a verdict and a slot allow it

When a muted account attempts an action that AUTH-004 withholds, the system shall
obtain a device verdict, and shall make the account full and occupy a slot if the
verdict reports one free.

**Acceptance criteria**
- Given a muted account created offline, when a connection returns and the user
  taps the fork control, then a verdict is obtained, a slot is occupied, the
  account becomes full and the fork proceeds.
- Given a muted account on a device with no free slot, when it attempts such an
  action, then it stays muted and AUTH-003 states the month a slot frees.
- Given a muted account whose verdict still cannot be obtained, when it attempts
  such an action, then it stays muted and AUTH-006 states why.
- Given an account that has become full, when it is used afterwards, then no
  further verdict is requested for it.

**Verification:** create a muted account offline, restore the connection and
attempt a fork; repeat on a device with its slots full.
**Source:** user decision, 2026-09-20. The verdict is asked for at the moment it
is needed rather than on every launch, which keeps the first screen independent
of an external service and keeps requests within the free quota the service
offers.

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

