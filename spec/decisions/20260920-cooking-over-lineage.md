# Cooking wins over lineage when the two conflict

**Status:** accepted

**Context:** The draft states two product values without ranking them: a recipe
as a recalculable structured object usable while cooking, and a git-like fork
model where recipes descend from other recipes. The two prescribe different
products at several concrete points.

Search is the sharpest one. A cooking-first search answers "what can I make from
chicken" with a ranked list of distinct dishes. A lineage-first search answers
"show me the variants of this carbonara" by grouping results under the root of a
fork chain. Grouping by root requires storing the root of every chain, which
contradicts draft section 4.3: only the direct parent is stored and no full tree
is ever built.

Immutability is the second. It is what makes lineage trustworthy — a published
version can never be rewritten under a fork that descends from it — and it is
what makes cooking worse, because a wrong quantity in a published recipe can
never be corrected, only forked.

Without a ranking, every MVP scope question has to be re-argued from scratch.

**Decision:** GOAL-001 (cooking) outranks GOAL-002 (lineage). Where the two
conflict, or where scope must be cut, the cooking behaviour is kept. Lineage in
the MVP is the direct parent reference and click-through navigation only.

**Consequences:**
- Search is designed to return distinct recipes. No grouping of forks of the
  same dish, no "N variants" affordance, no deduplication by lineage.
- The catalogue will show near-duplicate recipes when a popular recipe is forked
  many times with small changes. This is accepted, not mitigated, in the MVP.
- The root of a fork chain is not stored, consistent with draft section 4.3.
  Adding lineage aggregation later is a data model change, not a UI change.
- Immutability is retained because the draft states it as a principle, but its
  cost is now explicit: a typo in a published recipe is permanent, and the only
  correction is a fork, which adds a near-duplicate to the catalogue.

**Alternatives considered:**
- *Lineage outranks cooking.* Rejected by the user. It would also have forced a
  reversal of draft section 4.3, since variant grouping needs the chain root.
- *No ranking; treat both as equal.* Rejected: it leaves no arbiter, so every
  scope dispute returns to the product owner individually.
