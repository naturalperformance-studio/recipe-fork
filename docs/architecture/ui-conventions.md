# UI conventions

The general rules live in the `android-m3-ui` skill (`~/.claude/skills/android-m3-ui/SKILL.md`): every developer and reviewer packet for a task that touches the interface says to load it, and the reviewer checks its checklist. This file adds what is particular to recipe-fork and wins where it differs. Paths are relative to `app/src/main/kotlin/app/recipefork/app/`.

## Values

| What | Here |
|---|---|
| Touch targets | ≥ 48 dp everywhere; **≥ 56 dp on the card** (cooking with busy hands, A11Y-007). |
| Haptics | Only through `ui/Haptics.kt`: `Haptic.CONFIRM` → `EFFECT_CLICK`, `Haptic.TICK` → `EFFECT_TICK`, played on the `Vibrator` with touch usage (the view's `performHapticFeedback` was silent on the owner's Samsung). From API 33 the system's touch-feedback switch silences them; below 33 the app reads `HAPTIC_FEEDBACK_ENABLED` itself. Tests use the `LocalHaptics` double. |
| Strings | `res/values/strings_<feature>.xml` and `res/values-ru/strings_<feature>.xml`, both in the same change. |
| Contrast | `ui/ContrastTest` lists every colour pair the interface draws; a new pair is added there. |
| Theme | Every colour role (light and dark), corner size and main type size in `res/values/app_theme.xml` (ADR 20261004), built by `ui/AppTheme.kt`; screens take colours only from `MaterialTheme.colorScheme`. |

## Patterns already in the code — reuse them

- **Top bars**: the book (`library/LibraryScreen.kt`), the card (`card/CardScreen.kt`, `BarAction` with a long-press tooltip) and the editor (`editor/EditorScreen.kt`) each have an M3 `TopAppBar`. The editor's bar shows the recipe's title; Back and Done both wait until every change is written and then leave for the recipe — if a write fails the editor stays and says "Couldn't save the recipe · Retry" (a second Back leaves anyway); the checklist and the fork note are icon-only actions with tooltips. Settings (`settings/SettingsScreen.kt`) is the reference for a screen of `ListItem`s showing current values.
- **Statements**: one snackbar host per screen. In the editor, `EditorStatements.say` (a statement; the newest replaces) and `EditorStatements.offer` (with an action, e.g. Undo). An open Undo ends (becomes final) when the editor is left by Back or Done.
- **Undo**: removing an ingredient row or a step is undoable (`RecipeEditor.removal`); a removed step's media is deleted only once the removal is final. `MediaSweep` clears files left by process death.
- **Keyboard**: `editor/RowFocus.kt` and the row chain in `IngredientsSection.kt` — Next walks name → note → quantity → unit menu → next row; the last typed field shows Done.
- **Choices**: tags are `InputChip`s, offers `SuggestionChip`s, the recipe language a `SingleChoiceSegmentedButtonRow` (`editor/TagsAndLanguage.kt`).
- **Field messages**: errors in `supportingText` with `isError`; the free-text hint (FLOW-022) is muted supporting text shown once the name is left.

## Hand-built controls (audit 31)

All replaced in v0.21 (`ActionText` → `editor/EditorButton`, the card's `StepAction`/`FixAction` → M3 buttons). Removals in menus and sheets take the error colour (`BlockAction`/`SourceItem` tinted). Do not hand-build a control; use M3 components.

## Exceptions to the skill

- Recipe deletion asks nothing: the book offers Undo while its notice shows (FLOW-053 as rewritten 2026-10-04; v0.23, T-172).
- The proportions warning on the card (SCALE-009) may still appear and disappear; the recalculation status line above the ingredients does not shift them (v0.19).
