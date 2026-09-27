# An imported recipe belongs to its source, not to whoever imported it
**Status:** accepted, 2026-09-27. Feature is outside the first release (MVP-002).

**Context:** The product owner wants recipes imported from Instagram, TikTok and
Pinterest. Every product found that does this keeps the import private to the
person who saved it (MKT-F6, MKT-F7), and the two largest say so for a legal
reason. This product's whole value is a published recipe that others fork, so a
private import would be a notebook bolted onto a catalogue. Publishing an import
under the importing account, as an ordinary recipe, would make that account grant
LEG-001's licence over text it did not write.

**Decision:**
- A language model turns the source post into a recipe.
- The result is published as the source's recipe: no account is its author, and it
  carries a mark naming its source and linking to it.
- It carries no media of any kind.
- One source is turned into a recipe once; a second import of the same source
  leads to the recipe that already exists.
- Every recipe descending from an imported recipe, through any number of forks,
  shows that its original was imported.

**Consequences:**
- Buys: nobody grants a licence over another person's text, because nobody claims
  to be its author; one source is one recipe, so duplicates do not form; the fork
  model absorbs editing with no new mechanism; the product becomes a channel that
  sends readers to the source, which is the argument the product owner makes for
  it to cooking bloggers.
- Costs: the one copying is now the product itself, not a user. What protects it
  is the same line the fork already draws
  ([ADR — a fork carries the method](20260920-fork-carries-method-not-expression.md)):
  ingredients and steps are a method, the caption's prose is expression, and only
  the former is carried. Promotion benefits the blogger but is not their
  permission; their claim arrives by ABUSE-007 and removes the recipe.
- Costs: the model is a paid external dependency. EXT-001 is widened by the
  separate decision on where the text comes from, and CON-002 requires a cost
  estimate before the feature is accepted into scope.
- Costs: a model gets quantities wrong. An import published with no human in
  the loop can carry an error into the catalogue, and a removal for danger then
  flags every descendant ([ADR — danger travels down the chain](20260920-danger-travels-down-the-chain.md)).
- A published recipe is immutable (CON-003), so parsing the same source again with
  a newer model could only ever produce a new recipe, never correct the old one.
  Whether that is offered is not decided here.

**Alternatives considered:**
- *Private import, as ReciMe does.* Rejected: an import nobody else can see or
  fork contributes nothing to a product that exists to be forked.
- *Publish under the importing account.* Rejected: the account would grant
  LEG-001's licence over text it does not own, and would appear as the author of
  someone else's recipe.
- *Attach a frame of the source video as the picture.* Rejected: it requires
  downloading the video, which Google Play's intellectual property policy names
  as a violation ("apps that allow users to download a local copy of copyrighted
  content without authorization",
  [Google Play — Intellectual Property](https://support.google.com/googleplay/android-developer/answer/9888072?hl=en),
  checked 2026-09-27), and it would be the blogger's expression published under
  nobody's licence.
