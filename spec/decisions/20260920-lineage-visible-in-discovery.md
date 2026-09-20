# Lineage is visible in discovery

**Status:** accepted

**Context:** GOAL-003 keeps cooking above lineage and limited lineage in the MVP
to naming the direct parent and navigating to it. That limit is invisible at the
point where it matters most: a reader scanning the catalogue saw nothing about
forks until they opened a recipe. The product owner's market work
([`market-context`](../25-market-context.md), MKT-F3) established that scaling
and timers are shipped by several existing products and cannot differentiate
this one, and that the combination of forking, provenance, no personal data and
moderation is what has no counterpart. Leaving the differentiator behind a tap
was therefore a decision worth revisiting, and revisiting it needed one thing
GOAL-003 had ruled out: an aggregate over a recipe's descendants.

**Decision:** a recipe is marked as a fork wherever it is listed, its number of
direct published forks is stated there, and `most forked` is one of the four
orderings the reader may choose (CAT-001, CAT-004, CAT-005). This narrows the
second sentence of GOAL-003 and nothing else: the tiebreak rule itself — cooking
wins when cooking and lineage conflict — is untouched, and
[ADR — cooking over lineage](20260920-cooking-over-lineage.md) stays accepted.

Refined the same day, once CAT-008 made the number a way into the forks: where a
recipe's total fork count and its published fork count differ, both are stated —
five forks, two of them open. The ordering `most forked` and the list CAT-008
opens use the published count alone.

**Consequences:**
- The mechanic no competing product has is legible before a recipe is opened.
- One aggregate now exists over descendants. It is derived from the parent
  references DOM-004 already holds and is bounded to one level, so DOM-004's ban
  on holding a descendant list is not lifted; whether the number is stored as a
  counter or computed on read is an engineering choice.
- The count and the ordering both depend on the `was_published` test, so a fork
  that was never published is never revealed and never counted (FORK-005,
  CON-001).
- What a high fork count means stays ambiguous: it may mark a recipe people keep
  correcting or one that adapts to any taste. The product owner accepted the
  ambiguity on 2026-09-20 rather than resolve it, because the number harms no
  reader and no reading of it is promoted by the product.
- The total count carries the signal the product owner wanted — that people keep
  adapting this recipe — while the published count is the only one that ranks or
  opens anything, so `most forked` cannot be climbed by forking one's own recipe
  privately. Inflating it needs the moderator's approval, which is what every
  other route to visibility already needs (ABUSE).
- Two numbers have to be made legible in a small space, and the difference between
  them has to be understandable without explaining moderation to the reader.
- Every further aggregate — a fork tree, a comparison of siblings, a count of all
  descendants rather than direct ones — remains out of scope and would need its
  own decision.

**Alternatives considered:**
- *Leave the catalogue silent about lineage, as GOAL-003 had it.* Rejected: the
  one differentiating mechanic would be reachable only by readers who already
  opened a recipe, which is the audience that needs convincing least.
- *Mark forks but state no number.* Rejected by the product owner: the marker
  alone says a recipe descends from something, while the number is what says the
  recipe is a starting point others built on.
- *Count all descendants rather than direct forks.* Rejected: it is a tree
  traversal, GOAL-003 refuses tree work, and a deep chain would inflate the
  number of every ancestor without telling the reader anything they can act on.
- *One number counting published forks only.* Rejected by the product owner: it
  loses the signal that a recipe is adapted often, which is the property the
  number exists to show.
- *One number counting every fork, with the ordering and the list quietly using a
  different one.* Rejected: a number of five that opens a list of two reads as a
  defect, and nothing in the interface could explain it.
- *Order the catalogue by fork count by default.* Not chosen: `newest` is the
  default (CAT-001), because a rating- or fork-count-based default becomes nearly
  static as the catalogue grows.
