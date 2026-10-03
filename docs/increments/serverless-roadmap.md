# Serverless roadmap — what is left after v0.9

From an audit of spec/ against `develop` @ f150b1e on 2026-10-03, run because the user asked the lead to carry on, increment by increment, until the serverless product is complete (v0.9 decision log), with filling the ingredient dictionary last. Each increment below becomes a `docs/increments/v<next>.md` when it starts; this file then marks it.

## A — The editor helps you write  → v0.10 (built 2026-10-03, at demo)
FLOW-028 (min/max warnings on leaving a field, incl. the 60-character timer name and DOM-012 limits) · GUIDE-008 (references offered as the first step is begun) · GUIDE-006 (a long step with an unnamed timer shown what its notification will say) · FLOW-030 (what a fork carries) · GUIDE-003/004 (the checklist) · FLOW-015 (forking twice offers the fork already held).

## B — A card for busy hands, in your language  → v0.11 (built 2026-10-03, at demo)
A11Y-007 / A11Y-003 (2) (56 dp card controls, larger step text) · L10N-002 (choose the interface language) · L10N-005 (filter by a recipe's language) · L10N-006 (the description searched) · FLOW-062 (1) sweep (no sending marks in serverless) · FLOW-020 (3) (a card that cannot be read offers to retry) · A11Y-002 contrast pass.

## C — A dictionary that knows divisibility and comparison  → v0.12 (started 2026-10-03)
`divisible` and comparison factors in the dictionary (DTO, Room, server V3, snapshot script, ShippedSnapshotTest) · the editor takes divisibility from the entry (FLOW-001 (2), FLOW-023 (11), DOM-018) · the filter orders by factors (FLOW-035 (2)) · removed entries stay resolvable (FLOW-001 (7)) · the qualifier tail (FLOW-023 (4)) · the snapshot parsed once per version, measured at the caps (QUAL-002/003).

## D — Step video and media housekeeping  → v0.13 (optional)
MEDIA-004/007 short silent step video within the file format · FLOW-060 (4) delete held media.

## E — Fill the dictionary  → last
A hand-made dictionary of a few thousand ingredients in families plus a real tag dictionary, with divisibility and factors, a generator that computes the version digest, and a recorded departure from DOM-014 (the dictionary maintained in the repository while there is no server). Ids 1–5, 10, 11 stay as they are.

## Rules for a large dictionary file (`app/src/main/assets/dictionaries.json`)
- `version` is the lowercase hex SHA-256 of the compact JSON of `DictionariesDto(version="", …)` (field order version, ingredients, tags; ingredient `id, labelRu, labelEn[, parentId]`; tag `id, labelRu, labelEn`) — checked by `ShippedSnapshotTest`; generate it, never type it.
- Both labels non-blank; ids unique; a `parentId` names a top-level entry (two levels).
- Ids are permanent: never renumber, reuse or drop.
- Labels are short base nominative forms; varieties are leaves under a generic parent.
- Divisibility and factors are added by increment C first.

## Noted, not asked for
- Exporting the whole book at once (serverless has no other backup; a reinstall loses the book — FLOW-051 (5) accepts it). A product question for the user.
