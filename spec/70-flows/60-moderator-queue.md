---
slug: FLOW
status: closed
updated: 2026-09-21
ids: [FLOW-044, FLOW-045, FLOW-046, FLOW-047, FLOW-048, FLOW-049, FLOW-050,
  FLOW-056]
---

# The moderator's queue

The moderator works inside the same application as everyone else (CON-005); the
role is granted to an ordinary account (ACT-003) and is the only thing that puts
this section in front of it. What a moderator decides, and on what grounds, is
in [`moderation`](../100-moderation.md); what reports do before a moderator sees
them is in [`abuse`](../170-abuse.md). This part holds the screen.

### FLOW-044 — The moderation section belongs to the role, and holds two lists

Where an account holds the moderator role, the system shall present a moderation
section holding the submission queue of MOD-006 and the flagged-descendant list
of MOD-009 as two separate lists, and shall present no such section to any
account without the role.

**Acceptance criteria**
- Given an account without the role, when it uses the application, then no
  moderation section is reachable anywhere in it.
- Given the role is granted to an account, when that account next uses the
  application, then the section is present with both lists and nothing was
  reinstalled (ACT-003).
- Given the role is revoked, when the account next uses the application, then the
  section is gone and every moderator action is refused.
- Given the submission queue, when it is shown, then it holds submissions
  awaiting a decision (MOD-001), recipes hidden by counted reports (ST-006),
  reports that did not count (ABUSE-004) and reports about an author — one entry
  per reported author (ABUSE-006) — all ordered oldest first (MOD-006).
- Given the flagged-descendant list, when it is shown, then it holds what MOD-009
  puts there, ordered deepest descendant first, and a flagged recipe in `private`
  is absent from it.
- Given either list, when an entry is shown, then it carries nothing about the
  author beyond the nickname (CON-001).
- Given no network connection, when the section is opened, then FLOW-003 governs
  it and no entry is shown from anything the device holds.

**Verification:** grant and revoke the role on a test account and inspect the
navigation in both states; populate the queue with a submission, a hidden
recipe, an uncounted report and a flagged chain, and inspect both lists.
**Source:** user decision, 2026-09-20 (the moderator uses the Android
application, CON-005) together with MOD-006 and MOD-009, which already decided
that the two lists are ordered by opposite principles and therefore cannot be
one list.

### FLOW-045 — What a queue entry says before it is opened

The system shall show on every entry of the submission queue that concerns a
recipe its title, its author's nickname, the time the entry entered the queue,
what put it there, the mark the text filter left (MOD-002), and the safety flag
where the recipe carries one; and on an entry that concerns an author, that
author's nickname, the number of reports it holds and the categories they
carried.

**Acceptance criteria**
- Given a submission for publication and a submission for a private link, when
  both are listed, then the two are distinguishable without opening either
  (MOD-001).
- Given a recipe hidden by counted reports, when it is listed, then the entry
  states that reports hid it and lists the categories recorded against it
  (ABUSE-002).
- Given a report that did not count, when it is listed, then the entry states
  that the recipe is still visible and carries the report's category
  (ABUSE-004).
- Given a submission the text filter flagged, when it is listed, then the mark is
  on the entry and no decision has been taken on the strength of it (MOD-002).
- Given a submission of a recipe carrying the safety flag, when it is listed,
  then the flag is on the entry (MOD-009).
- Given any entry, when it is shown, then it states when it entered the queue, so
  that the age MOD-006 orders by is readable.
- Given an entry whose recipe holds media, when it is listed, then the media is
  not shown on the entry and is reviewed on opening it (MOD-003).
- Given an entry about an author, when it is listed, then it is distinguishable
  from every entry about a recipe without being opened, states how many reports
  it holds and under which categories, names no reporter (ABUSE-006), and opening
  it leads to that author's screen (FLOW-055), which is where the author's
  recipes and the ban are.

