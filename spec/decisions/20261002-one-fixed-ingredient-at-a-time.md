# One fixed ingredient at a time

**Status:** accepted, changes SCALE-006; adjusts SCALE-016 (2) and SCALE-017 (2)

**Context:** SCALE-006 let the reader fix several ingredients: the latest set the
factor, and every earlier fixing stayed in place and showed how far it fell short
of what the new factor required ("you have 300 g, the recipe now needs 500 g").
The v0.2 build implemented it. At the demo of 2026-10-02 the user found it wrong
in the hand: fixing a second ingredient should simply release the first.

**Decision:**
- At most one ingredient is fixed at any moment. Fixing another releases the
  earlier one; the latest sets the factor (SCALE-006).
- No shortfall is shown anywhere.
- Releasing the fixing still returns to the serving count in force before the
  first fixing of the chain (SCALE-017), and changing the count still releases
  the fixing (SCALE-016).

**Consequences:** a fixed line always means "the recipe is computed from this
amount", and the card never shows two competing statements about what the reader
has. The reader loses the comparison "what I have of A against what the recipe
now needs of A" while scaling by B; they can still read the needed amount of A on
its line. One rule — the latest action sets the scale — now holds without
exceptions ([ADR — the latest scaling action wins](20260927-the-latest-scaling-action-wins.md)).

**Alternatives considered:**
- *Keep every earlier fixing and show its shortfall* (SCALE-006 as written on
  2026-09-20). Rejected by the user at the v0.2 demo: in use it read as clutter,
  and the user expected the earlier fixing to go.
- *Several fixings, the scale set by the scarcest one* (what limits the dish).
  Not built and not asked for; it would need the reader to understand why a
  fixing they did not touch last decides the scale.
