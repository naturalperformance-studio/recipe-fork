---
slug: DOM
status: closed
updated: 2026-09-27
ids: [DOM-009, DOM-010, DOM-011, DOM-012, DOM-013, DOM-014, DOM-016, DOM-017,
  DOM-018]
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

**Verification:** store one ingredient of each kind and confirm the two rejected
shapes are refused.
**Source:** user decision, 2026-09-20. A filter on an ingredient has to compare
something stable, and free text is not stable (OQ-017). The author is never
blocked by a missing entry — free text stays available with the warning FLOW-022
requires. See
[ADR — the ingredient dictionary](../decisions/20260920-ingredient-dictionary.md).

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
[ADR — comparison factors](../decisions/20260920-comparison-factors.md).

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

### DOM-013 — A step carries at most one timer

If a step already carries a timer, then the system shall refuse to attach a
second one to it.

**Acceptance criteria**
- Given a step with no timer, when a timer is attached to it, then it is
  accepted.
- Given a step that already carries a timer, when another is attached, then the
  attachment is refused and the step still carries exactly one.
- Given two waits in sequence — thirty minutes covered, then fifteen uncovered —
  when the author writes them, then they are two steps with one timer each, and
  nothing in the product joins them back together.
- Given a step whose timer the author deleted, when a new timer is attached,
  then it is accepted.
- Given a recipe whose steps each carry a timer, when the reader starts several
  of them, then all of them run at once (FLOW-016).

**Verification:** attach a second timer to a step through every path the editor
offers, and confirm each is refused.
**Source:** user decision, 2026-09-20. A timer is identified by the text of the
step it belongs to and by nothing else (FLOW-016), so two timers on one step
would be two things carrying one name. Two waits in sequence are already two
steps in a kitchen, which is how a cook says them aloud. See
[ADR — a timer is named by its step](../decisions/20260920-timer-named-by-its-step.md).

### DOM-014 — Both dictionaries are maintained outside the application

The system shall offer no control, on any screen or at any address a user can
reach, that creates, changes, moves or removes an entry of the ingredient
dictionary or of the tag dictionary, and both shall be maintained only by direct
operation on the product's own store by whoever operates it.

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

**Verification:** enumerate every screen as an author and as a moderator;
change an entry in the store and confirm a device picks it up.
**Source:** user decision, 2026-09-21. DOM-008, DOM-009, DOM-010, DOM-011,
CAT-009 and L10N-003 all require a maintained dictionary — two levels, two
languages, a divisibility flag and comparison factors — and every one of them
said only that a user cannot edit it, never where the product owner does. An
editor for it is a real screen in an MVP whose whole scope is the fork mechanic,
and it would sit behind the moderator role, which ACT-008 has just placed
outside the application as well.
**Cost, stated:** a factor typed wrong changes the order of filtered results and
nothing in the product notices. DOM-011 already accepted that shape of error by
keeping the factor on the entry, where it is correctable for ever, rather than in
the recipe, where it would not be; and the share of ingredients entered as free
text (OBS-001) is the signal that says the dictionary is falling behind.

### DOM-016 — The units are a closed list of twelve

The system shall hold as an ingredient's unit only one of the units below, and
shall give each the unit type the table assigns it.

| Unit type | Russian | English |
|---|---|---|
| `exact` | г · кг · т · мл · л · м³ | g · kg · t · ml · l · m³ |
| `piece` | шт | pc |
| `household` | ч. л. · ст. л. · стакан | tsp · tbsp · cup |
| `taste_measured` | щепотка | pinch |
| `taste_unmeasured` | по вкусу | to taste |

**Acceptance criteria**
- Given the unit field of the editor, when it is opened, then it offers these
  twelve units, named in the interface language, and no other.
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

**Acceptance criteria**
- Given the quantity field, when the author enters `500 г`, `1,5 кг`, `⅓ ч. л.`,
  `1½ шт` of a divisible ingredient or `3 шт` of an indivisible one, then the field
  holds that value.
- Given the quantity field, when the author enters 0, a negative number, `7,5 г`,
  `1,2345 кг`, `0,3 ч. л.`, `2,5 шт` or `½ шт` of an indivisible ingredient, then
  the field does not hold it.
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

**Verification:** publish a recipe with a half piece of a divisible entry, mark the
entry indivisible, and open the recipe at its authored and at another count; open
a draft holding the same quantity.
**Source:** user decision, 2026-09-27. A recipe was valid when it was published,
and CON-003 forbids correcting it afterwards; the correction of the entry is the
product owner's, not the author's.
