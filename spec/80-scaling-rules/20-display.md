---
slug: SCALE
status: closed
updated: 2026-10-02
ids: [SCALE-004, SCALE-005, SCALE-007, SCALE-009, SCALE-012, SCALE-013, SCALE-014, SCALE-015]
---

# Display and rounding

What a scaled quantity looks like: the values of every grid, how a computed value reaches one, the floors, the derived marker and its direction, and legibility at any magnitude. The rounding grid these requirements invoke is in the [section index](00-index.md).

### SCALE-004 — Every derived quantity says it is derived and which way it moved

While a recipe is displayed at a scale factor other than 1, the system shall
state that every displayed quantity is derived; and where rounding moved a
quantity away from the exact computed value, the system shall show the direction
of that move on that quantity.

**Acceptance criteria**
- Given a scaled recipe, when its list is displayed, then every quantity is
  stated to be derived, including one whose displayed value equals its authored
  value; the statement may be made once for the whole ingredient list rather
  than on each line.
- Given a direction shown by a symbol, when it is read by a screen reader or by
  someone who does not know the symbol, then its meaning is available in words
  (a legend, a text alternative); colour alone never carries it.
- Given 3 indivisible pieces at a factor of 0.6, when the line is displayed,
  then it shows 2 pieces and states that the exact value was lower.
- Given a recipe authored for 4 servings with 1 indivisible egg, when the reader
  asks for 2 servings, then the line shows 1 egg, is marked as derived, and states
  that the exact value was lower.
- Given 1 teaspoon at a factor of 0.2, when the line is displayed, then it shows
  ¼ teaspoon and states that the exact value was lower.
- Given a quantity that the grid represented exactly, when it is displayed, then
  it is covered by the derived statement but carries no direction.

