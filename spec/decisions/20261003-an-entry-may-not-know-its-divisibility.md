# An entry may not know its divisibility

**Status:** accepted, narrows 20260920-divisibility-belongs-to-the-ingredient; changes FLOW-001 (2) and the IngredientEntry `divisible` field

**Context:** the 2026-09-20 decision made the dictionary entry decide `piece`
divisibility for every ingredient that names it; the author is asked only for a
free-text ingredient. The dictionary is filled by hand and is large (v0.14 aims at
a few thousand entries), and for many entries nobody counts in pieces at all. The
v0.12 build let an entry carry no flag and then asked the author, so that the
dictionary can ship before every entry is settled.

**Decision:**
- An entry's divisibility is `divisible`, `indivisible` or not set.
- Where it is set, it decides, and the author is not asked (as before).
- Where it is not set, the author answers for that ingredient as for free text.
- Every entry that carries a `piece` comparison factor carries a divisibility flag.

**Consequences:** the dictionary can grow without blocking on a question that
does not matter for most entries. Two recipes naming the same unflagged entry may
round its pieces differently; that disappears entry by entry as flags are added.
A flag added later does not rewrite a quantity already published (as the grid
rule of 20260927-stated-quantities-lie-on-the-grid already says).

**Alternatives considered:**
- *A flag on every entry, required.* Rejected: it makes the size of the dictionary
  wait on a question that is meaningless for flour or salt.
