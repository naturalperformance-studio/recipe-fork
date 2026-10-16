---
slug: FLOW
status: closed
updated: 2026-10-15
ids: [FLOW-074, FLOW-075]
---

# A shopping list, and printing

Added on 2026-10-11 by the sync of v0.22–v0.30, from the analyst's hypotheses the
user adopted on 2026-10-10 (`analytics/hypotheses.md`, H-002 and H-006), as intent.
Built in v0.32 (the shopping list) and v0.33 and v0.37 (printing); the rules below
were written from those builds at the sync of 2026-10-15.

### FLOW-074 — A shopping list is made from recipes

The system shall let the reader make one shopping list from one or several recipes,
each at the serving count chosen for it, in which one dictionary entry is one line
with its quantities added up — exactly where its lines share a unit type, and
approximately, marked "≈", where they do not (DOM-011) — and to which the reader adds
lines of their own. The reader ticks off what is bought. The list belongs to the
recipe book it was made in, is kept on the device and is sent nowhere (RET-010).

**Acceptance criteria**
- Given two recipes each needing onions in pieces, when both are on the list, then
  onions are one line with the two quantities added.
- Given a recipe at six servings, when it is added, then the list holds the
  six-serving quantities (`scaling-rules`); given its count changed in the list,
  then its lines follow.
- Given one entry in grams in one recipe and in cups in another, when both are on
  the list, then they are one line marked "≈", stating what was counted
  approximately; given an entry with no factor to count by, then the lines stay
  apart.
- Given an entry measured in one recipe and "to taste" in another, when both are on
  the list, then the measured line carries "+ to taste".
- Given free-text ingredients, when they are on the list, then each is its own line
  as written, scaled to its recipe, and never merged.
- Given the reader adds a line of their own — an ingredient, a piece of equipment
  or any text — when the list is shown, then it is there beside the recipes' lines.
- Given a ticked line, when a recipe added later makes its amount grow, then the
  tick is taken off, because what was bought is no longer enough.
- Given the list, when it is exported, sent or the book is saved (FLOW-073), then
  nothing of it leaves the device with the file.

Where the list opens, how lines and ticks look, and the undo of a removal or a
clearing are the team's. Merging lines by hand and editing an amount in the list
are not offered (`docs/increments/later.md`, the owner's note of 2026-10-12).

**Source:** user decision, 2026-10-10: H-002 adopted ("a shopping list built from
recipes, scaled and merged"); user decision at the planning of v0.32
(2026-10-12): one entry in different units merges into one approximate line, and
the reader adds lines of their own, equipment through them. The rest of the
criteria are the lead's rules built in v0.32, accepted by the owner with the build
(«Всё супер», 2026-10-12) and adopted at the sync of 2026-10-15.

### FLOW-075 — Recipes and the shopping list can be printed or shared as a PDF

The system shall let a person print a recipe, or share it as a PDF file, as it
reads on the card's View tab at the serving count and in the unit system shown,
without any network; shall do the same for the shopping list as it stands; and shall
make one document of several recipes chosen in the book (FLOW-078) or of a
collection (FLOW-076) — a first page with its title and the numbered titles of its
recipes, then each recipe from a new page.

**Acceptance criteria**
- Given a recipe scaled to four servings, when it is printed, then the printout
  holds the four-serving quantities.
- Given a recipe, when it is printed or shared, then the page holds what View holds
  and nothing that is only for the screen — no done marks, no "you lack"; a step's
  clip is not printed and the step says so.
- Given the shopping list, when it is printed, then what is still to buy comes
  first and what is ticked comes last, marked as bought.
- Given three recipes chosen in the book, or a collection of three, when they are
  printed or shared, then one document holds a contents page and the three recipes,
  each from a new page; a recipe that cannot be read is left out, and none readable
  is a failure the person is told of.
- Given "Share as PDF", when it is chosen, then a PDF file named after the recipe
  or the document's title goes to the operating system's share sheet; given
  "Print", then the system's print dialog opens.
- Given either mode, when anything is printed or shared, then nothing is sent to a
  service of the product's own.

The page's layout and the progress shown while a document is made are the team's.

**Source:** user decision, 2026-10-10: H-006 adopted against the analyst's
recommendation to defer it; user decisions at the planning of v0.33 (2026-10-12):
a PDF both through the print dialog and as a file for the share sheet, the shopping
list prints too; and at the planning of v0.37 (2026-10-13): several chosen recipes
and a collection print and share as one document. Written from the builds at the
sync of 2026-10-15.