**Verification:** build one entry of each kind — publication, private link,
counted reports, uncounted report, filter-flagged, safety-flagged — and read the
list without opening anything.
**Source:** user decision, 2026-09-20: the queue is worked by one person, so an
entry states what it is before it is opened; every field here is one MOD or
ABUSE already decided, shown at the point the moderator chooses what to open.

### FLOW-046 — Deciding an entry, and the reason a rejection carries

When a moderator records a decision on a queue entry, the system shall accept an
approval or a rejection, shall require free text as the reason of a rejection,
and shall remove the entry from the queue once the decision is recorded.

**Acceptance criteria**
- Given a queue entry, when the moderator rejects it, then free text must be
  entered before the rejection is recorded, and no category is offered or
  required (MOD-004).
- Given an approval, when it is recorded, then no reason is asked for and the
  recipe moves as the state table requires — to `published` or to `link_shared`
  by its trigger.
- Given an entry about an author (ABUSE-006), when the moderator decides it, then
  the two decisions it admits are dismissing it and banning that author
  (FLOW-056), neither asks for a reason, and either takes the entry out of the
  queue; no approval or rejection is offered on it, because an author is not a
  submission.
- Given an author already banned, when a further report about them arrives, then
  a new entry is created as ABUSE-006 requires, because a ban stops publication
  and stops nobody reporting.
- Given an entry whose recipe carries the safety flag (FLOW-045), when the
  moderator works on it, then clearing the flag is offered there beside the
  decision, and it is recorded independently of whether the submission is
  approved or rejected (MOD-009, MOD-010).
- Given a decision has been recorded, when the queue is shown again, then the
  entry is no longer in it.
- Given two moderators open the same entry and one decides it, when the second
  tries to decide it, then the attempt is refused, the entry states that it has
  already been decided, and the decision already recorded is unchanged (MOD-007).
- Given an entry the moderator opened but did not decide, when they leave it,
  then it stays in the queue in its place by age (MOD-006).
- Given the queue itself, when the moderator decides an entry from the list
  without opening it (FLOW-049), then the decision is recorded exactly as if it
  had been taken inside the entry.
- Given a decision taken from the list, when it is recorded, then the moderator
  stays in the list and the next entry is not opened for them.
- Given no network connection, when a decision is attempted, then it is refused
  and nothing is recorded (FLOW-003).
- Given a recipe under review, when the moderator works on it, then no action
  available to them changes its content (ACT-004).

**Verification:** decide entries of each kind through both outcomes; attempt a
second decision from a second moderator account; attempt a decision offline.
**Source:** user decision, 2026-09-20: the reason is free text for now, with no
fixed list of categories. The cost, stated and accepted: nothing can be counted
across rejections, and two rejections for the same fault may read differently to
two authors. Recorded as OQ-026 for after the MVP.

### FLOW-047 — A moderator acts on a recipe from its card

Where an account holds the moderator role, the system shall offer removal on the
card of any recipe in `published` or `link_shared`, and shall require the
moderator to choose exactly one category — danger to health, or use of someone
else's content — before the removal is recorded.

**Acceptance criteria**
- Given a moderator browsing the catalogue, when they open a published recipe
  nobody has reported, then removal is offered on its card.
- Given that removal, when it is recorded, then a category was chosen, and the
  category alone decides whether every descendant is flagged (MOD-008).
- Given a moderator upholding the reports against a hidden recipe, when the
  removal is recorded, then the category is chosen by the moderator and is not
  taken from the reports, even where every report carried the same one.
- Given an account without the role, when it opens the same card, then no
  removal is offered and the report action of ABUSE-002 is what it has.
- Given a recipe in `private`, `pending` or `rejected`, when a moderator reaches
  it, then removal is not offered, because removal acts on what is externally
  visible (ST-002).
- Given the removal is recorded, when the card is opened again by anyone, then
  ST-007 governs what a fork of it shows and no reason is given to any reader
  except as FLOW-010 requires.
- Given a moderator's own published recipe, when they open it, then removal is
  offered on it exactly as on anyone else's, and the category is chosen the same
  way (MOD-014).

