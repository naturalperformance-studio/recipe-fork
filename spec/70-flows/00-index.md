---
slug: FLOW
status: closed
updated: 2026-09-29
ids: []
---

# Flows and screens

This section holds screens, the four states each one must answer for — empty,
loading, error, offline — and the paths between them. The rules a screen obeys
stay where they were decided and are referenced by identifier: search and
filters in `catalogue`, rating and favourites in `ratings`, reporting and
blocking in `abuse`, what a moderator checks in `moderation`, recalculation in
`scaling-rules`. Nothing decided elsewhere is restated here.

The entry point of the application — the first account, its key, signing in on a
second device — belongs to `auth` and is not described here.

This section outgrew one file and is split into the parts below. Requirement
identifiers did not change on the split and never will.

| Part | What is in it | Requirements |
|---|---|---|
| [10-offline-and-sync.md](10-offline-and-sync.md) | The device first: writing with no account and no connection, sending a recipe to the server by the owner's act, what is refused, divergence, and a sending the server already holds | FLOW-001–FLOW-008, FLOW-059, FLOW-066 |
| [20-device-storage.md](20-device-storage.md) | The recipe book and what the device keeps, with and without an account; clearing media the device can fetch again | FLOW-009–FLOW-012, FLOW-058, FLOW-051, FLOW-052, FLOW-060 |
| [30-cooking.md](30-cooking.md) | Cooking from a recipe | FLOW-013–FLOW-021 |
| [40-editor.md](40-editor.md) | Writing a recipe: the ingredient row and what its field offers, the editor's order, media, warnings, the saved view and what a fork carries | FLOW-022–FLOW-026, FLOW-028–FLOW-030, FLOW-057 |
| [45-steps-timers-references.md](45-steps-timers-references.md) | A step's timer, and ingredient references in a step's text | FLOW-027, FLOW-031, FLOW-032 |
| [50-browsing.md](50-browsing.md) | The catalogue and the recipe book ("My recipe book"): its two views, the view it opens on, and what a device with no account sees | FLOW-033–FLOW-043 |
| [55-own-recipe-and-author.md](55-own-recipe-and-author.md) | Deleting a recipe of one's own, the private link, and the author's screen | FLOW-053–FLOW-055 |
| [60-moderator-queue.md](60-moderator-queue.md) | The moderator's queue, and where a publishing ban is applied | FLOW-044–FLOW-050, FLOW-056 |
| [70-serverless-mode.md](70-serverless-mode.md) | Serverless mode: what a release built for after the server is gone takes away and keeps, exporting and importing recipe files, and forks of imported recipes | FLOW-061–FLOW-065 |

## Open

- The exact trigger character of FLOW-031, which is a presentation choice and is
  settled with the editor's visual design, not here.
- Whether a step may link to another recipe is OQ-024.
- Where the single media setting of FLOW-011 and the action of FLOW-060 sit in
  the interface, which is a presentation choice and not a behaviour.
- The format of a recipe file (FLOW-063), which is an engineering choice; what it
  must carry is specified.

The account key and the first account belong to `auth`, not here. What the editor teaches
its author belongs to `author-guidance`, not here.
