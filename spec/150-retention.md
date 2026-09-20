---
slug: RET
status: closed
updated: 2026-09-21
ids: [RET-001, RET-002, RET-003, RET-004, RET-005, RET-006, RET-007, RET-008,
  RET-009, RET-010]
---

# What is kept and what is deleted

The product keeps almost everything it is given, because almost none of it is
about a person: a recipe is a method, a rating is a number, and an account is a
nickname with no one behind it (CON-001). Deletion therefore happens for three
reasons only — the holder asks for their account to go, a moderator takes a
recipe out of the world, or a stored file has nothing left holding it.

Decided elsewhere and not repeated here: a stored media file is deleted once no
surviving recipe holds it, in batches rather than at once (MEDIA-012,
MEDIA-013); publication is permanent and only a moderator removes a published
recipe (ST-001, ST-002); the deletion page outside the application takes a
deletion key and acts immediately (EXT-006, AUTH-017).

Nothing in this section deletes anything because time passed. That is a decision
with a price, and it is recorded as
[ADR — nothing is deleted for being old](decisions/20260921-nothing-expires.md).

## Deleting an account

### RET-001 — What deleting an account destroys

When an account is deleted, the system shall destroy the account record, its
account key and deletion key, its favourites, its list of blocked authors, and
every recipe it owns that has never been published, together with that recipe's
media.

**Acceptance criteria**
- Given a deleted account, when its key is entered anywhere, then it opens
  nothing and the product states that it matches no account.
- Given recipes in `private`, `pending`, `rejected` or `link_shared`, when their
  owner's account is deleted, then they are destroyed with it, and a private link
  that pointed at one opens nothing.
- Given the account's favourites and blocked authors, when the account is
  deleted, then they are gone and no device restores them.
- Given media belonging only to those recipes, when the account is deleted, then
  MEDIA-012 deletes the files.
- Given the deleted account's nickname, when a new account is created afterwards,
  then that nickname is never issued again (DOM-001).
- Given the device the account was created on, when the account is deleted, then
  the slot it occupied is not freed (AUTH-002).

**Verification:** delete an account holding recipes in every unpublished state,
with favourites, blocks and a live private link, and inspect each afterwards.
**Source:** user decision, 2026-09-20. The slot is deliberately not freed: if it
were, deleting and recreating would reset the device gate, and the gate would be
worth nothing. A `link_shared` recipe goes with the account because it was never
published, nobody but its owner could fork it (ST-004), and no other recipe can
depend on it.

### RET-002 — What survives deletion of the account that made it

The system shall keep, when an account is deleted, its published recipes, the
ratings it gave to other accounts' recipes, the reports it filed, and the
moderation record of every recipe it published.

**Acceptance criteria**
- Given a published recipe of a deleted account, when anyone searches for it,
  then it is found and opens normally.
- Given ratings that account gave, when the counts of those recipes are read,
  then they are unchanged by the deletion.
- Given a recipe hidden by three counted reports, one of them from an account
  since deleted, when the deletion happens, then the recipe stays hidden and the
  report still counts (ST-006).
- Given forks of that account's published recipes, when they are opened, then
  their parents still resolve and nothing about them changes (FORK-004).
- Given a moderator opening the record of a removed recipe whose author deleted
  their account, then the record is intact.

**Verification:** delete an account that has published, rated and reported, and
compare every surface it touched before and after.
**Source:** user decision, 2026-09-20 and 2026-09-21. Ratings stay because what
is left of one is a number that relates to nobody, and withdrawing them would
drop a recipe down `highest rated` for something its author did not do. Reports
stay because a report is about somebody else's recipe and because withdrawing
them would let a hidden recipe surface again — report from three accounts, delete
them, and the hiding undoes itself.

### RET-003 — A deleted account's recipes say the author is gone

Where a recipe's author account no longer exists, the system shall show in place
of the nickname a statement that the account was deleted, wherever that recipe's
author would be shown.

**Acceptance criteria**
- Given a published recipe of a deleted account, when it is listed in the
  catalogue, in search results or in favourites, then the author reads as
  deleted and the nickname is not shown (CAT-003).
- Given that recipe as a parent, when a fork shows its provenance, then the
  parent's author reads the same way (FORK-004).
- Given that recipe, when it is opened, then nothing on the card names the
  nickname the account held.
- Given a moderator, when they open the same recipe, then they see the same
  statement and no more.

**Verification:** publish from an account, delete it, and inspect the catalogue,
a search result, a fork's provenance and the card.
**Source:** user decision, 2026-09-20: the author is shown through a layer that
can say something other than a nickname, so that a future notion of a profile
has somewhere to attach and so that nothing public survives an account that
asked to be gone.

### RET-004 — Deletion is immediate and cannot be undone

When an account is deleted, whether from inside the application or from the page
outside it, the system shall complete the deletion at once and shall offer no
suspension, no grace period and no restoration.

**Acceptance criteria**
- Given a confirmed deletion, when it completes, then the account is gone
  immediately and nothing marks it as pending deletion.
- Given a deleted account, when its holder returns with the account key, then
  there is no path that restores it.
- Given the deletion page and the in-application path, when both are used, then
  they destroy exactly the same things (RET-001).

**Verification:** delete by both routes and compare the result.
**Source:** user decision, 2026-09-20; the distribution channel requires that
deletion be deletion rather than deactivation (EXT-005).

