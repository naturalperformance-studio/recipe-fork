---
slug: GLOSS
status: closed
updated: 2026-09-20
ids: []
---

# Glossary

This section holds no requirements. It fixes the meaning of terms that mean
something specific in this product, so that the same word never means two things
across the spec and no term is explained twice.

Every entry names where the term is actually decided. The definition here is a
pointer, never a second source: where this file and the requirement it points at
appear to disagree, the requirement is right and this file is a defect.

Terms in general use that carry no special meaning here — recipe, ingredient,
photograph, notification — are not listed.

## The fork mechanic

**Fork** — a new, independent recipe created from an existing one, holding its
title, tags, serving count, ingredients and steps, and naming it as parent
(FORK-001). Forking is how a recipe changes in this product; editing a visible
recipe is not possible at all (GOAL-002, CON-003).

**Parent** — the single recipe a fork was created from. Fixed at creation and
never changed (DOM-004). A recipe holds its direct parent and nothing else: no
root, no list of its own forks (GOAL-003).

**Chain** — the sequence reached by opening a recipe's parent, then that
parent's parent, and so on. It is walked one step at a time and is never
computed or shown whole (FORK-004).

**Descendant** — any recipe reachable downwards through parent references. The
product never enumerates descendants for a reader; the one place the relation is
used is a moderator's removal of a dangerous recipe (MOD-008).

**Provenance** — what a card shows about where the recipe came from: its direct
parent, where that parent is or has been published (FORK-004, FORK-005).

**Fork count** — the number of recipes naming this one as parent. Two numbers
exist where they differ: the total, and how many of those are published. Only
the published count orders the catalogue (CAT-005, CAT-001).

**Own fork** — a fork made by the recipe's own owner. It carries the parent's
media and description as well (FORK-007), unlike a fork by any other account
(FORK-002). It is the only way an author can correct a recipe they published.

## Visibility, freezing and removal

**Externally visible** — accessible to any account other than the owner. A
moderator acting in role is not such an account. This is the moment a recipe
freezes (CON-003).

**Frozen** — no longer editable, by anyone, through any interface, permanently.
A recipe freezes at external visibility and never thaws (CON-003, ST-003).

**Publication** — the act that puts a recipe in the public catalogue and search,
after a moderator approves it. It cannot be undone by the owner, ever (ST-001).

**Private link** — a link that opens one recipe for whoever holds it, issued
after moderation, without the recipe entering the catalogue. It makes the recipe
externally visible and therefore freezes it (`entity-states`, `link_shared`).

**Revoking a link** — making every issued link to a recipe inactive. It stops
the recipe opening for link holders; it does not unfreeze the recipe and is not
the same act as withdrawal (ST-005).

**Withdrawal** — the owner taking back a submission before a moderator has
decided. The recipe becomes editable again. Nothing can be withdrawn after a
decision (ST-003).

**Removal** — a moderator taking a recipe out of the catalogue, search and every
direct path. It is terminal, available to a moderator alone, and never cascades
to forks (ST-002, ST-007).

**Unavailable** — what a fork's card says about a parent that cannot be opened,
with no reason given and no further chain shown (ST-007). A favourite that stops
being accessible is marked the same way (FLOW-010).

**Safety flag** — the mark a recipe carries while an ancestor was removed as
dangerous to health and no moderator has cleared it for this recipe. Its card
states the descent and names nothing (MOD-008, FLOW-018).

## What a recipe is made of

**Step** — one instruction, holding its text, at most one timer (DOM-013) and
any ingredient references. A step has no title: what identifies it is its own
text (`domain-model`).

**Ingredient reference** — a marker inside a step's text pointing at an
ingredient of the same recipe. It renders as the quantity and unit at the
reader's current scale and never as the ingredient's name (DOM-007, FLOW-032).

**Timer** — a duration attached inside one step. A step carries at most one
(DOM-013).

**Timer name** — what a timer announces itself by. Derived from the first 60
characters of its step's text unless the author set a name of their own, in
which case theirs is used and stops following the step (FLOW-016).

**Cover** — the recipe's own image, attached where the title is written. Optional
(FLOW-026, DOM-006). Always a photograph, never a video (MEDIA-001).

**Conversion** — what the device does to every media file before uploading it:
re-encoding it to its limit, discarding every metadata field but orientation,
and removing a video's audio track. The author is always told it happened
(MEDIA-002, MEDIA-003, MEDIA-004, MEDIA-005).

