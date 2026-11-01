---
slug: FLOW
status: closed
updated: 2026-11-01
ids: [FLOW-013, FLOW-014, FLOW-015, FLOW-016, FLOW-017, FLOW-018, FLOW-020]
---

# Cooking from a recipe

The card is the screen the product exists for: it is where a person cooks. It has
two tabs, View for reading and Cooking for busy hands (FLOW-070); on Cooking the
current step is the first one not marked done (FLOW-068). What the card holds is
settled elsewhere — quantities by `scaling-rules`, provenance by `forking`.
Layout, wording, empty and loading states and the details of timers on screen are
the team's.

### FLOW-013 — A timer is heard even if the application is closed

When a step's timer ends, the system shall announce it whether the application is
in the foreground, in the background or closed, and with the screen locked.

- The announcement names the recipe and the timer (FLOW-016); acting on it opens
  the step the timer belongs to.
- It rings like an alarm clock — the device's alarm sound, repeating, with
  vibration — until the reader stops it, and falls silent by itself 2 minutes after
  it began. It is never mistakable for a message notification.
- It is the application's own timer, counted down on its step; it is not handed to
  the device's clock application.
- Cancelling a running timer is a control of its own, apart from the time it shows,
  and can be undone while its notice shows. Ended timers of one card are stopped
  together by one control, or one by one.
- Where the reader has not permitted notifications, the timer still runs and ends
  inside the application, and the reader is told, when they start one, that it will
  not be heard while the application is closed, with a way to the system settings.
  The timer never waits for that answer.

**Source:** user decision, 2026-09-20: something goes in the oven for thirty
minutes and the application is closed, so the timer cannot depend on it staying
alive. Alarm-like ringing: user decision at the v0.2 demo, 2026-10-02 — one short
sound was taken for a messenger message. See
[ADR — a timer is the application's own and rings like an alarm](../decisions/20261002-a-timer-rings-like-an-alarm.md).

### FLOW-014 — The screen does not sleep while cooking

While a card is open on its Cooking tab, or while one of its timers runs, the
system shall keep the screen awake, and shall leave it to the device's own setting
after 15 minutes without a touch, or at once when the reader leaves the card.

**Source:** user decision, 2026-09-20. Hands are dirty while cooking; fifteen
minutes is the owner's number, so that a card left on the counter does not drain
the battery all evening.

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

### FLOW-016 — Several timers at once, each named by its step unless the author names it

The system shall let the timers of several steps run at the same time, and shall
identify each, wherever it is announced, by the name its author gave it, or, where
the author gave none, by the beginning of its step's text — up to 60 characters in
an announcement. A timer is never identified by its step's number.

**Source:** user decision, 2026-09-20. A timer that fires without saying what it is
for makes the cook guess between the oven and the pan; the author has already
written «курица в духовку на 30 минут», and asking for it again would be asking
twice. 60 characters is the owner's number, so that the line survives a
notification uncut. See
[ADR — a timer is named by its step](../decisions/20260920-timer-named-by-its-step.md).

### FLOW-017 — The reader's usual serving count opens every recipe

Where the reader has set the serving count they usually cook for, the system
shall open every recipe at that count, with every changed quantity marked as
derived (SCALE-004). A count the reader changes on one recipe holds while they
keep cooking in this session, and changes neither the setting nor how the recipe
opens next time.

**Source:** user decision, 2026-09-20. A household does not change from one
evening to the next; a reader who scaled a recipe for twelve guests is unlikely to
have them again next time. The cost is deliberate: almost every recipe opens
derived, which is why the derived markings carry the weight they do.

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

### FLOW-020 — No quantity before the reader's scale is known

While a recipe is opening, the system shall show no quantity until the scale that
applies to the reader is known (FLOW-017).

**Source:** derived from FLOW-017: a quantity shown at the authored scale and then
replaced is a number a reader can act on before it changes — a wrong amount of an
ingredient.
