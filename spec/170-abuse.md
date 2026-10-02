---
slug: ABUSE
status: closed
updated: 2026-09-28
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
count only reports made by accounts that have published at least one recipe, and
shall count each such account once per recipe.

**Acceptance criteria**
- Given three reports from three accounts that have published nothing, when the
  threshold is evaluated, then the recipe is not hidden.
- Given three reports from three accounts each of which has published at least
  one recipe, when the third is recorded, then the recipe is hidden (ST-006).
- Given an account that publishes a recipe after having reported, when the
  threshold is evaluated, then its earlier report counts from that moment.
- Given an account whose only published recipe a moderator has since removed,
  when the threshold is evaluated, then its reports still count, because what is
  weighed is that the account once passed human review — the `was_published` the
  domain model already holds and never clears — and not what it owns today.

**Verification:** functional test with both kinds of reporting account, and with
an account whose published recipe was removed after it reported.
**Source:** user decision, 2026-09-20; the removed-recipe case decided
2026-09-21. An account that has published has passed human review and has waited
for it, which is the friction the threshold relies on; an account created minutes
ago has passed nothing. Counting only live publications was rejected: it would
let a moderator's removal of one recipe silently change the weight of reports
that account had already filed against unrelated recipes, which is an effect
nobody performing the removal is thinking about.
**Cost, stated:** an account whose recipe was removed as dangerous to health
keeps its full weight in this threshold. Nothing about the removal is a judgement
on how that account reports, and a moderator who believes otherwise has ACT-005
and ABUSE-006 to act with instead.

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

When anyone using the application blocks an author, with an account or without
one, the system shall stop returning that author's recipes in the blocker's
catalogue browsing and search results.

**Acceptance criteria**
- Given a blocked author, when the blocker searches the catalogue, then none of
  that author's recipes is returned.
- Given a blocked author, when the blocker opens a fork whose parent belongs to
  that author, then the parent still resolves and opens.
- Given a blocked author, when another account searches, then that author's
  recipes are returned normally.
- Given a blocked author, when the blocker unblocks them, then their recipes are
  returned again.
- Given a blocker holding no account, when they block an author, then the block
  is kept on the device under the rules FLOW-051 sets for favourites: it is sent
  nowhere, so which authors this device hides is never known to the product, and
  it is lost with the device or with a reinstallation.
- Given blocks kept with no account, when an account is created or signs in on
  that device, then they move into it with the rest of the pool if the offer of
  AUTH-026 is accepted, and stay in the pool if it is declined (FLOW-052).
- Given a blocker holding an account, when they sign in on a second device, then
  their blocks are there too, because the account holds them (DOM: Block).
- Given the author's screen (FLOW-055), when anyone opens it, then blocking that
  author and unblocking them are reachable there, which is where the act lives:
  the matrix in `actors` gave everyone this action and no screen carried it.

**Verification:** two-account functional test across block and unblock; block
from a device holding no account, capture traffic to confirm nothing was sent,
reinstall and confirm the loss; then create an account on such a device and
confirm the blocks are carried in.
**Source:** user decision, 2026-09-20; obligation origin CON-006; widened
2026-09-21 from accounts to anyone. The permission matrix and the domain model
had both said all along that a device with no account holds blocked authors,
citing FLOW-051 — which covers favourites and had never mentioned them. Since
reading needs no account (ACT-001), so does hiding what one does not want to
read. See
[ADR — report and block model](decisions/20260920-report-and-block-model.md).

### ABUSE-006 — An author can be reported, and that never acts automatically

The system shall allow anyone using the application, with an account or without
one, to report an author in the same two categories, shall hold at most one
moderation queue entry per reported author, and shall never apply an automatic
consequence to a reported account.

**Acceptance criteria**
- Given an author's screen (FLOW-055), when anyone opens it, then a report
  action is reachable there, whether or not they hold an account.
- Given any number of reports against one account, when they are recorded, then
  that account keeps publishing, and its recipes stay visible, until a moderator
  acts.
- Given an author nobody has reported, when the first report arrives, then one
  queue entry is created for that author, ordered by the time it was created
  (MOD-006).
- Given an author who already has an open entry, when a further report arrives,
  then that entry's count rises by one and the report's category is recorded on
  it, and no second entry appears — whoever sent it, and whether or not they hold
  an account.
- Given that entry, when the moderator reads it, then it states how many reports
  it holds and which categories they carried, and names no reporter, because the
  product holds nothing that identifies a person without an account (CON-001,
  AUTH-015, FLOW-051) and deliberately does not record who reported.
- Given the same person reporting the same author ten times, when the entry is
  read, then the queue holds one entry whose count is ten, so repetition costs
  the moderator one number rather than ten items to open.
