---
slug: FLOW
status: open
updated: 2026-09-20
ids: [FLOW-001, FLOW-002, FLOW-003, FLOW-004, FLOW-005, FLOW-006, FLOW-007,
  FLOW-008, FLOW-009, FLOW-010, FLOW-011, FLOW-012, FLOW-013, FLOW-014,
  FLOW-015, FLOW-016, FLOW-017]
---

# Flows and screens

This section holds screens, the four states each one must answer for — empty,
loading, error, offline — and the paths between them. The rules a screen obeys
stay where they were decided and are referenced by identifier: search and
filters in `catalogue`, rating and favourites in `ratings`, reporting and
blocking in `abuse`, what a moderator checks in `moderation`, recalculation in
`scaling-rules`. Nothing decided elsewhere is restated here.

The entry point of the application — first launch, the key file, signing in on a
second device — belongs to `auth` and is not described here.

## Offline and synchronisation

The product exists so that a person can cook from the recipe (GOAL-001), and
that happens in a kitchen, where the network is worst and the hands are busy. An
application that needs a connection to show a recipe fails at the moment it was
written for. The decisions below apply to every screen in this section and are
the reason most of them have a meaningful offline state at all.

The device therefore holds: the account's favourites, both fixed dictionaries,
and every recipe the account owns that has not been accepted by the server. The
catalogue itself is not held, and neither is a recipe the reader merely opened:
what goes on the device is what the reader marked.

### FLOW-001 — Both dictionaries are held on the device

The system shall hold the tag dictionary (DOM-008) and the dictionary of
indivisible ingredients (`scaling-rules`) on the device, and shall refresh them
whenever a network connection is available.

**Acceptance criteria**
- Given no network connection, when the author opens the tag editor, then the
  dictionary entries are offered exactly as they are with a connection.
- Given no network connection, when the author adds a `piece` ingredient that
  the dictionary marks indivisible, then its divisibility is set from the
  dictionary and the author is not asked to set it by hand.
- Given a connection returns after the product owner has changed a dictionary,
  when the application next reaches the server, then the held copy is replaced.
- Given a dictionary entry was removed upstream, when a held recipe already uses
  it, then the recipe keeps it and the editor stops offering it for new use.

**Verification:** open the editor in aeroplane mode on a device that has synced
at least once; change a dictionary upstream and confirm the refresh.
**Source:** user decision, 2026-09-20. The dictionaries are small and are
maintained by the product owner alone, so holding them costs little and removes
hand-set divisibility flags from offline authoring.

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

### FLOW-009 — Every favourite is readable without a network connection

The system shall hold on the device the full text of every recipe in the
account's favourites — its title, tags, serving count, ingredients, steps,
timers and provenance — and shall keep it there for as long as the recipe stays
in favourites.

**Acceptance criteria**
- Given no network connection, when the reader opens any recipe in their
  favourites, then it opens in full, can be scaled by `scaling-rules`, and its
  timers run.
- Given a recipe is added to favourites while a connection is available, when
  the addition completes, then its text is on the device without the reader
  asking for it.
- Given an account with several hundred favourites, when they are held, then no
  favourite's text is dropped to make room for another: text is never evicted.
- Given a recipe is removed from favourites, when the removal completes, then
  its held text and media are removed from the device.
- Given a favourite that stops being accessible, when the device next reaches
  the server, then its held content is deleted from the device and the favourite
  itself stays in the list (FLOW-010).
- Given a recipe the reader opened but never added to favourites, when the
  reader opens it with no connection, then it is not available (FLOW-003).

**Verification:** favourite a recipe, go offline, cook from it end to end
including timers; then have a moderator remove it and confirm the held copy
disappears on the next connection.
**Source:** user decision, 2026-09-20. Favouriting is the act of taking a recipe
with you; nothing is held that the reader did not mark, because a recipe's text
is a few kilobytes and the bound that matters is the reader's own choice.

### FLOW-010 — A favourite that becomes unavailable stays in the list

If a recipe in an account's favourites stops being accessible to that account,
then the system shall keep it in the favourites list marked as unavailable,
shall delete its held content from the device, shall not state why, and shall
not notify the reader.

**Acceptance criteria**
- Given a favourite that reports have hidden, when the device next reaches the
  server, then the recipe stays in the favourites list, is marked unavailable,
  cannot be opened, and its held text and media are gone from the device.
- Given that recipe, when a moderator dismisses the reports and it returns to
  `published`, then it becomes openable again and its content returns to the
  device without the reader doing anything.
- Given a favourite a moderator has removed, when the device next reaches the
  server, then it is marked unavailable and never becomes openable again.
- Given an unavailable favourite, when the reader looks at it, then no reason is
  given and no notification was delivered for it.
- Given an unavailable favourite, when the reader removes it from favourites,
  then it leaves the list like any other.
- Given no network connection, when the reader opens a favourite that has been
  hidden or removed since the device last reached the server, then it still
  opens, because the device cannot know.

