---
slug: DOM
status: closed
updated: 2026-09-29
ids: []
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

The examples of `piece` are ingredients counted in pieces, not units. The units
themselves are a closed list of twelve, each assigned to one type (DOM-016).

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
| `nickname` | three-word string | The only public representation of a user. Globally unique, chosen at the creation screen from generated candidates (AUTH-018), never changes afterwards. |
| `created_at` | timestamp | |
| `publishing_banned` | boolean | Set and cleared by a moderator (ACT-005). |
| `roles` | set | Empty, or contains the moderator role (ACT-003). |
| `terms_version` | version identifier and date | The version of the terms of use this account accepted and when. Set at creation, replaced on re-acceptance (LEG-004, LEG-005). Carries nothing about the person. |

An account holds, as separate collections: favourites (recipes), ratings (one
value per rated recipe), blocked authors (accounts), and the recipes it owns.
The account carries no field that identifies the person behind it (CON-001).
An account exists only where one was asked for (AUTH-001); recipes, favourites
and blocked authors also exist on a device with no account active, in a pool that
belongs to no account and lives only there (FLOW-051, AUTH-026). A device holds
the content of every account signed in on it, each apart from the others
(AUTH-011).

### Recipe

| Field | Type | Notes |
|---|---|---|
| `recipe_id` | identifier | Given when the recipe is created on the device, and kept unchanged when it is sent, exported or imported, so that a fork can name a parent the server does not yet hold (FLOW-007) and a file can be recognised when imported twice (FLOW-064). |
| `owner` | Account or none | None while the recipe is in the pool of a device (AUTH-026); an account once it is written in that account's book or moved into it, and fixed from the recipe's first sending, not transferable (ACT-002). |
| `parent` | Recipe identity or none | The direct parent this recipe was forked from, held by its identity, which need not be held on the device or on the server (FLOW-007, FLOW-065). Fixed at creation. Only the direct parent is held; no chain root, no descendant list (GOAL-003). |
| `sent` | never, sent, or sent with changes not sent | What FLOW-005 marks. Held on the device; the server holds only what was sent (FLOW-059). |
| `stated_author` | nickname as text, or none | Only on a recipe imported from a file: the author's nickname the file states, shown as not verified (FLOW-064). |
| `stated_parent` | title and nickname as text, or none | Only on a recipe imported from a file: the title and author's nickname its file states for its parent (FLOW-065). |
| `title` | text | |
| `description` | text | |
| `tags` | set of Tag | Chosen from the fixed dictionary. |
| `language` | one of the languages of L10N-001 | Declared by the author, never detected. Selects the catalogue filter of L10N-005 and the search analysis of L10N-007. Frozen with the rest of the content (L10N-004). |
| `servings` | whole number, 1 or more | The serving count the author wrote the quantities for (DOM-015). |
| `ingredients` | ordered list of Ingredient | |
| `steps` | ordered list of Step | |
| `media` | list of Media | |
| `state` | state value | See `entity-states`. |
| `replaces_parent` | boolean | Set only on a fork of the owner's own frozen recipe, before it is submitted (FORK-008). What FORK-009 shows on the parent. False where there is no parent. |
| `was_published` | boolean | False at creation; set once when the recipe first enters `published` and never cleared. What FORK-004 and FORK-005 depend on. |
| `safety_flag` | boolean | True while an ancestor has been removed as dangerous to health and no moderator has cleared it for this recipe. Set by MOD-008, cleared by MOD-010, inherited by a fork of a flagged recipe and by an owner's fork of a recipe that was itself removed in that category (ST-004, MOD-008). |

### Ingredient

| Field | Type | Notes |
|---|---|---|
| `position` | integer | Order within the recipe. |
| `dictionary_entry` | IngredientEntry or none | The dictionary entry this ingredient is an instance of. Absent exactly when the author matched no entry (DOM-009). What a filter matches (CAT-002). |
| `qualifier` | text or none | The author's own words narrowing the entry, e.g. `филе бедра` under `курица`. Allowed only where `dictionary_entry` is present. Not matched by a filter. |
| `free_text_name` | text or none | The author's own name for an ingredient that matched no entry. Present exactly when `dictionary_entry` is absent (DOM-009). |
| `unit_type` | unit type | One of the five above. |
| `unit` | unit | The concrete unit within the type, e.g. `g` within `exact`. Absent for `taste_unmeasured`. |
| `quantity` | number or none | Absent exactly when `unit_type` is `taste_unmeasured`. Otherwise a value above zero that the rounding grid of its unit type displays (DOM-017), except where DOM-018 keeps one written before a divisibility correction. |
| `piece_weight` | number or none | Optional, `piece` only: the mass of one piece, used to keep scaling smooth. |
| `divisible` | boolean or none | `piece` only. Taken from the entry where there is one, and set by the author exactly where there is none, because a free-text ingredient has no entry to take it from (DOM-009). The author never overrides an entry's value (`scaling-rules`). |

What is displayed for an ingredient is its entry's label together with its
qualifier, or its `free_text_name`. Which of the two parts comes first is a
presentation choice and is not specified here.

### IngredientEntry

| Field | Type | Notes |
|---|---|---|
| `entry_id` | identifier | |
| `label` | text | Localised; see `localization`. |
| `parent` | IngredientEntry or none | The broader entry this one belongs to, e.g. `крылышки` under `курица`. An entry that is itself a parent has none: the dictionary is two levels deep at most (DOM-010). |
| `divisible` | boolean | `piece` divisibility for every ingredient of this entry. The author does not override it (`scaling-rules`). |
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
| `text` | text | May contain ingredient references. It is also what names the step's timer (FLOW-016). |
| `ingredient_references` | list | Each points at an Ingredient of the same recipe and at a position in `text`. |
| `timer` | Timer or none | At most one (DOM-013). |

A step has no title. What identifies it to a reader is its own text, and that is
also what a timer on it is announced by, which is why no separate name exists in
either entity.

### Timer

| Field | Type | Notes |
|---|---|---|
| `duration` | duration | Hours and whole minutes, from 1 minute to 99 hours 59 minutes; zero is no duration and the timer is not stored (FLOW-027). |
| `label` | text or none | The author's own name for the timer, overriding the name derived from the step. Absent unless the author set one; at most 60 characters (DOM-012). |

A timer's name is derived from the text of the step it belongs to and is not
stored, unless the author overrode it, in which case `label` holds what they
wrote (FLOW-016). A step holds at most one timer (DOM-013), so a derived name is
never ambiguous. `label` absent is not "no name": it means the name follows the
step.

### Media

| Field | Type | Notes |
|---|---|---|
| `media_id` | identifier | |
| `owner` | Account | Media is bound to an account when its recipe is sent, before publication; before that it is on the device alone (FLOW-004). |
| `kind` | `photo` or `video` | |
| `attachment` | recipe cover, or a Step | |
| `visibility` | `private` or `published` | Follows the visibility of the recipe it belongs to. |

Limits on size, duration, count per recipe and total size of sent, unpublished
media per account are in [`media`](../90-media.md), MEDIA-001 to MEDIA-010.

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
| [10-invariants.md](10-invariants.md) | Invariants over the entities: nickname, quantity, household measure, parent, media ownership, the submission minimum, step references, tags, the authored serving count; and what is deliberately not modelled yet | DOM-001–DOM-008, DOM-015 |
| [20-ingredients-and-limits.md](20-ingredients-and-limits.md) | Ingredients and the dictionaries, comparison factors, the submission maximum, one timer per step, where the dictionaries are kept, the twelve units, the values a quantity may hold, and how a typed number is read | DOM-009–DOM-014, DOM-016–DOM-019 |
