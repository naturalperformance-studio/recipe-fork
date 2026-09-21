---
slug: OQ
status: open
updated: 2026-09-21
ids: [OQ-001, OQ-002, OQ-003, OQ-004, OQ-005, OQ-006, OQ-007, OQ-008, OQ-009, OQ-010, OQ-011,
  OQ-012, OQ-013, OQ-014, OQ-015, OQ-016, OQ-017, OQ-018, OQ-019, OQ-020,
  OQ-021, OQ-022, OQ-023, OQ-024, OQ-025, OQ-026, OQ-027, OQ-028, OQ-029, OQ-030,
  OQ-031]
open_before_mvp: [OQ-016, OQ-029, OQ-030]
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

### OQ-002 — Key format and cryptography — RESOLVED 2026-09-21
Resolved by the product owner by separating the two halves of the question: the
specification fixes the properties the keys must have — uniqueness,
unguessability at the rate QUAL-009 permits, and a server that stores nothing
from which any key could be reconstructed — and the format, the cryptography and
the protocol are an engineering choice constrained by those properties and by
nothing else. Written as AUTH-014.
The same day the question grew a third key. A leaked account key could not be
retired by anyone, and a symmetric replacement would have been worse than nothing
because a thief wins that race every time. An account now carries an account key,
a deletion key and a recovery key, the last issued apart, shown once and never
displayed again (AUTH-020, AUTH-021). NGOAL-003 is narrowed accordingly. See
[ADR — the recovery key](decisions/20260921-the-recovery-key.md).
Section `auth` is no longer blocked.

### OQ-003 — Media limits — RESOLVED 2026-09-20
Resolved by the product owner against the arithmetic of CON-002: one cover
photograph and at most one item per step, video allowed on any step; photographs
re-encoded on the device to 1 MB and 1600 pixels; video at most 15 seconds,
converted to at most 6 MB with its audio track removed; duration refused before
conversion and everything else settled by the conversion itself; the author told
what was done and told when it could not be done; every metadata field but
orientation discarded; and 150 MB of media per account across recipes that have
never been published.
What video is for, stated by the product owner 2026-09-20: a short clip showing
a technique — how something is cut, how one ingredient goes into another — never
a lesson. The limits chosen keep a clip close to a photograph's weight, which is
what FLOW-011 assumed when it put both under one setting.
Written as MEDIA-001 to MEDIA-013. See
[ADR — media limits are set by the storage budget](decisions/20260920-media-limits-are-set-by-the-budget.md).

### OQ-004 — Retention of inactive accounts and unpublished media — RESOLVED 2026-09-21
Resolved by the product owner: nothing is deleted because time has passed
(RET-006). The premises this question was written on had both changed: an account
is no longer minted at every first launch and costs a device slot (AUTH-001,
AUTH-002), and an account holds at most 150 MB of unpublished media (MEDIA-010),
so the cost this entry was meant to contain is bounded without deleting anyone's
work. What replaces the sweep is a measurement: the total unpublished media held
by accounts dormant for twelve months, reported as an operational signal
(RET-007).
Server logs are the one thing with a period: fourteen days (RET-008), chosen
short because whether an IP address is personal data in the product owner's
jurisdiction is unsettled (OQ-029).
Written as RET-006, RET-007 and RET-008. See
[ADR — nothing is deleted for being old](decisions/20260921-nothing-expires.md).

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
an operational signal (OBS-001) so the lists can be extended long before they
run out.
Narrowed twice on 2026-09-20. First by the device gate: a nickname can no longer
be consumed at an unbounded rate, because creating an account costs one of three
slots per device and a slot frees only after six months (AUTH-002). Then by
account creation on demand: an installation no longer mints an account at all,
so only a person who writes, forks, rates or wants favourites carried beyond the
device consumes a nickname (AUTH-001). The question survives as an arithmetic one
about the word lists, not as an abuse vector.
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