**Verification:** hide a favourite by reports and reconnect; dismiss the reports
and reconnect; remove another favourite as a moderator and reconnect; inspect
the list and the device's held content at each step.
**Source:** user decision, 2026-09-20. The reader marked the recipe and the
reader unmarks it; the product does not tidy the list on their behalf and does
not spend a notification on it. A shelf in a shop shows that an item ran out
rather than rearranging itself.

### FLOW-011 — One setting governs whether media is held

The system shall offer a single setting that governs whether the media of
favourited recipes is held on the device, and shall hold the text of every
favourite regardless of that setting.

**Acceptance criteria**
- Given the setting is on, when a recipe is added to favourites, then its cover,
  its step photos and its step video are held on the device.
- Given the setting is off, when a recipe is added to favourites, then only its
  text is held and its media requires a connection.
- Given held media, when the reader turns the setting off, then the held media
  is deleted from the device and every favourite keeps its text.
- Given the setting is on and the device has no space left, when media is being
  held, then the download fails, the recipe stays readable, its media requires a
  connection, and no other favourite's media is deleted to make room.
- Given any number of favourites, when media is held, then nothing is capped or
  evicted by count or by size: the setting is the only control.

**Verification:** switch the setting both ways with favourites already held, and
fill the device to confirm the failure is contained to the recipe being held.
**Source:** user decision, 2026-09-20. One setting rather than separate controls
for photo and video, because video here is a short clip showing a technique —
how something is cut, how one ingredient goes into another — and not a lesson,
so its weight is close to a photo's. The split into two settings is a change
that can be made later without changing anything else.
**Note:** the setting is on by default. What video is for is recorded in OQ-003,
which sets the limits that make this true.

### FLOW-012 — Leaving the application does not lose the recipe being read

When the reader returns to the application after leaving it, the system shall
show the recipe that was open, at the scale it was showing, at the step the
reader had reached.

**Acceptance criteria**
- Given a recipe open at a changed serving count with a fixed ingredient set,
  when the reader leaves the application and returns to it, then the same recipe
  is shown at the same scale with the same fixed ingredient, whether or not it
  is in favourites and whether or not there is a connection.
- Given a running timer, when the reader leaves the application and returns,
  then the timer shows the time remaining by the clock, not the time it held
  when the application was left.
- Given the operating system ended the application while it was away, when the
  reader opens it again, then the same recipe is shown if its content is
  available — held on the device, or fetchable over the network.
- Given the operating system ended the application, the recipe is not in
  favourites, and there is no connection, when the reader opens the application
  again, then it states that the recipe needs a connection and offers to retry,
  and does not silently drop the reader into the catalogue.

**Verification:** scale a recipe, fix an ingredient, start a timer, leave the
application for several minutes and return; repeat with the process ended by the
operating system, in both the favourited and the non-favourited case.
**Source:** user decision, 2026-09-20. Cooking is interrupted constantly, and an
interruption is not a reason to lose the page: leaving the application and
closing it are different events and only the second can cost the recipe.
**Note:** whether a running timer announces itself while the application is not
in the foreground is decided with the recipe card flow, not here.

## The recipe card

The card is the screen the product exists for: it is where a person cooks. What
it holds is settled elsewhere and referenced here — quantities and their
markings by `scaling-rules`, provenance by FORK-004 and FORK-005, rating and
favourites by `ratings`, reporting and blocking by `abuse`. What follows is what
the card itself must do.

### FLOW-013 — A timer announces itself even if the application is closed

When a step's timer ends, the system shall announce it whether the application
is in the foreground, in the background, or closed.

**Acceptance criteria**
- Given a timer started on a step, when the reader closes the application and
  the timer ends, then it announces itself.
- Given the screen is locked when the timer ends, then it announces itself.
- Given the announcement, when it is displayed, then it names the recipe and the
  step, so that timers from two recipes are told apart.
- Given the announcement, when the reader acts on it, then the application opens
  at the step the timer belongs to.
- Given the reader refused permission to show notifications, when a timer ends,
  then it is still finished and is shown as finished inside the application, and
  the permission is asked for again only when the reader next starts a timer.
- Given a running timer, when the reader opens the recipe, then the time
  remaining is shown on its step.
- Given a running timer, when the reader cancels it, then nothing announces
  itself afterwards.

**Verification:** start a timer, close the application from the task switcher,
lock the device and wait for the end; repeat with notifications refused.
**Source:** user decision, 2026-09-20: a person may start a timer on the last
step and close the application entirely — something goes in the oven and is
taken out in thirty minutes — so the timer cannot depend on the application
staying alive.
**Note:** this states what the reader observes. Whether it is implemented by a
system alarm or otherwise is an engineering choice, provided a closed
application still announces the timer.

### FLOW-014 — The screen does not sleep while a recipe is being read

While a recipe card is in the foreground, the system shall keep the screen
awake, and shall stop keeping it awake after 15 minutes without a touch.

**Acceptance criteria**
- Given a recipe card open and untouched for a minute, when the device's own
  screen timeout is shorter than that, then the screen is still on.
