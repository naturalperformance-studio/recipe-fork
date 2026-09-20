---
slug: FLOW
status: closed
updated: 2026-09-20
ids: [FLOW-001, FLOW-002, FLOW-003, FLOW-004, FLOW-005, FLOW-006, FLOW-007, FLOW-008]
---

# Offline work and synchronisation

What the device holds, what the editor does without a connection, what is
refused outright, and what happens when two devices diverge.

The product exists so that a person can cook from the recipe (GOAL-001), and
that happens in a kitchen, where the network is worst and the hands are busy. An
application that needs a connection to show a recipe fails at the moment it was
written for. The decisions below apply to every screen in this section and are
the reason most of them have a meaningful offline state at all.

The device therefore holds: the account's favourites, both fixed dictionaries,
and every recipe the account owns, in every state (FLOW-038) — not only those
the server has not yet accepted. The catalogue itself is not held, and neither
is a recipe the reader merely opened: what goes on the device is what the reader
marked and what they wrote themselves.

### FLOW-001 — Both dictionaries are held on the device

The system shall hold the tag dictionary (DOM-008) and the ingredient dictionary
(DOM-009) on the device, and shall refresh them whenever a network connection is
available.

**Acceptance criteria**
- Given no network connection, when the author opens the tag editor, then the
  dictionary entries are offered exactly as they are with a connection.
- Given no network connection, when the author adds a `piece` ingredient whose
  dictionary entry is marked indivisible, then its divisibility is set from the
  entry and the author is not asked to set it by hand.
- Given no network connection, when the author adds a `piece` ingredient as free
  text because no entry matched, then the author sets its divisibility, because
  there is no entry to take it from.
- Given no network connection, when the author looks for an ingredient, then the
  dictionary is searched on the device and offers the same entries it offers with
  a connection.
- Given no network connection, when the reader filters their favourites by an
  ingredient, then the held dictionary supplies both what the filter matches
  (CAT-006) and the comparison factors the ordering needs (CAT-009).
- Given a connection returns after the product owner has changed a dictionary,
  when the application next reaches the server, then the held copy is replaced.
- Given a dictionary entry was removed upstream, when a held recipe already uses
  it, then the recipe keeps it and the editor stops offering it for new use.

**Verification:** open the editor in aeroplane mode on a device that has synced
at least once; change a dictionary upstream and confirm the refresh.
**Source:** user decision, 2026-09-20. Both dictionaries are labels and flags
only, with no media and no weights, and both are maintained by the product owner
alone, so holding them costs little and removes hand-set divisibility flags from
offline authoring. Holding the ingredient dictionary is also what keeps the
editor usable offline at all, now that naming an ingredient means searching it
(DOM-009). Holding only the entries an account has used was considered and set
aside on the arithmetic — see OQ-021.

### FLOW-002 — The editor works without a network connection

While the device has no network connection, the system shall allow the owner to
create a recipe and to edit any recipe of theirs that is editable by
`entity-states`, and shall keep every change on the device until the server
accepts it.

**Acceptance criteria**
- Given no network connection, when the owner creates a recipe and fills its
  title, servings, ingredients and steps, then every change is kept and is
  present after the application is closed and reopened.
- Given no network connection, when the owner edits a recipe in state
  `private` or `rejected`, then the edit is accepted.
- Given no network connection, when the owner opens a recipe in state
  `pending`, `link_shared`, `published`, `reported_hidden` or `removed`, then
  it is not editable, exactly as it is with a connection.
- Given changes made offline, when a connection returns, then they are sent to
  the server without the owner asking for it.

**Verification:** author a complete recipe in aeroplane mode, restart the
application, restore the connection and confirm the recipe reaches the server.
**Source:** user decision, 2026-09-20.

### FLOW-003 — What is refused without a network connection

If the device has no network connection, then the system shall refuse the
catalogue, search over the catalogue, opening a recipe the device does not hold,
submission for moderation, and every moderator action, and shall state in each
case that the action needs a connection.

**Acceptance criteria**
- Given no network connection, when the reader opens the catalogue, then it
  shows that a connection is needed and offers to retry, and does not show a
  stale catalogue.
- Given no network connection, when the reader opens a private link, then it
  shows that a connection is needed.
- Given no network connection, when the owner tries to submit a recipe for
  publication or for a private link, then the control states that a connection
  is needed and the recipe stays in `private`.
- Given no network connection, when a moderator opens the queue, then it shows
  that a connection is needed and no queue entry is shown from a held copy.

**Verification:** walk each refused path in aeroplane mode and confirm the
message and that no stale data is displayed.
**Source:** user decision, 2026-09-20. The catalogue is a live surface — a held
copy would show recipes that moderation has since removed or hidden.

