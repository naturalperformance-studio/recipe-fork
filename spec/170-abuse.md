---
slug: ABUSE
status: closed
updated: 2026-09-21
ids: [ABUSE-001, ABUSE-002, ABUSE-003, ABUSE-004, ABUSE-005, ABUSE-006, ABUSE-007]
---

# Abuse, reporting and blocking

The product has one moderator, anonymous accounts that cost their holder
nothing but a device slot (AUTH-001, AUTH-002), and a rule that hides a recipe
once enough accounts report it (ST-006). Everything in this section exists to
keep those three facts from being turned against the product or against an
author.

Reporting and blocking are also obligations of the Google Play UGC policy
(CON-006), not optional features.

One vector is closed outside this section rather than inside it: the `most forked`
ordering ranks on a recipe's published fork count alone (CAT-001, CAT-005), so
forking one's own recipe privately any number of times moves nothing. Every route
to visibility still runs through the moderator.

### ABUSE-001 — At most three submissions per account per day

If an account that does not hold the moderator role has submitted three recipes
for moderation within the preceding 24 hours, then the system shall reject its
further submissions until that window has passed.

**Acceptance criteria**
- Given an account that has submitted three recipes in the last 24 hours, when
  it submits a fourth, then the submission is rejected and the interface states
  when the account may submit again.
- Given a submission that was withdrawn by its owner, when the day's count is
  computed, then that submission still counts.
- Given the account's other actions, when the limit is in force, then creating,
  editing, forking, scaling, searching and favouriting are unaffected.
- Given an account holding the moderator role, when it submits a fourth recipe in
  a day, then it is accepted, because the queue it would fill is its own.
- Given that account, when the moderator role is revoked from it (ACT-003), then
  the limit applies to it again from that moment.

**Verification:** functional test submitting four recipes in one day, including
a withdrawn one.
**Source:** user decision, 2026-09-20. The limit protects the waiting time
promised in MOD-005: a single account cannot fill the queue of a single
moderator. A withdrawn submission counts because the moderator's attention was
already spent on it.
**Narrowed 2026-09-21** by user decision: an account holding the moderator role
is exempt, because the reason for the limit — one account filling one moderator's
queue — does not apply when that account is the moderator. It follows from what
the limit is for rather than from a wish to publish faster, and it needs no
revisiting if a second moderator appears. The occasion was practical: the first
release opens with an empty catalogue that the product owner fills themselves
(`mvp-boundary`), and three a day would have made that take weeks.
**Not recorded:** the idea of lifting this limit as a paid feature was raised and
is not written here, because it contradicts NGOAL-006. Adopting it would be a
separate decision and a superseding ADR.

### ABUSE-002 — Anyone may report, in one of two categories

The system shall allow anyone using the application, with an account or without
one, to report a recipe that is `published` or `link_shared`, choosing exactly
one of two categories: use of someone else's content, or danger to health.

**Acceptance criteria**
- Given a published recipe, when anyone opens it, then a report action is
  reachable from the recipe itself, whether or not they hold an account.
- Given a report from a person with no account, when it is recorded, then it
  reaches the moderator's queue and counts toward no automatic hiding, exactly as
  ABUSE-003 already requires of an account that has published nothing.
- Given the report action, when it is opened, then exactly the two categories
  are offered and one must be chosen before the report is recorded.
- Given an account that already reported that recipe, when it reports it again,
  then no second report is recorded.

**Verification:** manual UI check; repeat-report test.
**Source:** user decision, 2026-09-20; widened the same day from accounts to
anyone, because CON-006 obliges the product to offer reporting and because the
person best placed to recognise a stolen photograph holds no account here
(ABUSE-007). The two categories are the two classes of
violation a moderator cannot detect before publication: the provenance of a
photograph is invisible at review, and an unsafe preserving time or a wrong
mushroom reads as an ordinary recipe. Every other class is expected to be caught
at review (MOD-003, MOD-004).

### ABUSE-003 — Only established accounts count toward automatic hiding

