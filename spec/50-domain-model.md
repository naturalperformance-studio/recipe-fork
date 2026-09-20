---
slug: DOM
status: closed
updated: 2026-09-20
ids: [DOM-001, DOM-002, DOM-003, DOM-004, DOM-005, DOM-006, DOM-007, DOM-008, DOM-009,
  DOM-010, DOM-011, DOM-012]
---

# Domain model

Entities, their fields and the invariants that hold over them. Storage form,
identifier format and indexing are implementation choices and are not specified
here. Behaviour over these entities lives in `entity-states`, `scaling-rules`,
`moderation`, `catalogue`, `ratings` and `abuse`.

## Unit types

The unit type of an ingredient decides how its quantity behaves when a recipe is
scaled. Five types exist; the scaling and rounding rules for each are specified
in section `scaling-rules`.

| Type | Examples | Carries a quantity | Scales |
|---|---|---|---|
| `exact` | g, kg, t, ml, l, m³ | yes | linearly, no rounding loss |
| `piece` | egg, garlic clove, onion | yes | linearly, then rounded to a countable value |
| `household` | teaspoon, tablespoon, cup | yes | linearly, then rounded to a convenient fraction |
| `taste_measured` | a pinch of salt | yes | linearly, then rounded to a convenient fraction |
| `taste_unmeasured` | pepper to taste | no | never — the ingredient has no quantity to scale |

An `exact` ingredient may be authored in any unit of its type, including tonnes
and cubic metres, so that a production card can be written in the units it is
already kept in. What is displayed is governed by the rounding grid in section
`scaling-rules`, not by the unit the author chose.

`household` quantities are never converted to mass: a tablespoon of flour and a
tablespoon of honey do not weigh the same, and the product holds no ingredient
weight table. `piece` quantities may be held internally in grams so that scaling
stays smooth, while being displayed in pieces.

## Entities

### Account

| Field | Type | Notes |
|---|---|---|
| `account_id` | identifier | Internal. Never shown to any user. |
| `nickname` | three-word string | The only public representation of a user. Globally unique, assigned at creation, never changes. |
| `created_at` | timestamp | |
| `publishing_banned` | boolean | Set and cleared by a moderator (ACT-005). |
| `roles` | set | Empty, or contains the moderator role (ACT-003). |

An account holds, as separate collections: favourites (recipes), ratings (one
value per rated recipe), blocked authors (accounts), and the recipes it owns.
The account carries no field that identifies the person behind it (CON-001).

### Recipe

| Field | Type | Notes |
|---|---|---|
| `recipe_id` | identifier | |
| `owner` | Account | Fixed at creation, not transferable (ACT-002). |
| `parent` | Recipe or none | The direct parent this recipe was forked from. Fixed at creation. Only the direct parent is held; no chain root, no descendant list (GOAL-003). |
| `title` | text | |
| `description` | text | |
| `tags` | set of Tag | Chosen from the fixed dictionary. |
| `servings` | number | The serving count the author wrote the quantities for. |
| `ingredients` | ordered list of Ingredient | |
| `steps` | ordered list of Step | |
| `media` | list of Media | |
| `state` | state value | See `entity-states`. |
| `was_published` | boolean | False at creation; set once when the recipe first enters `published` and never cleared. What FORK-004 and FORK-005 depend on. |
| `safety_flag` | boolean | True while an ancestor has been removed as dangerous to health and no moderator has cleared it for this recipe. Set by MOD-008, cleared by MOD-010, inherited by a fork of a flagged recipe. |

### Ingredient

| Field | Type | Notes |
|---|---|---|
| `position` | integer | Order within the recipe. |
| `dictionary_entry` | IngredientEntry or none | The dictionary entry this ingredient is an instance of. Absent exactly when the author matched no entry (DOM-009). What a filter matches (CAT-002). |
| `qualifier` | text or none | The author's own words narrowing the entry, e.g. `филе бедра` under `курица`. Allowed only where `dictionary_entry` is present. Not matched by a filter. |
| `free_text_name` | text or none | The author's own name for an ingredient that matched no entry. Present exactly when `dictionary_entry` is absent (DOM-009). |
| `unit_type` | unit type | One of the five above. |
| `unit` | unit | The concrete unit within the type, e.g. `g` within `exact`. Absent for `taste_unmeasured`. |
| `quantity` | number or none | Absent exactly when `unit_type` is `taste_unmeasured`. |
| `piece_weight` | number or none | Optional, `piece` only: the mass of one piece, used to keep scaling smooth. |
| `divisible` | boolean or none | `piece` only. Taken from the entry where there is one, set by the author where there is not, and overridable by the author in both cases (`scaling-rules`). |

What is displayed for an ingredient is its entry's label together with its
qualifier, or its `free_text_name`. Which of the two parts comes first is a
presentation choice and is not specified here.

