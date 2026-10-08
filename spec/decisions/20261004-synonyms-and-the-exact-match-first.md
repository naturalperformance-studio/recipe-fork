# Synonyms, and the exact match first

**Status:** accepted, supersedes in part [20260927-the-exact-label-and-the-order-of-the-offer.md](20260927-the-exact-label-and-the-order-of-the-offer.md)

**Context:** v0.14 filled the dictionary, and the words cooks actually write —
`картошка`, `манка`, `томат`, `icing sugar` — still found nothing or landed on a
look-alike entry. The 2026-09-27 rule put a parent's leaves before the parent even
when the author had typed the parent's name, and counted only the label itself,
not `яйца` for `яйцо`, as exact. The product owner asked for synonyms; v0.15 built
them with the two ordering changes, and the owner accepted the build on
2026-10-04.

**Decision:**
- An ingredient entry carries synonyms per language (DOM-021). They find the entry
  in the editor's offer and in the book's ingredient filter; the recipe holds the
  entry and reads by its label.
- An entry whose label is exactly what was typed comes first in the offer, before
  its own leaves.
- A plural or inflected form of a label is an exact match; an unfinished beginning
  is not.
- The folding of the 2026-09-27 ADR stands: case ignored, `ё` and `е` one letter,
  either language.

**Consequences:** the author who types the ingredient's everyday name gets the
entry and the filter finds the recipe. The author who types a parent's name gets
the parent first; its leaves are one step down. Which forms count as inflected is
the team's, done per language.

**Alternatives considered:**
- *Keep leaves before the parent always.* Rejected at v0.15, whose goal was that
  search stops landing on look-alike entries that are not what the author meant.
- *Synonyms as separate entries.* Not built; noted here because two entries for
  one ingredient would split the filter.