### RET-005 — What the account is told before it is deleted

When deletion is asked for, the system shall state that it is immediate and
irreversible, shall state that published recipes stay in the catalogue, shall
state that the only way to have those removed is a rights holder's claim
(ABUSE-007), and shall require an explicit confirmation.

**Acceptance criteria**
- Given the deletion control, when it is used, then all three statements are
  shown before anything is destroyed.
- Given an account with published recipes, when the statement is shown, then it
  says how many stay.
- Given the confirmation, when it is not given, then nothing is deleted and the
  account is untouched.
- Given any setting, when it is searched for, then none switches the statement
  off (GUIDE-001 governs the same pattern).
- Given the statement, when it names the rights holder route, then it does not
  promise that such a claim succeeds.

**Verification:** walk the deletion path with and without published recipes and
read both statements.
**Source:** user decision, 2026-09-21. An author who deletes their account to
make their recipes disappear will otherwise discover afterwards that they did
not, and by then they have nothing left to identify themselves with: the claim
they can still make arrives with no more standing than a stranger's, because the
account that proved authorship is gone.

## Time deletes nothing

### RET-006 — Inactivity destroys nothing

The system shall delete no account, recipe or media item because a period of
time has passed without the account being used.

**Acceptance criteria**
- Given an account with no session for two years, when its key is entered, then
  it opens with everything it held.
- Given that account's unpublished recipes and media, when it returns, then they
  are there.
- Given any scheduled process in the product, when they are enumerated, then
  none deletes by age.

**Verification:** age an account's last session beyond any proposed threshold and
confirm nothing was touched.
**Source:** user decision, 2026-09-21, resolving OQ-004. There is no channel
through which a warning could be delivered (CON-001), so deletion by age would
always arrive unannounced; and the cost it was meant to contain is already
bounded, because an account holds at most 150 MB of unpublished media
(MEDIA-010) and creating accounts costs device slots (AUTH-002). See
[ADR — nothing is deleted for being old](decisions/20260921-nothing-expires.md).

### RET-007 — Abandoned storage is watched rather than swept

The system shall measure the total size of unpublished media held by accounts
with no session in the last twelve months, and shall report it as an operational
signal.

**Acceptance criteria**
- Given the operational signals of `observability`, when they are enumerated,
  then this measurement is among them.
- Given the measurement, when it is read, then it states a total size and the
  number of accounts it covers.
- Given a rising measurement, when it is acted on, then the action is a product
  decision recorded as an ADR and not an automatic deletion (RET-006).

**Verification:** compute the measurement against a seeded store.
**Source:** user decision, 2026-09-21. The product owner accepted the arithmetic
that a thousand abandoned accounts at the cap would be 150 GB — a quarter of what
CON-002 buys — and chose to watch it rather than to prevent it, because reaching
that number by ordinary use would mean the product had succeeded.

## Everything else the server holds

### RET-008 — Server logs are kept for fourteen days

The system shall delete server log entries fourteen days after they are written.

**Acceptance criteria**
- Given a log entry written fifteen days ago, when the logs are read, then it is
  not there.
- Given the logs, when their contents are enumerated, then they hold no recipe
  content and no account key (EXT-010).
- Given an investigation of flooding, when it is run within fourteen days, then
  the logs it needs are available.

**Verification:** inspect the oldest entry present against the retention period.
**Source:** user decision, 2026-09-21. Logs hold IP addresses, which is the only
thing in the product that resembles personal data at all; whether an IP address
alone is personal data is unsettled in the product owner's jurisdiction (OQ-029),
so the period is kept short deliberately. Fourteen days was chosen over thirty as
the shorter position, at the cost of being unable to investigate an attack that
is noticed late.

### RET-009 — A removed recipe is kept for its owner

Where a moderator has removed a recipe, the system shall keep its text for as
long as its owner's account exists, and shall show it to that owner and to a
moderator.

**Acceptance criteria**
- Given a removed recipe, when its owner opens their own recipes, then it is
  listed and opens (FLOW-038).
- Given that recipe, when its media has been deleted by MEDIA-013, then the text
  still opens and the absent media is not replaced by a placeholder (FLOW-019).
- Given that recipe, when its owner deletes their account, then it is destroyed
  with everything else unpublished (RET-001).

**Verification:** remove a recipe, read it as its owner, then delete the account
and look again.
**Source:** ST-002 and FLOW-038, decided 2026-09-20: removal takes a recipe out
of the world, not out of its author's hands.

### RET-010 — Nothing is kept that was never needed

The system shall hold no record of what a person read, searched for, or cooked.

**Acceptance criteria**
- Given the stored data of any account, when it is enumerated, then it holds no
  history of recipes opened, searches made, timers run or scales applied.
- Given the reader's own device, when its held state is enumerated, then what is
  there serves the reader — favourites, the recipe last open (FLOW-012), the
  usual serving count — and is sent nowhere.
- Given `analytics`, when its measurements are defined, then they obey this
  requirement rather than qualifying it: the counters of ANL-004 and ANL-005 are
  kept against the product and against a recipe, never against a person.

**Verification:** data model review; traffic capture during an ordinary reading
session.
**Source:** CON-001 and NGOAL-002. The cheapest way to keep nothing personal is
not to collect it, and a reading history is the one thing in a recipe product
that would say something real about the person.