### OQ-014 — The orderings offered in the catalogue — RESOLVED 2026-09-20
Resolved by the product owner: the reader orders by `newest`, `highest rated`,
`most rated` or `most forked`, and `newest` is in force until they choose
(CAT-001). `newest` is the default because an ordering by rating goes nearly
static as the catalogue grows. `most forked` was kept although a high count is
ambiguous between "people keep correcting this" and "this adapts to any taste".
Written as CAT-001; see
[ADR — lineage is visible in discovery](decisions/20260920-lineage-visible-in-discovery.md).

### OQ-015 — Comparing an ingredient's quantity across unit types — RESOLVED 2026-09-20
Resolved by the product owner: the ingredient dictionary gains one level, so
`крылышки`, `филе бедра` and `тушка` are children of `курица`, and every entry
carries a comparison factor per unit on a scale shared by the family. The author
picks one entry — the leaf — and the dictionary supplies the parent, so the
hierarchy costs the author nothing. A line's comparable quantity is its quantity
per serving times the factor for its unit; a unit with no factor drops the line out
of the comparison, and a recipe with nothing left is ordered last.
Band-ordering was rejected with the case that killed it: 5 g of chicken per serving
would have outranked two whole carcasses. A flat dictionary with one factor per
entry was rejected because one piece of `курица` is a wing in one recipe and a bird
in another. A flag plus a required `piece_weight` was rejected as two actions for
the author and a place to mistype.
Written as DOM-010, DOM-011, CAT-006, CAT-007 and CAT-009. See
[ADR — comparison factors](decisions/20260920-comparison-factors.md).

### OQ-016 — Product name and its clearance (define before MVP)
Unresolved: the name the application is published under, and whether that name
is free in the only channel the MVP uses. `Recipe Fork` is a working title used
in this repository, not a decided product name. The product owner's market work
found `Forked` already taken in the Apple App Store (MKT-F5) and explicitly did
not check Google Play, trademarks or name registrations
([`market-context`](25-market-context.md), "Not checked").
Proposal (not a decision): keep `Recipe Fork` as the working title inside the
spec, and treat the published name as a release prerequisite to be cleared
against Google Play and trademark registers in the product owner's own
jurisdiction before the store listing is created.
Blocks: nothing that is written — LEG-009 stands as a requirement and says what
must have happened before the listing exists. It held section `legal` in
`blocked` until 2026-09-21, when the product owner decided that a question which
changes no sentence in a section should not hold that section open; the release
still waits for it (MVP-003), and the readiness banner still names it.

### OQ-017 — What makes two recipes use "the same" ingredient — RESOLVED 2026-09-20
Resolved by the product owner: an ingredient dictionary maintained by the product
owner, with an optional free-text qualifier for the cut, and free text still
accepted for anything the dictionary lacks — with a warning, not a refusal. A
filter matches the entry only. Written as DOM-009, FLOW-022 and CAT-006; the
`IngredientEntry` entity and the Ingredient fields are in `domain-model`, and the
dictionary of indivisible ingredients was absorbed into it (`scaling-rules`,
FLOW-001). See
[ADR — the ingredient dictionary](decisions/20260920-ingredient-dictionary.md).

### OQ-018 — What the fork number counts — RESOLVED 2026-09-20
Resolved by the product owner: two numbers where they differ — the total fork count
and how many of those are open — with the ordering `most forked` and the list of
CAT-008 both working on the published count alone. Written as CAT-001, CAT-005 and
CAT-008; the abuse vector this closes is noted in `abuse`. See
[ADR — lineage is visible in discovery](decisions/20260920-lineage-visible-in-discovery.md).

### OQ-019 — Requests to add a dictionary entry (define after MVP)
Unresolved: whether an author who finds no matching ingredient entry can ask for
one to be added, and what that queue looks like.
Decided for the MVP by the product owner 2026-09-20 by not adopting it: free text
with a warning is the only escape (FLOW-022).
Consequence to watch: every recipe that takes the free-text escape is invisible to
ingredient filters forever once published, so the fraction of ingredients added as
free text is an operational signal worth watching (OBS-001, with a threshold in
OBS-003) — it measures how far behind the dictionary is.
Proposal for later (not a decision): a request carries only the name the author
typed, and lands in the same queue the moderator already works.
Blocks: nothing.