### IngredientEntry

| Field | Type | Notes |
|---|---|---|
| `entry_id` | identifier | |
| `label` | text | Localised; see `localization`. |
| `parent` | IngredientEntry or none | The broader entry this one belongs to, e.g. `крылышки` under `курица`. An entry that is itself a parent has none: the dictionary is two levels deep at most (DOM-010). |
| `divisible` | boolean | `piece` divisibility for ingredients of this entry, which the author may override per ingredient (`scaling-rules`). |
| `comparison_factors` | unit → positive number | What one of that unit counts for on this entry's own comparison scale. Absent for a unit the product owner has not filled in. Used for ordering only, never displayed, never scaled with (DOM-011). |

The ingredient dictionary is maintained by the product owner, exactly as the tag
dictionary is. A user cannot create an entry. It replaces the separate dictionary
of indivisible ingredients the earlier draft of `scaling-rules` described: that
dictionary held divisibility keyed by an ingredient name, and this one holds the
same flag on the entry the name now refers to.

The comparison scale belongs to a family, not to a single entry: a top-level
entry and all its children share one scale, because a filter on the parent orders
a line of one child against a line of another — two wings against one whole
carcass (CAT-006, CAT-009). Scales of unrelated families are never compared, since
no filter ever puts chicken and flour in one order.

Within a family the scale has to make the family's units commensurable: pieces
against grams, and grams against millilitres where an entry is used both ways. A
family measured in grams anywhere will therefore be scaled in grams, with the
factor for `g` being 1, which makes a factor for `шт` numerically an approximate
mass. What it is *for* the product is not a mass: it is what one unit counts for
when quantities are put in order, in the sense a vote may count for ten, and
DOM-011 is what keeps it from ever becoming anything else.

### Step

| Field | Type | Notes |
|---|---|---|
| `position` | integer | Order within the recipe. |
| `text` | text | May contain ingredient references. |
| `ingredient_references` | list | Each points at an Ingredient of the same recipe and at a position in `text`. |
| `timers` | list of Timer | |

### Timer

| Field | Type | Notes |
|---|---|---|
| `duration` | duration | |
| `label` | text or none | |

### Media

| Field | Type | Notes |
|---|---|---|
| `media_id` | identifier | |
| `owner` | Account | Media is bound to an account at upload, before publication. |
| `kind` | `photo` or `video` | |
| `attachment` | recipe cover, or a Step | |
| `visibility` | `private` or `published` | Follows the visibility of the recipe it belongs to. |

Limits on size, duration and count per recipe are open — OQ-003.

### Tag

| Field | Type | Notes |
|---|---|---|
| `tag_id` | identifier | |
| `facet` | `cuisine` or `dish_type` | |
| `label` | text | Localised; see `localization`. |

Tags come from a fixed dictionary maintained by the product owner. A user cannot
create one.

### Moderation submission

| Field | Type | Notes |
|---|---|---|
| `recipe` | Recipe | |
| `trigger` | `publication` or `private_link` | Both make the recipe externally visible. |
| `submitted_at` | timestamp | |
| `decision` | `pending`, `approved` or `rejected` | |
| `reason` | text or none | Present when the decision is `rejected`. |
| `decided_by` | Account or none | A moderator. |
| `decided_at` | timestamp or none | |

### Report

| Field | Type | Notes |
|---|---|---|
| `report_id` | identifier | |
| `target` | Recipe or Account | |
| `reporter` | Account | |
| `category` | `foreign_content` or `health_risk` | ABUSE-002. |
| `created_at` | timestamp | |

### Rating, Favourite, Block

| Entity | Fields |
|---|---|
| Rating | `account`, `recipe`, `value` (`like` or `dislike`) |
| Favourite | `account`, `recipe`, `added_at` |
| Block | `account`, `blocked_author`, `created_at` |

Whether a rating may be changed or withdrawn is specified in section `ratings`.

## Invariants

### DOM-001 — A nickname is unique and permanent

The system shall assign each account a nickname that no other account holds, and
shall provide no means of changing it.

**Acceptance criteria**
- Given any two accounts, when their nicknames are compared, then they differ.
- Given any interface of the application, when it is enumerated, then it offers
  no action that changes a nickname.

**Verification:** uniqueness check over the account store; interface review.
**Source:** draft section 2.1; user decision, 2026-09-20.
**Note:** because a nickname never changes, a published recipe needs no snapshot
of its author's name — the current nickname is always the one that was shown at
publication. Behaviour when the nickname space is exhausted is OQ-009.

### DOM-002 — A quantity exists exactly when the unit type carries one

The system shall hold a quantity for every ingredient whose unit type is
`exact`, `piece`, `household` or `taste_measured`, and shall hold no quantity
for an ingredient whose unit type is `taste_unmeasured`.

