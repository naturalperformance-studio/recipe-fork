---
slug: DOM
status: closed
updated: 2026-10-15
ids: [DOM-009, DOM-010, DOM-011, DOM-012, DOM-014, DOM-016, DOM-017, DOM-019, DOM-018, DOM-020]
  DOM-019, DOM-018, DOM-020]
---

# Invariants over ingredients, the dictionaries, units and limits

Split on 2026-09-27 from [10-invariants.md](10-invariants.md); no identifier changed.

### DOM-009 — An ingredient is a dictionary entry or the author's own words, never both

The system shall hold for every ingredient either a `dictionary_entry` with an
optional `qualifier`, or a `free_text_name`, and shall never hold both and never
hold neither.

**Acceptance criteria**
- Given an ingredient the author picked from the dictionary, when it is stored,
  then it holds that entry, holds no `free_text_name`, and holds a `qualifier`
  only if the author wrote one.
- Given an ingredient the author added in their own words (FLOW-057), when it is
  stored, then it holds a `free_text_name`, no entry and no `qualifier`.
- Given a submission carrying both an entry and a `free_text_name`, or neither,
  when it is validated, then it is rejected.
- Given a submission carrying a `qualifier` with no entry, when it is validated,
  then it is rejected.
- Given a dictionary entry that the product owner removes upstream, when a recipe
  already refers to it, then the recipe keeps the reference, exactly as FLOW-001
  requires of a removed tag.
- Given an entry the product owner withdraws, when the dictionary is next read,
  then the entry is still there marked `removed`, under the same `entry_id`; it
  still names every recipe that holds it, and it is not offered when an author
  picks an ingredient.
- Given any later copy of the dictionary, when its identifiers are compared with
  an earlier one, then no identifier has disappeared, none has been renumbered,
  and none has been given to a different entry.

**Verification:** store one ingredient of each kind and confirm the two rejected
shapes are refused.
**Source:** user decision, 2026-09-20. A filter on an ingredient has to compare
something stable, and free text is not stable (OQ-017). The author is never
blocked by a missing entry — free text stays available with the warning FLOW-022
requires. See
[ADR — the ingredient dictionary](../decisions/20260920-ingredient-dictionary.md).
Amended 2026-10-03 (sync v0.3–v0.14): an entry is withdrawn by a `removed` mark and
never deleted, and its identifier is permanent (req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03).

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
[ADR — comparison factors](../decisions/20260920-comparison-factors.md).

### DOM-011 — A comparison factor orders, and converts only as "≈"

The system shall use an entry's comparison factors to put quantities in order, and
to derive a displayed quantity only where a quantity must cross from mass into a
household unit or between unit types — converting a mass into cups or spoons for a
US reader (L10N-008), and merging one entry's lines written in different unit types
in a shopping list (FLOW-074) — marking every quantity so derived as approximate
("≈"). A factor shall never change a stored quantity, never take part in scaling,
and never be shown as a number of its own.

**Acceptance criteria**
- Given an ingredient of 3 tablespoons, when the recipe is displayed at any scale
  and in either unit system, then it is displayed in tablespoons and no mass
  appears (DOM-003).
- Given an ingredient of 2 pieces whose entry carries a factor, when the reader
  scales the recipe, then the quantity is scaled by the rules of `scaling-rules`
  and the factor takes no part in it.
- Given 260 g of flour whose entry carries a factor for the cup, when a US reader
  opens the card, then it reads "≈ 2 cups"; given the same line for a metric
  reader, then it reads 260 g with no mark.
- Given a quantity derived through a factor, when it is shown anywhere — the card,
  the shopping list, a printed page — then it carries the "≈" mark, and the
  screen reader reads it as approximate (A11Y-008).
- Given the product owner corrects a factor, when a recipe is opened afterwards,
  then every quantity not marked "≈" is exactly what it was; only "≈" amounts and
  the order of filtered results change, and the recipe's stored quantities are
  untouched.
- Given an entry with no factor for the needed unit, or a free-text ingredient,
  when a conversion through a factor would be needed, then the quantity is shown
  as written.

