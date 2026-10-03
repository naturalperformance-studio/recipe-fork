# Divisibility belongs to the ingredient, not to the recipe

**Status:** accepted; narrowed by [20261003-an-entry-may-not-know-its-divisibility](20261003-an-entry-may-not-know-its-divisibility.md) — an entry may carry no flag, and then the author answers

**Context:** the ingredient dictionary carries a `divisible` flag per entry, which
decides whether a `piece` quantity rounds to whole pieces or to halves
(`scaling-rules`). Both the domain model and `scaling-rules` said the author
could override that flag per ingredient, but the editor has no room for it:
FLOW-023 composes an ingredient from exactly three fields — the ingredient, the
quantity and the unit — and an override is a fourth control.

**Decision:** the entry decides divisibility for every ingredient that names it,
and the author never overrides it. The one place a person supplies the flag is a
free-text ingredient, which has no entry to take it from (DOM-009, FLOW-001).

**Consequences:**
- The ingredient row stays literally three fields, and there is no control whose
  effect an author has to understand before using it.
- A recipe that needs half of something the dictionary calls indivisible cannot
  say so. This is accepted: divisibility is a property of the ingredient, not of
  the recipe that uses it.
- An entry whose flag is wrong is wrong for everybody at once, so the product
  owner corrects it once instead of every author correcting it again. The
  correction reaches devices with the next dictionary refresh (FLOW-001).
- A free-text `piece` ingredient still asks its author for the flag, and that
  question stays, because nothing else can answer it.

**Alternatives considered:**
- *A toggle on the row, shown only for `piece` ingredients.* Rejected: it is a
  fourth control on a row whose whole design is three fields, and it appears on
  some rows and not others, which is harder to read than either rule alone.
- *An override behind the row's menu, beside "delete" and "move".* Rejected: an
  author who needs half an onion will not look in a menu, so the override would
  exist without being found, and the recipe would be published with the wrong
  rounding — permanently, under CON-003.
- *Ask for divisibility on every `piece` ingredient.* Rejected: it puts a
  question the dictionary can already answer in front of every author, every
  time, and it is the question the dictionary was introduced to remove
  ([ADR — the ingredient dictionary](20260920-ingredient-dictionary.md)).
