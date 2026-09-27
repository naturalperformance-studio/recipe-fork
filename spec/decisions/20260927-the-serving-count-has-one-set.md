# The serving count has one set, and what the reader asked for is exact

**Status:** accepted, corrects SCALE-001, SCALE-008 and SCALE-011; narrows
[20260927-the-rounding-grid-completed.md](20260927-the-rounding-grid-completed.md)
for the serving count

**Context:** gate 1 found three faults in the serving count (SD-027, SD-032).
SCALE-001 (2) showed 6 pieces where the grid gives 5. A computed count from 0.875
up to 1 was ≈¾ by SCALE-008, which chose among the five fractions alone, and ≈1
by SCALE-015, which chose among one set that holds 1. And SCALE-008 (3) marked
"any" fraction as approximate, so a ½ the reader asked for was ≈½, while
SCALE-016 (3) and SCALE-017 (4) left a requested count unmarked.

**Decision:**
- The serving count is rounded over one set — ¼, ⅓, ½, ⅔, ¾ and the whole
  numbers — by SCALE-015. It is shown as a fraction only where that rounding stays
  below one; 0.875 and everything above go to 1.
- Only a computed count, one produced by a fixed ingredient, is marked as
  approximate. A count the reader requested is shown as requested and unmarked,
  in any form.
- SCALE-001 (2) shows 5 pieces for an exact 4.5 and states the exact value was
  lower. This corrects an example; the behaviour was already decided.

**Consequences:** one rounding rule, with no second grid below one, as the
scaling-extremes ADR already required. The largest error a displayed count can
carry is half the gap between neighbours in the set, and 0.99 no longer shows as
¾. The approximate mark means one thing: this count came from an ingredient you
fixed. A developer has no special case for fractions.

**Alternatives considered:**
- *Fractions alone below a computed one (0.92 → ≈¾).* Rejected: it is the second
  grid the scaling-extremes ADR refused, and it puts 0.99 a quarter-serving away.
- *Every fraction marked as approximate, asked for or not.* Rejected: a reader
  who asked for exactly ½ would be told it is about ½, and SCALE-016 and
  SCALE-017 would need an exception for fractions.
