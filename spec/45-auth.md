---
slug: AUTH
status: blocked
updated: 2026-09-20
ids: [AUTH-001, AUTH-002, AUTH-003, AUTH-004, AUTH-005, AUTH-006, AUTH-007,
  AUTH-008, AUTH-009, AUTH-010, AUTH-011, AUTH-012, AUTH-013, AUTH-014,
  AUTH-015, AUTH-016]
---

# Accounts, the key file and the device gate

An account carries no personal data (CON-001) and is held by a key file the user
stores themselves. Nothing here recovers an account: NGOAL-003 is a refusal, not
a missing feature.

Two things gate an account rather than identify its holder. The first is the key
file: whoever holds it holds the account. The second, decided 2026-09-20, is the
device: creating an account costs a slot on the device it is created from, and a
device has three. This exists because an account was otherwise free, and a free
account meant an unbounded number of submissions into a queue one moderator
works (ABUSE-001 caps submissions per account, not per person). See
[ADR — account creation is gated by the device](decisions/20260920-account-creation-gated-by-the-device.md).

The gate is an attestation service: the client obtains a verdict, the server
checks it, and the service holds a few bits of state per device that survive
reinstalling the application and resetting the device. The product never learns
a device identifier and stores no part of the verdict (AUTH-015). Which service
provides this, and its interface, belong to
[`external-interfaces`](130-external-interfaces.md); what the product does with
the answer is here.

Whether obtaining such a verdict makes the product an operator of personal data
under the product owner's own law is OQ-029 and is not answered here.

**Not in this section:** what the key file contains and how the server tells a
genuine one from a forgery — that is OQ-002, still open, which is why this
section is `blocked` and AUTH-014 carries no behaviour yet.

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

## The key file

### AUTH-007 — The key file is issued with a warning that must be acknowledged

When an account is created, the system shall offer its key file for download and
shall display an irreversibility warning that the user must explicitly
acknowledge before the download begins.

**Acceptance criteria**
- Given a newly created account, when the key file is offered, then the download
  control stays disabled until the acknowledgement control is chosen.
- Given the acknowledgement, when the user downloads the key file, then the
  warning text stays visible on the screen.
- Given the warning, when it is read, then it states that losing the file loses
  the account, that there is no recovery, and that nobody can restore it.
- Given a user who leaves without downloading, when they next open the
  application, then the key file is offered again on the same terms.
- Given the warning, when any setting is searched for, then none switches it off
  (GUIDE-001 governs the same pattern for submission).

**Verification:** manual UI check on a fresh account, including leaving the
screen and returning.
**Source:** draft section 2.2; user decision, 2026-09-20.

### AUTH-008 — The product neither stores the key file nor recovers an account

The system shall offer no storage of the key file, no backup of it and no path
that restores access to an account whose key file is lost.

**Acceptance criteria**
- Given any screen, when it is enumerated, then none offers to keep, back up or
  mail the key file.
- Given a lost key file, when the user looks for recovery, then the product
  states there is none and names what NGOAL-003 costs them.
- Given a support channel of any kind, when it is enumerated, then it can restore
  no account, because the product holds nothing that could.

**Verification:** interface review against this list.
**Source:** draft section 2.2; NGOAL-003.

### AUTH-009 — Importing a key file signs in and costs no slot

When the user imports a key file, the system shall sign in to the account that
file holds, and shall neither consume nor require a free slot on that device.

**Acceptance criteria**
- Given a second device, when the key file is imported, then the same account is
  active on both and both hold its recipes (FLOW-038).
- Given a device with all three slots occupied, when a key file is imported, then
  the import succeeds.
- Given a key file the product cannot read or that names no account, when it is
  imported, then the import is refused, the reason is stated, and the account
  that was active stays active.
- Given the same key file imported twice in succession, when the second import
  happens, then nothing changes and no data is wiped.

**Verification:** import on a fresh device, on a device with slots full, twice in
a row, and with a damaged file.
**Source:** derived from the slot decision of 2026-09-20 and from draft section
2.2: a slot limits how many accounts a device can mint, and an import mints
none. Without this, a family sharing one tablet could not sign in to accounts
they already hold. Flagged to the product owner as the spec author's inference
rather than a stated decision.

### AUTH-010 — Importing a key file requires a connection

If the device has no network connection, then the system shall refuse to import a
key file and shall state that a connection is needed.

**Acceptance criteria**
- Given no connection, when the user imports a key file, then the import is
  refused, the reason is stated, and the active account is unchanged.
- Given the connection returns, when the user imports again, then it proceeds.

**Verification:** import in aeroplane mode, then with a connection.
**Source:** the server decides whether a key file holds a real account, and that
test is OQ-002; until it can run, the client has accepted nothing. Consistent
with FLOW-003, which refuses every action whose answer only the server holds.
**Blocked by:** OQ-002.

