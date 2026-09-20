# Large quantities: a unit ladder for `exact` only, legibility for every type

**Status:** accepted

**Context:** A requested serving count has no ceiling
([ADR — scaling extremes](20260920-scaling-extremes.md)), so any quantity can be
driven to seven digits and beyond. Separately, and more importantly, a recipe
here may legitimately be a production card for a batch, authored in large units
from the start — the product structure fits that use and no non-goal refuses it.
Both routes lead to the same question: what a very large quantity looks like on
an ingredient line.

**Decision:**
- The `exact` unit ladder gains a rung: tonnes at or above 1000 kg, cubic metres
  at or above 1000 l.
- An `exact` ingredient may be authored in tonnes and cubic metres, not only in
  the smaller units, so a production card can be written in the units it is
  already kept in.
- `piece` and `household` gain nothing. They have no larger unit above them, and
  a recipe whose units do not suit a large batch is not rewritten by the product
  on the author's behalf.
- A quantity is displayed in full at any magnitude and is never truncated; where
  it and the ingredient name cannot both fit, the name wraps (SCALE-012).

**Consequences:** The industrial case reads naturally at its own scale — a card
for two tonnes of flour shows two tonnes. The absurd case stays readable rather
than being refused: ten million eggs are displayed as ten million eggs. Because
the ladder covers one unit type out of five, the legibility rule, not the
ladder, is what actually protects the layout; the ladder only shortens the
numbers where a real unit exists to shorten them with. Tonnes and cubic metres
enter the glossary, the localized unit names and the rounding tests.

**Alternatives considered:**
- *No rung at all — stop the ladder at kilograms and litres.* Rejected once the
  production-card use was named: it is not only an artefact of extreme scaling,
  and 10 000 000 kg is a worse rendering of a real quantity than 10 000 t.
- *Kilolitres as the volume counterpart of tonnes.* Rejected: bulk liquid in
  food production is measured in litres or in mass, and a kilolitre would be a
  unit added for symmetry that nobody uses. Cubic metres are the unit that is
  actually used at that scale.
- *A ladder for `piece` and `household` as well — larger countable units.* No
  such unit exists that keeps the meaning: a tablespoon has no larger relative,
  and converting either type to mass is forbidden by DOM-003.
- *A ceiling or a warning instead of displaying the number.* Rejected: the
  quantity is correct, and refusing to show a correct value because it is long
  is a layout problem solved in the wrong place.
