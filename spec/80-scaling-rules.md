---
slug: SCALE
status: blocked
updated: 2026-09-20
ids: [SCALE-001, SCALE-002, SCALE-003, SCALE-004, SCALE-005, SCALE-006, SCALE-007]
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
| `exact` | whole grams or millilitres; kilograms or litres at 1000 and above | never below the smallest displayable step |
| `piece`, indivisible | whole pieces | 1 piece |
| `piece`, divisible | whole pieces and halves | ½ piece |
| `household` | whole units, and the fractions ¼, ⅓, ½, ⅔, ¾ | ¼ |
| `taste_measured` | whole units, and the fractions ¼, ⅓, ½, ⅔, ¾ | ¼ |
| `taste_unmeasured` | not applicable — the ingredient carries no quantity | — |

The displayed unit of an `exact` ingredient follows the magnitude of the result,
not what the author typed: below 1000 g the value is shown in grams, at or above
1000 g in kilograms, and likewise for millilitres and litres.

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
**Blocked by:** OQ-010.

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

## Open

- OQ-010 — how the serving count is displayed when the computed value rounds to
  less than one.
- OQ-011 — bounds on the serving count a reader may request and on the scale
  factor.