**Verification:** display and scale a recipe holding household, piece and exact
ingredients in both unit systems; build a shopping list from two recipes naming
one entry in grams and in cups; then change a factor upstream and repeat.
**Source:** user decision, 2026-09-20. The factor's value came from three
properties: it is invisible, it never touches what the cook reads, and it lives on
the dictionary entry rather than in the recipe — so a wrong factor is correctable
for ever, while anything stored in a published recipe is not (CON-003). See
[ADR — comparison factors](../decisions/20260920-comparison-factors.md).
**Changed 2026-10-15 (sync v0.31–v0.38):** the second property is narrowed. By the
owner's decisions at the planning of v0.32 (a shopping list merges one entry across
units, approximately) and of v0.35 (the full kitchen conversion through the
dictionary, marked approximate), a factor may now produce what a reader sees, as
long as it says so with "≈". The first and third properties stand: no factor is
shown as such, and correcting one never touches a stored recipe. See
[ADR — one unit system, converted on display](../decisions/20261015-one-unit-system-converted-on-display.md).

### DOM-012 — Submission maximum

If a recipe holds more than 50 ingredients, more than 50 steps, a step of more than
2 000 characters, a title of more than 120 characters, a description of more than
2 000 characters or a timer name of more than 60 characters, then the system shall
refuse to accept it for moderation and shall name the part that is over its limit.

**Acceptance criteria**
- Given a recipe with 50 ingredients and 50 steps, when it is submitted, then it is
  accepted, because the limits are inclusive.
- Given a recipe with 51 steps, when it is submitted, then submission is refused and
  the steps are named as the part over the limit.
- Given a step of 2 001 characters, when the recipe is submitted, then submission is
  refused and that step is named.
- Given a recipe over two limits at once, when it is submitted, then both are named,
  so the author does not discover them one at a time.
- Given a timer name of 61 characters, when the recipe is submitted, then submission
  is refused and that timer is named.
- Given a step that holds only media and no text, when the recipe is submitted,
  then no step-length limit applies to it (DOM-006).
- Given a timer whose name the author never set, when the recipe is submitted, then
  no timer-name limit applies to it, because its name is derived from a step that has
  its own limit (FLOW-016).
- Given a recipe within every limit, when it is submitted, then no limit is mentioned
  anywhere.
- Given a recipe over a limit, when the author keeps editing it, then editing is not
  blocked and the recipe stays in its state (FLOW-028).

**Verification:** submit a recipe at each limit exactly, one over each limit, and one
over two at once.
**Source:** user decision, 2026-09-20; the timer-name limit added the same day with
the manual override. 60 characters is the same number FLOW-016 cuts a derived name
at, because both occupy one line of one notification and a longer name would be cut
in the same place. The counts are set where no real recipe reaches
them — a demanding one runs to about 20 steps and 30 ingredients — while a step and a
description get 2 000 characters each, because that is where an author actually writes.
Without any cap, a single submission could be a recipe of thousands of steps, which
the one moderator has to read (`abuse`) and every device has to render. These are the
functional limits; `quality` holds the measurable thresholds that refer to them.
Amended 2026-10-03 (sync v0.3–v0.14): a step without text is checked against no
length limit (req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03).

DOM-013 (a step carries at most one timer) was retired on 2026-10-04 and replaced
by [DOM-024](30-recipe-additions.md).

### DOM-014 — The dictionaries are maintained outside the application

The system shall offer no control, on any screen or at any address a user can
reach, that creates, changes, moves or removes an entry of the ingredient
dictionary, of the tag dictionary or of the equipment dictionary (DOM-026), and
each shall be maintained only by direct operation on the product's own store by
whoever operates it.

In serverless mode there is no store to operate on: every dictionary shall be
maintained in the product's source and shipped with the application, and a
device's copy shall change only with an update of the application. What this
requirement says of the store holds for the server.

**Acceptance criteria**
- Given every screen of the application, including those the moderator role
  opens, when they are enumerated, then none edits either dictionary and none
  shows a comparison factor (DOM-011).
- Given a dictionary changed in the store, when a device next reaches the server,
  then its held copy is replaced as FLOW-001 already requires, and an entry
  removed upstream stays resolvable in the recipes that already name it
  (DOM-009, DOM-010).
- Given the rule that an entry carries a label in both languages (L10N-003), when
  a half-translated entry is written, then what refuses it is the store's own
  constraint, because there is no screen at which the refusal could be shown.
- Given an author who finds no matching entry, when they look for a way to ask
  for one, then there is none and FLOW-022's free text is the whole escape; a
  queue of such requests is deferred as OQ-019 and has nowhere to land while this
  requirement stands.
