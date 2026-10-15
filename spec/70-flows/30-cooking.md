---
slug: FLOW
status: closed
updated: 2026-10-15
ids: [FLOW-013, FLOW-014, FLOW-015, FLOW-016, FLOW-017, FLOW-018, FLOW-019,
  FLOW-020, FLOW-021]
---

# Cooking from a recipe

The card is the screen the product exists for. Timers, the screen that will not
sleep, the serving count the reader always uses, and what the card does when parts
of a recipe are absent or will not load.

## The recipe card

The card is the screen the product exists for: it is where a person cooks. What
it holds is settled elsewhere and referenced here — quantities and their
markings by `scaling-rules`, provenance by FORK-004 and FORK-005, rating and
favourites by `ratings`, reporting and blocking by `abuse`. What follows is what
the card itself must do.

Since 2026-10-11 the card has two tabs, View and Cooking (FLOW-070); "the
cooking card" elsewhere in this specification is the Cooking tab. Nothing advances
from one step to the next: on Cooking the current step is simply the first one not
marked done (FLOW-068), and marking a step brings the next into view. The reader
scrolls, and what the product restores is the place they had scrolled to on each
tab (FLOW-012). A timer that ends leads to its own step (FLOW-013). See
[ADR — the card has a tab for reading and a tab for cooking](../decisions/20261011-the-card-has-a-reading-and-a-cooking-tab.md),
which supersedes the one-scrolling-screen decision of 2026-09-21.

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
  nothing announces it while the application is closed or the screen is locked;
  what the reader is shown about the permission is stated by criteria 8 to 10
  and 13.
- Given a running timer, when the reader opens the recipe, then the time
  remaining is shown on its step.
- Given a running timer, when the reader cancels it, then nothing announces
  itself afterwards.
- Given a platform that has a request for the permission to show notifications
  (Android 13 and later) and a device on which it has never been asked for, when
  the reader starts their first timer, then the platform's request is made at that
  moment, and at no earlier moment such as the first launch; on a platform that
  has no such request (Android 10 to 12) nothing is requested, and criteria 10 and
  11 govern.
- Given notifications are not permitted, when the reader starts a timer and the
  platform will still show its own request, then that request is made; and when
  the reader refuses the request made here or by criterion 8, then the
  explanation of criterion 10 is shown at once, before the reader returns to the
  recipe.
- Given notifications are not permitted, when the reader starts a timer and the
  platform will not show a request — because it has none to show or because the
  reader has refused it as often as it allows — then the application shows its own
  explanation that the timer will not be heard while the application is closed or
  the screen is locked, with a control that opens the system's notification
  settings for the application; the timer starts either way.
- Given notifications are permitted, when the reader starts a timer, then nothing
  about the permission is shown.
- Given any request or explanation of criteria 8 to 10, when it is shown, then the
  timer has already started at the moment the reader started it and runs whatever
  the reader answers.
- Given the application's notifications are permitted and those of its timers
  alone are turned off in the system's settings, when the reader starts a timer,
  then notifications count as not permitted: the explanation of criterion 10 is
  shown, stating that timer notifications are turned off and the timer will not be
  heard, and its control opens those settings.
- Given a timer that ends with notifications permitted, when it announces itself,
  then it rings like an alarm clock — the device's alarm sound, repeating, with
  vibration — until the reader stops it from the announcement or from the timer
  on its step, and falls silent by itself 2 minutes after it began; it is never
  mistakable for an ordinary message notification.
- Given two timers that have ended, when one is stopped, then the other keeps
  ringing until it is stopped or falls silent by itself.
- Given a step's timer, when it runs and ends, then it is the application's own
  timer, counted down on its step; it is not handed to the device's clock
  application.
- Given a running timer, wherever it is shown with its controls — on its step or
  among the running timers gathered on the card — when the reader looks for how to
  cancel it, then cancelling is a control of its own, apart from the control that
  shows the time, so that a hand reaching for the timer cannot cancel it by
  accident; and a cancel can be undone while its notice shows, which puts the
  timer back as it was.

