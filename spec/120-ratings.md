---
slug: RATE
status: closed
updated: 2026-09-20
ids: [RATE-001, RATE-002, RATE-003, RATE-004, RATE-005, RATE-006, RATE-007, RATE-008, RATE-009, RATE-010, RATE-011, RATE-012, RATE-013, RATE-014, RATE-015, RATE-016]
---

# Ratings and favourites

A rating is the only thing a reader gives back to a recipe they did not write.
It is binary — a like or a dislike — and it is what sorts quality, because the
product inspects a recipe's craft nowhere and a moderator never rejects for it
([ADR — quality is sorted by readers](decisions/20260920-quality-sorted-by-readers.md)).

Favouriting is a separate act with a separate purpose: it marks what the reader
wants to take with them onto the device (FLOW-009, FLOW-011). It says nothing
about quality and feeds no ordering (RATE-016).

What a fork does with the parent's ratings is settled in [`forking`](65-forking.md):
FORK-006 gives every fork none of them, whoever made it. This section does not
repeat that rule and does not qualify it.

Two costs below are accepted rather than solved, and both are worth an
operational signal in [`observability`](210-observability.md): score can still
be moved by creating accounts, though an account now costs one of three device
slots that free only after six months (RATE-005, AUTH-002); and `highest rated`
rewards accumulation, so its top ages into a hall of fame, which is why `newest`
is the default ordering (CAT-001).

## What a rating is

### RATE-001 — One rating per account per recipe

When an account likes or dislikes a recipe, the system shall record that as the
account's only rating of that recipe, replacing any rating the same account gave
it before.

**Acceptance criteria**
- Given an account that has not rated a recipe, when it likes the recipe, then
  the like count rises by one and the dislike count does not change.
- Given an account that liked a recipe, when it dislikes the same recipe, then
  the like count falls by one and the dislike count rises by one, so the two
  counts together rise by nothing.
- Given one account rating one recipe from two devices holding the same key
  file, when both are counted, then the recipe carries one rating from that
  account and not two.
- Given two different accounts liking the same recipe, when the counts are read,
  then the like count is two.

**Verification:** rate one recipe from two accounts and from two devices of one
account, and compare the counts against hand counting after each step.
**Source:** user decision, 2026-09-20.

### RATE-002 — A rating is withdrawn by repeating it

When an account activates the rating control it has already given to a recipe,
the system shall withdraw that account's rating of that recipe.

**Acceptance criteria**
- Given an account that liked a recipe, when it activates the like control
  again, then its rating is withdrawn and the like count falls by one.
- Given an account that disliked a recipe, when it activates the dislike control
  again, then its rating is withdrawn and the dislike count falls by one.
- Given a withdrawn rating, when the account likes the recipe once more, then a
  new rating is recorded and counted once.
- Given a withdrawn rating, when the recipe is displayed to that account, then
  neither control is shown as chosen.
- Given a recipe whose only rating has been withdrawn, when it is displayed,
  then it is shown as a recipe nobody has rated (RATE-008).

**Verification:** like, unlike, dislike, undislike one recipe in sequence and
confirm the counts return to their starting values.
**Source:** user decision, 2026-09-20: a reader who cooked the recipe after
rating it may find they judged it wrong, and a permanent rating would keep that
judgement. The counts are therefore not monotonic — a number the reader saw
yesterday may be lower today — and the product owner accepted that on the
grounds that an ordering which only ever accumulates is an ordering that stops
moving.

## Who may rate, and what

### RATE-003 — Only a published recipe can be rated

Where a recipe is in any state other than `published`, the system shall offer no
rating control for it and shall record no rating of it.

**Acceptance criteria**
- Given a recipe in `private`, `pending` or `rejected`, when its owner opens it,
  then no rating control is shown.
- Given a recipe in `link_shared`, when the holder of an active private link
  opens it, then no rating control is shown and no rating is recorded for it by
  any path.
- Given a recipe in `link_shared` that its owner then publishes, when it enters
  the catalogue, then it is rated by nobody.
- Given a recipe in `reported_hidden`, when its owner or a moderator opens it,
  then no rating control is shown.
- Given a rating submitted for a recipe in any state other than `published`,
  when it reaches the server, then it is refused and no count changes.

