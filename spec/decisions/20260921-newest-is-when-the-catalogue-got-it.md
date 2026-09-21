# `newest` is the moment the catalogue got the recipe

**Status:** accepted; completes CAT-001

**Context:** `newest` is the ordering in force for every reader who has not
chosen another, and CAT-001 never said what it orders by. The only definition
anywhere was FLOW-039's, written for the own-recipes view, where it means the
time a recipe was last changed and where it is explicitly contrasted with
published recipes. Two of the four orderings also had no rule for equal values:
RATE-010 and RATE-011 give the rating orderings a chain that makes them total
and paging stable, and `newest` and `most forked` had none, so FLOW-041's
endless scroll could repeat or skip a recipe.

**Decision:** in the catalogue and in search results, `newest` orders by the
instant a recipe entered the catalogue — the approval that published it — most
recent first. Every ordering breaks an equality by the chain RATE-010 already
defines: the more recent creation instant, then the title ascending, then the
identifier. FLOW-039 keeps its own meaning inside the own-recipes view, where
nothing need ever have been published.

**Consequences:**
- A recipe written in March, shared by a private link and published in September
  is new to the catalogue in September. That is the intended reading: the
  catalogue is where a reader meets recipes, and this one arrived there today.
- One chain serves all four orderings, so there is one thing to implement and one
  thing to test, and paging is stable under every ordering rather than under two.
- `newest` means two different things in two places, which is a real cost. It is
  paid because the own-recipes view lists drafts, which have no publication
  instant at all, and FLOW-039 already said so.

**Alternatives considered:**
- *Order by the recipe's creation instant.* Rejected: a recipe appears at the top
  of the catalogue on the day it is published while being ordered by a date
  months earlier, which reads as a defect in the sort and cannot be explained to
  a reader.
- *Order by the time the content last changed, as FLOW-039 does, for one rule
  everywhere.* Rejected: for a published recipe that instant is the last edit
  before it froze — a private fact about the author's drafting — and it would let
  an author's editing history set their position in somebody else's catalogue.
- *Leave the chain to whoever implements it.* Rejected: two implementers produce
  two catalogues, and the one bug it causes — a recipe seen twice while scrolling
  — is invisible in testing and permanent in a reader's impression of the
  product.
