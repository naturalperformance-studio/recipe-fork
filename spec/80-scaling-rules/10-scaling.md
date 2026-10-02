---
slug: SCALE
status: closed
updated: 2026-09-29
ids: [SCALE-001, SCALE-002, SCALE-003, SCALE-006, SCALE-008, SCALE-010, SCALE-011, SCALE-016, SCALE-017,
  SCALE-018]
---

# Scaling

The two entry points — a requested serving count and a fixed ingredient — how they combine, and the bounds on the requested count. The rounding grid these requirements invoke is in the [section index](00-index.md).

### SCALE-001 — Scaling by serving count

When the reader changes the serving count of a recipe, the system shall display
every ingredient quantity multiplied by the ratio of the requested count to the
authored count, rounded by the rounding grid (section index).

**Acceptance criteria**
- Given a recipe authored for 4 servings with 500 g of an `exact` ingredient,
  when the reader asks for 6, then it shows 750 g.
- Given the same recipe with 3 indivisible pieces, when the reader asks for 6
  (an exact 4.5 pieces), then it shows 5 pieces and states that the exact value
  was lower (SCALE-015, SCALE-004).
- Given the same recipe with an ingredient of type `taste_unmeasured`, when the
  reader asks for any count, then its line is unchanged.
- Given any scaled view, when the reader returns the count to the authored
  value, then every quantity equals the authored quantity and nothing is marked
  as derived.

**Verification:** scale one recipe covering all five unit types up and down, and
compare against hand computation.
**Source:** draft section 3.3.
**Correction, 2026-09-27:** criterion 2 showed 6 pieces for an exact 4.5, which
neither the grid nor SCALE-015 produces; 6 pieces is the value at 8 servings
(SD-027, the criterion on pieces). The behaviour was already decided by the grid
and SCALE-015; only the example was wrong.
**Note, 2026-09-27:** criterion 4 and the statement agree for every quantity,
because an authored quantity lies on its grid (DOM-017), so at a ratio of 1 the
grid value is the authored value. The one exception, a quantity published before
its entry's divisibility was corrected, is shown as written (DOM-018) (SD-034).

### SCALE-002 — Scaling from a fixed ingredient

When the reader fixes the available amount of an ingredient, the system shall
compute the scale factor as that amount divided by the authored amount of the
same ingredient, apply it to the whole recipe, and display the resulting serving
count as an approximation while keeping the exact factor for computation.

**Acceptance criteria**
- Given a recipe authored for 4 servings with 500 g of chicken, when the reader
  fixes 300 g of chicken, then the chicken line shows exactly 300 g.
- Given that state, when the serving count is displayed, then it is shown as an
  approximate 2 servings and is marked as approximate.
- Given that state, when another `exact` ingredient of 350 g is displayed, then
  it shows 210 g, computed from 0.6 and not from a rounded serving count.

**Verification:** fix an ingredient that produces a non-integer serving count and
check that no other quantity was computed from the rounded count.
**Source:** draft section 3.3; user decision, 2026-09-20.
**Note:** below one serving the count is displayed by SCALE-008 instead of as a
whole number.

### SCALE-003 — Rounding is applied to the display, never to the chain

The system shall compute every displayed quantity from the exact scale factor
and the authored quantity, and shall not use a rounded quantity as an input to
any further computation.

**Acceptance criteria**
- Given a factor of 0.6 and an authored 7 g, when the line is displayed, then it
  shows 4 g and the stored value remains 7 g.
- Given that same view, when the reader changes the factor to 1.0, then the line
  shows 7 g again, not a value derived from 4 g.

**Verification:** scale down, scale back up, and compare against the authored
values.
**Source:** CON-004; draft section 3.2.

### SCALE-006 — The most recent fixed ingredient sets the scale

When the reader fixes a second ingredient whose implied factor differs from the
current one, the system shall adopt the factor implied by the most recently
fixed ingredient, and shall mark every other fixed ingredient whose required
amount exceeds its fixed amount, showing the shortfall.

**Acceptance criteria**
- Given 300 g of chicken fixed against an authored 500 g, when the reader then
  fixes 3 eggs against an authored 3, then the factor in force is 1.0 and the
  chicken line shows a requirement of 500 g against 300 g available.
- Given that state, when the shortfall is displayed, then it names both the
  required and the available amount.
- Given that state, when the reader releases the egg fixing, then the factor
  returns to the one implied by the chicken and the shortfall marking
  disappears.

**Verification:** fix two conflicting ingredients in both orders and compare.
**Source:** draft section 3.4; user decision, 2026-09-20.
**Correction, 2026-09-27:** criterion 1 said "the displayed factor", the only place
the specification called the factor displayed. The factor is not shown to the
reader; it is observable only through the quantities and the approximate serving
count (user decision, 2026-09-27, SD-011).

### SCALE-008 — Serving count below one

While the computed serving count, taken to the nearest value of the serving-count
set by SCALE-015, is below one serving, the system shall display it as that
fraction — one of ¼, ⅓, ½, ⅔, ¾ — instead of as a whole number, and shall display
¼ serving when the computed count is below ¼.