**Verification:** manual UI check across one rounded-up, one rounded-down, one
exact case, and one case rounded back onto its authored value.
**Source:** draft sections 1 and 3.2; CON-004.
**Correction, 2026-09-27:** criterion 2 stated "higher" for a displayed 2 against
an exact 1.8; the exact value is lower, as CON-004 (2) and criterion 3 read it
(SD-012). The statement marked a quantity by its value ("differs from the
authored quantity"); it now marks by provenance — every quantity shown at a
scale factor other than 1 is derived — by user decision, 2026-09-27
([ADR — derived by provenance](../decisions/20260927-derived-by-provenance.md)).
**Changed 2026-10-02:** the derived marker was required on every line and the
direction in words. At the v0.2 demo the user found the per-line wording too long
and asked for symbols; the build states "recalculated" once above the
ingredients, with a legend, and marks a rounded quantity with an arrow. By that
decision the statement may cover the whole list, and the direction may be a
symbol whose meaning is available in words. The exact form is the team's.

### SCALE-005 — An ingredient is never scaled out of the recipe

If scaling would reduce a quantity below the floor of its unit type, then the
system shall display the floor value and mark it as rounded up.

**Acceptance criteria**
- Given 3 indivisible pieces at a factor of 0.1, when the line is displayed,
  then it shows 1 piece and not 0.
- Given 1 teaspoon at a factor of 0.05, when the line is displayed, then it
  shows ¼ teaspoon and not 0.
- Given any factor above zero, when the list is displayed, then no line shows a
  zero quantity.

**Verification:** scale a recipe by a very small factor and inspect every line.
**Source:** derived from the ingredient being present in the recipe at all: a
listed ingredient with a quantity of zero states something the author did not
write. The floors themselves are the user's decision of 2026-09-20.

### SCALE-007 — Quantities inside steps follow the same view

Where a step references an ingredient, the system shall display in the step the
same quantity that the ingredient list shows at the current scale.

**Acceptance criteria**
- Given a scaled recipe, when a step referencing an ingredient is read, then the
  quantity in the step equals the quantity in the list, including its rounding
  and its derived marker.
- Given a recipe at its authored scale, when a step is read, then the referenced
  quantity equals the authored one.

**Verification:** compare every referenced quantity against the list at three
different factors.
**Source:** draft section 3.5; DOM-007.

### SCALE-009 — Warning when the recipe stops holding its proportions

If two or more ingredients are displayed at the floor of their unit type
because scaling would have taken them below it, then the system shall display a
warning that the displayed quantities no longer hold the authored proportions.

**Acceptance criteria**
- Given a recipe scaled so that exactly one ingredient is held at its floor by
  SCALE-005, when the recipe is displayed, then no warning is shown.
- Given a recipe scaled so that two ingredients are held at their floors, when
  the recipe is displayed, then the warning is shown.
- Given a recipe scaled so that two ingredients are held at their floors, when
  the reader returns the recipe to its authored scale, then the warning
  disappears.
- Given the warning is shown, when the reader reads the ingredient list, then
  every affected line still carries its own rounded-up marking from SCALE-005.

**Verification:** scale one recipe containing at least three floor-bearing unit
types down until one, then two, then three ingredients reach their floors.
**Source:** user decision, 2026-09-20 (OQ-011). The condition counts ingredients
at their floor regardless of which entry point produced the scale, because a
floor is reached the same way from a serving count and from a fixed ingredient.

### SCALE-012 — A quantity is legible at any magnitude

The system shall display a quantity in full whatever its number of digits, and
shall not truncate or abbreviate it; where the quantity and the ingredient name
cannot both fit, the name wraps and the quantity is displayed whole.

**Acceptance criteria**
- Given an ingredient displayed as 10 000 000 pieces, when its line is shown in a
  window 320 dp wide (QUAL-011) with the device text scale at 200 per cent
  (A11Y-004), then every digit is visible and no ellipsis replaces part of the
  number.
- Given that same line, when the ingredient name does not fit beside the
  quantity, then the name wraps to another line and remains fully readable.
- Given an `exact` ingredient above 1000 kg, when its line is shown, then it is
  displayed in tonnes by the rounding grid, which is a change of unit and not an
  abbreviation of the number.
- Given a `household` ingredient of 2 000 000 tablespoons, when its line is
  shown, then it is displayed in tablespoons in full and is not converted to any
  other unit.

**Verification:** render a recipe at a factor producing seven-digit quantities in
`exact`, `piece` and `household` in a window 320 dp wide at 100 and 200 per cent
text scale and inspect every line.
**Source:** user decision, 2026-09-20. Large quantities are reachable both by
scaling (SCALE-010) and by authoring a production card in large units, and the
unit ladder protects only `exact`, so legibility is stated for every unit type.
**Note:** how the digits of a large number are grouped is decided in L10N-009.
**Correction, 2026-09-27:** "the narrowest supported screen width" named no figure;
it is 320 dp (QUAL-011), and the criteria hold there at the text scale of
A11Y-004.

### SCALE-013 — The values of the `exact` ladder above its first rung

The system shall display an `exact` quantity of 1000 g or 1000 ml and above in
kilograms or litres to the nearest whole gram or millilitre, and one of 1000 kg
or 1000 l and above in tonnes or cubic metres to the nearest whole kilogram or
litre, showing no trailing zero after the decimal mark.

**Acceptance criteria**
- Given an `exact` quantity computed as 1234 g, when it is displayed, then it
  shows 1.234 kg.
- Given an `exact` quantity computed as 1500 g, when it is displayed, then it
  shows 1.5 kg and not 1.500 kg or 2 kg.
- Given an `exact` quantity computed as 1 234 567 g, when it is displayed, then it
  shows 1.235 t and states that the exact value was lower (SCALE-004).
- Given an `exact` quantity computed as 999.6 g, when it is displayed, then it
  shows 1 kg, because the nearest whole gram is 1000 g.

**Verification:** unit test over the rung boundaries and over values with one,
two and three significant places after the decimal mark, in both mass and volume.
The decimal mark and digit grouping are those of L10N-009; the examples here use
the English form.
**Source:** user decision, 2026-09-27 (SD-002 item 1): each rung carries the
precision of the rung below it, which is what "linearly, no rounding loss" in the
unit-type table of the domain model already said.

### SCALE-014 — Fractions above one are mixed numbers

Where the unit type is `household`, `taste_measured` or divisible `piece`, the
system shall display a quantity of one or more as a whole number, alone or
followed by one fraction of its unit type's set.

**Acceptance criteria**
- Given 1 tablespoon at a factor of 1.5, when the line is displayed, then it
  shows 1½ tablespoons.
- Given 1 teaspoon at a factor of 2.3, when the line is displayed, then it shows
  2⅓ teaspoons.
- Given 1 divisible onion at a factor of 2.5, when the line is displayed, then it
  shows 2½ onions.
- Given 1 divisible onion at a factor of 2.3, when the line is displayed, then it
  shows 2½ onions and states that the exact value was lower.
- Given any quantity of these unit types, when it is displayed, then it never
  carries a decimal mark.

**Verification:** unit test over each of the three unit types at factors that
land on and between every value of their set above one.
**Source:** user decision, 2026-09-27 (SD-002 item 2). The rejection of "2⅓" in the
scaling-extremes ADR concerns the serving count and stands for it: the count
stays whole at one and above (SCALE-008).

### SCALE-015 — A computed value goes to the nearest grid value, and a tie goes up

The system shall display every computed quantity and every computed serving count
as the value of its grid nearest to the exact computed value, and where the
computed value lies exactly between two grid values, as the larger of the two.

**Acceptance criteria**
- Given an `exact` quantity computed as 4.5 g, when it is displayed, then it
  shows 5 g and states that the exact value was lower.
- Given an `exact` quantity computed as 4.4 g, when it is displayed, then it
  shows 4 g and states that the exact value was higher.
- Given a tablespoon quantity computed as 1 5⁄12, exactly between 1⅓ and 1½, when
  it is displayed, then it shows 1½ tablespoons.
- Given a recipe authored for 4 servings with 400 g of an ingredient, when the
  reader fixes 250 g of it, then the serving count shows an approximate 3
  servings.
- Given a recipe authored for 4 servings with 240 g of an ingredient, when the
  reader fixes 25 g of it (a computed 5/12 serving), then the serving count shows
  an approximate ½ serving.
- Given a value whose nearest grid value is below the floor of its unit type,
  when it is displayed, then SCALE-005 governs and the floor is shown.

**Verification:** unit test over computed values just below, exactly at and just
above the midpoint between every pair of adjacent values of every grid.
**Source:** user decision, 2026-09-27 (SD-002 item 3, SD-003 item 3). A tie goes
up because in a kitchen a little more is recoverable and a little less may not be.