- Given a moderator who closes that entry (FLOW-046), when a further report
  arrives afterwards, then a new entry is created with a count of one, ordered by
  its own arrival.
- Given a report against an author, when it is recorded, then it counts toward
  no threshold at all: ABUSE-003's threshold governs recipes, and nothing here
  acts without a moderator.

**Verification:** functional test recording several reports against one account,
from an account and from a device holding none, including repeats before and
after the moderator closes the entry.
**Source:** CON-006 requires reporting of users as well as content; the absence
of an automatic consequence is the user's decision of 2026-09-20 that banning is
a moderator action (ACT-005). Widened 2026-09-21 from accounts to anyone, for
the reason ABUSE-002 was widened the day before and the permission matrix already
assumed: the owner of a stolen photograph holds no account here, and letting them
report the one recipe they found but not the author who posted ten of them is a
half-open door. Nothing counts automatically, so the friction an account would
have bought is friction against nothing.
**Rewritten 2026-09-21:** the last criterion required the queue not to fill with
one person's repetitions, which no implementation could satisfy — ABUSE-002
deduplicates by account, and a person with no account is exactly the case this
requirement was widened for, while AUTH-015 and CON-001 leave nothing to tell two
of them apart. Collapsing by the reported author rather than by the reporter
needs no identity at all. **Cost, stated:** the count is a count of reports and
not of people, so one person pressing ten times reads the same as ten people
pressing once, and the moderator cannot tell those apart. See
[ADR — the author has a screen, and reports about them collapse onto it](decisions/20260921-the-author-has-a-screen.md).

### ABUSE-007 — A rights holder outside the product reaches it by a published address

The system's terms of use, its privacy statement and its store listing shall name
one electronic mail address for claims about someone else's content, that address
shall require no account and no installed application to use, and a claim reaching
it shall be answered within the period the product states for review (MOD-005).

**Acceptance criteria**
- Given the store listing, the terms of use and the privacy statement, when each
  is read, then all three name the same address, and none of them requires the
  claimant to hold an account, to install the application, or to identify
  themselves to the product in any other way.
- Given a claim naming a published recipe, when the moderator has read it and
  judges it founded, then they act on that recipe from its card (FLOW-047), where
  removal carries the category `foreign_content` like any other, and nothing had
  to be built inside the product for the claim itself.
- Given a claim the moderator judges unfounded, when it is closed, then nothing
  about it is recorded in the product and no recipe changes.
- Given the stated period, when it is published, then it is the one MOD-005
  states for a submission, and it is kept by whoever answers the address: the
  product cannot see the mailbox, no operational signal watches it (OBS-001), and
  nothing in the application knows that a claim exists.
- Given the address, when what the product depends on is enumerated, then it is
  not among them and EXT-001's list stays closed at four: nothing in the product
  sends to that address or reads from it, exactly as the availability probe
  observes the product without being a dependency of it (EXT-012).
- Given what arrives at the address, when it is enumerated, then it holds
  whatever the claimant chose to write, their own name and contact included. It
  is the one place anybody's personal data reaches the product owner, and it does
  not breach CON-001, which binds what the product collects from its users: a
  rights holder is not one, and nothing from a claim is stored against any
  account or any recipe. The privacy statement says so (LEG-008) and the review
  of OQ-029 covers it.
- Given a person who deleted their account, when they claim rights over recipes
  that account published, then the claim is handled here like any other, because
  deletion leaves them holding no proof of authorship.

**Verification:** read the three published texts and confirm they name one
address; send a claim from a device with no account and no application installed,
and act on the named recipe as a moderator.
**Source:** derived from ABUSE-002: the owner of a photograph used without
permission is normally not a user of this product and cannot be required to
become one. The product owner added on 2026-09-20 that this is also the route by
which a former author reaches their own published recipes: an account's deletion
does not remove them (ST-001), and this channel is the only way they can be taken
out of the catalogue. What it costs is stated in
[ADR — an account is created on demand](decisions/20260920-account-on-demand.md):
a deleted account leaves nothing that proves who wrote what, so such a claim
arrives with no more standing than a stranger's.
**Written 2026-09-21** by user decision. Until then this requirement said a
channel must be published and sent the reader to `legal` for what it is, while
LEG-008 said the terms must name the channel of ABUSE-007 and sent them back — so
no requirement anywhere said what the channel was, although RET-005 promises a
deleting author that it exists and MVP-003 holds the release for it. An address
was chosen over a form on the web: a rights holder already knows how to write a
letter, and a form would have needed a page, a screen for the moderator and a
retention rule of its own. **Cost, stated:** nothing in the product watches the
mailbox, so the stated period is kept by a person and by nothing else, and an
unread mailbox looks exactly like an empty one. See
[ADR — the claims channel is an address](decisions/20260921-the-claims-channel-is-an-address.md).
