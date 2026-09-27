---
slug: AUTH
status: closed
updated: 2026-09-28
ids: [AUTH-011, AUTH-012, AUTH-013, AUTH-026, AUTH-027]
---

# Accounts on a device

What a device holds for each account signed in on it and for nobody's account,
how the active account is switched, how one is removed, and how a key the device
has seen opens its account without the server. Split out of
[30-account-life.md](30-account-life.md) on 2026-09-28; no identifier changed.

## Several accounts on one device

Decided 2026-09-28, replacing one active account per device and the wipe that went
with it: [ADR — several accounts on one device](../decisions/20260928-several-accounts-on-one-device.md).
How the device keeps each account's content apart is the implementation's
choice; what is specified is that it is apart.

### AUTH-011 — A device holds every account signed in on it, each apart

The system shall hold on a device every account signed in on it, shall keep the
content each one holds there — its recipes, its changes not sent, its favourites
and its blocked authors — apart from every other account's and from the pool of
AUTH-026, shall show only the active account's, and shall let the person switch
the active account by choosing another from a list of the accounts signed in on
the device.

**Acceptance criteria**
- Given two accounts signed in on one device, when the account list is opened,
  then both are listed by nickname and the active one is marked.
- Given account A active with recipes and favourites, when the person switches to
  account B, then B's recipe book is shown and nothing of A's is visible anywhere.
- Given the switch back to A, when it completes, then A's recipes — including
  those never sent and those holding changes not sent — and its favourites are
  there unchanged.
- Given no network connection, when the person switches between accounts already
  on the device, then the switch works.
- Given the account list, when it is opened, then it also offers to continue with
  no account, which shows the pool, and to sign in with another key (AUTH-009).
- Given serverless mode (FLOW-061), when the account list is opened, then the
  accounts on the device are listed and switchable, and signing in with a key not
  yet used on the device is not offered (FLOW-062).

**Verification:** sign two accounts in on one device, write an unsent recipe and
favourite a recipe in each, switch back and forth with and without a connection,
and inspect both books each time.
**Source:** user decision, 2026-09-28: two people cooking from one phone, each
with their own key and their own book; switching from a list rather than by
entering a key each time. Supersedes the single active account decided 2026-09-20
(OQ-028).

### AUTH-012 — Switching the active account deletes nothing

When the active account is switched, or another account signs in on the device,
the system shall delete nothing that any account or the pool holds on the device.

**Acceptance criteria**
- Given account A holding recipes never sent, when account B signs in on the
  device, then A's recipes are still on the device and return when A is active.
- Given the pool holding recipes and favourites, when an account signs in and the
  person declines the offer of AUTH-026, then the pool is still on the device.
- Given the interface language (L10N-002), the reader's usual serving count
  (FLOW-017), the recipe that was open (FLOW-012) and the setting of FLOW-011,
  when the active account changes, then none of them is touched: they belong to
  the device and to nobody's account.
- Given a device handed on, when its new holder opens the account list, then
  every account signed in on it is still there until it is removed (AUTH-013).

**Verification:** switch between two accounts and the pool, sign a third in, and
confirm nothing held by any of them was deleted.
**Source:** user decision, 2026-09-28, superseding the wipe of 2026-09-20 and its
widening of 2026-09-21: once a recipe lives on the device first, a wipe would
destroy the only copy of everything its author never sent. **Cost, stated:** a
device handed on carries every account on it until each is removed; the promise
that a device handed on holds nothing of the previous holder is now kept by
AUTH-013, not by default.

### AUTH-013 — An account is removed from the device only on request, after being told what is lost

When the person asks to remove an account from the device, the system shall state
what will be deleted from the device, shall name each recipe of that account never
sent or holding changes not sent, shall require an explicit confirmation, and
shall then delete from the device everything that account holds there and remove
it from the account list.

**Acceptance criteria**
- Given three recipes never sent or holding changes not sent, when the removal is
  asked for, then all three are named on the screen that asks for confirmation.
