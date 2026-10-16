---
slug: DOM
status: closed
updated: 2026-10-15
ids: [DOM-021, DOM-022, DOM-023, DOM-024, DOM-025, DOM-026, DOM-027, DOM-028]
---

# Synonyms, ingredient groups, notes, timers, equipment and the author's note on a step

Added on 2026-10-04 by the sync of v0.15–v0.24: what the dictionary entry, the
ingredient list and the step gained after the builds the product owner touched.
[20-ingredients-and-limits.md](20-ingredients-and-limits.md) was already at its
size limit, so these live here. DOM-025 to DOM-027 were added on 2026-10-11 by
the sync of v0.22–v0.30, and DOM-028 on 2026-10-15 by the sync of v0.31–v0.38.

### DOM-021 — An entry carries synonyms in each language

An ingredient dictionary entry may carry, in each interface language, a list of
synonyms — other words cooks use for the same ingredient (`картошка` and
`картофель`, `манка` and `манная крупа`, `icing sugar` and `powdered sugar`). A
synonym finds the entry wherever the entry's label would: in the editor's offer
(FLOW-057) and in the book's ingredient filter. A recipe holds the entry, never the
synonym typed, so everywhere the ingredient is shown it reads by the entry's label.

**Acceptance criteria**
- Given the entry `картофель` with the synonym `картошка`, when the author types
  `картош`, then `картофель` is offered and the offer shows which synonym matched;
  once picked, the row and the card read `картофель`.
- Given the same entry, when the reader filters the book by `картошка`, then
  recipes holding `картофель` are found.
- Given a word that is the label of one entry and a synonym of another, when it
  is typed, then the entry whose label it is comes first.

Synonyms are maintained with the dictionaries (DOM-014) and are not shown on the
card. Which synonyms exist is the product owner's (DOM-020).

**Source:** user decision, 2026-10-03 (the product owner's idea after `картошка`
and `манка` found nothing in v0.14), built in v0.15 and accepted at its demo,
2026-10-04. See
[ADR — synonyms, and the exact match first](../decisions/20261004-synonyms-and-the-exact-match-first.md).

### DOM-022 — Ingredients can fall into named groups

The author may divide a recipe's ingredients into named groups ("Dough",
"Glaze"). A group is a heading in the ingredient list: the rows below it belong to
it until the next heading, and rows above the first heading belong to none. A
group is presentation only — it changes nothing in scaling, in ingredient
references, in filters or in search, and an ingredient is the same ingredient
whichever group it sits in.

**Acceptance criteria**
- Given a recipe with groups, when its card is read, then the groups show as
  subheadings over their rows, and scaling, fixing an amount and step references
  behave exactly as without them.
- Given a group the author removes, when the recipe is read, then its rows stay
  where they were and so belong to the group above it, or to none if it was the
  first; no row moves.
- Given a recipe with groups, when it is forked or exported and imported again,
  then the groups and the rows in them are kept.
- Given a recipe written before groups existed, when it is read or imported, then
  it has no groups and nothing else changes.

Groups of steps are outside the vision for now. How groups are added, renamed and
reordered in the editor is the team's.

**Source:** user decision, 2026-10-04 (planning of v0.23); the rules above are the
lead's proposal in that plan, accepted by the user at the sync of 2026-10-04.
**Changed 2026-10-11 (sync v0.22–v0.30):** a removed group's rows used to go
"outside any group", which for any group but the first would have meant moving
them above the first heading and reordering the recipe. As built in v0.23 and
accepted by the user at the sync of 2026-10-11, they stay in place and join the
group above.

### DOM-023 — An ingredient line can carry a short note

Any ingredient line — a dictionary entry or free text — may carry a note of at
most 60 characters on how it is prepared ("finely chopped", "room temperature").
The note is shown after the ingredient in a muted style; it is not scaled, it is
not searched, and it plays no part in the ingredient filter.

The note and the qualifier (FLOW-023) answer different questions. The qualifier
says *what* the ingredient is — it is part of its name and the book's text search
finds it (`филе без кожи`). The note says *how to prepare* it, and is found by
nothing. Guidance to authors and the editor's labels keep the two apart.

**Acceptance criteria**
- Given an ingredient with a note, when the servings change, then the note reads
  as written.
- Given a note holding a word, when the book is searched or filtered by that word,
  then the note alone does not make the recipe match.
- Given a recipe with notes, when it is forked or exported and imported again,
  then the notes are kept.

**Source:** user decision, 2026-10-04 (planning of v0.23); the 60-character limit
and "not searched, not scaled" are the lead's proposal in that plan; the split
from the qualifier is a req-forge proposal accepted by the user at the sync of
2026-10-04.

### DOM-024 — A step carries up to five timers, each with its own name

A step may carry from none to five timers. Each timer has its own duration and,
optionally, its own name; each starts, runs, rings and is stopped on its own, with
its own announcement (FLOW-013, FLOW-016). This replaces DOM-013, retired
2026-10-04.

**Acceptance criteria**
- Given a step with five timers, when the author tries to add a sixth, then it is
  refused.
- Given a step with two timers, when the reader starts both, then both run, the
  running-timers display lists both, and each rings and is stopped on its own.
- Given two timers of one step that the author did not name, when they are shown
  or announced, then the reader can tell them apart; how is the team's.
- Given a recipe with several timers on a step, when it is forked or exported and
  imported again, then every timer is kept with its name and duration.