**Acceptance criteria**
- Given an ingredient of type `taste_unmeasured`, when it is stored, then it has
  no quantity and no unit, and the editor offers no field for either.
- Given an ingredient of any other type, when it is submitted without a
  quantity, then the recipe cannot be submitted for moderation.

**Verification:** editor check per unit type; validation test on submission.
**Source:** draft section 3.2; user decision, 2026-09-20.

### DOM-003 — A household quantity is never expressed as mass

The system shall not convert a `household` quantity to mass or volume, and shall
not display a mass or volume equivalent for it.

**Acceptance criteria**
- Given an ingredient measured in tablespoons, when the recipe is displayed at
  any scale, then its quantity is shown in tablespoons only.

**Verification:** manual UI check on a recipe using household units.
**Source:** user decision, 2026-09-20. The product holds no ingredient weight
table, and inventing one per ingredient would be a separate product with its own
moderation burden.

### DOM-004 — A parent reference is fixed and shallow

The system shall set a recipe's parent at creation, shall provide no means of
changing it, and shall hold no reference from a recipe to its descendants or to
the root of its chain.

**Acceptance criteria**
- Given a forked recipe, when its parent is resolved, then it is the recipe it
  was forked from, at every point in its lifetime.
- Given any recipe, when its stored fields are enumerated, then none of them
  lists descendants or names a chain root.

**Verification:** data model review; interface review.
**Source:** draft section 4.3; GOAL-003.

### DOM-005 — Media is owned by an account from upload

When a user adds a media item to a recipe, the system shall bind that item to
the user's account at upload time, before the recipe becomes externally visible.

**Acceptance criteria**
- Given an unpublished recipe with media, when the same account signs in on
  another device with its key file, then that media is available there.
- Given a media item belonging to an unpublished recipe, when any other account
  requests it, then it is not served.

**Verification:** two-device test with a key file; access test from a second
account.
**Source:** draft section 5.2.

### DOM-006 — Submission minimum

If a recipe lacks a title, at least one ingredient, at least one step, or a
serving count, then the system shall reject its submission for moderation.

**Acceptance criteria**
- Given a recipe missing any one of those four, when the user submits it for
  publication or requests a private link, then the submission is rejected and
  the interface names what is missing.
- Given a recipe that has all four and no media, when it is submitted, then the
  submission is accepted.

**Verification:** validation test over each of the four omissions.
**Source:** user decision, 2026-09-20. Media is deliberately not required: a
mandatory photo would add a manual media review and storage cost (CON-002) to
every publication.

### DOM-007 — Step ingredient references resolve within the recipe

The system shall resolve every ingredient reference in a step to an ingredient
of the same recipe.

**Acceptance criteria**
- Given a step containing a reference, when the step is displayed, then the
  referenced quantity equals the quantity shown for that ingredient in the
  ingredient list at the current scale.
- Given an ingredient that a step references, when the author deletes that
  ingredient before the recipe becomes externally visible, then the reference is
  removed from the step text in the same action.

**Verification:** manual check of a scaled recipe, comparing every referenced
quantity in the steps against the ingredient list.
**Source:** draft section 3.5; user decision, 2026-09-20 (moved from "desirable"
into the MVP, because otherwise every scaled recipe contradicts itself between
its list and its steps).

### DOM-008 — Tags come from the dictionary only

The system shall accept as a recipe's tags only entries of the tag dictionary.

**Acceptance criteria**
- Given the tag editor, when the author looks for a tag, then only dictionary
  entries are offered and free text cannot be committed.
- Given a submission carrying a tag that is not in the dictionary, when it is
  received, then it is rejected.

**Verification:** editor check; service-level submission test.
**Source:** user decision, 2026-09-20. Free-text tags would become another text
surface for the single moderator to check, and would fragment search.

## Deliberately not modelled yet

These are named so their absence is visible, not an oversight.

- Whether a rating can be changed or withdrawn — section `ratings`.
- Media size, duration and count limits — OQ-003.
- What a fork shows when its parent is gone — OQ-005, OQ-007.
- Account deletion — section `retention`.

### DOM-009 — An ingredient is a dictionary entry or the author's own words, never both

The system shall hold for every ingredient either a `dictionary_entry` with an
optional `qualifier`, or a `free_text_name`, and shall never hold both and never
hold neither.

**Acceptance criteria**
- Given an ingredient the author picked from the dictionary, when it is stored,
  then it holds that entry, holds no `free_text_name`, and holds a `qualifier`
  only if the author wrote one.
- Given an ingredient the author typed because no entry matched, when it is
  stored, then it holds a `free_text_name`, no entry and no `qualifier`.
- Given a submission carrying both an entry and a `free_text_name`, or neither,
  when it is validated, then it is rejected.
- Given a submission carrying a `qualifier` with no entry, when it is validated,
  then it is rejected.
