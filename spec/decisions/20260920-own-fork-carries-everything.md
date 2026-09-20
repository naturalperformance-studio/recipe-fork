# An own fork carries everything

**Status:** accepted

**Context:**
[ADR — a fork carries the method, not the expression](20260920-fork-carries-method-not-expression.md)
withholds media and the description from every fork, on the reasoning that a
published fork must never redistribute a photograph or prose its author did not
write. That reasoning is entirely about one author and another author.

It collides with the fact that publication is permanent (ST-001) and content
freezes at external visibility (CON-003). An author who finds a mistake in their
own published recipe has exactly one route: fork it, correct the fork, publish
the fork. Under the rule as written, that route deleted every photograph they had
taken and every word of their description — a price for fixing a typo that the
product owner would not have accepted had it been stated that way, and one no
rights holder benefits from, because the author is the rights holder.

**Decision:** when the owner of a recipe forks that recipe, the fork carries the
parent's media and description as well as its title, tags, servings, ingredients
and steps (FORK-007). A fork by any other account carries neither (FORK-002).
Ratings, favourites, reports and moderation history are carried by no fork at
all, including an own fork (FORK-006).

**Consequences:**
- Correcting one's own published recipe costs the author nothing but the
  correction itself, which is what makes the product's one repair route usable.
- What a fork inherits now depends on who is forking, so the editor's statement
  of what travels has to say both cases (FLOW-030), and so does the statement
  before submission (GUIDE-001).
- The fork holds Media entities of its own, owned by the same account (DOM-005),
  so removing or hiding either recipe leaves the other's media alone. Whether the
  stored bytes are shared is an engineering question bounded by CON-002.
- An own fork of a recipe that was itself forked from someone else carries only
  the media and description the owner added — there are no others on it, by
  FORK-002 — so no chain of own forks can launder another author's photograph
  into a new recipe.
- The catalogue will hold pairs of near-identical recipes by one author, the
  corrected one and the mistaken one, both with the same photographs, and the
  mistaken one can never be taken down (ST-001). This is the accepted cost, and
  it is a cost of permanent publication rather than of this decision.

**Alternatives considered:**
- *Leave FORK-002 as it was.* Rejected: it prices a typo at the author's entire
  photographic work, which makes the only correction route one that authors will
  not take. The result is not fewer mistakes but uncorrected ones.
- *Let the owner edit a published recipe instead.* Rejected: it is CON-003 and
  [ADR — publication is permanent](20260920-publication-is-permanent.md), and
  both stand. A reader who cooked from a recipe must find the same recipe there
  afterwards.
- *Carry the media but not the description.* Rejected: both are the author's own
  expression and neither is somebody else's, so there is no line between them to
  draw here.
- *Carry ratings and favourites into an own fork too, so a correction keeps its
  standing.* Rejected: a like is a reader's judgement of a particular recipe,
  which the author has just changed. Carrying it over would let an author move
  accumulated approval onto content nobody approved, which is also an abuse
  vector (`abuse`).
- *Offer the author a "correct this recipe" action distinct from forking.*
  Rejected as the same thing under another name: it would produce a new recipe
  naming the old as parent, which is a fork, and a second word for it would need
  its own rules about provenance, chains and counts.
