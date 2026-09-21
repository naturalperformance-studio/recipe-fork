# A removed recipe is still its author's work, and they may fork it

**Status:** accepted; corrects ST-004

**Context:** ST-004 said two different things about one case. Its requirement
sentence and
[ADR — forking from published only](20260920-forking-published-only.md) both
allow an owner to fork their own recipe *in any state*; its acceptance criterion
and the `Disallowed from removed` list both refused an owner's fork of a recipe a
moderator had removed. A developer reading the section could implement either.

The case is not theoretical. Removal has two categories (FLOW-047). A recipe
removed for using someone else's content is removed for a photograph or a
description — precisely the parts a fork does not carry (FORK-002) — while its
ingredients and steps are the author's own method, which RET-009 keeps readable
to them for as long as their account exists.

**Decision:** An owner may fork their own removed recipe. No other account may.
A fork made this way carries only the media that still exists, because MEDIA-013
deletes what nothing else holds at the moment of removal.

**Consequences:**
- An author whose recipe was removed can carry the work forward instead of
  retyping it from a screen they can still read.
- The fork names the removed recipe as its parent, which was published, so
  ST-007 shows that parent as unavailable to every reader. The chain stops
  there. Nothing about the removal is disclosed except in the one category
  ST-007 already names.
- A removal for using someone else's content does not prevent the same author
  republishing the method without the photograph. That is the intended outcome:
  the method was never the thing removed.
- A recipe removed as dangerous to health is the case that needed a further
  decision, because MOD-008 flags descendants at the moment of removal and a
  fork made afterwards is not among them.
- The `removed` state stays terminal for the recipe itself. Forking creates a
  new recipe and changes nothing about the old one, which is true of every fork
  in the product.

**Alternatives considered:**
- *Keep the criterion: nobody forks a removed recipe, its owner included.*
  Rejected by the product owner. It makes removal for someone else's photograph
  cost the author their own text, which no other rule in the product does — and
  the text is visible to them the whole time under RET-009, so the loss is
  arbitrary rather than principled.
- *Allow anyone to fork a removed recipe.* Not considered seriously: a removed
  recipe is not accessible to anyone but its owner and a moderator, so there is
  nobody else who could reach it to fork it.
