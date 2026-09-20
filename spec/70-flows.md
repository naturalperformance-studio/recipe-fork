---
slug: FLOW
status: open
updated: 2026-09-20
ids: [FLOW-001, FLOW-002, FLOW-003, FLOW-004, FLOW-005, FLOW-006, FLOW-007,
  FLOW-008, FLOW-009, FLOW-010, FLOW-011, FLOW-012, FLOW-013, FLOW-014,
  FLOW-015, FLOW-016, FLOW-017, FLOW-018, FLOW-019, FLOW-020, FLOW-021,
  FLOW-022, FLOW-023, FLOW-024, FLOW-025, FLOW-026, FLOW-027, FLOW-028,
  FLOW-029, FLOW-030, FLOW-031, FLOW-032]
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
shall delete its held content from the device, shall state why only where it was
removed as dangerous to health, and shall not notify the reader.

**Acceptance criteria**
- Given a favourite that reports have hidden, when the device next reaches the
  server, then the recipe stays in the favourites list, is marked unavailable,
  cannot be opened, and its held text and media are gone from the device.
- Given that recipe, when a moderator dismisses the reports and it returns to
  `published`, then it becomes openable again and its content returns to the
  device without the reader doing anything.
- Given a favourite a moderator has removed, when the device next reaches the
  server, then it is marked unavailable and never becomes openable again.
- Given a favourite removed as dangerous to health, when the reader looks at it,
  then that reason is stated, because the reader has cooked from it and may cook
  from it again.
- Given a favourite removed for using someone else's content, or one hidden by
  reports, when the reader looks at it, then no reason is given and the two
  cases are indistinguishable.
- Given any of these, when they happen, then no notification was delivered.
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
- Given a reader who scaled one recipe to a different count and left it, when
  they open that recipe again in a later session, then it opens at their usual
  count and not at the count they used last time.
- Given a reader who scaled a recipe and stays in the application, when they
  leave the card and come back to it, then the count they set is still there.

**Verification:** set the count to 2 and to 12, open recipes authored for 1, 4
and 20, and compare against the authored values and the derived markings.
**Source:** user decision, 2026-09-20. A person's household does not change from
one evening to the next, so recomputing the same recipe for the same number of
people every time is work the product can do once. The usual count outranks the
count last used on a particular recipe: a reader who scaled a recipe for twelve
guests one evening is unlikely to have twelve guests the next time they open it,
and the household is the durable fact. The cost is deliberate: with the setting
in use almost every recipe opens in a derived state, and the numbers the author
wrote are not the numbers the reader meets first — which is why the derived
markings of SCALE-004 carry the weight they do.
**Note:** a count the reader set survives for as long as the application keeps
running, including leaving the card and returning to it; a fresh start of the
application opens every recipe at the usual count again. This boundary follows
the reason for the decision — a week later the guests are gone, ten minutes
later the reader is still cooking the same meal.

### FLOW-018 — A flagged recipe says what it descends from

While a recipe carries the safety flag, the system shall state on its card that
it descends from a recipe removed as dangerous to health, and shall not name
that recipe.

**Acceptance criteria**
- Given a flagged recipe, when anyone opens it, then the statement is shown.
- Given a flagged recipe, when the statement is shown, then it neither names nor
  links the removed recipe, and the chain above it is not walked to reach it.
- Given a flagged recipe in state `private`, when its owner opens it, then the
  statement is shown to them too.
- Given a flagged recipe, when it is shown in the catalogue, in search results
  and in favourites, then the statement is present there as well and not only
  inside the card.
- Given a recipe whose flag a moderator cleared, when its card is opened, then
  the statement is gone and nothing records that it was ever there.

**Verification:** flag a chain, open each member as its owner, as another
account and as a moderator, then clear one and look again.
**Source:** user decision, 2026-09-20. The statement is a fact about descent,
not an accusation against this recipe's author, which is why it names nothing:
naming the removed recipe would both point at another author and rebuild the
lineage that FORK-005 deliberately stops showing.

### FLOW-019 — What the card shows when parts of a recipe are absent

Where a recipe has no description, no media or no parent to show, the system
shall omit those parts of the card entirely and shall not display a placeholder
in their place.