### OQ-020 — Whether the qualifier survives the two-level dictionary — RESOLVED 2026-09-20
Resolved by the product owner: the qualifier stays. It is not a fourth field — it is
the tail of the first one, so the author picks `филе`, keeps typing `без кожи`, and
the row reads `филе без кожи` while the ingredient holds the entry plus the
qualifier. The editor offers leaves before parents, so a qualifier is the last resort
and not a way around picking the right leaf. A filter still matches the entry only
(CAT-006).
The reason it stays is the product owner's: the dictionary will never be complete, so
an author whose leaf is missing would otherwise have to choose between a recipe that
can be found and a row that says what they mean. Written into FLOW-023; DOM-009 and
CAT-006 needed no change.

### OQ-021 — How much of the ingredient dictionary is held on the device — RESOLVED 2026-09-20
Resolved by the product owner: the device holds the whole ingredient dictionary, as
FLOW-001 already requires. Partial holding is not built, and with it none of a
per-account set of entries to keep in step, no unsynchronised state for an
ingredient, and no reconciliation screen at synchronisation; offline authoring keeps
the full dictionary to search.
The product owner's own proposal — hold only the entries the account's favourites and
recipes use, keep an unknown ingredient as an unsynchronised plain string, and offer
matching entries when a connection returns — was set aside on the arithmetic: 3 000
entries of a label, a parent and a few factors are a few hundred kilobytes of text,
which is less than the single cover photo OQ-003 proposes to cap at 2 MB, while
partial holding would have made an author offline able to pick only ingredients they
had used before.
Revisit trigger, not a decision: the dictionary's transfer size belongs among the
operational signals of OBS-001, because tens of thousands of entries,
or several locales held at once, are where partial holding becomes the right answer.
No threshold is set today and nothing depends on one while the dictionary is small.

### OQ-022 — What the recipe editor is — RESOLVED 2026-09-20
Resolved by the product owner: one scrolling screen in the order title with cover,
tags, servings, ingredients, steps, description (FLOW-024); steps and ingredients
inserted anywhere and reordered by dragging (FLOW-025); the cover attached with the
title and step media at the step (FLOW-026); a timer attached inside its step with
its duration entered there (FLOW-027); the minimum of DOM-006 shown as a warning when
the author leaves a part, never as an error while typing (FLOW-028); no preview,
because saving already shows the reader's own view and the recipe stays editable until
it is visible to anyone else (FLOW-029); and the editor states which parts a fork
carries away and which stay the author's (FLOW-030).
Two pieces of the editor were separated out rather than answered: OQ-023, how an
ingredient reference is inserted and what it renders as, and OQ-024, whether a step
may name another recipe as an ingredient.

### OQ-023 — How an ingredient reference is written and what it renders as — RESOLVED 2026-09-20
Resolved by the product owner, adopting the spec author's proposal: a trigger
character in a step's text offers the recipe's own ingredients, a visible control on
the step does the same for an author who would not guess a trigger character, and a
reference renders as the quantity and unit alone so that the ingredient's word stays
the author's text in whatever case the sentence needs. Written as FLOW-031 and
FLOW-032. The trigger character itself is a presentation choice and is left to the
editor's visual design.
Why references exist, recorded because the product owner asked and the answer belongs
with the decision: the ingredient list scales and step text does not, so without
references a reader at 8 servings would read `200 г` in a step while the list said
`400 г`, and it is the steps people cook from.

