---
slug: GLOSS
status: closed
updated: 2026-10-11
ids: []
---

# Glossary — Recipes, forks, ingredients and scaling

Part of the [glossary](00-index.md); holds no requirements.

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
to forks (ST-002, ST-007). A moderator's own recipe is removed on the same terms
as anyone else's (MOD-014).

**Deletion of a recipe** — the owner destroying a recipe that was never
externally visible, so that it and its submission records cease to exist
(ST-010). It reaches `local`, `private` and `rejected` and nothing else — a
`local` recipe on the device alone, the others on the server too — and it is a
different act from removal, which a moderator performs on what everyone can see,
and from deletion of an account, which destroys every unpublished recipe at once
(RET-001).

**Local** — the state of a recipe never sent: it exists on one device, in an
account's recipe book or in the pool, and the server holds nothing of it (ST-004,
ST-010, FLOW-059).

**Unavailable** — what a fork's card says about a parent that cannot be opened,
with no reason given and no further chain shown (ST-007); in serverless mode, a
parent not held in the recipe book, named as its file states (FLOW-065). A favourite that stops
being accessible is marked the same way (FLOW-010).

**Safety flag** — the mark a recipe carries while an ancestor was removed as
dangerous to health and no moderator has cleared it for this recipe. Its card
states the descent and names nothing (MOD-008, FLOW-018). An owner's fork of a
recipe that was itself removed in that category carries it too, although the
removal happened before the fork existed (ST-004, MOD-008).

## What a recipe is made of

**Step** — one instruction, holding text, media or both — never neither (DOM-006)
— up to five timers (DOM-024) and any ingredient references. A step has no title: what identifies it is its own
text (`domain-model`).

**Ingredient reference** — a marker inside a step's text pointing at an
ingredient of the same recipe, either the whole of it or a **part**: a quantity
the author typed in the ingredient's own unit (FLOW-031). It renders as the
quantity and unit at the reader's current scale and never as the ingredient's
name; a whole reference equals the list line, a part is scaled and rounded on its
own (DOM-007, FLOW-032, SCALE-007).

**Timer** — a duration of hours and minutes, at most 99:59, attached inside one
step (FLOW-027). A step carries up to five (DOM-024).

**Timer name** — what a timer announces itself by. Derived from the first 60
characters of its step's text unless the author set a name of their own, in
which case theirs is used and stops following the step (FLOW-016).

**Author's note on a step** — optional prose of at most 500 characters beside a
step's method; the author's expression, so another person's fork does not carry
it (DOM-027, FORK-002).

**Done mark** — the reader's mark on an ingredient, equipment item or step on the card's Cooking tab, kept on
the device per recipe until cleared; never part of the recipe, its file or a fork
(FLOW-068).

**View and Cooking** — the card's two tabs: View, compact and complete, for
reading and forking; Cooking, large, for busy hands — "the cooking card" of
A11Y-003 and A11Y-007 (FLOW-070).

**Current step** — on Cooking, the first step not marked done; derived from the
marks, never stored (FLOW-070).

**Equipment** — what a recipe needs to be cooked with, up to 15 items, each an
entry of the equipment dictionary with an optional qualifier, or free text
(DOM-025, DOM-026). Carried by every fork.

**My kitchen** — the equipment the reader ticks as held, kept on the device; what
a card marks missing and the book's "Enough equipment" filter compare against
(FLOW-071, FLOW-072). Consumables never count.

**Cover** — the recipe's own image, attached where the title is written. Optional
(FLOW-026, DOM-006). Always a photograph, never a video (MEDIA-001).

**Conversion** — what the device does to every media file before uploading it:
re-encoding it to its limit, discarding every metadata field but orientation,
and removing a video's audio track. The author is always told it happened
(MEDIA-002, MEDIA-003, MEDIA-004, MEDIA-005).

**Unpublished allowance** — the 150 MB of media one account may hold on the
server across recipes it sent and has not published. Publishing a recipe frees
what it holds; a recipe never sent counts nothing (MEDIA-010).

**Tag** — a label from a fixed dictionary the product owner maintains, grouped by
family — such as cuisine, meal, diet, method or appliance, occasion. A user
cannot create one (DOM-008, DOM-020).

**Submission minimum** — title, at least one ingredient, at least one step, a
serving count and a declared language. Below it a recipe cannot be submitted
(DOM-006, L10N-004).

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
does not override it; a free-text ingredient, which has no entry, and one whose
entry leaves divisibility unset have it set by hand (`scaling-rules`, FLOW-001).

**Ingredient dictionary** — the list of ingredients the product owner maintains,
at most two levels deep, holding divisibility and invisible comparison factors
and no displayed weight. A user cannot add an entry (DOM-009, DOM-010, DOM-020).
In serverless mode it ships with the application (DOM-014).

**Entry** — one item of that dictionary. A **leaf** entry has a parent entry
(`крылышки` under `курица`); a **parent entry** has none and is usable in a
recipe on its own. The editor offers leaves before parents (DOM-010, FLOW-023).
An entry's identifier is permanent; a **removed** entry is withdrawn, not deleted:
it still names the recipes that hold it and is not offered for new use (DOM-009).

**Qualifier** — the author's own words narrowing an entry, typed as the tail of
the ingredient's own field so the row reads `филе без кожи`. It is not a fourth
field, and a filter never matches it (DOM-009, FLOW-023, OQ-020). It says *what*
the ingredient is; a note says how to prepare it.

**Synonym** — another word for a dictionary entry, per language (`картошка` for
`картофель`). It finds the entry in the editor's offer and the book's filter;
the recipe still reads by the entry's label (DOM-021).

**Ingredient group** — a named heading among a recipe's ingredients ("Dough");
presentation only, changing nothing in scaling, references or filters (DOM-022).

**Note** — up to 60 characters on an ingredient line saying how it is prepared
("finely chopped"); not scaled and found by no search (DOM-023).

**Free text** — an ingredient the author named in their own words instead of
picking an offered entry (FLOW-057). Accepted with a warning, never refused, and never found by an
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
quantities for: a whole number of one or more, with no ceiling (DOM-015). The
basis of every recalculation (SCALE-001).

**Requested serving count** — the number the reader asks for. Floored at ¼
serving, with no ceiling; below one serving it is one of ¼, ⅓, ½, ⅔, ¾, and at
one and above a whole number (SCALE-010, SCALE-011).

**Usual serving count** — the reader's own setting for how many they normally
cook for. Where it is set, every recipe opens at it, and it outranks the count
the reader last used on that particular recipe (FLOW-017).

**Fixed ingredient** — an ingredient whose available amount the reader states, so
the whole recipe is scaled by the factor that implies. One ingredient is fixed
at a time; fixing another releases it (SCALE-002, SCALE-006). The amounts that may be fixed, and
the units they may be stated in, are SCALE-018.

**Rounding grid** — the set of values each unit type may display, and the floor
below which it does not go (`scaling-rules`). Rounding applies to what is shown,
never to the stored recipe and never to the chain of computation (SCALE-003). It
also bounds what is stated: an author's quantity and a reader's fixed amount are
values of the grid of their unit type (DOM-017, SCALE-018).

**Floor** — the smallest quantity a unit type displays: 1 piece indivisible, ½
piece divisible, ¼ for `household` and `taste_measured`, ¼ serving for the
serving count itself. An ingredient is never scaled out of a recipe (SCALE-005).

**Derived** — any quantity a reader sees while the recipe is displayed at a scale
factor other than 1, whether or not its displayed value happens to equal the
authored one. Every derived value is marked as derived, and says which way
rounding moved it where it did (CON-004, SCALE-004).
