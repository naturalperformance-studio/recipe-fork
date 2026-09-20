---
slug: AUTH
status: blocked
updated: 2026-09-20
ids: [AUTH-011, AUTH-012, AUTH-013, AUTH-014, AUTH-015, AUTH-016]
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

Not yet written: the account key's form, the cryptography behind it and the test
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