**Acceptance criteria**
- Given a recipe authored for 4 servings, when the reader fixes 50 g of an
  ingredient authored at 500 g, then the serving count shows an approximate
  ⅓ serving.
- Given the same recipe, when the reader fixes 25 g of that ingredient
  (a computed 0.2 servings), then the serving count shows an approximate
  ¼ serving.
- Given a computed serving count displayed as a fraction, when it is displayed,
  then it carries the same approximate marking that SCALE-002 requires; a count
  the reader requested is never marked as approximate, in any form (SCALE-011).
- Given a computed count whose nearest value in the set is one serving or more,
  when it is displayed, then it is displayed as a whole number and not as a
  fraction.
- Given any state, when the serving count is displayed, then no quantity in the
  recipe is computed from it; SCALE-003 governs.
- Given a recipe authored for 4 servings with 500 g of chicken, when the reader
  fixes 115 g of it (a computed 0.92 servings), then the serving count shows an
  approximate 1 serving and not ¾.
- Given a recipe authored for 4 servings with 320 g of chicken, when the reader
  fixes 70 g of it (a factor of 0.21875, a computed 0.875 servings, exactly
  between ¾ and 1), then the serving count shows an approximate 1 serving.
- Given a recipe authored for 4 servings with 500 g of chicken, when the reader
  fixes 100 g of it (a factor of 0.2, a computed 0.8 servings), then the serving
  count shows an approximate ¾ serving.

**Verification:** fix an ingredient at factors producing 0.2, 0.4, 0.8, 0.875,
0.92 and 1.0 servings and compare the displayed count against the serving-count
set.
**Source:** user decision, 2026-09-20 (OQ-010).
**Correction, 2026-09-27:** a computed count from 0.875 up to 1 was ≈¾ by this
statement and ≈1 by SCALE-015 (SD-027, the criterion on counts below one); and
criterion 3 marked "any" fraction as approximate while SCALE-016 and SCALE-017
left a requested count unmarked (SD-032). By user decisions of that date the count
is rounded over one set that holds 1, so fractions are shown only where that
rounding stays below one; and only a computed count is approximate. See
[ADR — the serving count has one set, and what the reader asked for is exact](../decisions/20260927-the-serving-count-has-one-set.md).
**Correction, 2026-09-27:** criterion 7 fixed 109.375 g, an amount SCALE-018 does
not accept (SD-033). It now reaches the same 0.875 servings with amounts on the
grid; the behaviour is unchanged.
**Correction, 2026-09-29:** the amounts that correction chose, 350 g of 400 g,
give a factor of 0.875 and so 3.5 servings by SCALE-002, not 0.875 servings; and
criterion 8's "the same recipe" came to name that 400 g recipe, where 100 g gives
1 serving (SD-036). By user decision of that date criterion 7 keeps its case —
0.875 servings shown as ≈1 — with 70 g of 320 g, and criterion 8 names its own
500 g recipe. Only the examples changed; the behaviour is the one the
[ADR — the serving count has one set](../decisions/20260927-the-serving-count-has-one-set.md)
decided.

### SCALE-010 — The requested serving count has a floor and no ceiling

The system shall accept a requested serving count of ¼ serving or more, and
shall not refuse a requested count for being large.

**Acceptance criteria**
- Given any recipe, when the reader requests 1000 servings, then the recipe is
  displayed at that count and nothing is refused.
- Given a recipe authored for 4 servings with 5 g of an `exact` ingredient, when
  the reader fixes 10 kg of that ingredient, then the derived count is displayed
  by SCALE-002 and is not capped.
- Given a recipe scaled to a very large count, when an `exact` ingredient
  exceeds 1000 g, then it is displayed in kilograms by the rounding grid.
- Given any scaled view, when a quantity is displayed, then it was computed from
  the exact factor by SCALE-003, whatever the size of the factor.

**Verification:** request the floor, a four-digit count, and a count derived
from a fixed ingredient that exceeds any count the field itself can be given.
**Source:** user decision, 2026-09-20 (OQ-011). No cost was found that a ceiling
would have bought: scaling is a view, it is computed on the client, it stores
nothing, and the grid already displays quantities above 1000 g in kilograms.

### SCALE-011 — The form a requested serving count may take

The system shall accept a requested serving count below one serving only as one
of the fractions ¼, ⅓, ½, ⅔, ¾, and at one serving or above only as a whole
number.

**Acceptance criteria**
- Given the serving count control, when the reader takes the count below one
  serving, then the values it can hold are ¼, ⅓, ½, ⅔ and ¾ and no others.
- Given the serving count control, when the reader takes the count to one
  serving or above, then it can hold whole numbers only.
- Given the serving count control at ¼ serving, when the reader tries to go
  lower, then the count stays at ¼.
- Given a count requested as a fraction, when the recipe is displayed, then the
  count is displayed in the same form it was requested and is not marked as
  approximate, because it is the count the reader asked for (SCALE-008).

