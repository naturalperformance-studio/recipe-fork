# Scaling extremes: a floor of ¼ serving, no ceiling, and a warning instead of a bound

**Status:** accepted

**Context:** Scaling has two entry points — a requested serving count and a
fixed ingredient amount — and both can be pushed far past what the authored
recipe describes. Scaling down runs the quantities into the floors of their
unit types (SCALE-005), at which point the displayed recipe no longer holds the
authored proportions. Scaling up produces large numbers and nothing else. The
draft left both ends undefined; OQ-010 and OQ-011 carried the gap.

**Decision:**
- Below one serving the count is displayed as the nearest of ¼, ⅓, ½, ⅔, ¾ —
  the fraction set the `household` unit type already uses — with a floor of ¼
  serving (SCALE-008).
- A requested count may be ¼ serving or more. Below one serving it may be one of
  those five fractions; at one serving and above it may be a whole number
  (SCALE-010, SCALE-011).
- There is no upper bound on the requested count and none on the factor derived
  from a fixed ingredient.
- Once two or more ingredients are held at the floor of their unit type, a
  warning states that the displayed quantities no longer hold the authored
  proportions (SCALE-009).

**Consequences:** One fraction set now governs both the requested and the
displayed serving count, so there is no second grid to keep consistent. The
floor is where the product stops pretending: below ¼ serving the recipe would be
mostly floors, and the count can no longer be requested there. Above, nothing is
refused, so a reader can reach a serving count no kitchen will cook, and the
display must carry whatever number that produces — the rounding grid already
sends quantities above 1000 g into kilograms, so no new display rule was needed.
The warning replaces the bound that was considered for the downward direction: a
reader who fixes a very small amount still gets a recipe, and is told what
happened to it, rather than being refused.

**Alternatives considered:**
- *A wordless "less than one serving" in place of a number.* Rejected: the
  system knows the computed count and would be withholding it.
- *A decimal serving count, e.g. 0.4 servings.* Rejected: no other quantity in
  the product is decimal — SCALE-004 displays ¼ and ⅓ — and the field would have
  been the only place where a reader meets a decimal point.
- *The fraction set at every magnitude, e.g. 2⅓ servings.* Rejected: it needs a
  display rule distinct from SCALE-008 and gives two grids where one suffices.
- *Free numeric entry of any value at or above ¼.* Rejected for the same reason
  as the decimal count.
- *A ceiling of 1000 requested servings.* Considered and rejected: no cost was
  found that it would buy. Scaling is a view computed on the client, it writes
  nothing, it does not touch the USD 20 per month ceiling of CON-002, and the
  floors and the warning act only downward. A ceiling on the field would also
  have left the fixed-ingredient path unbounded, so the two entry points would
  have disagreed.
- *Clamping a derived count to the ceiling instead of refusing it.* Rejected:
  clamping breaks SCALE-002 — the fixed ingredient would stop showing the amount
  the reader actually has.
