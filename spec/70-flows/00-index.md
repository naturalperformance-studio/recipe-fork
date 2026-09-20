---
slug: FLOW
status: open
updated: 2026-09-20
ids: [FLOW-001, FLOW-002, FLOW-003, FLOW-004, FLOW-005, FLOW-006, FLOW-007,
  FLOW-008, FLOW-009, FLOW-010, FLOW-011, FLOW-012, FLOW-013, FLOW-014,
  FLOW-015, FLOW-016, FLOW-017, FLOW-018, FLOW-019, FLOW-020, FLOW-021,
  FLOW-022, FLOW-023, FLOW-024, FLOW-025, FLOW-026, FLOW-027, FLOW-028,
  FLOW-029, FLOW-030, FLOW-031, FLOW-032]
---

# Flows and screens

This section holds screens, the four states each one must answer for — empty,
loading, error, offline — and the paths between them. The rules a screen obeys
stay where they were decided and are referenced by identifier: search and
filters in `catalogue`, rating and favourites in `ratings`, reporting and
blocking in `abuse`, what a moderator checks in `moderation`, recalculation in
`scaling-rules`. Nothing decided elsewhere is restated here.

The entry point of the application — first launch, the key file, signing in on a
second device — belongs to `auth` and is not described here.

This section outgrew one file and is split into the parts below. Requirement
identifiers did not change on the split and never will.

| Part | What is in it | Requirements |
|---|---|---|
| [10-offline-and-sync.md](10-offline-and-sync.md) | Offline work and synchronisation | FLOW-001–FLOW-008 |
| [20-device-storage.md](20-device-storage.md) | Favourites and what the device keeps | FLOW-009–FLOW-012 |
| [30-cooking.md](30-cooking.md) | Cooking from a recipe | FLOW-013–FLOW-021 |
| [40-editor.md](40-editor.md) | Writing a recipe | FLOW-022–FLOW-032 |

## Open

- For the editor flow: whether a recipe carrying more than one timer must label
  them, since FLOW-016 falls back to a step's position when a label is missing.
- For the editor flow: the form the divisibility override takes, given FLOW-023
  admits only three fields — a secondary control on the row, or somewhere else.
- The exact trigger character of FLOW-031, which is a presentation choice and is
  settled with the editor's visual design, not here.
- Whether a step may link to another recipe is OQ-024.
- The rest of the screens: catalogue, favourites, the moderator's queue, the key
  file.
