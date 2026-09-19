---
slug: OQ
status: open
updated: 2026-09-20
ids: [OQ-001, OQ-002, OQ-003, OQ-004, OQ-005, OQ-006, OQ-007, OQ-008]
---

# Open questions

Every entry is classified **define before MVP** or **define after MVP**. A
proposal attached to an entry is a proposal, not a decision, and is never
written into a requirement until the product owner agrees to it.

The entries below were carried over from section 11 of the source draft, where
the product owner marked them open. Classification and proposals are the spec
author's; the product owner may reclassify any of them.

### OQ-001 — Target platform (define before MVP)
Unresolved: whether the product is a web application, a native mobile
application, a PWA, or a combination.
Proposal (not a decision): PWA, because timers and offline recipe viewing while
cooking need a resident surface, while store review would otherwise gate every
release of a non-commercial project.
Blocks: sections `flows`, `quality`, `accessibility`, `observability`.

### OQ-002 — Key file format and cryptography (define before MVP)
Unresolved: the format of the account key file, the cryptography behind it, and
how the server distinguishes a genuine key from a forged one.
Proposal (not a decision): the key file carries a private key; the account
identifier is derived from the matching public key; authentication is a
challenge signed by the client, so the server never stores anything that can
recreate the key.
Blocks: section `auth`.

### OQ-003 — Media limits (define before MVP)
Unresolved: maximum image size, maximum video duration and file size, number of
media items per recipe, total storage per account.
Proposal (not a decision): images re-encoded client-side and rejected above
2 MB after re-encoding; video limited to 30 seconds and 15 MB; at most 1 cover
image plus 1 media item per step.
Blocks: section `media`.

### OQ-004 — Retention of inactive accounts and unpublished media (define before MVP)
Unresolved: whether unpublished media and dormant accounts are deleted to
contain storage cost, and after how long.
Proposal (not a decision): unpublished media belonging to an account with no
authenticated session for 12 months is deleted after an in-product warning is
impossible to deliver (there is no contact channel), so deletion is announced
only in the published terms.
Blocks: sections `retention`, `legal`.

### OQ-005 — Deleted parent recipe (define before MVP)
Unresolved: what a fork shows when its direct parent is no longer available.
Proposal (not a decision): the parent reference is retained and rendered as an
unavailable parent with the reason class (removed by moderation / deleted by
author), and the chain stops there.
Blocks: sections `provenance`, `entity-states`.

### OQ-006 — Reporting published recipes (define after MVP)
Unresolved: whether a reader can report a published recipe, and what happens to
it while a report is open.
Proposal (not a decision): no reporting in the MVP; the single moderator
approves everything before it becomes visible, so post-moderation has no queue
to feed it.
Blocks: section `abuse`.

### OQ-007 — Deleting a recipe that has forks (define before MVP)
Unresolved: whether a published recipe can be deleted at all, and what happens
to recipes that descend from it.
Proposal (not a decision): descendants are never touched; deletion of an
ancestor never cascades. Related to OQ-005.
Blocks: sections `entity-states`, `provenance`.

### OQ-008 — Text auto-filter and false positives (define before MVP)
Unresolved: which automatic text filter is used, and what happens to a recipe it
flags incorrectly.
Proposal (not a decision): a flag routes the recipe to the human moderator
instead of rejecting it, so a false positive costs latency rather than a lost
recipe.
Blocks: section `moderation`.