**Acceptance criteria**
- Given a recipe with no media at all, when its card is opened, then no image
  frame, no placeholder image and no empty media area is shown.
- Given a recipe with an empty description, when its card is opened, then no
  description heading and no empty block is shown.
- Given a recipe whose parent is not shown by FORK-005, when its card is opened,
  then no provenance area is shown at all.
- Given a recipe nobody has rated, when its card is opened, then the rating
  controls are offered and no count reads as a judgement of the recipe.
- Given a recipe reduced to the minimum DOM-006 accepts, when its card is
  opened, then it reads as a complete recipe and not as a broken one.

**Verification:** publish a recipe with no media, no description and no shown
parent, and compare its card against one that has all three.
**Source:** the parts are optional in the domain model; an empty frame states
that something is missing, which is a claim the recipe does not make.

### FLOW-020 — The card while it loads and when it cannot be loaded

While a recipe is being loaded, the system shall show no quantity until the
scale that applies to the reader is known; and if the recipe cannot be loaded,
the system shall say so and offer to try again.

**Acceptance criteria**
- Given a reader whose usual serving count differs from the authored one
  (FLOW-017), when the card is opening, then no quantity is shown at the
  authored scale first and then replaced.
- Given a card that is loading, when it is displayed, then the reader can leave
  it without waiting for it to finish.
- Given a recipe that fails to load, when the failure is shown, then it states
  that the recipe could not be loaded and offers to try again, and does not
  state that the recipe does not exist.
- Given a recipe that is unavailable rather than unreachable, when it is opened,
  then FLOW-010 governs what is shown, not this requirement.
- Given a failed load, when the reader retries and it succeeds, then the card
  opens at the scale FLOW-017 requires.

**Verification:** open a card with the network throttled and with it cut, and
watch the quantities through the whole opening.
**Source:** derived from FLOW-017: a quantity shown at the authored scale and
then replaced is a number a reader can act on before it changes, which in this
product is a wrong amount of an ingredient.

### FLOW-021 — Starting a timer that is already running asks first

If the reader starts a timer on a step whose timer is already running, then the
system shall state that it is already running and ask whether to restart it, and
shall restart it from its full duration only on confirmation.

**Acceptance criteria**
- Given a running timer, when the reader starts it again, then they are told it
  is running and are asked whether to restart it.
- Given that question, when the reader confirms, then the timer restarts from
  its full duration and the time it had already counted is gone.
- Given that question, when the reader declines, then the timer keeps running
  with the time it had.
- Given two steps with running timers, when one is restarted, then the other is
  untouched (FLOW-016).
- Given a timer that has already ended, when the reader starts it again, then it
  starts with no question.

**Verification:** start a timer, wait, start it again and take both branches.
**Source:** user decision, 2026-09-20. Restarting is what the reader almost
always means, and losing the count silently is what they never mean.

### FLOW-022 — An ingredient outside the dictionary is accepted with a warning

When the author names an ingredient that matches no entry of the ingredient
dictionary, the system shall accept the ingredient as free text and shall state
that no filter on that ingredient will find the recipe and that this cannot be
changed once the recipe has been published.

**Acceptance criteria**
- Given the ingredient editor, when the author types a name and the dictionary
  offers nothing that matches, then adding it as free text is offered and is not
  refused.
- Given the author adds it, when the warning is shown, then it states both that
  filters will not find the recipe by this ingredient and that publication makes
  it permanent (CON-003).
- Given a recipe that holds a free-text ingredient, when the author later finds a
  matching entry while the recipe is still editable, then the ingredient can be
  replaced by the entry and the warning is gone.
- Given a recipe that holds a free-text ingredient, when it is submitted for
  moderation, then submission is not blocked by the warning.
- Given a `piece` ingredient added as free text, when it is added, then the
  author is asked for its divisibility, because no entry supplies it (FLOW-001).

**Verification:** add an ingredient absent from the dictionary, confirm the
warning's two statements, then replace it with an entry and confirm the warning
disappears.
**Source:** user decision, 2026-09-20: a missing entry never blocks authoring,
and the author is told what it costs rather than protected from it. See
[ADR — the ingredient dictionary](decisions/20260920-ingredient-dictionary.md).

### FLOW-023 — An ingredient is three fields and nothing more

