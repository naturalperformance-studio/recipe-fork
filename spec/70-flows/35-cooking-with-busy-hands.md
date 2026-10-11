---
slug: FLOW
status: closed
updated: 2026-10-11
ids: [FLOW-068, FLOW-069, FLOW-070]
---

# Cooking with busy hands

Added on 2026-10-04 by the sync of v0.15–v0.24, beside
[30-cooking.md](30-cooking.md), which was at its size limit: marking what is done,
and controls that answer the hand. The card's two tabs (FLOW-070) were added on
2026-10-11 by the sync of v0.22–v0.30.

### FLOW-070 — The card has a tab for reading and a tab for cooking

The recipe card shall hold two tabs, always in sight under its top bar — **View**,
compact, for reading and deciding, and **Cooking**, large, for busy hands — switched
by a tap or a horizontal swipe. Each tab keeps its own place.

- **View** shows the whole recipe in this order: the title block (title, a summary
  of what the recipe holds, the stated author, where a fork came from), the
  description, the equipment, the ingredients, the steps. Its lines are compact.
  Done marks are shown but not changed. Timers show their duration and do not
  start. The serving count can be changed. Forking is View's primary action,
  always in reach on the screen, never hidden in a menu; the edit action sits in
  the bar (FLOW-029).
- **Cooking** holds only what cooking needs: the equipment, the ingredients and the
  steps, with no cover, no description, no fork and no edit action. It is the
  cooking card of A11Y-003 and A11Y-007: larger targets and larger step text. A tap
  marks a line done (FLOW-068), and timers start here. The first step not yet
  marked done is shown as the current one, and after a step is marked the tab
  brings the next unmarked step into view. A list whose lines are all marked folds
  into one line that opens it again.

The current step is derived from the done marks and nothing else: no step is
stored as current, and there is no control that advances from one step to the
next.

**Acceptance criteria**
- Given a card, when it is opened, then both tabs are in sight and one tap or one
  swipe switches them.
- Given a card on View, when the reader looks for the fork action, then it is on
  the screen and not inside a menu.
- Given a card on Cooking, when its controls are listed, then there is neither a
  fork nor an edit action.
- Given marks on steps one and two of a recipe, when Cooking is shown, then step
  three is shown as the current step; given no marks, then step one is.
- Given every ingredient marked done, when Cooking is shown, then the ingredient
  list is folded into one line, and opening it shows every line again.
- Given a timer's announcement, when the reader follows it, then the card opens on
  Cooking at the timer's step (FLOW-013).
- Given a card, when the reader opens a recipe, then it opens on the tab last
  chosen on this device.

Which tab opens the first time, the summary's wording, and how the current step and
the folded line look are the team's.

**Source:** user decision, 2026-10-09 (planning of v0.27): two tabs, always
visible, switched by swipe too; nothing on Cooking that distracts from cooking — no
fork, no edit. Forking as View's primary action, not hidden: user decision,
2026-10-09 (v0.26: "Fork, the main feature, should not be hidden on the recipe
screen"). The description before the ingredients: user decision, 2026-10-10. The
current step, the folding, the larger step text and the summary are the lead's
ideas, taken with the user's agreement at that planning and accepted with the
builds at the sync of 2026-10-11. See
[ADR — the card has a tab for reading and a tab for cooking](../decisions/20261011-the-card-has-a-reading-and-a-cooking-tab.md).

### FLOW-068 — The reader marks ingredients and steps as done

When the reader taps an ingredient, an equipment item or a step on the card's
Cooking tab (FLOW-070), the system shall mark it done, and tapping it again shall
unmark it. The View tab shows the marks and does not change them. The marks are the reader's, kept on
the device for that recipe until the reader clears them; they are never part of
the recipe.

**Acceptance criteria**
- Given an ingredient or a step marked done, when the card is shown, then it is
  visibly marked and the screen reader reads it as done.
- Given marks on a recipe, when the reader goes to the book, switches application
  or restarts the device and comes back, then the marks are still there.
- Given marks on a recipe, when the reader chooses to clear the marks on its card,
  then every mark of that recipe goes.
- Given marks on a recipe, when it is exported, forked or sent, then the file, the
  fork and the server copy carry no marks.
- Given marks on a recipe, when the servings change or an amount is fixed, then
  the marks stay where they were.

How a mark looks, and where the clearing control sits, is the team's.
**Changed 2026-10-11 (sync v0.22–v0.30):** marking moved to the Cooking tab when
the card split into two (v0.27), and equipment items are marked like ingredients
(v0.27).

**Source:** user decision, 2026-10-04 (planning of v0.23, recommended option
chosen). The marks are what the reader keeps on their own device, so RET-010
still holds: nothing about them is sent anywhere. See
[ADR — done marks live on the device](../decisions/20261004-done-marks-live-on-the-device.md).

### FLOW-069 — Controls used while cooking answer with a vibration

The controls a reader uses while cooking — the serving count, the timers, and the
editor's dragging of rows — shall answer a touch with a short vibration, and the
system's own touch-feedback setting shall turn it off.

**Acceptance criteria**
- Given the device's touch feedback on, when the reader changes the serving
  count or starts or stops a timer, then the phone vibrates briefly.
- Given the device's touch feedback off, when the same controls are used, then
  nothing vibrates.

Which control gives which kind of vibration is the team's.

**Source:** user decision, 2026-10-04: the vibration came with v0.17's cooking
with busy hands; at the planning of v0.22 the owner decided it follows the
system's touch-feedback setting.
