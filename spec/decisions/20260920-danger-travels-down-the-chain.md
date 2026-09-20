# Danger to health travels down the fork chain; a rights claim does not

**Status:** accepted. Supersedes the part of the decision recorded in ST-007
that gave no reason for any unavailable recipe.

**Context:** A recipe can be removed in one of the two report categories of
ABUSE-002: use of someone else's content, or danger to health. Until now a
removed recipe was simply unavailable wherever it appeared, with no reason
given, and nothing happened to its forks. The two categories are not alike. A
photograph's provenance does not survive a fork — FORK-002 copies no media, and
what a fork does carry is the method and the ingredient list, which are not the
protected work. An unsafe instruction is the opposite: it lives in the steps,
and the steps are exactly what every fork inherits, to any depth.

**Decision:**
- A removal in the category danger to health sets a safety flag on every
  descendant of the removed recipe, however deep (MOD-008). The flag does not
  withdraw anything: flagged recipes stay published and readable.
- Flagged recipes are reviewed as a list separate from the submission queue,
  ordered from the deepest descendant upwards, so the moderator can walk the
  chain until the unsafe instruction stops appearing (MOD-009).
- Clearing a flag clears it on one recipe and never on its descendants
  (MOD-010). Confirming the danger means removing that recipe, which flags its
  own descendants in turn.
- A flagged recipe states on its card that it descends from a recipe removed as
  dangerous to health, and names nothing (FLOW-018).
- The reason for a removal is stated only for danger to health, wherever the
  removed recipe still appears — in favourites and as an unavailable parent
  (FLOW-010, ST-007). A recipe removed for using someone else's content is
  unavailable with no reason, indistinguishable from one hidden by reports.

**Consequences:** A single removal can put an unbounded number of recipes in
front of one moderator, which is the price of leaving the descendants readable
instead of withdrawing them automatically. An author who rewrote a forked recipe
beyond recognition is still flagged, because the flag is inherited from an event
and not measured against the text; the moderator's review is what removes it.
Private descendants carry the flag and are seen by their owners, but cannot be
reviewed until they are submitted, because a moderator cannot see a private
recipe. The product now walks the fork tree downwards in one place, which
FORK-004 refuses to do for a card.

**Alternatives considered:**
- *Name the reason for both categories.* Rejected by the product owner: a
  rights claim decided by one moderator and then published beside the recipe
  brands its author, and tells a reader nothing they can act on. Legal
  obligations are met by removing the recipe, not by announcing why.
- *Give no reason at all, as ST-007 originally required.* Rejected for the
  safety category: a reader who cooked from a recipe and may cook from it again
  has a use for that one fact.
- *Withdraw every descendant automatically when an ancestor is removed as
  dangerous.* Rejected: it punishes authors whose forks may be safe, on no
  evidence, and a single false removal would take a whole subtree off the
  catalogue.
- *Flag only the direct forks.* Rejected: the text reaches a fork of a fork
  unchanged, so depth is not evidence of safety.
- *Clear a flag down the whole chain when one recipe is cleared.* Rejected: the
  moderator has read one recipe, not its descendants, and any of them may have
  reintroduced what was cleared above it.