## One account at a time

### AUTH-011 — One account is active on a device

The system shall hold exactly one active account on a device, and shall offer no
means of holding several accounts at once or switching between them.

**Acceptance criteria**
- Given an active account, when a key file is imported, then the imported account
  replaces it and the previous one is no longer reachable on that device.
- Given any screen, when it is enumerated, then none lists accounts or offers a
  choice between them.
- Given a device whose account was replaced, when the previous key file is
  imported again, then that account becomes active again and the current one is
  replaced in turn.

**Verification:** import two key files in sequence and inspect every screen for
an account list.
**Source:** user decision, 2026-09-20: the MVP is the fork mechanic done well,
and multi-account switching multiplies favourites, held media and the storage
setting of FLOW-011 by the number of accounts. Deferred as OQ-028.

### AUTH-012 — Replacing the account wipes what the device held

When the active account is replaced, the system shall delete from the device
every recipe, draft and media item the previous account held.

**Acceptance criteria**
- Given the previous account's drafts, when the account is replaced, then they
  are gone from the device and no screen reaches them.
- Given a recipe the server had accepted, when the previous key file is imported
  again, then that recipe is fetched from the server and is readable once more.
- Given a recipe holding changes the server had not accepted (FLOW-005), when the
  account is replaced, then those changes are gone permanently and importing the
  key file again does not bring them back.
- Given media queued for upload (FLOW-004), when the account is replaced, then it
  is deleted with the rest.
- Given a device sold or handed on, when its new holder creates or imports an
  account, then nothing of the previous account's content is present on it.

**Verification:** author unsent drafts, replace the account, import the original
key file again, and compare what returns against what was accepted.
**Source:** user decision, 2026-09-20. See
[ADR — replacing the account wipes the device](decisions/20260920-replacing-the-account-wipes-the-device.md).

### AUTH-013 — The warning before replacing names what will be lost

When the user asks to replace the active account, the system shall state what
will be deleted, shall name each recipe holding changes the server has not
accepted, and shall require an explicit confirmation before anything is deleted.

**Acceptance criteria**
- Given three recipes with unsent changes, when the replacement is confirmed,
  then all three were named on the screen that asked for confirmation.
- Given an account whose every change the server holds, when the replacement is
  asked for, then the statement says nothing will be lost and still asks for
  confirmation.
- Given the confirmation control, when any setting is searched for, then none
  switches this statement off (GUIDE-001 governs the same pattern).
- Given the user declines, when they return to the application, then the active
  account and everything it holds are untouched.

**Verification:** replace an account with unsent drafts present and absent, and
read both statements.
**Source:** user decision, 2026-09-20. FLOW-005 already marks exactly which
recipes hold changes the server has not accepted, so the product can name them
instead of warning in general terms.

## Losing the key file

### AUTH-014 — What the server accepts as proof of ownership

Not yet written: the key file's format, the cryptography behind it and the test
the server applies are OQ-002, classified define before MVP. No behaviour is
specified here, because a requirement written before that decision would be an
invented one.

**Blocked by:** OQ-002.

### AUTH-015 — Nothing from the device verdict is kept

The system shall keep no part of a device verdict beyond the decision it
produced, and shall store no device identifier of any kind.

**Acceptance criteria**
- Given a verdict received and checked, when the stored record of the account is
  enumerated, then it holds no field from that verdict.
- Given the stored data of any account, when it is enumerated, then it holds
  nothing that identifies a device (CON-001).
- Given the attestation service, when the slot state is needed, then it is read
  from the service and not from a copy the product keeps.

**Verification:** data model review against a live account created through the
gate.
**Source:** user decision, 2026-09-20: the gate was chosen over an identity
provider precisely because it leaves the product with nothing personal to hold,
and a stored verdict would give that back. See OQ-029.

### AUTH-016 — Losing the key file keeps the published work in the world

Where an account's key file is lost, the system shall keep that account's
published recipes in the catalogue, reachable by everyone including the person
who wrote them, and shall allow that person to fork such a recipe from a new
account.

**Acceptance criteria**
- Given a lost key file, when the former author searches the catalogue, then
  their published recipes are found and open normally.
- Given a new account on the same device, when the slot rule allows it to exist
  (AUTH-002), then it may fork those recipes like any other account.
- Given such a fork, when it is created, then its author is the new account and
  the fork names the published recipe as its parent (FORK-006).
- Given the lost account's unpublished recipes, when anything is attempted, then
  they are reachable by nobody, on the server or on any device (AUTH-012).

**Verification:** publish, discard the key file, create a new account and fork the
published recipe.
**Source:** draft section 2.3; user decision, 2026-09-20.
