# A quantity anyone states lies on the grid

**Status:** accepted
**Context:** The rounding grid decided what a computed quantity displays, but
nothing decided what may be stated. An author's quantity was "number or none"
(SD-034), and a reader's fixed amount had no stated admissible values at all
(SD-033). At the authored serving count an off-grid value such as 7.5 g or
0.3 teaspoon was displayed as the grid value by SCALE-001's statement and as the
authored value by its criterion (4) — a contradiction, not only a gap. The fixing
control could take zero, which drives the factor to zero and shows every line at
its floor, and nothing said whether a household ingredient could be fixed in
another household unit.
**Decision:** An authored quantity is a value above zero that the grid of its unit
type displays, with no upper bound (DOM-017). A row whose unit or divisibility is
changed so that its value leaves the grid keeps the value, is marked as an error
and blocks submission. A quantity published before its entry's divisibility was
corrected stays as written at the authored count (DOM-018). A reader's fixed amount
obeys the same set, in any unit of the same dimension for `exact` and only in the
author's own unit for `household`; clearing the control changes nothing until an
accepted amount is entered or the fixing is released (SCALE-018).
**Consequences:** At the authored count the authored value and the grid value are
the same, so SCALE-001 (4), CON-004 (3) and the grid agree without an exception
anyone has to remember — save DOM-018, which exists because CON-003 forbids
correcting a published recipe. Zero never reaches the factor. The cost: 2.5 g of
yeast and 0.3 teaspoon cannot be written (the author writes 3 g and ⅓); half of an
indivisible piece cannot be written at all; a reader holding teaspoons of an
ingredient written in tablespoons converts in their head.
**Alternatives considered:**
- *Any positive number, shown as entered at the authored count; the grid for
  computed values only.* Rejected: decimal marks on household quantities and 2.5
  indivisible eggs on the card, against SCALE-014 (5), and a value that jumps by
  the grid at the first step away from the authored count.
- *Any positive number, shown by the grid even at the authored count with a
  rounding mark.* Rejected: an author sees 8 g in their own recipe where they wrote
  7.5 g, and CON-004 (3) and SCALE-001 (4) would have to be rewritten.
- *A fixed amount of any positive number, off the grid.* Rejected: it lets a reader
  state 2.5 indivisible eggs, which the card then shows as 3.
- *Household units convertible among themselves.* Rejected: tablespoon to teaspoon
  is a fixed ratio, but a cup has no single volume (200, 240 and 250 ml are all in
  use), and a partial conversion would be a rule with a hole in it.
- *Emptying the quantity field when a unit change takes its value off the grid.*
  Rejected: the author loses what they typed without being asked.
- *Showing a pre-correction quantity by the new grid, or freezing a divisibility
  flag once a published recipe relies on it.* Rejected: the first rewrites a valid
  published recipe on the reader's screen; the second makes a wrong entry
  uncorrectable for everyone.
