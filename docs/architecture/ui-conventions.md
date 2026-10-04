# UI conventions

The general rules live in the `android-m3-ui` skill (`~/.claude/skills/android-m3-ui/SKILL.md`): every developer and reviewer packet for a task that touches the interface says to load it, and the reviewer checks its checklist. This file adds what is particular to recipe-fork and wins where it differs. Paths are relative to `app/src/main/kotlin/app/recipefork/app/`.

## Values

| What | Here |
|---|---|
| Touch targets | ≥ 48 dp everywhere; **≥ 56 dp on the card** (cooking with busy hands, A11Y-007). |
| Haptics | Only through `ui/Haptics.kt`: `Haptic.CONFIRM` → `Confirm`, `Haptic.TICK` → `VirtualKey` (the owner's phone plays no `SegmentTick`). Tests use the `LocalHaptics` double. |
| Strings | `res/values/strings_<feature>.xml` and `res/values-ru/strings_<feature>.xml`, both in the same change. |
| Contrast | `ui/ContrastTest` lists every colour pair the interface draws; a new pair is added there. |
| Theme | Every colour role (light and dark), corner size and main type size in `res/values/app_theme.xml` (ADR 20261004), built by `ui/AppTheme.kt`; screens take colours only from `MaterialTheme.colorScheme`. |

## Patterns already in the code — reuse them

- **Top bars**: the book (`library/LibraryScreen.kt`), the card (`card/CardScreen.kt`, `BarAction` with a long-press tooltip) and the editor (`editor/EditorScreen.kt`) each have an M3 `TopAppBar`. The editor's back arrow leaves for the recipe; its Done saves and says "Saved" without closing.
- **Statements**: one snackbar host per screen. In the editor, `EditorStatements.say` (a statement; the newest replaces) and `EditorStatements.offer` (with an action, e.g. Undo). An open Undo is never replaced by "Saved".
- **Undo**: removing an ingredient row or a step is undoable (`RecipeEditor.removal`); a removed step's media is deleted only once the removal is final. `MediaSweep` clears files left by process death.
- **Keyboard**: `editor/RowFocus.kt` and the row chain in `IngredientsSection.kt` — Next walks name → quantity → unit menu → next row; the last typed field shows Done.
- **Choices**: tags are `InputChip`s, offers `SuggestionChip`s, the recipe language a `SingleChoiceSegmentedButtonRow` (`editor/TagsAndLanguage.kt`).
- **Field messages**: errors in `supportingText` with `isError`; the free-text hint (FLOW-022) is muted supporting text shown once the name is left.

## Hand-built controls still to replace (audit 31, v0.21)

`ActionText` (editor), `StepAction` and `FixAction` (card), `ViewTab` (book), the timer actions. Do not copy them into new screens; use M3 components.

## Exceptions to the skill

- Recipe deletion asks for confirmation (FLOW-053 says immediate and final); Undo instead is an open product question in `docs/increments/ux-roadmap.md`.
- The proportions warning on the card (SCALE-009) may still appear and disappear; the recalculation status line above the ingredients does not shift them (v0.19).
