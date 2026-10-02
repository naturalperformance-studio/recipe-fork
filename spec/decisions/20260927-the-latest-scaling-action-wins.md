# The latest scaling action wins: a new count releases every fixing, releasing the last fixing undoes it, and the factor is never shown

**Status:** accepted

**Context:** Scaling has two entry points and the specification required both
without saying how they combine (SD-003). Asking for 6 servings while 300 g of
chicken was fixed gave 750 g under SCALE-001 and 300 g under SCALE-002. Releasing
the only fixed ingredient left the scale undefined. Separately, SCALE-006 (1)
called the factor "displayed" where nothing else showed one (SD-011).

**Decision:**
- Changing the serving count releases every fixed ingredient, and the recipe is
  displayed at the requested count (SCALE-016).
- Releasing the only remaining fixing restores the serving count that was in
  force immediately before the first fixing (SCALE-017).
- The scale factor is not shown to the reader; it is observable through the
  quantities and the approximate serving count. SCALE-006 (1) now says "the
  factor in force".

**Consequences:** One rule governs every combination — the reader's latest action
sets the scale — which SCALE-006 already applied between two fixings. A reader who
changes the count loses their fixings without being asked; the lines lose their
fixed marking, which is how they learn it. Releasing a fixing behaves as an undo,
so no count the reader did not ask for — the approximate count shown during the
fixing — is ever taken as a basis (SCALE-003).

**Alternatives considered:**
- *Lock the count control while an ingredient is fixed.* Rejected: an extra step
  to release before every change, for a state the reader just left behind.
- *Keep the fixing and show a shortfall at the new count.* Rejected: two
  entry points competing for the scale, where SCALE-006 already chose recency.
- *Return to the authored count, or to the usual count, on release.* Rejected:
  either discards a count the reader had set before fixing anything.
- *Stay at the approximate count shown during the fixing.* Rejected: it computes
  from a rounded value.
- *Show the factor (×0.6) beside the count.* Considered and rejected by the user:
  the same state would be stated twice, once rounded and once not.