- Given a recipe card open and untouched for 15 minutes, when that time passes,
  then the screen is left to the device's own setting.
- Given the screen was left to sleep that way, when the reader touches the card,
  then it is kept awake again for another 15 minutes without a touch.
- Given a card that is keeping the screen awake, when the reader leaves the
  card, then the screen is left to the device's own setting at once.
- Given a running timer and no touch for 15 minutes, when the screen sleeps,
  then the timer still announces itself (FLOW-013).

**Verification:** open a card on a device with a 30-second screen timeout, do not
touch it, and observe at one minute, at fifteen minutes and after a touch.
**Source:** user decision, 2026-09-20. Hands are dirty while cooking and waking
the screen costs a knuckle; fifteen minutes is the product owner's number, set
so that a card left open on a counter does not drain the battery all evening.

### FLOW-015 — Forking twice is offered as the fork already held

If the reader forks a recipe of which they already hold a fork that has not
been changed since it was created, then the system shall tell them that fork
exists and offer to open it, and shall create another fork only if they ask for
one.

**Acceptance criteria**
- Given the reader holds an unchanged fork of a recipe, when they fork it again,
  then they are told the fork exists and can open it, and no second fork is
  created unless they ask.
- Given the reader asks for another fork anyway, when they confirm, then a
  second fork is created.
- Given the reader holds a fork they have changed in any way — title, tags,
  serving count, ingredients, steps, description or media — when they fork the
  same recipe again, then a new fork is created and nothing is said.
- Given the reader holds several forks of one recipe and at least one is
  unchanged, when they fork it again, then the unchanged one is the one offered.
- Given the reader holds no fork of that recipe, when they fork it, then a fork
  is created and nothing is said.
- Given an unchanged fork that has not yet reached the server, when the reader
  forks the same recipe again, then it counts exactly as one the server holds
  (FLOW-007).

**Verification:** fork a recipe twice without touching the first fork; change the
first fork and repeat; confirm both outcomes and the explicit second fork.
**Source:** user decision, 2026-09-20. Two branches from one original are the
point of the product, so a second fork is never forbidden; an untouched fork,
however, is the signature of a misplaced tap and collects as rubbish in the
author's own list.

### FLOW-016 — Several timers run at once, each named by what it is for

The system shall allow the timers of several steps to run at the same time, and
shall identify each one by what it is for wherever it is shown or announced.

**Acceptance criteria**
- Given two steps that carry timers, when the reader starts both, then both run
  and each is shown running on its own step.
- Given two running timers, when one ends, then it announces itself and the
  other keeps running.
- Given a timer whose author gave it a label, when it ends, then the
  announcement uses that label, so the reader knows whether the sauce is done or
  the chicken is to come out.
- Given a timer with no label, when it ends, then the announcement names the
  recipe and the step's position.
- Given several running timers, when the reader opens the recipe, then each
  shows its own remaining time on its own step.
- Given several running timers, when the reader leaves the application and
  returns, then all of them still run by the clock (FLOW-012).

**Verification:** start three timers on one recipe, leave the application, and
confirm each announcement identifies itself and does not disturb the others.
**Source:** user decision, 2026-09-20. A timer that fires without saying what it
is for makes the cook guess between the oven and the pan, which is the moment
the timer existed to prevent. The label is the `Timer.label` already in the
domain model.

### FLOW-017 — The reader's usual serving count opens every recipe

Where the reader has set the serving count they usually cook for, the system
shall open every recipe at that count.

**Acceptance criteria**
- Given the setting holds 2, when the reader opens a recipe authored for 4, then
  it opens showing 2 servings and every changed quantity carries its derived
  marking (SCALE-004).
- Given the setting has never been set, when the reader opens a recipe, then it
  opens at the count its author wrote and nothing is marked as derived.
- Given the setting holds a value, when the reader changes the count while
  reading one recipe, then the setting itself is unchanged.
- Given the setting is being chosen, when the reader picks a value, then the
  values available are the ones SCALE-010 and SCALE-011 allow for a requested
  count.
- Given a recipe opened at the reader's count, when the reader returns it to the
  authored count, then nothing is marked as derived (SCALE-001).

**Verification:** set the count to 2 and to 12, open recipes authored for 1, 4
and 20, and compare against the authored values and the derived markings.
**Source:** user decision, 2026-09-20. A person's household does not change from
one evening to the next, so recomputing the same recipe for the same number of
people every time is work the product can do once. The cost is deliberate: with
the setting in use almost every recipe opens in a derived state, and the numbers
the author wrote are not the numbers the reader meets first — which is why the
derived markings of SCALE-004 carry the weight they do.

## Open

- Whether a serving count the reader last used on one recipe outranks their
  usual count when that recipe is opened again, or the usual count always wins.
- For the editor flow: whether a recipe carrying more than one timer must label
  them, since FLOW-016 falls back to a step's position when a label is missing.
- The rest of the screens.