**Verification:** remove a published recipe from its card in each category and
inspect the descendants; attempt the same as a non-moderator and on a private
recipe; remove one of the moderator's own recipes.
**Source:** user decision, 2026-09-20; the exemption for the moderator's own
recipe was removed 2026-09-21 (MOD-014). The queue only holds what someone else
put there, so without an action on the card a recipe nobody reported could never
be removed, however plainly it breaks a rule. The category is the moderator's
own choice because MOD-008 makes it consequential — it decides whether an
unbounded number of descendants are flagged — and reporters cannot be trusted
with that.

### FLOW-048 — The moderation section when there is nothing in it, and when it cannot be loaded

The system shall state in each list of the moderation section when that list is
empty, shall offer no action from an empty list, and shall behave on loading and
on failure as FLOW-043 requires of any listing.

**Acceptance criteria**
- Given nothing awaits a decision, when the moderator opens the queue, then it
  states that there is nothing to review and offers no action, because no action
  belongs to a moderator with an empty queue.
- Given no recipe carries the safety flag, when the flagged list is opened, then
  it states so and offers no action.
- Given the queue is being loaded, while it is loading, then no entry from a
  previous loading is shown (FLOW-043).
- Given the load fails while a connection exists, when the failure is known, then
  the section states it and offers to retry.
- Given no network connection, when the section is opened, then it states that a
  connection is needed and shows nothing (FLOW-003).

**Verification:** empty both lists, then open them with the server refusing
requests and with the connection cut.
**Source:** user decision, 2026-09-20, applying the empty-listing rule of
FLOW-042 to this section: the offer there is the action that leads out of the
empty listing, and a moderator whose queue is empty has none — the work arrives
on its own.

### FLOW-049 — A decision can be taken from the list, and every such decision is confirmed

The system shall offer on every entry of both lists, without opening the entry,
the decisions that entry admits, and shall require an explicit confirmation
before recording any of them.

**Acceptance criteria**
- Given a queue entry in the list, when the moderator gestures on it, then the
  decision that gesture stands for is named and a confirmation is asked for
  before anything is recorded.
- Given one gesture direction and the other, when they are offered, then one
  records the restricting decision — reject, uphold the reports, remove, ban the
  author — and the other the releasing one — approve, dismiss the reports, clear
  the flag, dismiss the reports about an author — and the mapping is the same in
  both lists.
- Given a gesture that bans an author, when the confirmation is shown, then it
  names what the ban stops and what it leaves alone (ACT-005, ACT-006) and the
  ban is not recorded without it.
- Given a gesture that rejects a submission, when the confirmation is shown, then
  the free-text reason of FLOW-046 is entered there and the rejection is not
  recorded without it.
- Given a gesture that removes a recipe, when the confirmation is shown, then the
  category of FLOW-047 is chosen there and the removal is not recorded without
  it.
- Given a confirmation, when the moderator dismisses it, then nothing is
  recorded, the entry keeps its place in the list, and the gesture leaves no
  trace.
- Given a decision recorded from the list, when the list is shown again, then
  that entry is gone from it (FLOW-046).
- Given no network connection, when a gesture is made, then FLOW-003 governs and
  no confirmation is offered.

**Verification:** take every decision each list admits by gesture, dismiss the
confirmation in each case, and confirm nothing was recorded; then record one of
each and inspect the recipe's state.
**Source:** user decision, 2026-09-20: a decision must be reachable from the list
for the case that cannot wait, and every one of them is confirmed — in both
directions, not only the destructive one. An unintended release is as expensive
as an unintended removal, because undoing either means finding that one recipe
again in a system that offers no way to search for it.

### FLOW-050 — Working a list through, one entry after another

Where the moderator starts a run on a list, the system shall present its entries
one at a time in that list's own order, shall open the next entry as soon as a
decision on the current one is recorded, and shall end the run when the list is
exhausted or the moderator leaves it.

**Acceptance criteria**
- Given the submission queue, when a run is started, then entries arrive oldest
  first (MOD-006); given the flagged list, then they arrive deepest descendant
  first (MOD-009).
