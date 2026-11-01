---
slug: GOAL
status: closed
updated: 2026-10-15
ids: [GOAL-001, GOAL-002, GOAL-003, NGOAL-001, NGOAL-002, NGOAL-003, NGOAL-004, NGOAL-005, NGOAL-006, NGOAL-007]
  NGOAL-005, NGOAL-006, NGOAL-007]
---

# Goals and non-goals

This section states what the product is for and what it deliberately refuses to
be. It contains no `shall` requirements by design: goals are the arbiter that
settles scope disputes in every other section, not behaviour a developer
implements.

## Goals

### GOAL-001 — Cooking from a structured recipe

The product exists so that a person can cook from a recipe that is a
recalculable structured object rather than a block of prose: ingredients with
typed units, scalable quantities, and steps with timers.

**Source:** user decision, 2026-09-20.

### GOAL-002 — Forking as the way a recipe changes

A recipe changes by being forked into an independent recipe that names its
direct parent, never by being edited in place after it has become visible to
anyone other than its owner.

The story this serves: someone else's recipe — from a blog, received as a file —
becomes the person's own version, and sometimes a friend's version follows from
that. It is not a family tree: what a person sees is one step back, the direct
parent (FORK-004).

**Source:** draft sections 1, 4; user decision, 2026-09-20. The story: user
decision, 2026-10-15 (`docs/brand/identity/decisions.md`).

### GOAL-003 — Tiebreak rule: cooking wins over lineage

When GOAL-001 and GOAL-002 demand different behaviour, or when scope must be
cut, the behaviour that serves cooking is kept and the behaviour that serves
lineage visibility is cut. Lineage in the MVP is limited to naming the direct
parent, navigating to it, marking a listed recipe as a fork, and stating how many
direct published forks it has (CAT-004, CAT-005); grouping, comparing or
aggregating a fork tree beyond that single count is out of scope. See
[ADR 20260920 — cooking over lineage](decisions/20260920-cooking-over-lineage.md)
and [ADR 20260920 — lineage is visible in discovery](decisions/20260920-lineage-visible-in-discovery.md),
which narrowed this sentence.

**Source:** user decision, 2026-09-20; narrowed by user decision, 2026-09-20.
Narrowed again 2026-09-27, for the import feature only (not in the first
release): a descendant of an imported recipe also names that imported ancestor
(IMPORT-018). See
[ADR — the imported ancestor is named](decisions/20260927-the-imported-ancestor-is-named.md).

## Non-goals

These are refusals, not unimplemented features. A change to any of them is a
product decision that supersedes this section, recorded as a new ADR.

### NGOAL-001 — No personal data
No name, email address, phone number or avatar is collected at any point.

### NGOAL-002 — No social layer
No comments, no feed, no subscriptions, no messaging, no follower graph.

### NGOAL-003 — No account recovery by the product
An account whose account key and recovery key are both lost, with no device still
signed in to it, is unrecoverable by design. Nobody can restore it — there is no
support channel that could, because the product holds nothing that would let it
(AUTH-008, AUTH-014). Narrowed 2026-09-21: the holder of the recovery key can
retire a lost or stolen account key and be issued a new one (AUTH-021). That is
recovery by the holder, not by the product, and it needs no identity, no personal
data and nobody's help. See
[ADR — the recovery key](decisions/20260921-the-recovery-key.md).

### NGOAL-004 — No editing of published content
Once a recipe is visible to anyone other than its owner it is immutable forever.
A recipe file exported in serverless mode (FLOW-063) is not visibility through the
product and freezes nothing: by user decision, 2026-09-28, once the server is gone
the product publishes nothing, and what a person posts elsewhere is for the place
they post it to answer for.

### NGOAL-005 — No automated media moderation
Automatic classification of images and video is not purchased or built.

### NGOAL-006 — No monetisation
No advertising, no paid tiers, no revenue of any kind.

### NGOAL-007 — No protection of the author from their own mistakes
The product does not check a recipe for spelling, plausibility or craft, does not
correct an author, and does not keep a badly written recipe out of the catalogue. A
rejection under MOD-004 is never for the quality of the writing. Readers decide what
rises: a recipe written carelessly is rated as such, and the orderings of CAT-001 are
what sorts the catalogue.

**Source for NGOAL-001..006:** draft section 10.
**Source for NGOAL-007:** user decision, 2026-09-20. See
[ADR — quality is sorted by readers](decisions/20260920-quality-sorted-by-readers.md).
