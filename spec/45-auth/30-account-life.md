---
slug: AUTH
status: closed
updated: 2026-09-21
ids: [AUTH-011, AUTH-012, AUTH-013, AUTH-014, AUTH-015, AUTH-016, AUTH-020,
  AUTH-021]
---

# One account at a time, and losing it

What a device holds, what replacing the active account destroys, and what
survives an account whose key is gone.

## One account at a time

### AUTH-011 — One account is active on a device

The system shall hold at most one active account on a device, and shall offer no
means of holding several accounts at once or switching between them.

**Acceptance criteria**
- Given an active account, when another account's key is entered, then that
  account replaces it and the previous one is no longer reachable on that device.
- Given a device with no account at all, when an account is created or signed in
  to, then it becomes the one active account.
- Given any screen, when it is enumerated, then none lists accounts or offers a
  choice between them.
- Given a device whose account was replaced, when the previous key is entered
  again, then that account becomes active again and the current one is replaced
  in turn.

**Verification:** sign in with two keys in sequence and inspect every screen for
an account list.
**Source:** user decision, 2026-09-20: the MVP is the fork mechanic done well,
and multi-account switching multiplies favourites, held media and the storage
setting of FLOW-011 by the number of accounts. Deferred as OQ-028.

### AUTH-012 — Replacing the account wipes what the device held

When the active account is replaced, the system shall delete from the device
every recipe, draft, media item and favourite the previous account held.

**Acceptance criteria**
- Given the previous account's drafts, when the account is replaced, then they
  are gone from the device and no screen reaches them.
- Given a recipe the server had accepted, when the previous key is entered again,
  then that recipe is fetched from the server and is readable once more, and so
  are the favourites that account carried.
- Given a recipe holding changes the server had not accepted (FLOW-005), when the
  account is replaced, then those changes are gone permanently and importing the
  key again does not bring them back.
- Given media queued for upload (FLOW-004), when the account is replaced, then it
  is deleted with the rest.
- Given a device sold or handed on, when its new holder creates an account or
  signs in, then nothing of the previous account's content is present on it.
- Given favourites kept on a device that had no account, when an account is then
  created, then they are carried into it rather than deleted (FLOW-051).

**Verification:** author unsent drafts, replace the account, enter the original
key again, and compare what returns against what was accepted.
**Source:** user decision, 2026-09-20. See
[ADR — replacing the account wipes the device](../decisions/20260920-replacing-the-account-wipes-the-device.md).

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

## Losing the key

### AUTH-014 — What the server accepts as proof of ownership

The system shall accept an account key as proof of ownership only where it
matches exactly one account, shall hold nothing from which any of an account's
three keys could be reconstructed, and shall be unable to act as any account
without a key presented to it.

**Acceptance criteria**
- Given two accounts, when their keys are compared, then they differ, and no key
  of any of the three kinds is ever issued twice — including keys issued again
  under AUTH-021.
- Given the account key, the deletion key and the recovery key, when each is
  examined, then the properties in this requirement govern all three alike; the
  three differ in what they do and in where they are kept, never in how strong
  they are.
- Given an account key, when its length and randomness are examined, then trying
  keys at the rate QUAL-009 permits cannot find a live one in any practical time.
- Given everything the server stores, when it is examined by someone holding all
  of it, then nothing in it allows them to reconstruct any of an account's keys,
  to act as that account, or to retire its account key.
- Given a key that matches no account, when it is presented, then it is refused
  and the answer discloses nothing beyond that it matches no account.
- Given a key presented on any device, when it is checked, then the check happens
  at the server (AUTH-010) and the client decides nothing about it.
- Given the account key, when it is issued, then it is text the holder can copy
  and keep anywhere (AUTH-008, EXT-008) and it carries the deletion key
  (AUTH-017).

**Verification:** present a forged key and a key of a deleted account; take a
full copy of the stored data and attempt, from it alone, to reconstruct a key or
to act as an account.

**What this requirement deliberately does not say:** the key's format, the
cryptography behind it and the protocol by which the server tests it. Those are
an engineering choice, constrained by the properties above and by nothing else
here. The third criterion is the one that constrains it hardest: a server that
could recreate a key would be a server from which every account could be taken,
and it would quietly turn NGOAL-003 from a refusal into a lie.

**Source:** user decision, 2026-09-21, resolving OQ-002 by separating the
product's requirements on the key from the mechanism that satisfies them.

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

### AUTH-016 — Losing the key keeps the published work in the world