- Given this requirement, when it is read against CON-005 and EXT-001, then it
  adds neither a client surface nor an external dependency.
- Given serverless mode, when the dictionaries are changed, then the change is
  made in the product's source and reaches a device only with an application
  update; nothing the device downloads or a user does changes its copy, and an
  entry the update marks `removed` stays resolvable in the recipes that name it
  (DOM-009).

**Verification:** enumerate every screen as an author and as a moderator;
change an entry in the store and confirm a device picks it up; in serverless
mode, change an entry in the source and confirm a device sees it only after
installing the updated application.
**Source:** user decision, 2026-09-21. DOM-008, DOM-009, DOM-010, DOM-011,
CAT-009 and L10N-003 all require a maintained dictionary — two levels, two
languages, a divisibility flag and comparison factors — and every one of them
said only that a user cannot edit it, never where the product owner does. An
editor for it is a real screen in an MVP whose whole scope is the fork mechanic,
and it would sit behind the moderator role, which ACT-008 has just placed
outside the application as well. Amended 2026-10-03 (sync v0.3–v0.14): the
serverless variant — the dictionaries ship with the application (req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03). See
[ADR — the serverless dictionary ships with the app](../decisions/20261003-the-serverless-dictionary-ships-with-the-app.md).
Amended 2026-10-11 (sync v0.22–v0.30): the equipment dictionary (DOM-026), added in
v0.28, is kept the same way.
**Cost, stated:** a factor typed wrong changes the order of filtered results and
nothing in the product notices. DOM-011 already accepted that shape of error by
keeping the factor on the entry, where it is correctable for ever, rather than in
the recipe, where it would not be; and the share of ingredients entered as free
text (OBS-001) is the signal that says the dictionary is falling behind.

### DOM-016 — The units are a closed list of fifteen

The system shall hold as an ingredient's unit only one of the units below, and
shall give each the unit type and the unit system the table assigns it.

| Unit type | System | Russian | English |
|---|---|---|---|
| `exact` | metric | г · кг · т · мл · л · м³ | g · kg · t · ml · l · m³ |
| `exact` | US | the team's names | oz · lb · fl oz |
| `piece` | both | шт | pc |
| `household` | both | ч. л. · ст. л. · стакан | tsp · tbsp · cup |
| `taste_measured` | both | щепотка | pinch |
| `taste_unmeasured` | both | по вкусу | to taste |

An ounce is 28.349523125 g, a pound 16 oz, a US fluid ounce 29.5735295625 ml,
exactly. Whether a unit belongs to one system or both decides what the editor
offers and what is converted on display (L10N-008).

**Acceptance criteria**
- Given the unit field of the editor, when it is opened, then it offers the units
  of the reader's unit system and those of both, named in the interface language,
  and no other; a row already holding a unit of the other system keeps it, shown
  in its place (L10N-008).
- Given the author picks `стакан`, when the ingredient is stored, then its unit
  type is `household`; given `щепотка`, `taste_measured`; given `шт`, `piece`.
- Given a submission carrying a unit outside the table, when it is validated, then
  it is rejected.
- Given a unit shown in English, when it is compared with its Russian name, then
  it is the same unit translated and not a different measure (L10N-008).

**Verification:** open the unit field in both languages; store one ingredient per
unit; submit a recipe with a unit outside the table.
**Source:** user decision, 2026-09-27 (SD-008 item 2). The domain model listed
units as examples, and whoever wrote the field would have decided which exist. A
bunch, a clove, a can, a slice and a pack are not units here: a `piece` ingredient
is counted in pieces, and what a piece is comes from the entry. Whether to add
them is OQ-037.
**Cost, stated:** an author writes a bunch of dill as `укроп · 1 · шт`, and a
reader reads "1 шт" of dill.
**Changed 2026-10-15 (sync v0.31–v0.38):** ounces, pounds and fluid ounces added,
and every unit given a system, as built in v0.35 by the owner's decision at its
planning. The household units belong to both systems — owner's choice at that sync.
See [ADR — one unit system, converted on display](../decisions/20261015-one-unit-system-converted-on-display.md).

### DOM-017 — An authored quantity is a value of its unit type's grid

The system shall hold as an ingredient's quantity only a value above zero that
the rounding grid of its unit type displays (`scaling-rules`), and shall set no
upper bound on it.

The values held are, by unit type:

