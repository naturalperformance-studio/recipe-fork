---
slug: FLOW
status: closed
updated: 2026-10-04
ids: [FLOW-068, FLOW-069]
---

# Cooking with busy hands

Added on 2026-10-04 by the sync of v0.15–v0.24, beside
[30-cooking.md](30-cooking.md), which was at its size limit: marking what is done,
and controls that answer the hand.

### FLOW-068 — The reader marks ingredients and steps as done

When the reader taps an ingredient or a step on the card, the system shall mark it
done, and tapping it again shall unmark it. The marks are the reader's, kept on
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
