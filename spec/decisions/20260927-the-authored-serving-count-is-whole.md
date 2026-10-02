# The authored serving count is a whole number of one or more

**Status:** accepted

**Context:** The requested serving count had a decided form (SCALE-010,
SCALE-011); the authored one had none (SD-013). The domain model typed it "number",
so it admitted zero — a divisor of every scaled quantity — and values the serving
count control cannot hold.

**Decision:** An authored serving count is a whole number of one or more, with no
upper bound (DOM-015).

**Consequences:** Zero cannot occur, and every authored count is one a reader can
request back (FLOW-017, SCALE-011). A recipe written for less than one serving
cannot say so; its author writes it for one, and a reader can still scale it down
below one.

**Alternatives considered:**
- *The requested count's own set, fractions below one included.* Rejected by the
  user in favour of whole numbers.
- *Whole numbers with a ceiling.* Rejected: the requested count has none, and a
  ceiling here would be a second limit with nothing to justify it.
