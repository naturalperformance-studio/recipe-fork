# US units on display; writing in imperial units later

**Status:** superseded by [one unit system, converted on display](20261015-one-unit-system-converted-on-display.md) (2026-10-15); it had superseded the "no conversion" rule of L10N-008 (2026-09-21)

**Context:** Since 2026-09-21 the product was metric and offered no conversion
(L10N-008). On 2026-10-10 the product owner adopted the analyst's hypothesis H-003:
English-speaking readers want grams shown as cups and spoons, a request others
leave open or charge for. Three readings were put to the owner at the sync of
2026-10-11: conversion on display only; leaving L10N-008 and deferring; letting
authors write in US units.

**Decision:** recipes are stored and written in metric for now; a reader may ask
for a recipe's measured quantities to be shown in US units, while the recipe, its
file, its forks and its scaling keep the authored units (L10N-008). Writing a
recipe in imperial or US units is not refused: the owner asked for it to be added
by a later increment (OQ-043).

**Consequences:**
- Conversion needs its own rounding per unit and a rule for what converts to what
  (OQ-042); converting volume to mass would need the comparison factors of DOM-011
  to be used for something other than ordering.
- Until imperial writing arrives, every recipe stays comparable on one grid:
  scaling, the filters' ordering and the shopping list (FLOW-074) work on metric
  quantities.
- When imperial writing is planned, the unit model (DOM-002, DOM-016), its grids and
  how a metric reader sees such a recipe have to be decided together.

**Alternatives considered:**
- *Keep L10N-008 as it was and hold H-003 open.* Rejected: the owner had adopted
  the hypothesis.
- *Let authors write in US units now.* Not rejected, deferred by the owner to a
  later increment: it reworks the unit model, which display conversion does not.