### FLOW-004 — Media added offline is queued, not lost

When the owner adds media to a recipe while the device has no network
connection, the system shall keep the media on the device, mark the recipe as
not accepted by the server, and upload the media when a connection returns.

**Acceptance criteria**
- Given no network connection, when the owner adds a photo to a recipe, then it
  is displayed in the recipe and the recipe is marked as not accepted by the
  server.
- Given queued media, when a connection returns, then the upload starts without
  the owner asking for it.
- Given queued media, when the upload fails, then the media stays on the device
  and the recipe stays marked as not accepted.
- Given queued media, when the owner removes that media before it is uploaded,
  then nothing is uploaded for it.

**Verification:** add media in aeroplane mode, restore the connection and
confirm the upload; repeat with the upload forced to fail.
**Source:** user decision, 2026-09-20. Media reach the server before publication
so that a second device picks them up by key file (draft section 5.2); offline
that is impossible, and refusing the addition would make the editor useless in
the kitchen.

### FLOW-005 — Every recipe shows whether the server has accepted it

The system shall display, for each recipe the account owns, whether every local
change to it has been accepted by the server.

**Acceptance criteria**
- Given the list of the account's own recipes, when it is displayed, then each
  entry states whether it is fully accepted by the server or holds changes that
  are not.
- Given a recipe open in the editor, when it holds changes the server has not
  accepted, then the editor states so.
- Given a recipe whose text has been accepted but whose media is still queued,
  when it is displayed, then it counts as holding changes that are not accepted.
- Given a recipe fully accepted by the server, when it is displayed, then it is
  marked as accepted and not merely left unmarked.

**Verification:** compare the marking against the server state in four cases —
fully accepted, text pending, media pending, both pending.
**Source:** user decision, 2026-09-20. The marking is per recipe rather than one
global indicator, so that the owner can see which recipe they would lose by
reinstalling the application.

### FLOW-006 — Nothing is submitted for moderation before the server holds it

If a recipe holds any local change the server has not accepted, then the system
shall refuse to submit it for publication or for a private link, and shall state
that it is waiting to reach the server.

**Acceptance criteria**
- Given a recipe with queued media, when the owner submits it for publication,
  then the submission is refused and the recipe stays in `private`.
- Given a recipe whose local changes are all accepted, when the owner submits
  it, then it moves to `pending` by `entity-states`.
- Given a refused submission, when the queued changes reach the server, then the
  submission control becomes available without the owner reopening the recipe.

**Verification:** submit with media queued, then after the queue drains.
**Source:** user decision, 2026-09-20. A moderator must review the content that
will be published; content the server does not yet hold is not that content.

### FLOW-007 — A fork is never sent before its parent

While a recipe has not been accepted by the server, the system shall keep every
fork of it on the device, and shall send a fork only after its parent has been
accepted.

**Acceptance criteria**
- Given a recipe that has not reached the server, when the owner forks it, then
  the fork is created on the device and both are marked as not accepted.
- Given that pair, when a connection returns, then the parent reaches the server
  before the fork does.
- Given that pair, when the parent's upload fails, then the fork is not sent.
- Given a fork of a published recipe held on the device, when the owner forks it
  offline, then the fork is created locally and is sent as soon as a connection
  returns, because its parent is already on the server.

**Verification:** build a chain of three local forks offline, restore the
connection and confirm the order in which they are accepted.
**Source:** user decision, 2026-09-20. A fork names its direct parent
(GOAL-002), so a fork on the server whose parent is not there would name
something that does not exist.

### FLOW-008 — Two devices that diverge produce a fork, never an overwrite

If the same account edits one recipe on two devices while at least one of them
is offline, so that the local version and the version the server holds have
diverged, then the system shall keep the server's version as that recipe and
turn the divergent local version into a fork of it owned by the same account,
and shall tell the owner that it did so.

**Acceptance criteria**
- Given a recipe edited offline on device B while device A's edit reached the
  server, when device B reconnects, then the server's version keeps the recipe's
  identity and device B's version becomes a `private` fork naming it as parent.
- Given that outcome, when the owner opens the application on device B, then
  they are told a fork was created and why, and both recipes are reachable.
- Given the server's version has since been published or otherwise frozen, when
  device B reconnects with a divergent version, then the same rule applies and
  nothing is written to the frozen recipe.
- Given no divergence — device B's version is the one the server already holds —
  when device B reconnects, then no fork is created.

**Verification:** copy the key file to a second device, edit one recipe on both
with one offline, reconnect and inspect both recipes.
**Source:** user decision, 2026-09-20. The product's own answer to a changed
recipe is a fork (GOAL-002), and the server's version keeps its identity because
other recipes may already name it as their parent. No edit is discarded.