### OQ-024 — A recipe as an ingredient of another recipe (define after MVP)
Unresolved: whether a step or an ingredient may name another recipe, so that the
chocolate topping a brownie needs is the topping's own recipe rather than a list of its
ingredients copied out.
Raised by the product owner 2026-09-20 as the case where a link in a step is clearly
worth having.
What it would drag with it, none of which is decided:
- scaling across the boundary: doubling the brownie has to double the topping, which
  means SCALE applies to a recipe the reader never asked about;
- a second relation between recipes, distinct from `parent`, with its own rules — what
  a fork of the brownie does with the link, and what the fork chain shows;
- the same publication test as FORK-005: linking to a recipe that was never published
  would reveal that it exists;
- a removed target, which needs the treatment ST-007 gives a removed parent;
- offline: FLOW-009 holds a favourite for reading without a connection, so a favourite
  that links to another recipe has to bring it along;
- the ingredient filter: CAT-006 matches dictionary entries, and a linked recipe is not
  one, so either its ingredients count for the parent recipe or the filter quietly
  misses them.
Deferring costs little: a published recipe is immutable, nothing is migrated, and a
recipe written before the feature exists simply does not use it.
Proposal (not a decision): keep it out of the MVP and revisit it once the catalogue
holds enough recipes for one to be worth reusing, which is also when it stops being a
feature with nothing to point at.
Blocks: nothing.

### OQ-025 — Caps on the size of one recipe — RESOLVED 2026-09-20
Resolved by the product owner: 50 ingredients and 50 steps per recipe, 2 000
characters per step, 120 characters of title, 2 000 characters of description. The
counts were taken from the tighter of the two proposed sets and the text lengths from
the more generous, on the product owner's reasoning that an author writes inside a step
and inside the description and needs room there, while fifty steps and fifty
ingredients are already more than any real recipe. Refused at submission, never while
typing. Written as DOM-012 and FLOW-028; `quality` will carry the measurable thresholds
that refer to them.

### OQ-026 — Rejection reasons as categories (define after MVP)
Unresolved: whether the reason a moderator gives for rejecting a submission
(MOD-004, FLOW-046) becomes a fixed list of categories, alone or with a comment
beside it.
Decided for the MVP by the product owner 2026-09-20: free text, "for now".
Consequence to watch, stated and accepted: nothing can be counted across
rejections, so the product owner cannot learn what submissions most often fail
on, and two authors rejected for the same fault may read two different
sentences. Removal already carries a category, because MOD-008 makes it
consequential; rejection does not, because nothing downstream depends on it.
Proposal for later (not a decision): the categories are derived from the
rejections actually written in the first months, rather than guessed now.
Blocks: nothing.

### OQ-027 — What the moderator sees after deciding an entry — RESOLVED 2026-09-20
Resolved by the product owner: both modes, and they are not alternatives. A
decision can be taken on an entry straight from the list, for the case that
cannot wait, and the moderator stays in the list afterwards (FLOW-049). A run
started on a list opens the next entry as soon as the current one is decided and
ends when the list is exhausted (FLOW-050). Every decision taken by gesture is
confirmed in both directions — restricting and releasing — because undoing
either means finding that one recipe again with no way to search for it.
Written as FLOW-049 and FLOW-050; FLOW-046 is no longer blocked.

### OQ-028 — Several accounts on one device (define after MVP)
Unresolved: whether a device may hold more than one account at a time, with a
switch between them, instead of the single active account of AUTH-011.
Decided for the MVP by the product owner 2026-09-20 by not adopting it: one
active account, and signing in with a key replaces it and wipes what the previous
account held (AUTH-012).
The reason is the product owner's own: the first release is the fork mechanic
done well, not account management, and multi-account use multiplies favourites,
held media and the single storage setting of FLOW-011 by the number of accounts,
with the limit of FLOW-011 then having to be divided between them.
Consequence to watch, stated and accepted: a household sharing one tablet cannot
use two accounts without re-signing in with a key each time, and each such sign-in
destroys the other person's unsent drafts (AUTH-013 names them before it
happens).
Blocks: nothing.