**Verification:** start a timer, close the application from the task switcher,
lock the device and wait for the end; repeat with notifications refused; start a
first timer on a fresh install, refuse the request and confirm the explanation
follows at once while the timer runs; repeat the refusal until the platform stops
asking; on an Android 10 device start a first timer with notifications on and
confirm nothing is shown, then turn them off in settings and confirm the
explanation of criterion 10.
**Correction, 2026-09-27:** criterion 5 had the permission "asked for again" where
on Android 10–12 there is no request to make and on later versions the platform
stops showing one after refusals, placed no first request, and was read two ways
against A11Y-007 (3) (SD-021). By user decisions of that date: the first request
comes with the first timer; where the platform will not ask, the application
explains and leads to the settings; and a timer with notifications refused is
not heard while the application is closed.
**Completed 2026-09-27:** after the re-check of SD-021 criterion 5 still carried
"asked for again only when the reader next starts a timer", which criteria 8 to 10
had replaced, and a reader who refused the platform's request at a timer's start
was told nothing, while A11Y-007 (3) says they have been told. By user decision of
that date a refusal at that moment is followed at once by the explanation, and the
timer never waits for the answer. Criterion 12 is new. See
[ADR — a refusal is explained at once](../decisions/20260927-a-refusal-is-explained-at-once.md).
**Corrected 2026-09-27:** criterion 8 required the platform's request on every
device, while Android 10 to 12 have none, so it could be met on no device the
product supports below Android 13 and collided there with criteria 10 and 11
(SD-021, re-check against spec/ @ 4cc2c18). By user decision of that date the
request is made only where the platform has one; below that, notifications on
means nothing is shown and notifications off means the explanation. By the same
decision, timer notifications turned off alone count as notifications off
(criterion 13, new), since otherwise the timer would be silent with nobody told
(A11Y-007 (3)). See
[ADR — no request where the platform has none](../decisions/20260927-no-request-where-the-platform-has-none.md).
**Source:** user decision, 2026-09-20: a person may start a timer on the last
step and close the application entirely — something goes in the oven and is
taken out in thirty minutes — so the timer cannot depend on the application
staying alive.
**Changed 2026-10-02:** criteria 14 to 16 added by user decision at the v0.2
demo: the first build announced an ended timer with one short sound, easy to take
for a messenger message in a kitchen. Handing timers to the device's clock
application was considered and rejected. See
[ADR — a timer is the application's own and rings like an alarm](../decisions/20261002-a-timer-rings-like-an-alarm.md).
**Changed 2026-10-15:** criterion 17 added by user decision of that date
(`docs/brand/identity/decisions.md`, fixes to the redesign): cancelling a running
timer is a separate, quiet control, and the undo that follows it stays. Its look
and placement are the team's.
**Note:** this states what the reader observes. Whether it is implemented by a
system alarm or otherwise is an engineering choice, provided a closed
application still announces the timer.

### FLOW-014 — The screen does not sleep while a recipe is being read

While a recipe card is in the foreground on its Cooking tab (FLOW-070), or while
one of its timers runs, the system shall keep the screen awake, and shall stop
keeping it awake after 15 minutes without a touch. On the View tab with no timer
running, the screen follows the device's own setting.

**Acceptance criteria**
- Given a recipe card open on Cooking and untouched for a minute, when the
  device's own screen timeout is shorter than that, then the screen is still on.
- Given a recipe card open and untouched for 15 minutes, when that time passes,
  then the screen is left to the device's own setting.
- Given the screen was left to sleep that way, when the reader touches the card,
  then it is kept awake again for another 15 minutes without a touch.
- Given a card that is keeping the screen awake, when the reader leaves the
  card, then the screen is left to the device's own setting at once.
- Given a running timer and no touch for 15 minutes, when the screen sleeps,
  then the timer still announces itself (FLOW-013).
- Given a card on View with no timer running, when it is left untouched, then
  the screen follows the device's own setting.

**Verification:** open a card on a device with a 30-second screen timeout, do not
touch it, and observe at one minute, at fifteen minutes and after a touch.
**Source:** user decision, 2026-09-20. Hands are dirty while cooking and waking
the screen costs a knuckle; fifteen minutes is the product owner's number, set
so that a card left open on a counter does not drain the battery all evening.
**Changed 2026-10-11 (sync v0.22–v0.30):** the rule follows the Cooking tab and a
running timer, as built in v0.27; reading a recipe on View is not cooking.

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
- Given an unchanged fork that was never sent (FLOW-059), when the reader forks
  the same recipe again, then it counts exactly as one the server holds.

**Verification:** fork a recipe twice without touching the first fork; change the
first fork and repeat; confirm both outcomes and the explicit second fork.
**Source:** user decision, 2026-09-20. Two branches from one original are the
point of the product, so a second fork is never forbidden; an untouched fork,
however, is the signature of a misplaced tap and collects as rubbish in the
author's own list.

### FLOW-016 — Several timers run at once, each named by its step unless the author says otherwise

The system shall allow the timers of several steps to run at the same time, and
shall identify each one wherever it is shown or announced by the name its author
gave it, or, where the author gave none, by the first 60 characters of its own
step's text.

**Acceptance criteria**
- Given two steps that carry timers, when the reader starts both, then both run
  and each is shown running on its own step.
- Given two running timers, when one ends, then it announces itself and the
  other keeps running.
- Given a timer whose author gave it no name, when it ends, then the
  announcement carries the recipe's title and the first 60 characters of its
  step's text, cut at a word boundary, with an ellipsis where the text was
  longer.
- Given a timer on a step of 60 characters or fewer and with no name of its own,
  when it ends, then the step's whole text is in the announcement and nothing
  marks it as cut.
- Given a timer whose author named it, when it ends, then the announcement
  carries the recipe's title and that name, and no part of the step's text.
- Given a timer whose author named it, when its step's text is later changed,
  then the name is unchanged, because it was overridden and no longer follows the
  step.
- Given a timer whose author gave it no name, when its step's text is changed,
  then what the announcement will say for any later start changes with it,
  because the name is derived and not stored.
- Given a step whose first 60 characters hold an ingredient reference, when a
  derived name is built from it, then the reference reads there exactly as it
  reads on the card, at the scale the reader is using (FLOW-032).
- Given a timer already running, when the reader changes the scale, then the
  timer keeps the name it started with, a reference's quantity read at the scale
  of that moment, while the derived name of a timer not running on the card
  follows the new scale.
- Given a step holding media and no text (DOM-006), when it carries a timer, then
  there is no text to derive a name from, and the author names the timer.
- Given several running timers, when the reader opens the recipe, then each
  shows its own remaining time on its own step, under the same name the
  announcement would use.
- Given several running timers, when the reader leaves the application and
  returns, then all of them still run by the clock (FLOW-012).
- Given two timers of different steps that end up with the same name, whether
  derived or given, when both end, then the two announcements read alike and the
  product does nothing to tell them apart.
- Given two or more timers of one step with no name of their own, when they are
  shown or announced, then the reader can tell them apart (DOM-024); how is the
  team's.

**Verification:** start four timers in one recipe — one on a short step, one on a
step longer than 60 characters, one on a step carrying an ingredient reference,
and one the author named by hand — leave the application, and compare every
announcement against the step text and the given name.
**Source:** user decision, 2026-09-20. A timer that fires without saying what it
is for makes the cook guess between the oven and the pan, which is the moment
the timer existed to prevent. The default name is the step's own text: the author
has already written `курица в духовку на 30 минут`, and asking them to type it
again would be asking twice for one fact. The name is overridable because the
opening of a step is not always the point of the wait, and an author who can see
that is the one who should fix it. The step's position is never used, because a
number tells the cook nothing about which pan. See
[ADR — a timer is named by its step](../decisions/20260920-timer-named-by-its-step.md).
Amended 2026-10-03 (sync v0.3–v0.14): a running timer keeps the name it started
with, and a timer on a step with no text is named by its author — req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03.
Amended 2026-10-04 (sync v0.15–v0.24): a step may carry several timers (DOM-024),
so unnamed timers of one step are told apart; same names across steps still are
not — req-forge proposal from that sync, accepted by the user, 2026-10-04.
**Note:** 60 characters is the product owner's number, set so that the line
survives a notification without the operating system deciding where to cut it. It
bounds a name the author types as well (DOM-012). That the first 60 characters
carry the weight, and that the name can be corrected, is taught in
`author-guidance` rather than left to be discovered.

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
  they open that recipe again in a later session (FLOW-058), then it opens at
  their usual count and not at the count they used last time.
- Given a reader who scaled a recipe and stays in the application, when they
  leave the card and come back to it, then the count they set is still there.
- Given the count, the fixing and the place held for the session, when more than
  50 recipes have been shown since a recipe was last shown, then that recipe
  opens at the usual count: the session holds them for the 50 recipes most
  recently shown.

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
markings of SCALE-004 carry the weight they do. Amended 2026-10-03 (sync
v0.3–v0.14): what the session holds is bounded to the 50 recipes most recently
shown — req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03.
**Note:** a count the reader set survives for as long as the session lasts
(FLOW-058), including leaving the card and returning to it; a fresh start of the
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