**Unpublished allowance** — the 150 MB of media one account may hold across
recipes that have never been published. Publishing a recipe frees what it holds
(MEDIA-010).

**Tag** — a label from a fixed dictionary the product owner maintains, in one of
two facets, `cuisine` or `dish_type`. A user cannot create one (DOM-008).

**Submission minimum** — title, at least one ingredient, at least one step and a
serving count. Below it a recipe cannot be submitted (DOM-006).

**Submission maximum** — 50 ingredients, 50 steps, 2 000 characters per step,
120 per title, 2 000 per description, 60 per timer name. Above any of them a
recipe cannot be submitted (DOM-012).

## Ingredients and units

**Unit type** — which of five behaviours an ingredient's quantity has under
scaling: `exact` (grams, millilitres and the larger units above them),
`piece`, `household` (teaspoon, tablespoon, cup), `taste_measured` (a pinch),
`taste_unmeasured` (pepper to taste, carrying no quantity at all). The author
never picks a unit type: it follows from the unit they chose (DOM-002,
FLOW-023).

**Divisible / indivisible** — whether a `piece` quantity may show halves or only
whole pieces. It comes from the ingredient's dictionary entry and the author
does not override it; only a free-text ingredient has its divisibility set by
hand, because it has no entry (`scaling-rules`, FLOW-001).

**Ingredient dictionary** — the list of ingredients the product owner maintains,
at most two levels deep. A user cannot add an entry (DOM-009, DOM-010).

**Entry** — one item of that dictionary. A **leaf** entry has a parent entry
(`крылышки` under `курица`); a **parent entry** has none and is usable in a
recipe on its own. The editor offers leaves before parents (DOM-010, FLOW-023).

**Qualifier** — the author's own words narrowing an entry, typed as the tail of
the ingredient's own field so the row reads `филе без кожи`. It is not a fourth
field, and a filter never matches it (DOM-009, FLOW-023, OQ-020).

**Free text** — an ingredient the author named themselves because no entry
matched. Accepted with a warning, never refused, and never found by an
ingredient filter — permanently, once the recipe is published (DOM-009,
FLOW-022).

**Comparison factor** — what one unit of an entry counts for on the comparison
scale its family shares. Never displayed, never scaled with, used only to put
quantities in order (DOM-011).

**Comparable quantity** — an ingredient line's quantity for one serving
multiplied by its comparison factor. What the ingredient filter orders results
by (CAT-009, CAT-002).

## Scaling

**Authored serving count** — the number of servings the author wrote the
quantities for. The basis of every recalculation (SCALE-001).

**Requested serving count** — the number the reader asks for. Floored at ¼
serving, with no ceiling; below one serving it is one of ¼, ⅓, ½, ⅔, ¾, and at
one and above a whole number (SCALE-010, SCALE-011).

**Usual serving count** — the reader's own setting for how many they normally
cook for. Where it is set, every recipe opens at it, and it outranks the count
the reader last used on that particular recipe (FLOW-017).

**Fixed ingredient** — an ingredient whose available amount the reader states, so
the whole recipe is scaled by the factor that implies. The most recently fixed
one sets the scale (SCALE-002, SCALE-006).

**Rounding grid** — the set of values each unit type may display, and the floor
below which it does not go (`scaling-rules`). Rounding applies to what is shown,
never to the stored recipe and never to the chain of computation (SCALE-003).

**Floor** — the smallest quantity a unit type displays: 1 piece indivisible, ½
piece divisible, ¼ for `household` and `taste_measured`, ¼ serving for the
serving count itself. An ingredient is never scaled out of a recipe (SCALE-005).

**Derived** — any quantity a reader sees that is not the authored one. Every
derived value is marked as derived, and says which way it moved (CON-004,
SCALE-004).

## People and accounts

**Account** — created only when an action requires one — writing, forking,
rating, or carrying favourites beyond the device — and carrying no personal data
of any kind (ACT-001, AUTH-001, CON-001). Not an identity: a person on two
devices holds one account by holding one key.

**Person with no account** — an actor, not an absence: they browse, search,
cook, keep favourites on the device, report and block, and the product creates
nothing for them (ACT-001, FLOW-051).

**Account key** — the string that holds an account, offered both to copy and to
save as a file. Whoever holds it holds the account; losing it, with no device
still signed in, loses the account and there is no recovery (NGOAL-003). It is
shown in full exactly once, at creation, and afterwards only masked with a
control that copies it whole (AUTH-007, AUTH-019). Entering it signs in and
replaces whatever account was active, wiping what the device held for it
(AUTH-009, AUTH-012). Its form and cryptography are OQ-002.

