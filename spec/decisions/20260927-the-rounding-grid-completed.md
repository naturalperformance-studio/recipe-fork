# The rounding grid is completed: each rung keeps the precision below it, fractions above one are mixed, a tie goes up

**Status:** accepted

**Context:** Gate 1 of T-001 found the grid silent on three things that change what a
cook reads (SD-002): the values the kilogram and tonne rungs of `exact` may hold,
whether `household`, `taste_measured` and divisible `piece` show fractions above
one, and how a computed value is mapped to a grid value at all — nearest was stated
for the serving count only, and no rule said where a tie goes. The same tie was open
for the serving count (SD-003 item 3).

**Decision:**
- A kilogram or litre is displayed to a whole gram or millilitre, a tonne or cubic
  metre to a whole kilogram or litre, with no trailing zero (SCALE-013).
- At one and above, `household`, `taste_measured` and divisible `piece` are
  displayed as a whole number alone or followed by one fraction of their set: 1½
  tablespoons, 2⅓ teaspoons, 2½ onions (SCALE-014).
- Every computed quantity and every computed serving count goes to the nearest
  value of its grid, and a value exactly between two goes to the larger; the floor
  of SCALE-005 overrides (SCALE-015).

**Consequences:** "Linearly, no rounding loss" in the unit-type table becomes true
up to the tonne rung: 1234 g reads 1.234 kg. A kilogram value can carry three
places after the decimal mark, which is precise rather than tidy. Mixed numbers
keep a household quantity within a fraction of its exact value at any size, at
the cost of a display form the serving count does not share: the count stays
whole at one and above (SCALE-008), so two neighbouring numbers on the card follow
different forms. A tie going up means a reader never gets the smaller of two
equally near values; in a kitchen a little more is recoverable and a little less
may not be.

**Alternatives considered:**
- *One decimal place on the kilogram rung (1.2 kg).* Rejected: it rounds to
  100 g, which is a loss the unit-type table says `exact` does not have.
- *Whole kilograms (1500 g → 2 kg).* Rejected: an error up to a third of the
  quantity.
- *Fractions below one only, whole numbers above (1.4 tablespoons → 1).*
  Rejected: an error up to a third at 1½, exactly where household measures are
  most used.
- *A tie going down.* Rejected for the kitchen reason above.
