# One unit system, chosen once, converted on display

**Status:** accepted, 2026-10-15. Supersedes
[US units on display; writing in imperial units later](20261011-us-units-on-display-imperial-writing-later.md).
Narrows [comparison factors](20260920-comparison-factors.md) (DOM-011) and
[a quantity anyone states lies on the grid](20260927-stated-quantities-lie-on-the-grid.md)
(SCALE-018). Answers OQ-042 and OQ-043.

## Context

Until v0.35 recipes were written in metric and household units only, and the
dictionary's comparison factors — approximate masses per unit — ordered the
ingredient filter and were never allowed to produce a displayed quantity (DOM-011).
The owner adopted metric ↔ US conversion (H-003) on 2026-10-10 and, at the
planning of v0.35 on 2026-10-12, chose its shape: one setting for the whole
application, the editor offering the chosen system's units, every recipe shown in
that system and never in both at once, and the full kitchen conversion — mass to
cups and spoons too — through the dictionary, marked approximate. v0.32 had already
merged one entry's lines in different units in the shopping list the same way. The
owner accepted v0.35 on the lead's review (2026-10-12).

## Decision

- One setting, Metric or US, its first value taken from the device's region.
- Units belong to a system: g, kg, t, ml, l, m³ to metric; oz, lb, fl oz to US;
  pieces, the household units (tsp, tbsp, cup — «стакан») and pinch and to taste
  to both. The editor offers the chosen system's units and the shared ones.
- A quantity is stored as written and never rewritten. On display it is scaled in
  its own unit, converted into the reader's system where its unit is not in it,
  and rounded once on the target unit's grid.
- Exact conversions (ounces and grams, fluid ounces and millilitres, millilitres
  into US cups and spoons by the 8 fl oz cup) are shown like any scaled amount.
  A conversion through an entry's comparison factor — a mass into cups or spoons
  for a US reader — is marked "≈".
- A shared unit is shown as written in both systems. The household units are
  shared, so a cup written by a Russian author reads as «стакан» to a metric reader
  and is not turned into grams (owner's choice at the sync of 2026-10-15, over the
  v0.35 build, which showed a cup as "≈ grams" in Metric).
- A comparison factor may therefore produce a displayed quantity, always marked
  "≈", and nothing else changes: it never alters a stored quantity and never takes
  part in scaling.

## Consequences

- DOM-011 is rewritten; DOM-016 grows from twelve units to fifteen; DOM-017 gains
  the US grids; SCALE-018 accepts a fixed amount in the unit a line is shown in;
  L10N-008 is rewritten. DOM-003 stands as it was: a household quantity is never
  shown as a mass.
- Correcting a factor in the dictionary now changes the "≈" amounts a reader sees;
  what the author wrote never changes.
- The v0.35 build converts a cup into grams for a metric reader; that is a known
  departure until the team changes it.

## Alternatives considered

- **Conversion within a dimension only** (OQ-042's proposal: grams to ounces,
  millilitres to cups, never mass to volume): rejected by the owner — a US cook
  measures flour in cups, and a recipe in grams would stay unusable to them.
- **The cup as a US unit, converted to grams for a metric reader** (as built in
  v0.35): rejected at the sync — «стакан» is a measure of the Russian kitchen, and
  a cook would be measuring with something other than what the author wrote.
- **Writing in metric only** (L10N-008 until 2026-10-11): superseded.

**Source:** user decisions, 2026-10-12 (planning of v0.35) and 2026-10-15 (sync of
v0.31–v0.38).