Where an account's key is lost and no device still holds it signed in, the system
shall keep that account's published recipes in the catalogue, reachable by
everyone including the person who wrote them, and shall allow that person to fork
such a recipe from a new account.

**Acceptance criteria**
- Given a lost key, when the former author searches the catalogue, then their
  published recipes are found and open normally.
- Given a new account on the same device, when the slot rule allows it to exist
  (AUTH-002), then it may fork those recipes like any other account.
- Given such a fork, when it is created, then its author is the new account and
  the fork names the published recipe as its parent (FORK-006).
- Given the lost account's unpublished recipes, when anything is attempted, then
  they are reachable by nobody, on the server or on any device (AUTH-012).

**Verification:** publish, discard the key, create a new account and fork the
published recipe.
**Source:** draft section 2.3; user decision, 2026-09-20.

## The recovery key

An account carries three keys, each doing one job. The account key signs in. The
deletion key destroys the account from outside the application (AUTH-017). The
recovery key, below, is the only thing that can retire an account key and issue
a new one.

### AUTH-020 — The recovery key is issued apart from the account key and shown once

When an account is created, the system shall issue a recovery key in addition to
the account key and the deletion key, shall present it as its own artefact rather
than inside the account key, shall display it once in full with its own
confirmation, and shall never display it again.

**Acceptance criteria**
- Given account creation, when the keys are issued, then the recovery key is
  presented on its own, is not contained in the file AUTH-007 offers, and is not
  offered to be saved in the same action as the account key.
- Given that screen, when it is shown, then it states that the recovery key is
  the only way to retire a leaked or stolen account key, that it must be kept
  somewhere other than the account key, and that losing it loses that ability for
  good; and creation does not complete until the person confirms they have saved
  it.
- Given a signed-in account, when its own screen is opened, then the recovery key
  is not there in any form, masked or otherwise — AUTH-019 governs the account
  key and the deletion key and does not reach this one.
- Given anywhere else in the application, when it is searched, then the recovery
  key appears nowhere after the moment it was issued.
- Given the recovery key, when its uniqueness and its resistance to guessing are
  examined, then AUTH-014 governs it exactly as it governs the account key, at
  the rate QUAL-009 permits.

**Verification:** create an account and confirm the recovery key is a separate
artefact; sign in and search every screen for it; inspect the account key file
and confirm it is absent from it.
**Source:** user decision, 2026-09-21. This deliberately contradicts the
reasoning of AUTH-019 for this one key: a key that can never be seen again is
indeed lost with the note it was written on, and that is the price. It is paid
because a recovery key readable from a signed-in session would be readable by
whoever stole the account key, which would leave the product exactly where it was
before the key existed. See
[ADR — the recovery key, and what it does to "no recovery"](../decisions/20260921-the-recovery-key.md).

### AUTH-021 — Using the recovery key retires every key the account had

Where a recovery key is presented, the system shall stop accepting that account's
account key and deletion key, shall sign out every device holding the account,
shall issue a new account key, deletion key and recovery key, and shall display
the new recovery key once.

**Acceptance criteria**
- Given a recovery key, when it is presented, then no account key is required and
  no device needs to be signed in, because the case it exists for is an account
  key in the wrong hands or lost.
- Given the previous account key, deletion key and recovery key, when any of them
  is presented after the replacement, then each matches no account.
- Given every device holding the account, when it next reaches the server, then
  it is signed out and holds nothing of that account afterwards, exactly as
  AUTH-012 leaves a device whose account was replaced — including a device its
  rightful holder is using.
- Given the screen that accepts a recovery key, when it is reached, then it needs
  no account, and it is rate-limited as QUAL-009 requires.
- Given no connection, when a recovery key is presented, then FLOW-003 refuses
  it, because only the server can retire a key.
- Given the account's nickname, published recipes, favourites, ratings and
  moderator role, when the keys are replaced, then none of them changes: it is
  the same account throughout.
- Given the new recovery key, when it is issued, then AUTH-020 governs it in full
  — shown once, kept apart, never displayed again.

**Verification:** present a recovery key with a second device signed in and
holding an unsent recipe; confirm all three old keys stop working, the second
device is emptied at its next contact, and the account is otherwise unchanged.
**Source:** user decision, 2026-09-21. What it does not do is stated rather than
implied: it does not stop a thief destroying the account, because the deletion
key travels in the account key's own file and EXT-006 is deliberately easy to
use; it does not help anyone who kept both keys in one place; and it makes the
recovery key exactly as valuable as the account key, so an account is now two
artefacts to keep safe rather than one.
