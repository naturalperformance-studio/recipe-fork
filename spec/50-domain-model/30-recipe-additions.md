---
slug: DOM
status: closed
updated: 2026-10-04
ids: [DOM-021, DOM-022, DOM-023, DOM-024]
---

# Synonyms, ingredient groups, notes and timers

Added on 2026-10-04 by the sync of v0.15–v0.24: what the dictionary entry, the
ingredient list and the step gained after the builds the product owner touched.
[20-ingredients-and-limits.md](20-ingredients-and-limits.md) was already at its
size limit, so these live here.

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
- Given a group the author removes, when the recipe is read, then its rows stay,
  outside any group.
- Given a recipe with groups, when it is forked or exported and imported again,
  then the groups and the rows in them are kept.
- Given a recipe written before groups existed, when it is read or imported, then
  it has no groups and nothing else changes.

Groups of steps are outside the vision for now. How groups are added, renamed and
reordered in the editor is the team's.

**Source:** user decision, 2026-10-04 (planning of v0.23); the rules above are the
lead's proposal in that plan, accepted by the user at the sync of 2026-10-04.

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