**Verification:** walk the control from its floor to a two-digit count and back,
and confirm no value outside the two sets can be produced.
**Source:** user decision, 2026-09-20 (OQ-011). The requested form matches the
displayed form of SCALE-008, so one fraction set governs both sides.
**Correction, 2026-09-27:** criterion 4 routed a requested fraction through
SCALE-008's approximate marking, which SCALE-016 (3) and SCALE-017 (4) contradicted
(SD-032). By user decision of that date a requested count is never approximate.

### SCALE-016 — Changing the serving count releases every fixed ingredient

When the reader changes the serving count while one or more ingredients are
fixed, the system shall release every fixing and display the recipe at the
requested count by SCALE-001.

**Acceptance criteria**
- Given a recipe authored for 4 servings with 500 g of chicken fixed at 300 g,
  when the reader asks for 6 servings, then the chicken line shows 750 g and is
  no longer marked as fixed.
- Given two fixed ingredients, one showing a shortfall (SCALE-006), when the
  reader changes the serving count, then neither is marked as fixed and no
  shortfall is shown.
- Given that the fixings were released by a change of count, when the reader
  looks at the serving count, then it is the count they asked for and is not
  marked as approximate.

**Verification:** fix one and then two ingredients, change the count in each
state, and inspect every line and the count.
**Source:** user decision, 2026-09-27 (SD-003 item 1): the reader's latest action
sets the scale, as SCALE-006 already decides between two fixings.

### SCALE-017 — Releasing the last fixed ingredient restores the count in force before the first

When the reader releases the only remaining fixed ingredient, the system shall
display the recipe at the serving count that was in force immediately before the
first of the fixings that led to that state.

**Acceptance criteria**
- Given a recipe opened at its authored 4 servings, when the reader fixes an
  ingredient and then releases it, then the recipe is displayed at 4 servings and
  nothing is marked as derived.
- Given a recipe the reader had set to 6 servings, when they fix an ingredient,
  then fix a second, then release the second and then the first, then the recipe
  is displayed at 6 servings.
- Given a recipe opened at the reader's usual count of 2 (FLOW-017), when the
  reader fixes an ingredient and releases it, then the recipe is displayed at 2
  servings.
- Given the fixing is released, when the serving count is displayed, then it is
  not marked as approximate.

**Verification:** fix and release from the authored count, from a requested
count and from a usual count, with one and with two fixings.
**Source:** user decision, 2026-09-27 (SD-003 item 2). Releasing a fixing undoes
it; the approximate count shown during the fixing is not a count the reader asked
for, and taking it as one would compute from a rounded value (SCALE-003).

### SCALE-018 — The amounts a reader may fix

The system shall accept as the fixed amount of an ingredient only a value that the
quantity field of its unit type holds by DOM-017, stated in a unit the author's
unit can be read in without conversion, and shall accept nothing else.

A unit the author's unit can be read in without conversion is, by unit type:
for `exact`, any unit of the same dimension — g, kg, t for a mass and ml, l, m³ for
a volume; for `household`, the author's own unit and no other; for `piece` and
`taste_measured`, their one unit.

**Acceptance criteria**
- Given 500 g of chicken, when the reader fixes `300 г` or `0,3 кг`, then either is
  accepted and the chicken line shows 300 g (SCALE-002).
- Given 5 g of an ingredient, when the reader fixes `10 кг`, then it is accepted
  (SCALE-010).
- Given the fixing control, when the reader enters 0, a negative number or
  `109,375 г`, then the control does not hold it and the scale in force does not
  change.
- Given an indivisible piece ingredient, when the reader enters `2,5 шт` or `½ шт`,
  then the control does not hold it; given a divisible one, `2½ шт` is held.
- Given an ingredient authored in grams, when the reader looks for a unit to fix it
  in, then ml, l and m³ are not offered: nothing converts a mass to a volume.
- Given an ingredient authored in `ст. л.`, when the reader fixes it, then the
  control accepts tablespoons only, on the `household` grid, and offers neither
  `ч. л.` nor `стакан` (DOM-003).
- Given a `taste_unmeasured` ingredient, when the ingredient list is displayed, then
  it offers no fixing, because it has no authored amount to divide by (DOM-002,
  SCALE-002).
- Given the fixing control holding an amount, when the reader clears it, then the
  scale in force does not change until the reader enters an accepted amount or
  releases the fixing (SCALE-017); clearing the control is not a release.

**Verification:** fix an ingredient of each unit type at every example above, and
clear the control of a fixed ingredient.
**Source:** user decision, 2026-09-27 (SD-033, and its follow-up on a cleared
control). Scaling runs on the device, so the control behaves the same with no
connection. See [ADR — a quantity anyone states lies on the grid](../decisions/20260927-stated-quantities-lie-on-the-grid.md).
**Note, 2026-09-29:** how the typed amount is read — a comma or a point as its
decimal mark, no grouping separator — is DOM-019 (SD-037).
**Cost, stated:** a reader holding 2 teaspoons of an ingredient written in
tablespoons converts in their head; the product holds no ratio between household
units, and a cup has no single volume to hold one for.
