---
slug: OQ
status: open
updated: 2026-09-20
ids: [OQ-001, OQ-002, OQ-003, OQ-004, OQ-005, OQ-006, OQ-007, OQ-008, OQ-009]
open_before_mvp: [OQ-002, OQ-003, OQ-004, OQ-005, OQ-007, OQ-008]
---

# Open questions

Every entry is classified **define before MVP** or **define after MVP**. A
proposal attached to an entry is a proposal, not a decision, and is never
written into a requirement until the product owner agrees to it.

The entries below were carried over from section 11 of the source draft, where
the product owner marked them open. Classification and proposals are the spec
author's; the product owner may reclassify any of them.

### OQ-001 — Target platform — RESOLVED 2026-09-20
Resolved by the product owner: native Android application, no other client
surface in the MVP. See CON-005 and
[ADR 20260920 — native Android MVP](decisions/20260920-native-android-mvp.md).

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
Bounded by: CON-002 (USD 20 per month). Any limit proposed here is only
acceptable if its storage and egress cost at the expected load fits that ceiling.
Blocks: section `media`.

### OQ-004 — Retention of inactive accounts and unpublished media (define before MVP)
Unresolved: whether unpublished media and dormant accounts are deleted to
contain storage cost, and after how long.
Proposal (not a decision): unpublished media belonging to an account with no
authenticated session for 12 months is deleted. There is no contact channel, so
no warning can be delivered to the account holder and the rule can only be
announced in the published terms.
Bounded by: CON-002.
Also covers accounts that never produced anything: an account is created on every
first launch (ACT-001) and permanently consumes one nickname (DOM-001), so
installs that are opened once and deleted accumulate as empty accounts.
Blocks: sections `retention`, `legal`.

### OQ-005 — Deleted parent recipe (define before MVP)
Unresolved: what a fork shows when its direct parent is no longer available.
Proposal (not a decision): the parent reference is retained and rendered as an
unavailable parent with the reason class (removed by moderation / deleted by
author), and the chain stops there.
Blocks: sections `provenance`, `entity-states`.

### OQ-006 — Reporting published recipes — RESOLVED 2026-09-20
Reporting is in the MVP, not after it: Google Play requires in-app reporting of
content and of authors and in-app blocking of authors for apps with publicly
accessible user-generated content (CON-006). A published recipe is hidden once
three distinct accounts report it; blocking an author hides that author's
recipes from the blocker's catalogue and search only. See
[ADR 20260920 — report and block model](decisions/20260920-report-and-block-model.md).
Requirements to be written in section `abuse`.

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

### OQ-009 — Nickname space exhaustion (define after MVP)
Unresolved: what happens when no unused three-word combination remains, given
that nicknames are globally unique, permanent, and consumed by every first
launch (ACT-001, DOM-001).
Proposal (not a decision): size the word lists so the combination count exceeds
any plausible account count by a wide margin, and watch the consumed fraction as
an operational signal (section `observability`) so the lists can be extended
long before they run out.
Blocks: nothing while the fraction consumed stays low.
