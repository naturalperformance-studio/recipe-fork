---
slug: FLOW
status: closed
updated: 2026-10-11
ids: [FLOW-071, FLOW-072]
---

# Equipment and My kitchen

Added on 2026-10-11 by the sync of v0.22–v0.30: the reader says once what their
kitchen holds, a recipe's card shows what is missing, and the book can hide what
cannot be cooked. What a recipe's equipment is lives in DOM-025, the dictionary in
DOM-026.

### FLOW-071 — My kitchen says what the reader has, and the card what is missing

The system shall let the reader tick, in a list of the equipment dictionary
(DOM-026), what their kitchen holds — "My kitchen". The ticks are the reader's, kept
on the device and sent nowhere (RET-010), and are not part of any recipe. While My
kitchen holds at least one item, the card shall mark every equipment item of a
recipe that names an entry the kitchen lacks as not in the kitchen, in words and
not by colour alone (A11Y-008); a tap on such a mark on the View tab opens My
kitchen at that item, and going back returns to the same recipe at the same place.

**Acceptance criteria**
- Given My kitchen empty, when any card is shown, then nothing is marked missing.
- Given My kitchen holding a frying pan and not an oven, when a recipe needing both
  is shown, then the oven is marked not in the kitchen and the frying pan is not.
- Given a free-text equipment item or a consumable (DOM-026), when the card is
  shown, then it is never marked missing.
- Given a recipe naming a whole group ("Appliances"), when any item of that group
  is ticked, then the group counts as held.
- Given an item marked missing on View, when the reader taps it, ticks it in My
  kitchen and goes back, then they are on the same recipe, tab and place, and the
  mark is gone.
- Given the dictionary refreshed by an application update, when My kitchen is
  opened, then the ticks are still there; an entry that left the dictionary drops
  out of My kitchen and stays in the recipes that name it.

Where My kitchen is reached from besides the card, how its list is grouped and
searched, and how a mark looks are the team's.

**Source:** user decision, 2026-10-10 (planning of v0.28: "a user fills in once what
their kitchen has and at once sees what a recipe lacks"); the tap to My kitchen and
back, user decision after the v0.28 demo, 2026-10-10. A group counting as held when
any of its items is, and consumables never counting, are the lead's, built in
v0.28 and accepted at the sync of 2026-10-11.

### FLOW-072 — The book can hide what the kitchen cannot cook

While My kitchen holds at least one item, the recipe book's filters shall offer
"Enough equipment": when it is on, every recipe naming an equipment entry the
kitchen lacks (FLOW-071) is hidden. It combines with the other filters and is
shown among the active ones like them.

**Acceptance criteria**
- Given the filter on, when the book is listed, then a recipe needing an oven the
  kitchen lacks is hidden, and one whose equipment is all held, free text or
  consumables is shown.
- Given My kitchen emptied, when the filters are shown, then "Enough equipment" is
  off and not offered.
- Given the filter alone hiding every recipe, when the book is shown, then it says
  so and offers turning the filter off (FLOW-042).

**Source:** user decision, 2026-10-10 (planning of v0.28: "can hide the recipes
they have no equipment for"); built in v0.28.