**Deletion key** — a second, smaller secret issued beside the account key,
unique and unguessable. It is the only thing the deletion page accepts, and it
can destroy an account but never take it over (AUTH-017, EXT-006).

**Device slot** — one of three places a device has for creating an account. A
slot is occupied by creating an account and is free again six calendar months
later; signing in with a key consumes none (AUTH-002, AUTH-009). The slots are
held by the attestation service, not by the product, and yield no device
identifier (AUTH-015).

**Device verdict** — the answer the attestation service gives about the device
the application is running on, including which slots are free. Nothing from it
is stored (AUTH-015).

**Muted account** — an account created where no verdict could be obtained. It
carries favourites beyond the device and does nothing else an account is for: it
does not create, fork, edit, submit or rate. It becomes full when a verdict and
a free slot allow (AUTH-004, AUTH-005).

**Nickname** — a random three-word name generated at the creation screen, where
it may be replaced by another generated one any number of times. Once the account
exists it is globally unique, permanent, and the only thing about an account that
is ever shown (DOM-001, AUTH-018).

**Owner** — the account that created a recipe, for that recipe's whole life.
Ownership is never transferred (ACT-002). Not an actor: a role an account holds
with respect to one recipe.

**Reader** — an account looking at a recipe it does not own. Also a role, not an
actor (`actors`).

**Moderator** — an account holding an assignable role: it approves, rejects and
removes, and it can never alter content (ACT-003, ACT-004).

**Established account** — an account with at least one published recipe. Only
their reports count towards automatic hiding (ABUSE-003).

**Publishing ban** — a moderator stopping an account's submissions. It touches
nothing else the account does and leaves its published recipes in place
(ACT-005, ACT-006).

## Moderation and abuse

**Moderation submission** — the record created when a recipe is sent for review,
carrying the trigger that caused it (ABUSE and `moderation`). Approval is final
for that content, which can never change afterwards (MOD-007).

**Trigger** — which act caused a submission: `publication` or `private_link`.
Both make the recipe externally visible, so both go through moderation (MOD-001).

**Report** — one account marking a published or link-shared recipe in exactly
one of two categories: use of someone else's content, or danger to health
(ABUSE-002).

**Counted report** — a report from an established account, one per account per
recipe. Three of them hide a recipe automatically (ABUSE-003, ST-006).

**Blocking** — one account hiding another author's recipes from its own
catalogue and search. It does not hide them from anybody else and is not a
report (ABUSE-005).

## Ratings

**Rating** — one account's like or dislike of one published recipe. An account
holds at most one rating per recipe, may change it and may withdraw it by
repeating it (RATE-001, RATE-002). An author never rates their own recipe
(RATE-004).

**Withdrawn rating** — a rating the account took back. It counts for nothing:
not in the two counts, not in either ordering, and the recipe may end up rated
by nobody again (RATE-002, RATE-011).

**Net score** — likes minus dislikes. It is what `highest rated` orders by, and
it is never displayed: the reader is shown the two counts (RATE-007, RATE-010).

## The device

**Held on the device** — content stored locally so it opens without a
connection: the account's favourites, both dictionaries, and every recipe the
account owns that the server has not accepted (`flows`, FLOW-001, FLOW-009).

**Accepted by the server** — said of a recipe whose every local change, text and
media, has reached the server. Nothing is submitted for moderation before it is
(FLOW-005, FLOW-006).

**Favourite** — a recipe the reader marked to take with them. Its text is always
held on the device; whether its media comes too is one setting covering all
favourites (FLOW-009, FLOW-011). Without an account a favourite lives on that
device and nowhere else, and is lost with it; an account carries its favourites
to every device it signs in to (FLOW-051, FLOW-052).

## What the editor tells the author

**The statement before submission** — what the product says about what becomes
permanent, shown before every submission and before publishing from a private
link, confirmed explicitly, and impossible to switch off (GUIDE-001, GUIDE-002).

**Checklist** — seven fixed pieces of advice on writing a recipe, opened from a
control in the editor, read-only, recording nothing and blocking nothing
(GUIDE-003, GUIDE-004).

**Hint** — a short statement shown only at a moment the author's own action
creates. Hints hold no state, need no dismissal, and are never triggered by
reading what the author wrote (GUIDE-005).

## Names

**Recipe Fork** — the working title used throughout this repository. It is not a
decided product name and has been cleared against nothing; the published name is
a release prerequisite (OQ-016).
