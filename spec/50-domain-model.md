---
slug: DOM
status: closed
updated: 2026-09-20
ids: [DOM-001, DOM-002, DOM-003, DOM-004, DOM-005, DOM-006, DOM-007, DOM-008]
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
| `exact` | g, kg, ml, l | yes | linearly, no rounding loss |
| `piece` | egg, garlic clove, onion | yes | linearly, then rounded to a countable value |
| `household` | teaspoon, tablespoon, cup | yes | linearly, then rounded to a convenient fraction |
| `taste_measured` | a pinch of salt | yes | linearly, then rounded to a convenient fraction |
| `taste_unmeasured` | pepper to taste | no | never — the ingredient has no quantity to scale |

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

### Ingredient

| Field | Type | Notes |
|---|---|---|
| `position` | integer | Order within the recipe. |
| `name` | text | |
| `unit_type` | unit type | One of the five above. |
| `unit` | unit | The concrete unit within the type, e.g. `g` within `exact`. Absent for `taste_unmeasured`. |
| `quantity` | number or none | Absent exactly when `unit_type` is `taste_unmeasured`. |
| `piece_weight` | number or none | Optional, `piece` only: the mass of one piece, used to keep scaling smooth. |

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