| Unit type | Values the quantity field holds |
|---|---|
| `exact` | below 1000 kg or 1000 l, a whole number of grams or millilitres, entered in any unit of the same dimension (g, kg, t or ml, l, m³); at 1000 kg or 1000 l and above, a whole number of kilograms or litres |
| `piece`, indivisible | a whole number of one or more |
| `piece`, divisible | ½, a whole number, or a whole number followed by ½ |
| `household`, `taste_measured` | ¼, ⅓, ½, ⅔, ¾, a whole number, or a whole number followed by one of those fractions |
| `exact`, in US units | ounces and pounds: a multiple of ¼ oz below 4 oz, of ½ oz from 4 oz to 2 lb, of ¼ lb from 2 lb; fluid ounces: a multiple of ½ fl oz |

**Acceptance criteria**
- Given the quantity field, when the author enters `500 г`, `1,5 кг`, `⅓ ч. л.`,
  `1½ шт` of a divisible ingredient or `3 шт` of an indivisible one, then the field
  holds that value.
- Given the quantity field, when the author enters 0, a negative number, `7,5 г`,
  `1,2345 кг`, `0,3 ч. л.`, or, of an indivisible ingredient, `2,5 шт` or `½ шт`,
  then the field does not hold it.
- Given the quantity field, when the author enters a value of a fraction grid
  with a decimal mark — `2,5 шт` of a divisible ingredient, `0,5 ч. л.`,
  `0,25 ч. л.` — then the field holds it and it is displayed as the grid writes
  it: 2½, ½, ¼. `0,33 ч. л.` and `0,66 ч. л.` are not held, because ⅓ and ⅔ have
  no decimal form on the grid; the field's error names the fractions it accepts.
- Given the quantity field, when the author enters `1 г`, `¼ ч. л.`, `½ шт` of a
  divisible ingredient or `1 шт` of an indivisible one, then the field holds it:
  each is the floor of its grid.
- Given the quantity field, when the author enters a number however large that lies
  on the grid, then the field holds it.
- Given a row whose field holds a value, when the author changes its unit or, for
  a free-text ingredient, its divisibility, so that the value no longer lies on the
  new grid — `½ ст. л.` changed to `г`, `1½ шт` made indivisible — then the value
  stays in the field, the row is marked as an error naming the values the field
  accepts, and the recipe cannot be submitted until the row holds an accepted value.
- Given a submission carrying a quantity that does not lie on the grid of its unit
  type, when it is validated, then it is rejected, exactly as DOM-016 rejects a
  unit outside its table.
- Given a recipe at its authored serving count, when an ingredient is displayed,
  then it shows the authored value, which is a grid value and so is the same under
  SCALE-001 (4) and under the grid; DOM-018 is the one exception.
- Given a fork of a recipe holding a value that no longer lies on its grid
  (DOM-018), when the fork is edited, then that row is marked as an error in the
  same way and the fork cannot be submitted until it is corrected.

**Verification:** enter every example above in the editor, in both directions;
change the unit and the divisibility of a row holding an off-grid value for its
new grid and try to submit; submit an off-grid quantity to the server directly.
**Source:** user decision, 2026-09-27 (SD-034, and SD-034's follow-up on changing a
unit). The field works on the device, so it behaves the same with no connection.
See [ADR — a quantity anyone states lies on the grid](../decisions/20260927-stated-quantities-lie-on-the-grid.md).
**Cost, stated:** an author cannot write 2,5 g of yeast or 0,3 teaspoon; they write
3 g and ⅓. Half of an indivisible piece cannot be written at all, which the section
`scaling-rules` had already accepted.
**Completed 2026-09-29:** whether `2,5 шт` of a divisible ingredient and
`0,5 ч. л.` are held was read two ways — by the value the grid displays, or by the
written form of its fraction grids (SD-037 item 2). By user decision of that date
the value decides; criterion 2 now names the indivisible ingredient for both of its
piece examples, and criterion 3 is new. How the typed number is read is DOM-019.
See [ADR — a typed quantity is read by its value](../decisions/20260929-a-typed-quantity-is-read-by-its-value.md).

### DOM-019 — A typed number takes a comma or a point as its decimal mark

When a person types a number into a quantity field (DOM-017) or into the fixing
control (SCALE-018), the system shall read both a comma and a point as the decimal
mark, in either interface language, and shall not accept a digit grouping
separator.

**Acceptance criteria**
- Given the interface in Russian, when `1,5 кг` or `1.5 кг` is typed, then either
  is read as 1.5 kg and displayed as `1,5 кг` (L10N-009).
