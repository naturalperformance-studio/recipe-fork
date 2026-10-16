# Comparison factors and the two-level dictionary

**Status:** accepted; narrowed by [one unit system, converted on display](20261015-one-unit-system-converted-on-display.md) (2026-10-15) — a factor may now produce a displayed quantity marked "≈"

**Context:** a filter on an ingredient orders recipes by how much of it a serving
contains (CAT-002), and the same ingredient is stated in units that do not
compare: 500 g of chicken in one recipe, two wings in another, three tablespoons
in a third. Placing the comparable recipes first and the rest after them was
rejected by the product owner with the case that kills it — a recipe using 5 g of
chicken per serving would be ordered above one that needs two whole carcasses,
purely because the first is written in grams. A single factor per dictionary entry
was not enough either: one piece of `курица` is a wing in one recipe and a whole
bird in another, and the qualifier that tells them apart is free text carrying no
data.

**Decision:** the ingredient dictionary gains one level — `крылышки`, `филе бедра`
and `тушка` are children of `курица` — and every entry carries a comparison factor
per unit, on a scale shared by the whole family. The author picks one entry, the
leaf they actually mean, and the dictionary supplies the parent, so the hierarchy
is the product owner's data and not a second choice at authoring time. A filter on
a parent matches every child; a filter on a child matches only it (CAT-006). A
line's comparable quantity is its quantity for one serving times its entry's
factor for its unit (CAT-009). A factor is never displayed, never used when a
recipe is scaled, and lives on the entry rather than in the recipe (DOM-011).

**Consequences:**
- Ordering is total. There are no bands, no boundary to explain to the reader, and
  a whole carcass outranks five grams of fillet.
- Because a factor lives on the dictionary entry and a recipe is immutable after
  publication (CON-003), a wrong factor is correctable for ever and the correction
  improves the ordering of every recipe already published. Nothing the author
  stores is involved, so no author can mistype it.
- Numerically, a factor for `шт` on a family measured in grams is an approximate
  mass; DOM-003 is untouched only because DOM-011 forbids showing it, deriving a
  displayed quantity from it, or scaling with it.
- The product owner now maintains, per family, a hierarchy and a small table of
  factors, on top of the labels. This is the third increment of the same
  continuous cost the ingredient dictionary introduced.
- A unit with no factor yet drops its line out of the comparison, and a recipe
  whose every matched line drops out is ordered last. The dictionary can therefore
  ship incomplete and improve without any recipe being wrong.
- `piece_weight` keeps its original purpose — keeping scaling smooth — and takes no
  part in ordering, so ordering has exactly one source of truth and it is not
  author-supplied.

**Alternatives considered:**
- *Two bands, comparable quantities first.* Rejected by the product owner: five
  grams of chicken would outrank two whole carcasses.
- *Three bands, with pieces compared against pieces.* Rejected for the same reason
  at one remove: two wings would outrank one carcass.
- *A flat dictionary with one factor per entry.* Rejected: it cannot tell a wing
  from a whole bird, which is the case that prompted the question.
- *A flag on the entry marking "a piece means different things", making
  `piece_weight` required for those ingredients.* Rejected by the product owner:
  two actions for the author and a place to mistype, for data the dictionary can
  hold once.
- *A real table of ingredient weights.* Rejected: DOM-003 forbids it, and it is a
  different product with its own maintenance burden.
