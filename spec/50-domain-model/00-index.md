---
slug: DOM
status: closed
updated: 2026-09-20
ids: [DOM-001, DOM-002, DOM-003, DOM-004, DOM-005, DOM-006, DOM-007,
  DOM-008, DOM-009, DOM-010, DOM-011, DOM-012]
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

## Parts

The entities are above; the invariants that hold over them have their own file,
because this section outgrew one. Requirement identifiers did not change on the
split and never will.

| Part | What is in it | Requirements |
|---|---|---|
| [10-invariants.md](10-invariants.md) | Invariants over the entities | DOM-001–DOM-012 |