- Given the interface in English, when `1.5 kg` or `1,5 kg` is typed, then either
  is read as 1.5 kg and displayed as `1.5 kg`.
- Given the interface in English, when `1,500 kg` is typed, then it is read as
  1.5 kg, not 1500 kg, and the field displays `1.5 kg`.
- Given either interface language, when `1,2345 кг` or `1.2345 kg` is typed, then
  it is read as 1.2345 kg and not held, because it is not a whole number of grams
  (DOM-017).
- Given either interface language, when a number carrying a space, two decimal
  marks, or both a comma and a point is typed — `1 500 г`, `1.500,5 г`,
  `1,5,0 кг` — then the field does not hold it and states that a number is written
  without separators.
- Given the fixing control, when `0,3 кг` or `0.3 кг` is typed for an ingredient
  authored in grams, then either is accepted as 300 g (SCALE-018 (1)).

**Verification:** type every example above in both interface languages, into the
editor's quantity field and into the fixing control, and compare what is held and
displayed.
**Source:** user decision, 2026-09-29 (SD-037 item 1). L10N-009 fixes how a number
is displayed and nothing fixed how one is read. A phone's keyboard often offers
only a point or only a comma whatever the interface language, so reading one mark
per language would refuse a number typed the only way the person could type it.
See [ADR — a typed quantity is read by its value](../decisions/20260929-a-typed-quantity-is-read-by-its-value.md).
**Cost, stated:** an English-speaking author who types `1,500 kg` meaning a tonne
and a half gets 1.5 kg; the field displays the value it read, so the difference is
visible at once, but it is theirs to catch.

### DOM-018 — A quantity published before a divisibility correction stays as written

Where a dictionary entry's divisibility is changed after a recipe holding a
quantity of that entry was published, the system shall display that quantity at
the recipe's authored serving count as it was written, even where the new grid
does not hold it.

**Acceptance criteria**
- Given a published recipe with `1½ шт` of an entry, when the product owner marks
  the entry indivisible, then the recipe at its authored serving count still shows
  `1½ шт`, and nothing in it is marked as derived or rounded (CON-004 (3)).
- Given the same recipe at any other serving count, when the ingredient is
  displayed, then its value is computed from the written 1½ and rounded by the new
  grid, as for any computed value (SCALE-001, SCALE-015).
- Given a draft or a withdrawn or rejected recipe holding such a quantity, when it
  is edited, then DOM-017 marks the row as an error and it cannot be submitted
  until corrected.
- Given serverless mode, when a found recipe (imported from a file and frozen,
  FLOW-064) holds such a quantity, then it is treated as published for this
  requirement; an own draft holding one follows criterion 3.
- Given serverless mode, when a found recipe holds a row with no quantity yet or a
  quantity off its grid, then it is kept and shown as written, and nothing on the
  device corrects it.

**Verification:** publish a recipe with a half piece of a divisible entry, mark the
entry indivisible, and open the recipe at its authored and at another count; open
a draft holding the same quantity.
**Source:** user decision, 2026-09-27. A recipe was valid when it was published,
and CON-003 forbids correcting it afterwards; the correction of the entry is the
product owner's, not the author's. Amended 2026-10-03 (sync v0.3–v0.14): in
serverless mode a found recipe counts as published, and may hold incomplete and
off-grid rows as written (req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03).

### DOM-020 — What the dictionaries hold

The product owner shall fill the ingredient and tag dictionaries by these rules:
where cooks count different things under one word, those are separate entries; an
entry names no brand; a recipe is not an ingredient; and tags are grouped by
family.

**Acceptance criteria**
- Given garlic, which cooks count both as heads and as cloves, when the dictionary
  is read, then a head of garlic and a clove of garlic are two entries, so what one
  `шт` is always comes from the entry (DOM-016).
- Given any entry of the ingredient dictionary, when its labels are read, then
  none is a brand or trade name.
- Given any entry of the ingredient dictionary, when it is read, then it names an
  ingredient a cook buys or keeps, not a recipe presented as an ingredient.
- Given the tag dictionary, when it is read, then every tag belongs to one family —
  such as cuisine, meal, diet, method or appliance, occasion.

**Verification:** review of the dictionary's content against each rule.
**Source:** req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03. How large the dictionaries are and exactly which tag
families exist are the team's.