The system shall compose an ingredient in the editor from exactly three fields —
the ingredient, the quantity and the unit — and shall never ask the author for the
unit type, which follows from the unit chosen.

**Acceptance criteria**
- Given the editor, when the author adds an ingredient, then exactly three fields
  are offered: `крылышки` · `500` · `г`, or `тушка` · `2` · `шт`.
- Given the ingredient field, when the author types, then dictionary entries are
  offered whether they are leaves or parents (DOM-010), so `курица · 500 · г` is an
  ordinary row.
- Given the ingredient field, when both a leaf and its parent could match what the
  author typed, then the leaves are offered before the parent, so that a qualifier
  is a last resort and not a substitute for picking the right entry.
- Given the author picks the entry `филе` and keeps typing `без кожи`, when the row
  is read, then it reads `филе без кожи`, the ingredient holds that entry and that
  qualifier, and no fourth field has appeared.
- Given such an ingredient, when a reader filters on `курица`, then the recipe
  matches through the entry, and the qualifier plays no part in the match
  (CAT-006).
- Given the ingredient field, when nothing in the dictionary matches, then free
  text is offered with the warning FLOW-022 requires.
- Given the unit field, when the author picks `шт`, then the ingredient's unit type
  is `piece` and the author is never shown a unit type at all.
- Given the unit field, when the author picks the unit that means "to taste", then
  the quantity field is withdrawn and the ingredient is stored with no quantity
  (DOM-002).
- Given a `piece` ingredient whose dictionary entry supplies its divisibility, when
  the row is shown, then divisibility is not one of the three fields (FLOW-001).

**Verification:** author one ingredient of each unit type through the editor and
confirm that no row ever presents a fourth field or asks for a unit type.
**Source:** user decision, 2026-09-20. Three fields is also why a parent entry has
to be usable on its own: a row is an ingredient, a number and a unit, and there is
no place in it for the product to demand a narrower entry than the author has. The
qualifier survives as the tail of the first field rather than as a field of its
own, because the dictionary will always lag behind a kitchen: without it an author
whose leaf is missing would have to choose between a recipe that can be found and
a row that says what they mean (OQ-020).

### FLOW-024 — The editor is one screen in a fixed order

The system shall present the editor as one scrolling screen holding, in this order,
the title with the cover, the tags, the serving count, the ingredients, the steps
and the description, and shall not require the author to complete one part before
reaching another.

**Acceptance criteria**
- Given a new recipe, when the editor opens, then every part is present and
  reachable by scrolling, in the order above.
- Given a half-filled recipe, when the author scrolls to the steps without having
  entered a serving count, then the steps accept input.
- Given a recipe created by forking, when the editor opens, then the inherited parts
  arrive already filled in this same screen (FORK-001).
- Given the author leaves the editor at any point, when they return, then they
  return to a screen in the same order with everything they had entered.

**Verification:** open the editor on a new recipe and on a fork, and fill the parts
out of order.
**Source:** user decision, 2026-09-20: an author needs to see the whole recipe at
once, and a walk-through would send them back and forth for a change of one line.

### FLOW-025 — Steps and ingredients are inserted anywhere and reordered by dragging

The system shall let the author insert a step or an ingredient at any position,
including between two existing ones, and reorder either by dragging it up or down.

**Acceptance criteria**
- Given a recipe with five steps, when the author inserts a step between the second
  and the third, then it takes that position and the ones after it move down.
- Given a recipe with five steps, when the author drags the fourth above the second,
  then the order is as dragged and no step's content changed.
- Given a step that references an ingredient, when either is reordered, then the
  reference still resolves to the same ingredient (DOM-007).
- Given a timer inside a step, when the step is moved, then the timer moves with it
  (FLOW-027).
- Given an ingredient that a step references, when the author deletes that
  ingredient, then the reference is dropped as DOM-007 already requires.

**Verification:** insert and drag steps and ingredients in a recipe that has
references and timers, and confirm positions and references afterwards.
**Source:** user decision, 2026-09-20.

### FLOW-026 — The cover is attached with the title, step media with the step

The system shall offer the cover image where the title is entered and shall offer
media for a step at that step.

**Acceptance criteria**
- Given the top of the editor, when the author looks at the title field, then
  attaching a cover image is offered there and is described as optional.
