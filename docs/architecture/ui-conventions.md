# UI conventions

The general rules live in the `android-m3-ui` skill (`~/.claude/skills/android-m3-ui/SKILL.md`): every developer and reviewer packet for a task that touches the interface says to load it, and the reviewer checks its checklist. This file adds what is particular to recipe-fork and wins where it differs. Paths are relative to `app/src/main/kotlin/app/recipefork/app/`.

## Values

| What | Here |
|---|---|
| Touch targets | ≥ 48 dp everywhere; **≥ 56 dp on the card** (cooking with busy hands, A11Y-007). |
| Haptics | Only through `ui/Haptics.kt`: `Haptic.CONFIRM` → an 80 ms one-shot, `Haptic.TICK` → a 50 ms one-shot, at the default amplitude, played on the `Vibrator` with touch usage (the view's `performHapticFeedback` was silent on the owner's Samsung, and its predefined click and tick effects were not felt there — v0.34, T-245). From API 33 the system's touch-feedback switch silences them; below 33 the app reads `HAPTIC_FEEDBACK_ENABLED` itself. Tests use the `LocalHaptics` double. |
| Strings | `res/values/strings_<feature>.xml` and `res/values-ru/strings_<feature>.xml`, both in the same change. |
| Contrast | `ui/ContrastTest` lists every colour pair the interface draws; a new pair is added there. |
| Theme | Every colour role (light and dark), corner size and main type size in `res/values/app_theme.xml` (ADR 20261004), built by `ui/AppTheme.kt`; screens take colours only from `MaterialTheme.colorScheme`. |

## Patterns already in the code — reuse them

- **Top bars**: the book (`library/LibraryScreen.kt`), the card (`card/CardScreen.kt`, `BarAction` with a long-press tooltip) and the editor (`editor/EditorScreen.kt`) each have an M3 `TopAppBar`. The editor's bar shows the recipe's title only once the title field has scrolled away (v0.38); Back and Done both wait until every change is written and then leave for the recipe — if a write fails the editor stays and says "Couldn't save the recipe · Retry" (a second Back leaves anyway); the checklist and the fork note are icon-only actions with tooltips. Settings (`settings/SettingsScreen.kt`) is the reference for a screen of `ListItem`s showing current values.
- **Statements**: one snackbar host per screen. In the editor, `EditorStatements.say` (a statement; the newest replaces) and `EditorStatements.offer` (with an action, e.g. Undo). An open Undo ends (becomes final) when the editor is left by Back or Done. While a sheet is open (it calls `CoversStatements()`), statements wait; an Undo showing when a sheet rises is offered again once the sheet goes. A sheet ignores further actions once one has started, and an action finds its row by key when it runs.
- **Undo**: removing an ingredient row or a step is undoable (`RecipeEditor.removal`); a removed step's media is deleted only once the removal is final. `MediaSweep` clears files left by process death.
- **Keyboard**: `editor/RowFocus.kt` and the chain in `editor/IngredientSheet.kt` — an ingredient is edited in its own bottom sheet; picking a dictionary offer goes on to the amount; Next walks name → amount → unit menu → note inside the sheet, and the note's Done closes it. "Add ingredient" and Next from the servings or a heading open the target row's sheet with its name focused; a just-added item's sheet is titled "New …" and shows its actions once it holds something.
- **Rows as text**: the editor's ingredient rows read as the card writes them (`card` line formatting reused) and open a modal bottom sheet (`ModalBottomSheet`, `skipPartiallyExpanded`, closed by hide() then the state) holding the row's fields; edits apply as typed, so Done, a swipe or Back all keep them.
- **Choices**: a recipe's tags are chip-shaped `Surface`s whose only control is a trailing M3 `IconButton` × (removal from the × only, v0.38; an `InputChip` makes its whole surface tappable), offers `SuggestionChip`s, the recipe language a `SingleChoiceSegmentedButtonRow` (`editor/TagsAndLanguage.kt`).
- **Field messages**: errors in `supportingText` with `isError`; the free-text hint (FLOW-022) is muted supporting text shown once the name is left.

## Hand-built controls (audit 31)

All replaced in v0.21 (`ActionText` → `editor/EditorButton`, the card's `StepAction`/`FixAction` → M3 buttons). Removals in menus and sheets take the error colour (`BlockAction`/`SourceItem` tinted). Do not hand-build a control; use M3 components.

One exception since v0.31 (T-224): the servings − and + (`card/CardScreen.kt`, `StepAction`) are each a 40 dp filled-tonal drawing inside a 56 dp `clickable` target, the drawing set against the seam so the two halves still read as one connected group. No M3 icon button gives an off-centre drawing in a target that is the node's own bounds and does not overlap its neighbour (A11Y-003). It must keep what `FilledTonalIconButton` gave: `Role.Button`, the name and the click label, disabled semantics and the disabled tonal colours (its target unchanged when disabled), a bounded ripple in the content colour clipped to the drawing's shape, focus and press states on the drawing, the theme's filled-tonal colours. Any other hand-built control still needs its own line here.

## Exceptions to the skill

- Recipe deletion asks nothing: the book offers Undo while its notice shows (FLOW-053 as rewritten 2026-10-04; v0.23, T-172).
- The proportions warning on the card (SCALE-009) may still appear and disappear; the recalculation status line above the ingredients does not shift them (v0.19).