**Verification:** attempt to rate one recipe in each state, as its owner, as a
link holder and as a moderator.
**Source:** user decision, 2026-09-20. A private link is a channel the author
chooses who reaches, so ratings collected through it would be ratings the author
selected the raters for; the recipe would then enter the catalogue carrying a
score the public never gave it. `entity-states` was corrected on the same date to
list rating among the transitions disallowed from `rejected`, `link_shared` and
`reported_hidden`, which it did not name before.

### RATE-004 — An author never rates their own recipe

Where the account viewing a recipe owns it, the system shall show no rating
control and shall record no rating from that account.

**Acceptance criteria**
- Given the owner opening their own published recipe, when the card is shown,
  then the counts are present and the controls are not.
- Given a rating from the owner's account submitted by any path, when it reaches
  the server, then it is refused and no count changes.
- Given an account that forked another account's recipe, when it opens the
  parent, then it may rate the parent, because it does not own it.
- Given that same account opening its own fork, when the card is shown, then no
  rating control is present.

**Verification:** open an own recipe and a forked parent from the same account
and compare the two cards.
**Source:** user decision, 2026-09-20. A self-rating is both a claim about one's
own work and, on every fork of it, a free unit of score for the same hand.

### RATE-005 — Every account's rating counts

The system shall count a rating from every account, whether or not that account
has ever published a recipe.

**Acceptance criteria**
- Given an account that has published nothing, when it rates a published recipe,
  then the counts change and the orderings of RATE-010 and RATE-011 reflect it.
- Given that account, when it reports a recipe, then ABUSE-003 still governs
  whether that report counts toward automatic hiding, unchanged by this
  requirement.
- Given a muted account (AUTH-004), when it opens a published recipe, then no
  rating control is offered at all, so this requirement never reaches it.
- Given a person holding no account, when they use the rating control, then
  account creation is offered (AUTH-001) and the rating is recorded once the
  account exists; rating anonymously is impossible, because one rating per
  account (RATE-001) has nothing to hold on to and AUTH-015 forbids holding a
  device identifier instead.

**Verification:** rate from a freshly created account and confirm both the count
and the ordering position change.
**Source:** user decision, 2026-09-20. The gate ABUSE-003 puts on reports was
weighed for ratings and rejected: a report acts on someone else's recipe while a
rating only sorts, and readers who cook without ever publishing are most of the
people the ordering is meant to carry. Farming is accepted and watched rather
than prevented here, because the device slots of AUTH-002 already bound the rate
at which accounts can be made at all.

### RATE-006 — Blocking an author does not affect rating

Where the reader has blocked an author (ABUSE-005), the system shall accept that
reader's ratings of that author's recipes on the same terms as any other
reader's.

**Acceptance criteria**
- Given a blocked author's recipe already in the blocker's favourites, when the
  blocker opens it, then the rating controls are offered and a rating is
  recorded and counted.
- Given a rating the blocker gave before blocking, when they block the author,
  then that rating stays and no count changes.
- Given a blocked author, when the blocker unblocks them, then no rating is
  recreated or removed by either act.

**Verification:** rate, block, unblock across two accounts and compare the counts
at each step.
**Source:** user decision, 2026-09-20, taking the spec author's recommendation.
Blocking is a filter on what the catalogue and search return
([ADR — report and block model](decisions/20260920-report-and-block-model.md)),
not a permission system; making it also govern rating would be a second
permission system built for a recipe the blocker no longer sees listed.

## What a rating looks like

### RATE-007 — A rating is two counts

The system shall display a recipe's rating as two counts, likes and dislikes,
shown together, and shall display no figure derived from them in their place.

**Acceptance criteria**
- Given a recipe with 24 likes and 3 dislikes, when its rating is displayed,
  then both numbers are present.
- Given that recipe, when its rating is displayed, then no net score, percentage
  or star figure is shown instead of the two counts.
- Given the recipe card and a catalogue entry for the same recipe, when both are
  shown, then the rating is in this same form in both, which is the form
  CAT-003 defers to.
- Given a reader who has rated the recipe, when its card is open, then the
  control they chose is shown as chosen.

**Verification:** compare a card and a catalogue entry of one rated recipe
against this form, with and without the reader's own rating.
**Source:** user decision, 2026-09-20. See
[ADR — two counts, ordered by net score](decisions/20260920-two-counts-ordered-by-net-score.md).