- Given a step, when the author looks at it, then attaching an image or a video to
  that step is offered at that step and nowhere else.
- Given a recipe with no cover, when it is saved, then it saves, because a cover is
  not part of the minimum (DOM-006).
- Given media attached with no connection, when it is attached, then FLOW-004
  governs and the recipe is marked as not accepted by the server (FLOW-005).
- Given the limits of OQ-003, when they are set, then how many items a step may
  carry follows from them and not from this requirement.

**Verification:** attach a cover and step media online and offline, and save a
recipe with neither.
**Source:** user decision, 2026-09-20: media is attached where the thing it belongs
to is written, so a step's photo is never separated from its step.

### FLOW-027 — A timer is attached inside the step it belongs to

The system shall let the author attach a timer to a step and enter its duration
there, and shall hold the timer as part of that step.

**Acceptance criteria**
- Given a step reading `в духовку на 190 °C на 30 минут`, when the author attaches a
  timer and enters 30 minutes, then the timer belongs to that step.
- Given a step with a timer, when the author adds a second timer to the same step,
  then both belong to it (FLOW-016).
- Given a timer, when the author enters its label or leaves it empty, then FLOW-016
  governs what the reader sees.
- Given a step with a timer, when the step is deleted, then its timers are deleted
  with it.
- Given a duration the author has not entered, when the recipe is saved, then the
  timer is not stored, because a timer without a duration is not a timer.

**Verification:** attach one timer and two timers to a step, delete the step, and
save a recipe with an unfinished timer.
**Source:** user decision, 2026-09-20.

### FLOW-028 — The minimum and the maximum are shown on leaving a field, as a warning

When the author leaves a part of the recipe that is below the minimum DOM-006 requires
or above a limit of DOM-012, the system shall mark that part without treating it as an
error, and shall not prevent further editing.

**Acceptance criteria**
- Given an empty ingredient list, when the author moves from it to the steps, then
  the ingredient list is marked as still needing at least one ingredient.
- Given a warning of that kind, when the author keeps typing anywhere in the recipe,
  then nothing is blocked and no input is refused.
- Given a part that was marked, when the author completes it, then the mark is gone
  without the author asking for it.
- Given a recipe still below the minimum, when the author tries to submit it, then
  submission is refused as DOM-006 requires — the warning informs, the minimum
  decides.
- Given a step that passes 2 000 characters, when the author leaves it, then it is
  marked as over its limit and the text they typed is kept in full, not truncated.
- Given a recipe over a limit, when the author tries to submit it, then submission is
  refused as DOM-012 requires — again the warning informs and the limit decides.
- Given a field the author has not yet visited, when the editor opens, then it is
  not marked, because nothing has been left.

**Verification:** leave each part of a recipe below the minimum in turn, then
complete it, then attempt submission.
**Source:** user decision, 2026-09-20: a warning on leaving a field, never an error
while typing in it, and the same treatment at both ends — a part that is too small and
a part that is too large are both reported the same way and both decided at
submission.

### FLOW-029 — There is no preview; saving shows the reader's view

The system shall show the author their saved recipe exactly as a reader sees it, and
shall offer no separate preview inside the editor.

**Acceptance criteria**
- Given a recipe the author saves, when it is opened afterwards, then it is the same
  card a reader sees (FLOW-019, FLOW-020), not an editor with reader styling.
- Given that view of a recipe that is still editable, when the author chooses to
  edit, then the editor opens on the same recipe.
- Given that view, when the author looks for an edit control on the card itself,
  then there is none: the card is read, the editor is written, and they are two
  screens.
- Given a recipe that is no longer editable by `entity-states`, when the author
  opens it, then no way into the editor is offered at all.

**Verification:** save a recipe, open it, edit it, publish it and try again.
**Source:** user decision, 2026-09-20: a preview would be a third view of the same
recipe to build and keep true, when saving already produces the reader's own view
and the recipe stays editable until it becomes visible to anyone else.

### FLOW-030 — The editor says which parts a fork will carry away

The system shall show in the editor which parts of the recipe a fork inherits and
which remain the author's own, following FORK-001 and FORK-002.

**Acceptance criteria**
- Given the editor, when the author looks at the title, the tags, the serving count,
  the ingredients and the steps, then these are shown as the parts a fork inherits
  (FORK-001).
