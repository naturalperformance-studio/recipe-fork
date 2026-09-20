---
slug: SCALE
status: closed
updated: 2026-09-20
ids: [SCALE-001, SCALE-002, SCALE-003, SCALE-004, SCALE-005, SCALE-006, SCALE-007,
  SCALE-008, SCALE-009, SCALE-010, SCALE-011, SCALE-012]
---

# Scaling and rounding

Scaling is what makes a recipe here an object rather than a text (GOAL-001). Two
entry points exist and both are required: the reader changes the serving count,
or the reader fixes the amount of an ingredient they actually have.

Everything in this section is a view over the stored recipe. No scaling ever
changes what is stored — for a published recipe that is forbidden outright
(CON-003), and for a private one it would silently rewrite the author's own
numbers.

## The rounding grid

| Unit type | Displayed values | Floor |
|---|---|---|
| `exact` | whole grams or millilitres; kilograms or litres at 1000 g or 1000 ml and above; tonnes or cubic metres at 1000 kg or 1000 l and above | never below the smallest displayable step |
| `piece`, indivisible | whole pieces | 1 piece |
| `piece`, divisible | whole pieces and halves | ½ piece |
| `household` | whole units, and the fractions ¼, ⅓, ½, ⅔, ¾ | ¼ |
| `taste_measured` | whole units, and the fractions ¼, ⅓, ½, ⅔, ¾ | ¼ |
| `taste_unmeasured` | not applicable — the ingredient carries no quantity | — |

The displayed unit of an `exact` ingredient follows the magnitude of the result,
not what the author typed: below 1000 g the value is shown in grams, at or above
1000 g in kilograms, at or above 1000 kg in tonnes, and likewise for millilitres,
litres and cubic metres. The ladder exists because a recipe may legitimately be
a production card written in large units, not only because scaling can be pushed
there. It is built for `exact` alone: `piece` and `household` have no larger unit
above them and are never re-expressed as anything else. A recipe whose units do
not suit a large batch is not rewritten by the product on the author's behalf.

The serving count itself is displayed on the same fraction set as `household` —
whole numbers and the fractions ¼, ⅓, ½, ⅔, ¾ — with a floor of ¼ serving, and
is rounded to the nearest value the set can express. Fractions of a serving are
displayed below one serving only; at or above one serving the count is displayed
as a whole number (SCALE-002, SCALE-008).

Divisibility of a `piece` ingredient comes from a dictionary of indivisible
ingredients (egg, garlic clove, bay leaf and so on), which the author may
override per ingredient with a divisibility flag. The dictionary holds
divisibility only. It holds no weights: DOM-003 stands.

### SCALE-001 — Scaling by serving count

When the reader changes the serving count of a recipe, the system shall display
every ingredient quantity multiplied by the ratio of the requested count to the
authored count, rounded by the grid above.

**Acceptance criteria**
- Given a recipe authored for 4 servings with 500 g of an `exact` ingredient,
  when the reader asks for 6, then it shows 750 g.
- Given the same recipe with 3 indivisible pieces, when the reader asks for 6,
  then it shows 6 pieces.
- Given the same recipe with an ingredient of type `taste_unmeasured`, when the
  reader asks for any count, then its line is unchanged.
- Given any scaled view, when the reader returns the count to the authored
  value, then every quantity equals the authored quantity and nothing is marked
  as derived.

**Verification:** scale one recipe covering all five unit types up and down, and
compare against hand computation.
**Source:** draft section 3.3.

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

### SCALE-004 — Every derived quantity says it is derived and which way it moved

Where a displayed quantity differs from the authored quantity, the system shall
mark it as derived; and where rounding moved it away from the exact computed
value, the system shall show the direction of that move.

**Acceptance criteria**
- Given a scaled recipe, when its list is displayed, then every changed quantity
  carries a derived marker.
- Given 3 indivisible pieces at a factor of 0.6, when the line is displayed,
  then it shows 2 pieces and states that the exact value was higher.
- Given 1 teaspoon at a factor of 0.2, when the line is displayed, then it shows
  ¼ teaspoon and states that the exact value was lower.
