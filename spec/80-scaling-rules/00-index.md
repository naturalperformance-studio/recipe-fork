---
slug: SCALE
status: closed
updated: 2026-09-27
ids: []
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
| `exact` | below 1000 g or 1000 ml, whole grams or millilitres; at 1000 g or 1000 ml and above, kilograms or litres to a whole gram or millilitre; at 1000 kg or 1000 l and above, tonnes or cubic metres to a whole kilogram or litre (SCALE-013) | never below the smallest displayable step |
| `piece`, indivisible | whole pieces | 1 piece |
| `piece`, divisible | below one, ½; at one and above, a whole number alone or followed by ½ (SCALE-014) | ½ piece |
| `household` | below one, the fractions ¼, ⅓, ½, ⅔, ¾; at one and above, a whole number alone or followed by one of them (SCALE-014) | ¼ |
| `taste_measured` | below one, the fractions ¼, ⅓, ½, ⅔, ¾; at one and above, a whole number alone or followed by one of them (SCALE-014) | ¼ |
| `taste_unmeasured` | not applicable — the ingredient carries no quantity | — |

A computed value is displayed as the grid value of its unit type nearest to it,
and a value exactly between two grid values is displayed as the larger of the two
(SCALE-015). The floor of SCALE-005 overrides this where the nearest value would be
below it.

The grid bounds what is stated as well as what is shown: an author's quantity
(DOM-017) and a reader's fixed amount (SCALE-018) are both values of the grid of
their unit type, so the authored value and the grid value differ only where DOM-018 keeps a
quantity written before its entry's divisibility was corrected.

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
is rounded to the nearest value the set can express, a value exactly between two
of them going to the larger (SCALE-015). The set holds 1, so a computed count
from 0.875 up is shown as 1: fractions of a serving are displayed only where
that rounding stays below one serving, and otherwise the count is displayed as a
whole number (SCALE-002, SCALE-008). Only a computed count is marked as
approximate; a count the reader asked for is shown as asked, whatever its form
(SCALE-011).

Divisibility of a `piece` ingredient comes from its entry in the ingredient
dictionary (DOM: IngredientEntry — egg, garlic clove, bay leaf and so on are
indivisible), and the author does not override it: the entry decides for every
ingredient that names it. An ingredient the author typed as free text has no
entry (DOM-009), so there its author sets divisibility, which is the one place a
person supplies the flag. The dictionary holds divisibility only. It holds no
weights: DOM-003 stands.

A recipe that needs half of something the dictionary calls indivisible has no way
to say so — the quantity field does not hold it (DOM-017) — and that cost is
accepted: divisibility is a property of the
ingredient rather than of the recipe, a per-recipe override is a fourth control
on a row that has three fields (FLOW-023), and an entry whose flag is wrong is
one the product owner corrects once for everybody instead of every author
correcting it again.

This section is split into the parts below. Requirement identifiers did not
change on the split and never will.

| Part | What is in it | Requirements |
|---|---|---|
| [10-scaling.md](10-scaling.md) | The two entry points — a requested serving count and a fixed ingredient — how they combine, the bounds on the requested count, and the amounts a reader may fix. | SCALE-001, SCALE-002, SCALE-003, SCALE-006, SCALE-008, SCALE-010, SCALE-011, SCALE-016, SCALE-017, SCALE-018 |
| [20-display.md](20-display.md) | What a scaled quantity looks like: the values of every grid, how a computed value reaches one, the floors, the derived marker and its direction, and legibility at any magnitude. | SCALE-004, SCALE-005, SCALE-007, SCALE-009, SCALE-012, SCALE-013, SCALE-014, SCALE-015 |

## Open

Nothing. Every requirement in this section carries acceptance criteria and a
verification method, and no open question blocks it.