- Given the editor, when the author looks at the description and the media, then
  these are shown as staying with the author and not travelling to a fork
  (FORK-002).
- Given that marking, when the author reads it, then it states what a fork does, not
  whether forking can be prevented — it cannot (FORK-002).
- Given a recipe opened for editing after a fork was made of it, when the author
  looks at the same marking, then it is unchanged, because what a fork inherits does
  not depend on the recipe.

**Verification:** compare the marking in the editor against FORK-001 and FORK-002
part by part.
**Source:** user decision, 2026-09-20: an author writing a description or attaching
photographs should know, while they do it, that this part is theirs and the method
is what travels. This marking is also the whole of the product's answer to an author
who writes the method into the description and leaves the steps empty: the product
warns before the fact and never inspects afterwards, because inspecting would be a
judgement on the writing (NGOAL-007,
[ADR — quality is sorted by readers](decisions/20260920-quality-sorted-by-readers.md)).

### FLOW-031 — A reference is inserted by a trigger character or by a control on the step

The system shall offer the recipe's own ingredients for insertion into a step's text
both when the author types a trigger character and when the author uses a visible
control on that step, and shall offer no ingredient of any other recipe.

**Acceptance criteria**
- Given a step being written, when the author types the trigger character, then the
  recipe's ingredients are offered and choosing one inserts a reference at that
  position.
- Given the same step, when the author uses the control on the step instead, then the
  same list is offered and the result is the same reference, so an author who never
  discovers the trigger character loses nothing.
- Given the list of ingredients offered, when it is shown, then it holds only
  ingredients of this recipe (DOM-007) and no ingredient whose unit type is
  `taste_unmeasured`, because such an ingredient carries no quantity to show
  (DOM-002).
- Given an inserted reference, when the author deletes the character range it
  occupies, then the reference is gone and the step's text is left as the author
  typed it.
- Given a reference to an ingredient, when that ingredient is deleted from the
  recipe, then DOM-007 governs and the reference is dropped.

**Verification:** insert references both ways in one step, attempt to reference a
`taste_unmeasured` ingredient, and delete a referenced ingredient.
**Source:** user decision, 2026-09-20, adopting the spec author's proposal. The
trigger character is for whoever expects one; the control is why the feature is not
hidden from everybody else, and the product's readers and authors are not programmers
(`market-context`).

### FLOW-032 — A reference renders as the quantity alone

The system shall render an ingredient reference in a step as that ingredient's
quantity and unit at the reader's current scale, and shall render no part of the
ingredient's name.

**Acceptance criteria**
- Given a recipe authored for 4 servings with 200 g of flour and a step reading
  `всыпьте [200 г] муки`, when the reader asks for 8 servings, then the step reads
  `всыпьте 400 г муки` and the word `муки` is the author's own text, untouched.
- Given the same step at any scale, when it is read, then the value in the reference
  equals the value the ingredient list shows for that ingredient (DOM-007).
- Given a scaled value that the rounding grid changed, when the step is read, then
  the reference carries the same derived marker the list carries (SCALE-004), so the
  step never states a rounded number as though it were exact.
- Given a value held at the floor of its unit type, when the step is read, then it
  shows the floor exactly as the list does (SCALE-005).
- Given the editor, when the author inserts a reference, then what they see in place
  of it is the authored quantity, because the authored scale is the recipe's own.

**Verification:** scale a recipe up, down and to a value the grid rounds, and compare
every step's reference against the ingredient list line by line.
**Source:** user decision, 2026-09-20, adopting the spec author's proposal. The name
is left out because a substituted name would arrive in one grammatical case and every
sentence needs its own; the author already writes the word, and only the number has
to change under scaling.

## Open

- For the editor flow: whether a recipe carrying more than one timer must label
  them, since FLOW-016 falls back to a step's position when a label is missing.
- For the editor flow: the form the divisibility override takes, given FLOW-023
  admits only three fields — a secondary control on the row, or somewhere else.
- The exact trigger character of FLOW-031, which is a presentation choice and is
  settled with the editor's visual design, not here.
- Whether a step may link to another recipe is OQ-024.
- The rest of the screens: catalogue, favourites, the moderator's queue, the key
  file.