- Given a quantity that the grid represented exactly, when it is displayed, then
  it is marked as derived but carries no direction.

**Verification:** manual UI check across one rounded-up, one rounded-down and one
exact case.
**Source:** draft sections 1 and 3.2; CON-004.

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

### SCALE-006 — The most recent fixed ingredient sets the scale

When the reader fixes a second ingredient whose implied factor differs from the
current one, the system shall adopt the factor implied by the most recently
fixed ingredient, and shall mark every other fixed ingredient whose required
amount exceeds its fixed amount, showing the shortfall.

**Acceptance criteria**
- Given 300 g of chicken fixed against an authored 500 g, when the reader then
  fixes 3 eggs against an authored 3, then the displayed factor is 1.0 and the
  chicken line shows a requirement of 500 g against 300 g available.
- Given that state, when the shortfall is displayed, then it names both the
  required and the available amount.
- Given that state, when the reader releases the egg fixing, then the factor
  returns to the one implied by the chicken and the shortfall marking
  disappears.

**Verification:** fix two conflicting ingredients in both orders and compare.
**Source:** draft section 3.4; user decision, 2026-09-20.

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

### SCALE-008 — Serving count below one

While the computed serving count is below one serving, the system shall display
it as the nearest of the fractions ¼, ⅓, ½, ⅔, ¾ instead of as a whole number,
and shall display ¼ serving when the computed count is below ¼.

**Acceptance criteria**
- Given a recipe authored for 4 servings, when the reader fixes 50 g of an
  ingredient authored at 500 g, then the serving count shows an approximate
  ⅓ serving.
- Given the same recipe, when the reader fixes 25 g of that ingredient
  (a computed 0.2 servings), then the serving count shows an approximate
  ¼ serving.
- Given any serving count displayed as a fraction, when it is displayed, then it
  carries the same approximate marking that SCALE-002 requires.
- Given a computed count of exactly one serving or more, when it is displayed,
  then it is displayed as a whole number and not as a fraction.
- Given any state, when the serving count is displayed, then no quantity in the
  recipe is computed from it; SCALE-003 governs.

**Verification:** fix an ingredient at factors producing 0.4, 0.2 and 1.0
servings and compare the displayed count against the fraction set.
**Source:** user decision, 2026-09-20 (OQ-010).

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
  count is displayed by SCALE-008 in the same form it was requested.

**Verification:** walk the control from its floor to a two-digit count and back,
and confirm no value outside the two sets can be produced.
**Source:** user decision, 2026-09-20 (OQ-011). The requested form matches the
displayed form of SCALE-008, so one fraction set governs both sides.

### SCALE-012 — A quantity is legible at any magnitude

The system shall display a quantity in full whatever its number of digits, and
shall not truncate or abbreviate it; where the quantity and the ingredient name
cannot both fit, the name wraps and the quantity is displayed whole.

**Acceptance criteria**
- Given an ingredient displayed as 10 000 000 pieces, when its line is shown on
  the narrowest supported screen width, then every digit is visible and no
  ellipsis replaces part of the number.
- Given that same line, when the ingredient name does not fit beside the
  quantity, then the name wraps to another line and remains fully readable.
- Given an `exact` ingredient above 1000 kg, when its line is shown, then it is
  displayed in tonnes by the rounding grid, which is a change of unit and not an
  abbreviation of the number.
- Given a `household` ingredient of 2 000 000 tablespoons, when its line is
  shown, then it is displayed in tablespoons in full and is not converted to any
  other unit.

**Verification:** render a recipe at a factor producing seven-digit quantities in
`exact`, `piece` and `household` on the narrowest supported width and inspect
every line.
**Source:** user decision, 2026-09-20. Large quantities are reachable both by
scaling (SCALE-010) and by authoring a production card in large units, and the
unit ladder protects only `exact`, so legibility is stated for every unit type.
**Note:** how the digits of a large number are grouped is a localization matter
and belongs to section `localization`.

## Open

Nothing. Every requirement in this section carries acceptance criteria and a
verification method, and no open question blocks it.