While counting reports against the automatic hiding threshold, the system shall
count only reports made by accounts that have at least one published recipe, and
shall count each such account once per recipe.

**Acceptance criteria**
- Given three reports from three accounts with no published recipe, when the
  threshold is evaluated, then the recipe is not hidden.
- Given three reports from three accounts each with at least one published
  recipe, when the third is recorded, then the recipe is hidden (ST-006).
- Given an account that publishes a recipe after having reported, when the
  threshold is evaluated, then its earlier report counts from that moment.

**Verification:** functional test with both kinds of reporting account.
**Source:** user decision, 2026-09-20. An account that has published has passed
human review and has waited for it, which is the friction the threshold relies
on; an account created minutes ago has passed nothing.

### ABUSE-004 — A report that does not count is still delivered

When a report is recorded that does not count toward the threshold, the system
shall place it in the moderation queue without hiding the recipe.

**Acceptance criteria**
- Given a report from an account with no published recipe, when the moderator
  opens the queue, then the report is listed with its category and its recipe.
- Given that report, when it is recorded, then the reported recipe stays
  visible.

**Verification:** queue inspection after a report from a new account.
**Source:** user decision, 2026-09-20. The people most likely to recognise
someone else's photograph are outside the product and have published nothing in
it, so closing the channel to them would close it to the category's main source.

### ABUSE-005 — Blocking hides an author from catalogue and search

When an account blocks an author, the system shall stop returning that author's
recipes in the blocking account's catalogue browsing and search results.

**Acceptance criteria**
- Given a blocked author, when the blocker searches the catalogue, then none of
  that author's recipes is returned.
- Given a blocked author, when the blocker opens a fork whose parent belongs to
  that author, then the parent still resolves and opens.
- Given a blocked author, when another account searches, then that author's
  recipes are returned normally.
- Given a blocked author, when the blocker unblocks them, then their recipes are
  returned again.

**Verification:** two-account functional test across block and unblock.
**Source:** user decision, 2026-09-20; obligation origin CON-006. See
[ADR — report and block model](decisions/20260920-report-and-block-model.md).

### ABUSE-006 — An author can be reported, and that never acts automatically

The system shall allow any account to report an author in the same two
categories, and shall never apply an automatic consequence to a reported
account.

**Acceptance criteria**
- Given an author's public representation, when any signed-in account opens it,
  then a report action is reachable.
- Given any number of reports against one account, when they are recorded, then
  that account keeps publishing, and its recipes stay visible, until a moderator
  acts.
- Given reports against an account, when the moderator opens the queue, then
  they are listed with their categories.

**Verification:** functional test recording several reports against one account.
**Source:** CON-006 requires reporting of users as well as content; the absence
of an automatic consequence is the user's decision of 2026-09-20 that banning is
a moderator action (ACT-005).

### ABUSE-007 — A rights holder outside the product needs a way in

The system shall publish a contact channel for claims about someone else's
content that does not require holding an account.

**Acceptance criteria**
- Given the store listing and the terms of use, when they are read, then each
  names a channel for such claims.
- Given a claim received through that channel, when it is handled, then the
  moderator can act on the recipe named in it.

- Given a person who deleted their account, when they claim rights over recipes
  that account published, then the claim is handled through this channel like any
  other, because deletion leaves them holding no proof of authorship.

**Verification:** review of the store listing and the published terms.
**Source:** derived from ABUSE-002: the owner of a photograph used without
permission is normally not a user of this product and cannot be required to
become one. The product owner added on 2026-09-20 that this is also the route by
which a former author reaches their own published recipes: an account's deletion
does not remove them (ST-001), and this channel is the only way they can be taken
out of the catalogue. What it costs is stated in
[ADR — an account is created on demand](decisions/20260920-account-on-demand.md):
a deleted account leaves nothing that proves who wrote what, so such a claim
arrives with no more standing than a stranger's. The exact channel and its legal wording are specified in section
[`legal`](190-legal.md).