### OQ-029 — The law the first release answers to (define before MVP)
Rewritten 2026-09-21 after the product owner decided the market. The first
release is not offered in the Russian Federation (LEG-010), so the duties that
made this question expensive there — notification before processing,
localisation of citizens' data, the leak liability of 2025, and the age mark a
platform owes for content it does not classify in advance — do not reach it. They
return in full with any decision to distribute there, and that decision is one of
the things this entry now exists to inform.
Unresolved: what the data protection law of the countries the product *is*
distributed in obliges before release, given an audience of Russian-speaking
people living outside the Russian Federation and English speakers. The product
stores no part of a device verdict and no device identifier (AUTH-015), which is
why this route was taken instead of an identity provider, and it collects no
personal data at all (CON-001). What remains is the server logs, and under the
European and United Kingdom regimes an IP address is personal data without
argument — which is what RET-008's fourteen days were already chosen for.
Widened 2026-09-21 with ABUSE-007: the claims address is now written down, and
what arrives at it is correspondence carrying a claimant's own name and contact.
It is the one place personal data reaches the product owner at all, so how long
it is kept and what the privacy statement must say about it belong to the same
review and not to a number guessed here.
Proposal (not a decision): a review by a lawyer qualified in the regime the
listed countries fall under, before the store listing is created, covering the
logs, the published terms, the privacy statement and the claims address; nothing
in the spec changes until that review reports.
Blocks: nothing that is written — LEG-001 to LEG-010 stand — but the release
waits for it (MVP-003).

### OQ-030 — Whether the account deletion obligation reaches this product (define before MVP)
Unresolved: whether an account the user never asks to create falls under the
distribution channel's account deletion requirement, and whether a page that
accepts a deletion key satisfies the web resource part of it.
What is established, checked 2026-09-20: the requirement reads *"If your app
enables account creation, you must: provide users with an in-app path to delete
their app accounts and associated data; and provide a web link resource"*, and
the only exemptions named are permanently private and enterprise device
management applications
([Google Play — app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)).
Nothing exempts an anonymous account or an application that collects no personal
data. The one opening was that the scope is written around a user who *completes*
creating an account, while the account then appeared without the user doing
anything. That opening closed on 2026-09-20: an account is now created
deliberately, on a screen the person passes through (AUTH-001, AUTH-007). The
product is in scope by the plain reading, and what remains to confirm is only
whether a page that accepts a deletion key satisfies the web resource part.
What is decided regardless, because it costs little and settles the matter if the
obligation does apply: deletion exists, in the application and on a page, and it
is specified in EXT-006, AUTH-017 and `retention`. Published recipes survive it
(ST-001); what goes is the account record and everything unpublished.
Proposal (not a decision): confirm the reading before the store listing is
created, rather than discovering it in a rejected release, and record the answer
here either way.
Blocks: nothing that is written; a negative answer only removes work.

### OQ-031 — Raising the Android floor after the MVP (define after MVP)
Unresolved: whether the lowest supported Android version rises from 10 (QUAL-005)
to 12 once the first release has an installed base to read figures from.
Raised by the product owner 2026-09-21 as the intended direction rather than an
open doubt: Android 10 is the MVP floor precisely so that the decision can be
taken on the product's own distribution figures from the channel instead of on
the worldwide ones, and the trigger is the share of the product's own installs
below Android 12 becoming small enough to drop.
What it would buy: the media conversion of MEDIA-002 and MEDIA-005 and the
attestation request of AUTH-001 are the two places where the older API costs code,
so raising the floor removes a fallback path in each rather than adding a feature.
What it costs: every installed device below the new floor stops receiving updates
through the channel, and the people holding them keep whatever version they have,
including its faults. Nothing they hold is destroyed — RET-006 applies to their
accounts and recipes like anyone else's.
Proposal (not a decision): read the channel's distribution figures for the
product's own installs after the first release has been out long enough for them
to mean anything, and decide then rather than on a date.
Blocks: nothing.