- Given a dictionary entry that the product owner removes upstream, when a recipe
  already refers to it, then the recipe keeps the reference, exactly as FLOW-001
  requires of a removed tag.

**Verification:** store one ingredient of each kind and confirm the two rejected
shapes are refused.
**Source:** user decision, 2026-09-20. A filter on an ingredient has to compare
something stable, and free text is not stable (OQ-017). The author is never
blocked by a missing entry — free text stays available with the warning FLOW-022
requires. See
[ADR — the ingredient dictionary](decisions/20260920-ingredient-dictionary.md).

### DOM-010 — The ingredient dictionary is two levels deep at most

The system shall hold for an ingredient entry either one parent entry or none,
shall hold no parent for an entry that is itself a parent, and shall accept a
reference from a recipe to an entry at either level.

**Acceptance criteria**
- Given the entry `крылышки` whose parent is `курица`, when `курица` is read,
  then it has no parent of its own.
- Given an attempt to give a parent to an entry that already has children, when
  it is validated, then it is rejected.
- Given a recipe naming `крылышки`, when it is stored, then it holds that entry
  and nothing about `курица` is copied into the recipe.
- Given a recipe whose author had only generic chicken to state, when they pick
  `курица` itself, then it is accepted as the ingredient's entry.
- Given the product owner moves an entry under a different parent, when a recipe
  already refers to the entry, then the recipe is unchanged and only what a filter
  matches changes (CAT-006).

**Verification:** build a two-level dictionary, store one recipe on a leaf and one
on a parent, and attempt a third level.
**Source:** user decision, 2026-09-20. The author picks one entry — the leaf they
actually mean, `крылышки` — and the dictionary supplies the broader entry, so the
hierarchy costs the author nothing and exists only as the product owner's data. A
parent stays usable directly because `курица, 500 г` is an ordinary recipe line,
and refusing it would push the author into the free text of FLOW-022 for no gain;
this last point is derived, not stated by the product owner. See
[ADR — comparison factors](decisions/20260920-comparison-factors.md).

### DOM-011 — A comparison factor orders and nothing else

The system shall use an entry's comparison factors only to put quantities in
order, and shall never display a factor, never derive a displayed quantity from
one, and never apply one when a recipe is scaled.

**Acceptance criteria**
- Given an ingredient of 3 tablespoons, when the recipe is displayed at any scale,
  then it is displayed in tablespoons and no mass appears anywhere (DOM-003).
- Given an ingredient of 2 pieces whose entry carries a factor, when the reader
  scales the recipe, then the quantity is scaled by the rules of `scaling-rules`
  and the factor takes no part in it.
- Given the product owner corrects a factor, when a published recipe is opened
  afterwards, then every displayed quantity is exactly what it was, and only the
  order in which filtered results appear changes.
- Given any screen of the application, when it is inspected, then no comparison
  factor is shown to any user, including the moderator.

**Verification:** display and scale a recipe holding household, piece and exact
ingredients, then change a factor upstream and repeat.
**Source:** user decision, 2026-09-20. The factor's value comes from three
properties: it is invisible, it never touches what the cook reads, and it lives on
the dictionary entry rather than in the recipe — so a wrong factor is correctable
for ever, while anything stored in a published recipe is not (CON-003). DOM-003
stands because it forbids expressing a household quantity as mass, and an ordering
key expresses nothing to anybody. See
[ADR — comparison factors](decisions/20260920-comparison-factors.md).

### DOM-012 — Submission maximum

If a recipe holds more than 50 ingredients, more than 50 steps, a step of more than
2 000 characters, a title of more than 120 characters or a description of more than
2 000 characters, then the system shall refuse to accept it for moderation and shall
name the part that is over its limit.

**Acceptance criteria**
- Given a recipe with 50 ingredients and 50 steps, when it is submitted, then it is
  accepted, because the limits are inclusive.
- Given a recipe with 51 steps, when it is submitted, then submission is refused and
  the steps are named as the part over the limit.
- Given a step of 2 001 characters, when the recipe is submitted, then submission is
  refused and that step is named.
- Given a recipe over two limits at once, when it is submitted, then both are named,
  so the author does not discover them one at a time.
- Given a recipe within every limit, when it is submitted, then no limit is mentioned
  anywhere.
- Given a recipe over a limit, when the author keeps editing it, then editing is not
  blocked and the recipe stays in its state (FLOW-028).

**Verification:** submit a recipe at each limit exactly, one over each limit, and one
over two at once.
**Source:** user decision, 2026-09-20. The counts are set where no real recipe reaches
them — a demanding one runs to about 20 steps and 30 ingredients — while a step and a
description get 2 000 characters each, because that is where an author actually writes.
Without any cap, a single submission could be a recipe of thousands of steps, which
the one moderator has to read (`abuse`) and every device has to render. These are the
functional limits; `quality` holds the measurable thresholds that refer to them.
