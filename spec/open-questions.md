---
slug: OQ
status: open
updated: 2026-09-20
ids: [OQ-001, OQ-002, OQ-003, OQ-004, OQ-005, OQ-006, OQ-007, OQ-008, OQ-009, OQ-010, OQ-011,
  OQ-012, OQ-013]
open_before_mvp: [OQ-002, OQ-003, OQ-004]
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
What video is for, stated by the product owner 2026-09-20: a short clip showing
a technique — how something is cut, how one ingredient goes into another — never
a lesson. The limits chosen here must be tight enough that a clip weighs close
to a photo, because FLOW-011 holds photo and video under one setting on that
assumption.
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

### OQ-005 — Deleted parent recipe — RESOLVED 2026-09-20
Resolved: the parent reference is kept and shown as unavailable, with no reason
given, and the chain stops there (ST-007). The only way a parent becomes
unavailable is removal by a moderator, because publication is permanent
([ADR — publication is permanent](decisions/20260920-publication-is-permanent.md)).

### OQ-006 — Reporting published recipes — RESOLVED 2026-09-20
Reporting is in the MVP, not after it: Google Play requires in-app reporting of
content and of authors and in-app blocking of authors for apps with publicly
accessible user-generated content (CON-006). A published recipe is hidden once
three distinct accounts report it; blocking an author hides that author's
recipes from the blocker's catalogue and search only. See
[ADR 20260920 — report and block model](decisions/20260920-report-and-block-model.md).
Requirements to be written in section `abuse`.

### OQ-007 — Deleting a recipe that has forks — RESOLVED 2026-09-20
Resolved: an owner can never remove a published recipe (ST-001); only a
moderator can (ST-002). Removal never cascades to descendants — a fork survives
its parent's removal and shows the parent as unavailable (ST-007).

### OQ-008 — Text auto-filter and false positives — RESOLVED 2026-09-20
Resolved: a flag never rejects anything by itself; it routes the submission to
the human moderator (MOD-002). Which filter implementation is used is an
engineering choice constrained by CON-002 and by section `localization`, not a
product decision.

### OQ-009 — Nickname space exhaustion (define after MVP)
Unresolved: what happens when no unused three-word combination remains, given
that nicknames are globally unique, permanent, and consumed by every first
launch (ACT-001, DOM-001).
Proposal (not a decision): size the word lists so the combination count exceeds
any plausible account count by a wide margin, and watch the consumed fraction as
an operational signal (section `observability`) so the lists can be extended
long before they run out.
Blocks: nothing while the fraction consumed stays low.

### OQ-010 — Serving count below one — RESOLVED 2026-09-20
Resolved by the product owner: below one serving the count is displayed as the
nearest of the fractions ¼, ⅓, ½, ⅔, ¾ — the same set the `household` unit type
uses — with a floor of ¼ serving. The rejected alternatives were a wordless
"less than one serving" and a decimal count; see
[ADR — scaling extremes](decisions/20260920-scaling-extremes.md). Written as
SCALE-008; SCALE-002 is no longer blocked.

### OQ-011 — Bounds on requested servings and scale factor — RESOLVED 2026-09-20
Resolved by the product owner: a requested serving count has a floor of ¼
serving and no ceiling; the factor derived from a fixed ingredient is unbounded
too, so both entry points obey one rule. Below one serving the count is both
requested and displayed as one of ¼, ⅓, ½, ⅔, ¾; at one serving and above it is
a whole number. Instead of an upward bound, a warning appears once two or more
ingredients are held at the floor of their unit type. Written as SCALE-009,
SCALE-010 and SCALE-011; see
[ADR — scaling extremes](decisions/20260920-scaling-extremes.md).

### OQ-012 — Managing favourites by weight (define after MVP)
Unresolved: whether a recipe's card states how much adding it to favourites will
download, and whether favourites gain a management mode that lists them by the
space they occupy.
Decided for the MVP by the product owner 2026-09-20: neither is built. Device
space is governed by a setting that covers all favourites at once, not
per recipe, and no size is shown on a card.
Proposal for later (not a decision): if it returns, the number on a card means
exactly what adding to favourites will download, not the recipe's total media
weight, since what is downloaded depends on that setting.
Blocks: nothing.

### OQ-013 — Holding the recipe currently being read (define after MVP)
Unresolved: whether the last recipe opened is held on the device even when it is
not in favourites.
Decided for the MVP by the product owner 2026-09-20 by not adopting it: only
favourites are held.
Consequence to watch: a recipe opened from the catalogue and not favourited is
held nowhere, so it is lost once the application is closed, or once the
operating system ends it while it is away, and returning to it then needs a
connection. Merely leaving the application does not lose it: FLOW-012 requires
the reader to come back to the recipe they were reading.
Proposal (not a decision): hold exactly one recipe — the last one opened — until
the next one replaces it. One slot, no accounting, no management screen.
Blocks: nothing.
