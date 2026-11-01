---
slug: FLOW
status: closed
updated: 2026-10-15
ids: []
---

# Flows and screens

This section holds screens, the four states each one must answer for — empty,
loading, error, offline — and the paths between them. The rules a screen obeys
stay where they were decided and are referenced by identifier: search and
filters in `catalogue` (the recipe book's own text query and orderings in
FLOW-035 and FLOW-039), recalculation in `scaling-rules`. Nothing decided
elsewhere is restated here. Everything that needs a server is later
(`35-server-later.md`).

This section outgrew one file and is split into the parts below. Requirement
identifiers did not change on the split and never will.

| Part | What is in it | Requirements |
|---|---|---|
| [10-offline-and-sync.md](10-offline-and-sync.md) | The device holds everything: the dictionaries, and writing with no account and no connection | FLOW-001, FLOW-002 |
| [20-device-storage.md](20-device-storage.md) | The recipe book and what the device keeps, with and without an account; clearing media the device can fetch again | FLOW-009–FLOW-012, FLOW-058, FLOW-051, FLOW-052, FLOW-060 |
| [30-cooking.md](30-cooking.md) | Cooking from a recipe: timers heard with the application closed and named by their step, the screen kept awake, the usual serving count, forking twice, a flagged recipe's statement | FLOW-013–FLOW-018, FLOW-020 |
| [35-cooking-with-busy-hands.md](35-cooking-with-busy-hands.md) | The card's two tabs, View and Cooking; marking ingredients, equipment and steps done; and the vibration that answers a touch while cooking | FLOW-068–FLOW-070 |
| [38-equipment-and-my-kitchen.md](38-equipment-and-my-kitchen.md) | My kitchen: the equipment the reader has, what a card marks missing, and the book's filter that hides what cannot be cooked | FLOW-071, FLOW-072 |
| [40-editor.md](40-editor.md) | Writing a recipe: the ingredient row and what its field offers, the editor's order, media, warnings, the saved view and what a fork carries | FLOW-022–FLOW-026, FLOW-028–FLOW-030, FLOW-057 |
| [45-steps-timers-references.md](45-steps-timers-references.md) | A step's timers (up to five), and ingredient references in a step's text | FLOW-027, FLOW-031, FLOW-032 |
| [50-browsing.md](50-browsing.md) | The catalogue and the recipe book ("My recipe book"): its two views, the view it opens on (the one last left), and what a device with no account sees | FLOW-033–FLOW-043 |
| [55-own-recipe-and-author.md](55-own-recipe-and-author.md) | Deleting a recipe of one's own — from its entry or its card, undone while its notice shows — the private link, and the author's screen | FLOW-053–FLOW-055 |
| [70-serverless-mode.md](70-serverless-mode.md) | Serverless mode: what a release built for after the server is gone takes away and keeps, exporting and importing recipe files — opened from other applications too — forks of imported recipes, and saving and restoring the whole book — added to or replaced | FLOW-061–FLOW-065, FLOW-073 |
| [75-shopping-and-printing.md](75-shopping-and-printing.md) | The shopping list — merged across units as "≈", with lines of the reader's own and ticks — and printing or sharing as a PDF one recipe, the list, several recipes or a collection | FLOW-074, FLOW-075 |
| [78-collections-and-selection.md](78-collections-and-selection.md) | Collections in the book, a collection as one file, and choosing several recipes to act on at once | FLOW-076–FLOW-078 |

## Open

- The exact trigger character of FLOW-031, which is a presentation choice and is
  settled with the editor's visual design, not here.
- Whether a step may link to another recipe is OQ-024.
- Where the single media setting of FLOW-011 and the action of FLOW-060 sit in
  the interface, which is a presentation choice and not a behaviour.
- The format of a recipe file (FLOW-063), which is an engineering choice; what it
  must carry is specified.

What the editor teaches its author belongs to `author-guidance`, not here.