- Given an account whose every recipe is on the server, when the removal is asked
  for, then the statement says nothing will be lost that the key cannot bring
  back, and still asks for confirmation.
- Given the confirmation given, when it completes, then nothing of that account
  is on the device, the account is not in the list, and the account itself — on
  the server and on its other devices — is unchanged.
- Given the removed account's key entered again, when it signs in (AUTH-009),
  then what the server holds for it returns and what was never sent does not.
- Given the confirmation control, when any setting is searched for, then none
  switches this statement off (GUIDE-001 governs the same pattern).
- Given the person declines, when they return to the application, then the
  account and everything it holds are untouched.
- Given serverless mode (FLOW-061), when an account is removed, then the
  statement says that nothing of it can come back, because the key can no longer
  fetch anything.

**Verification:** remove an account with unsent recipes present and absent, read
both statements, then sign the key in again and compare what returns.
**Source:** user decision, 2026-09-28: with several accounts on a device and no
wipe at sign-in, a device is cleaned by removing accounts from it. The statement
keeps what AUTH-013 said before the change: FLOW-005 marks exactly which recipes
hold what the server does not, so the product names them instead of warning in
general terms.

### AUTH-026 — What is made with no account is a pool, offered once to each account

The system shall keep what is made on the device while no account is active —
recipes, favourites and blocked authors — in one pool that belongs to no account,
shall show the pool whenever no account is active, and, the first time each
account becomes active on the device while the pool holds anything, shall offer
once to move the whole pool into that account.

**Acceptance criteria**
- Given a pool holding recipes and favourites, when an account is created or
  signs in on the device, then the offer is shown before the account's recipe
  book is.
- Given the offer accepted, when it completes, then everything in the pool is
  that account's, the pool is empty, and the account's recipe book shows it.
- Given the offer declined, when the account's recipe book is shown, then nothing
  from the pool is in it, and the pool is shown again whenever no account is
  active.
- Given an account that declined, when it becomes active again later, then the
  offer is not shown again; the account's menu holds an action that makes the same
  offer at any time while the pool holds anything.
- Given a second account that becomes active for the first time on the device,
  when the pool holds anything, then the offer is shown to it once as well.
- Given a pool that is empty, when any account becomes active, then no offer is
  shown and the menu action is not offered.
- Given recipes in the pool that were moved into an account, when they are sent
  (FLOW-059), then that account is their owner (ACT-002).

**Verification:** fill the pool, create an account and decline, switch away and
back, use the menu action and accept; sign a second account in with the pool
refilled.
**Source:** user decision, 2026-09-28: what is made without an account lies in a
common pool; at a sign-in the person is offered to move it into the account,
may refuse, and the offer then stays in the menu rather than returning at every
sign-in.

### AUTH-027 — A key already used on this device opens its account without the server

When a person enters on a device the account key of an account signed in on that
device before, the system shall make that account active and show what the device
holds for it, whether or not the server can be reached.

**Acceptance criteria**
- Given an account signed in on the device and then switched away from, when its
  key is entered with no connection, then it becomes active and its recipe book
  and recipes are shown.
- Given an account removed from the device (AUTH-013), when its key is entered with
  no connection, then it is treated as a key not yet used on the device and
  AUTH-010 refuses it.
- Given serverless mode (FLOW-061), when the key of an account on the device is
  entered, then the account becomes active.
- Given an account whose keys were retired by its recovery key (AUTH-021), when
  the device next reaches the server, then AUTH-021 governs what the device keeps;
  until then the device cannot know, as FLOW-010 (8) says of a favourite.

**Verification:** sign in, switch away, cut the connection and enter the key;
remove the account and enter the key offline; repeat in serverless mode.
**Source:** user decision, 2026-09-28: a key already used on the device opens its
content on the device even without the server, which is what makes several
accounts on one device survive the server's shutdown.

