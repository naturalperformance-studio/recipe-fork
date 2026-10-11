---
slug: FLOW
status: closed
updated: 2026-10-11
ids: [FLOW-074, FLOW-075]
---

# A shopping list, and printing a recipe

Added on 2026-10-11 by the sync of v0.22–v0.30, from the analyst's hypotheses the
user adopted on 2026-10-10 (`analytics/hypotheses.md`, H-002 and H-006). Neither is
built yet. Each is written as intent; its rules are settled when an increment is
planned for it.

### FLOW-074 — A shopping list is made from recipes

The system shall let the reader make a shopping list from one or several recipes,
each at the serving count chosen for it, in which the same dictionary entry in the
same unit type is one line with its quantities added up. The list is the reader's,
kept on the device and sent nowhere (RET-010).

**Acceptance criteria**
- Given two recipes each needing onions in pieces, when a list is made from both,
  then onions are one line with the two quantities added.
- Given a recipe scaled to six servings, when a list is made from it, then the
  list holds the six-serving quantities (`scaling-rules`).
- Given free-text ingredients, when a list is made, then each is its own line as
  written.

Whether lines in different unit types of one entry merge, how items are ticked
off, and whether the kitchen's equipment plays a part are settled at planning.

**Source:** user decision, 2026-10-10: H-002 adopted ("a shopping list built from
recipes, scaled and merged"). The structured, dictionary-bound ingredients are what
make merging reliable here.

### FLOW-075 — A recipe can be printed or saved as a PDF

The system shall let a person print a recipe, or save it as a PDF, as it reads on
the card's View tab at the serving count shown, without any network.

**Acceptance criteria**
- Given a recipe scaled to four servings, when it is printed, then the printout
  holds the four-serving quantities.
- Given serverless mode, when a recipe is printed, then nothing is sent to a
  service of the product's own.

Printing several recipes at once and the page's layout are settled at planning.

**Source:** user decision, 2026-10-10: H-006 adopted against the analyst's
recommendation to defer it.