### RATE-008 — A recipe nobody has rated shows no counts

Where a recipe carries no rating from any account, the system shall show no
counts for it and shall still offer its rating controls on its card.

**Acceptance criteria**
- Given an unrated recipe, when it is listed in the catalogue or in search
  results, then the entry omits the rating rather than showing zeroes (CAT-003).
- Given an unrated recipe, when its card is opened by an account that does not
  own it, then the rating controls are offered and no number reads as a
  judgement of the recipe (FLOW-019).
- Given an unrated recipe, when the first account rates it, then the counts
  appear.

**Verification:** publish a recipe, inspect its entry and card before any rating
and after the first one.
**Source:** CAT-003 and FLOW-019, both decided 2026-09-20; this requirement is
where the two meet and is not a further decision.

### RATE-009 — A rating is given on the card

The system shall offer the rating controls on a recipe's card only, and shall
offer no control that records a rating in a catalogue entry or in a search
result.

**Acceptance criteria**
- Given a catalogue entry or a search result, when it is shown, then it carries
  the two counts of RATE-007 and nothing that records a rating.
- Given a recipe card, when it is opened by an account that may rate the recipe,
  then the controls are present on it.
- Given a favourites list, when it is shown, then the same holds as for a
  catalogue entry.

**Verification:** enumerate the controls of an entry and of a card side by side.
**Source:** derived from the closed field list of CAT-003, which holds no
control, and from FLOW-019, which places the controls on the card. Not a new
decision.

## Ordering

### RATE-010 — `highest rated` orders by likes minus dislikes

Where the reader has chosen the ordering `highest rated` (CAT-001), the system
shall order the recipes by the number of likes minus the number of dislikes,
largest first, breaking an equality by the more recent creation instant, then by
title in ascending alphabetical order, then by the recipe's identifier.

**Acceptance criteria**
- Given a recipe with 24 likes and 3 dislikes and one with 30 likes and 12
  dislikes, when they are ordered, then the first precedes the second.
- Given two recipes whose likes minus dislikes are equal, when they are ordered,
  then the one created later precedes the other.
- Given two recipes equal on both, when they are ordered, then their titles order
  them, and if those are equal too, their identifiers do, so the order of any two
  recipes is decided.
- Given an unchanged set of recipes and ratings, when the reader pages through
  the ordering, then every recipe appears exactly once and no recipe is skipped.
- Given a recipe nobody has rated and a recipe with 50 likes and 50 dislikes,
  when they are ordered, then neither precedes the other by rating and the
  equality rules decide, because both stand at zero.

**Verification:** build a fixture with equal net scores, equal creation instants
and equal titles, and compare the order against hand computation; page through it
twice.
**Source:** user decision, 2026-09-20. A percentage ordering with no minimum was
weighed and rejected, as was a minimum number of ratings and a confidence bound;
see [ADR — two counts, ordered by net score](decisions/20260920-two-counts-ordered-by-net-score.md).
The creation instant is the first tie-breaker because it is recorded to the
millisecond and rarely collides; the two behind it exist so that the order is
total and paging is stable.

### RATE-011 — `most rated` orders by how many ratings there are

Where the reader has chosen the ordering `most rated` (CAT-001), the system shall
order the recipes by the number of likes plus the number of dislikes, largest
first, breaking an equality by the same chain RATE-010 uses.

**Acceptance criteria**
- Given a recipe with 30 likes and 12 dislikes and one with 24 likes and 3
  dislikes, when they are ordered by `most rated`, then the first precedes the
  second although RATE-010 orders them the other way.
- Given a withdrawn rating, when the ordering is computed, then it counts for
  nothing, because a withdrawn rating is not a rating.
- Given two recipes with the same number of ratings, when they are ordered, then
  the chain of RATE-010 decides.

**Verification:** one fixture ordered under both orderings, compared against hand
computation.
**Source:** user decision, 2026-09-20. This ordering is what a reader chooses to
see what the most people have had an opinion about, whichever way that opinion
went.

## Without a connection, and when it fails

### RATE-012 — Rating needs a connection and is not queued

While the device has no network connection, the system shall show the rating
controls of a held recipe and shall state, when one is activated, that the action
needs a connection, and shall record nothing.

