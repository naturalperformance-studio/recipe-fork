# Forking is allowed from published recipes, and from one's own in any state

**Status:** accepted — supersedes draft section 4.1

**Context:** The draft allows forking of "any recipe accessible to the user",
which includes a recipe received through a private link. A fork of a link-shared
recipe would name a parent that most readers can never open, and that the owner
can make unreachable at any time by revoking the link. The catalogue would carry
recipes whose stated origin is permanently invisible.

The product owner also fixed publication as permanent
([ADR — publication is permanent](20260920-publication-is-permanent.md)), which
makes published recipes the only reliably durable parents in the system.

**Decision:** Any account may fork a `published` recipe. An owner may fork their
own recipe in any state, `removed` included — see
[ADR — a removed recipe is still its author's work](20260921-a-removed-recipe-is-still-its-authors-work.md),
which settled a case where ST-004's own criteria contradicted this sentence.
Every other fork is rejected — in particular, a recipient of a private link
cannot fork what they received.

**Consequences:**
- Every parent reference that a non-owner can see points at a recipe that was
  public at the time of forking, and remains so unless a moderator removes it.
- A `link_shared` recipe is not a dead end for its author: the author can fork
  their own copy, edit the fork and submit it. It is a dead end for its
  recipient, who can read, scale, favourite and report it but cannot build on
  it. *Corrected 2026-09-21:* this sentence also said "rate". RATE-003, decided
  the same day as this ADR, offers no rating control in `link_shared` at all,
  because a rating collected through a channel the author chose the recipients
  of is a score the public never gave. The requirement is right and this ADR was
  stale.
- The draft's "any accessible recipe can be forked" no longer holds and should
  not be quoted from section 4.1.
- A private link is now a way to show a recipe, never a way to seed one. Sharing
  a recipe for someone to build on requires publishing it.

**Alternatives considered:**
- *Any accessible recipe is forkable, as in the draft.* Rejected: it produces
  forks whose parent is invisible to everyone but the link holders, and whose
  parent can be revoked afterwards.
- *Own recipes are not forkable either, for a single uniform rule.* Rejected: it
  would leave a link-shared recipe permanently frozen with no way for its own
  author to carry the work forward.