- Given an entry shown in a run, when the moderator records a decision on it,
  then the next entry opens without returning to the list.
- Given an entry shown in a run, when the moderator records a decision by
  gesture, then the confirmation of FLOW-049 is asked for exactly as it is in
  the list.
- Given an entry the moderator will not decide now, when they pass it, then it
  keeps its place in the list, the run moves to the next entry, and the passed
  entry does not return within that run.
- Given the last entry of the list, when a decision on it is recorded, then the
  run ends and states that the list is exhausted.
- Given a run in progress, when the moderator leaves it, then every decision
  already recorded stands and the entries not yet reached are untouched in the
  list.
- Given an entry another moderator decided while the run was in progress, when
  the run reaches it, then it is not offered for a decision (MOD-007).
- Given an entry whose owner withdrew it while the run was in progress, when the
  run reaches it, then it is not offered either, and if the moderator had already
  opened it the refusal of MOD-011 is what they see.

**Verification:** run a queue of several entries to exhaustion, pass some and
decide others, leave a run halfway and inspect the list, and have a second
moderator decide an entry mid-run.
**Source:** user decision, 2026-09-20: two modes, because picking one entry out
of the list and working the whole list through are different tasks for the same
person. The run is what makes the stated waiting time (MOD-005) achievable by
one moderator; the list is what makes an urgent single case reachable without
starting one.
**Note:** the run is read as available on both lists, because the product owner
named the mode for "the list" and both lists are worked by the same person.

### FLOW-056 — A ban is applied and lifted on the author's screen

Where an account holds the moderator role, the system shall offer on an author's
screen (FLOW-055) to ban that author from publishing and to lift an existing
ban, shall require an explicit confirmation for each, and shall offer the ban
nowhere else except as the restricting decision FLOW-049 admits on a queue entry
about that author.

**Acceptance criteria**
- Given a moderator opening the screen of an author who is not banned, when it
  is shown, then banning is offered there; given an author who is banned, then
  the screen states that they are and offers to lift it.
- Given the confirmation, when it is shown, then it names what the ban stops
  (ACT-005) and what it leaves alone — the account keeps writing, forking,
  editing, scaling, searching and favouriting, and its published recipes stay in
  the catalogue (ACT-006) — and nothing is recorded until it is given.
- Given the ban recorded, when the banned account next attempts an act ACT-005
  rejects, then it is told inside the application (ACT-007), because there is no
  channel to a device from outside it (EXT-009).
- Given the ban lifted, when the account submits again, then the submission is
  accepted and nothing of what it held changed while the ban stood.
- Given an account without the moderator role, when it opens the same screen,
  then neither control is present and no space is reserved for one; what it has
  there is reporting and blocking (FLOW-055).
- Given a moderator opening their own screen, when it is shown, then the same two
  controls are offered on the same terms, because ownership suspends nothing
  (MOD-014) and a ban touches publication and not the role — the account that
  banned itself can lift it.
- Given the ban or its lifting, when what else changes is enumerated, then it is
  nothing: no recipe moves state, no rating, favourite or fork count changes, and
  no descendant is touched.
- Given no network connection, when either control is used, then FLOW-003
  refuses it, because only the server records the state of an account.

**Verification:** ban an author from their screen and from a queue entry about
them, decline one confirmation and give the other; attempt a submission and a
publication from `link_shared` as the banned account; lift the ban and repeat;
open the same screen without the role and in aeroplane mode.
**Source:** user decision, 2026-09-21. ACT-005, ACT-006 and ACT-007 described a
ban in full — what it stops, what it leaves and what the banned account is told —
and no screen anywhere in `flows` offered it, so the one action a moderator has
against a person rather than against a recipe could not be performed at all. It
lives on the author's screen for the reason deletion lives on the recipe's entry
(FLOW-053): the act belongs to the thing it acts on. See
[ADR — the author has a screen, and reports about them collapse onto it](../decisions/20260921-the-author-has-a-screen.md).