**Acceptance criteria**
- Given a favourite opened without a connection, when its card is shown, then
  the rating controls are present.
- Given no connection, when the reader activates a rating control, then a
  statement that a connection is needed is shown and neither count changes.
- Given that activation, when a connection returns, then nothing is sent for it
  and no rating appears.
- Given a connection, when the reader activates the control again, then the
  rating is recorded normally.

**Verification:** rate a favourite in aeroplane mode, restore the connection and
confirm that no rating arrived.
**Source:** user decision, 2026-09-20. Queuing it as FLOW-004 queues media was
rejected; see
[ADR — a rating is not queued](decisions/20260920-rating-is-not-queued.md).

### RATE-013 — Counts without a connection are the last ones synchronised

While the device has no network connection, the system shall show a held
recipe's counts as they stood at the last synchronisation.

**Acceptance criteria**
- Given a favourite whose counts were 24 and 3 when it was last synchronised,
  when it is opened without a connection, then 24 and 3 are shown.
- Given those counts, when they are shown without a connection, then they are
  shown in the form of RATE-007 and are not marked as old.
- Given a connection that returns, when the recipe is next reached, then the
  counts are replaced by the server's without the reader asking.
- Given a held recipe that has never been synchronised with any rating, when it
  is opened without a connection, then no counts are shown (RATE-008).

**Verification:** synchronise a favourite, change its counts from another
account, and open it in aeroplane mode.
**Source:** user decision, 2026-09-20. A deliberate exception to FLOW-003, which
refuses stale data because a held catalogue would list recipes moderation has
since removed; a count a day old misleads nobody about what the recipe is. No
staleness marking is shown: it would speak about the plumbing, not the recipe.

### RATE-014 — A rating that cannot be recorded says so

If a rating cannot be recorded — because the recipe has been removed, hidden or
published no longer, or because the request does not reach the server — then the
system shall say so and shall leave the counts as the server holds them.

**Acceptance criteria**
- Given a recipe removed by a moderator while its card was open, when the reader
  rates it, then the rating is refused, the reader is told the recipe is no
  longer available, and no count is shown as changed.
- Given a request that fails, when the reader rates, then the reader is told the
  rating was not recorded and the control does not stay shown as chosen.
- Given a failed rating, when the reader tries again and it succeeds, then it is
  recorded once.

**Verification:** remove a recipe from a second session with its card open, and
separately force the request to fail.
**Source:** user decision, 2026-09-20: a control that shows itself as chosen when
the server recorded nothing is the one failure a reader cannot detect.

## Across the recipe's life

### RATE-015 — Hiding a recipe keeps its ratings

When a recipe returns from `reported_hidden` to the state it came from, the
system shall show the counts it had when it was hidden.

**Acceptance criteria**
- Given a recipe with 12 likes and 1 dislike that reports hide, when a moderator
  dismisses the reports, then the counts are 12 and 1.
- Given a recipe while it is hidden, when anyone attempts to rate it, then
  RATE-003 refuses and the counts do not move.
- Given a rating withdrawn while the recipe was visible, when it is hidden and
  restored, then the withdrawal stands.

**Verification:** rate, hide by reports, dismiss, and compare the counts before
and after.
**Source:** user decision, 2026-09-20. Hiding is a suspension pending a
moderator's decision, not a verdict on the recipe, so it takes nothing away that
readers gave.

### RATE-016 — Favouriting is not rating

The system shall keep favouriting and rating separate: favouriting a recipe
shall create no rating, and the number of accounts that have favourited a recipe
shall take part in no ordering.

**Acceptance criteria**
- Given a reader who favourites a recipe, when the counts are read, then neither
  has changed.
- Given a reader who rates a recipe, when their favourites are listed, then the
  recipe was not added to them.
- Given the orderings `highest rated` and `most rated`, when they are computed,
  then no count of favourites takes part.
- Given a reader who removes a recipe from favourites, when the counts are read,
  then their rating of it stands.

**Verification:** favourite without rating and rate without favouriting, and
inspect both the counts and the favourites list after each.
**Source:** user decision, 2026-09-20. Favouriting states that the reader wants
the recipe on their device (FLOW-009); treating it as approval would make the
ordering depend on how much storage a reader is willing to spend.