**Source:** user decision, 2026-10-04 (planning of v0.24). The cap of five is a
req-forge proposal, accepted by the user at the sync of 2026-10-04. See
[ADR — a step may carry several timers](../decisions/20261004-a-step-may-carry-several-timers.md).

### DOM-025 — A recipe lists the equipment it needs

A recipe may list the equipment it needs ("Oven", "Frying pan 26 cm"): an ordered
list of at most 15 items. Each item is either an entry of the equipment dictionary
(DOM-026) with an optional qualifier after it, or free text — exactly one of the
two, as an ingredient's name is (DOM-009) — and its shown text is at most 40
characters. Equipment is not scaled, has no quantity and plays no part in the
ingredient filter.

**Acceptance criteria**
- Given a recipe with equipment, when it is forked by anyone or exported and
  imported again, then every item is kept, in order, with its entry, qualifier or
  free text — equipment is part of the method (FORK-001).
- Given an item whose entry later leaves the dictionary, when the recipe is read,
  then the item still reads as it did.
- Given a recipe or a file written before equipment was bound to the dictionary,
  when it is read, then an item whose whole text is an entry's label or synonym in
  either language becomes that entry, and any other item stays free text.
- Given a recipe written before equipment existed, when it is read or imported,
  then it has no equipment and nothing else changes.
- Given the servings changed, when the equipment is read, then it reads as
  written.

How equipment is offered and edited in the editor is the team's.

**Source:** user decisions, 2026-10-09 (planning of v0.27: "a recipe lists the
equipment it needs") and 2026-10-10 (planning of v0.28: an item binds to the
dictionary with a qualifier or stays free text, chosen over matching by text
alone); the limits of 15 items and 40 characters are the lead's, accepted with
the builds. See
[ADR — equipment is bound to its dictionary](../decisions/20261011-equipment-is-bound-to-its-dictionary.md).

### DOM-026 — The equipment dictionary

The product shall carry a third dictionary, of kitchen equipment, kept exactly as
the ingredient dictionary is: entries with permanent identifiers, a label in each
interface language, synonyms that find an entry (DOM-021), at most two levels —
groups and the items in them — and maintained only outside the application
(DOM-014). An entry is withdrawn, never deleted, and stays readable in every recipe
that names it. What L10N-003 and FLOW-001 say of the two older dictionaries — both
languages always, a copy held on the device — holds for this one too.

Consumables — foil, parchment, cling film, paper towels and the like — are entries
a recipe may name, kept in a group of their own that never counts as missing from
a kitchen (FLOW-071) and never hides a recipe (FLOW-072).

**Acceptance criteria**
- Given an author typing an equipment item, when the text matches a label or a
  synonym in either language, then the entry is offered, as for an ingredient
  (FLOW-057).
- Given a recipe naming a consumable, when the reader's kitchen does not hold it,
  then nothing marks it missing and no filter hides the recipe for it.

**Source:** user decision, 2026-10-10 (planning of v0.28: "an equipment dictionary
like the ingredients' one"); consumables never counting is the lead's, built in
v0.28 and accepted by the user at the sync of 2026-10-11.

### DOM-027 — A step may carry the author's note

Beside its required method, a step may carry the author's own note («Бабушка
всегда добавляла щепотку сахара»): optional free text of at most 500 characters,
shown with the step on both tabs of the card (FLOW-070), not scaled and not
searched.

The note is the author's expression, not the method: like the description, a fork
by anyone other than the owner does not carry it (FORK-002), and a fork of one's
own recipe does (FORK-007).

**Acceptance criteria**
- Given a step with a note, when another account forks the recipe, then the fork's
  step has the parent's text, timers and references and no note.
- Given a step with a note, when the owner forks their own recipe, then the note is
  kept.
- Given a step with a note, when the recipe is exported and imported again, then
  the note is kept.
- Given a note holding a word, when the book is searched by that word, then the
  note alone does not make the recipe match.

**Source:** user decision, 2026-10-10 (planning of v0.28, for v0.29: "each step gets
an optional author's part beside its required text", not carried by another
account's fork like the description); the 500-character limit is the lead's,
accepted with the build.

### DOM-028 — A collection is a named, ordered list of a book's recipes

A recipe book holds collections. A collection has an identity, a name of 1 to 60
characters, an ordered list of recipes of the same book, and an origin — made in
this book (own) or imported from a collection file (FLOW-077) — and, when imported,
the author's nickname the file states, if it states one. A recipe may be in any
number of collections, and is in each at most once. A collection holds references
to recipes, not copies: it never changes a recipe, and a recipe never changes by
being in one.

**Acceptance criteria**
- Given a recipe in two collections, when it is opened from either, then it is the
  same recipe, with the same done marks and the same parent.
- Given a recipe deleted or removed from the book, when its collections are shown,
  then it is in none of them; given that deletion undone, then it is back in each
  that still exists, at its place.
- Given a collection deleted, when the book is shown, then every recipe it held is
  still there.
- Given an imported collection, when its owner looks for renaming, reordering or
  removing a recipe from it, then none is offered: it is kept as the file stated
  it, like a found recipe (FLOW-064).
- Given two collections with the same name, when both are made, then both exist.

**Source:** user decisions at the planning of v0.36 (2026-10-12): one concept named
Collection («Коллекция»); an imported collection is not editable; a recipe may be in
several. The name's length and the deletion rules are the lead's, built in v0.36
and accepted with it (2026-10-13). Adopted at the sync of 2026-10-15.
