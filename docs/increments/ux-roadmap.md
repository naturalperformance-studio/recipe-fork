# UX roadmap — the interface polish series (F2…)

From a read-only audit of `develop` @ dd8d900 against current Material 3 / Android guidance, run on 2026-10-04 because the owner asked to work on UX before the theme (roadmap F1). Each increment below becomes a `docs/increments/v<next>.md` when it starts; this file then marks it. Findings are numbered as in the audit; paths are relative to `app/src/main/kotlin/app/recipefork/app/`.

The root cause behind many findings: no Scaffold, TopAppBar, FAB or M3 buttons/chips in the bars, no haptics, no motion; library, card and editor are not navigation destinations (`LibraryScreen` swaps them by early return, one NavHost route); most controls are hand-built text links (`ActionText`, `BarAction`, `StepAction`, `FixAction`, `TimerAction`, `ViewTab`, `UnitField`).

## The owner's own complaints (2026-10-04)
- Cannot swipe between the imported ("Marked") and own ("Written here") recipes.
- The recipe's cover should show large in the book — the entry redone as a card, with its tags as well as its title.

## U1 — The book and the card feel like a real Android app → v0.16 (started 2026-10-04)
1. Status-bar icons probably invisible: platform `Theme.Material.Light.NoActionBar`, no `enableEdgeToEdge()` (`MainActivity.kt:62-66`, manifest). S.
2. The book list jumps to the top after closing a card: list state inside `OwnList` is dropped by the early return (`library/LibraryScreen.kt:291`). S.
3. No predictive back, no transitions: single-route NavHost, `BackHandler`s in card and editor. M.
4. The book's header takes ~270 dp before the first recipe: title row, tabs, full-width "New recipe", search, filters (`library/LibraryScreen.kt:206-233, 322-332`). M.
5. Primary actions out of thumb reach: "New recipe" at the top, Edit/Fork top right, the editor's Done top left. S–M.
6. The card's top bar is three blue words, not an app bar (`card/CardScreen.kt:176-207`). S–M.
23. Blank loading states on book and card (FLOW-043 asks to show loading). S.
27. List entries are hand-built rows, no `animateItem()`; "Draft" louder than the title's secondary text. S.

## U2 — Cooking with busy hands (the card) → v0.17 (shipped 2026-10-04; 9 left out, FLOW-014)
7. Running timers vanish once scrolled past (`timer/StepTimer.kt:232-296`) → a docked bar of running timers. M.
8. Timer controls look like links → tonal buttons with an icon, 56 dp. S.
9. The screen may sleep during a long untouched timer (`card/KeepScreenAwake.kt`) → stay on while a timer of the recipe runs (FLOW-014 sync). S.
10. No haptics anywhere → Confirm on timer actions, SegmentTick on servings, reorder drag. S.
20. Fixing an amount needs a second tap; hand-drawn unit boxes (`card/FixAmount.kt`). S.
29. Step text hierarchy: larger steps, number badges, spacing; reading-size control optional. S/M; type scale waits on the theme.
30. The ingredient list has no grouping or aligned quantities. S.

## U3 — Writing a recipe fast (the editor) → v0.18 (planned 2026-10-04; 17 left for later)
11. No sentence capitalisation in title, steps, description, ingredient and timer names. S.
12. IME Done closes the keyboard after every field → Next chaining name → quantity → unit → next row; new rows focused. M.
13. Removing an ingredient or step is instant, no undo → snackbar Undo; media deleted after it goes. M.
14. Tags are text rows with "Remove" → InputChips, offers as suggestion chips. S.
15. The recipe-language toggle is unclear → segmented [RU | EN]. S.
16. Editor has no top app bar; saving never acknowledged. S.
17. Permanent fork note and references hint push the form down. S.
19. Errors drawn outside fields → `supportingText` with `isError`. S.
34. The free-text warning shows mid-typing and reads as an error. S.

## U4 — Media, files and filters → v0.19 (planned 2026-10-04, with 17 and the card's recalculation legend)
18. Photo actions are three text links; inconsistent with step media → placeholder + bottom sheet (Camera / Gallery). S–M.
21. Filter choosers are AlertDialogs with fields and lists → one Filters bottom sheet. M.
22. Filtering takes two hops → a quick FilterChip row under search. M.
24. Bare empty states → icon, headline, one action. S.
25. Import/export give no progress; success is a blocking dialog → progress, snackbar with "Open", scroll to the imported entry. S–M.
26. Destructive confirm not marked; delete only from the list's ⋮. S.
33. The crop screen has no Cancel after the camera. S.

## U5 — Settings and the theme follow-up (with or after F1) → 28 in v0.20 with F1; 31, 32 and the theme-dependent parts in v0.21 (planned 2026-10-04)
28. Book settings hidden in the title's ⋮ → a Settings screen with current values (language, usual servings, theme, drag side, storage). M.
31. Replace hand-built controls with M3 components so every colour and shape comes from the theme. M.
32. In-screen motion (AnimatedVisibility / AnimatedContent), respecting the animation scale. S.

Theme-dependent and best left for after F1: 26 (error tone), 29 (type scale), 30 (tonal surface), 31, the colour parts of 7 and 8, the dark-mode part of 1.

## Product questions the audit raised (not UX fixes; for the owner, one at a time when they become relevant)
- *(→ v0.23)* Undo instead of a confirmation for deleting a recipe (FLOW-053 says immediate and final).
- A step-by-step cooking mode (rejected by ADR 20260921; a lighter "you are here" highlight fits the ADR).
- Which view the book opens on (FLOW-034: "Marked", which in serverless holds only imports).
- A blank new recipe left untouched stays as an "Untitled" draft.
- A fork carries the parent's title unchanged, so two identical titles sit in the book.
- *(→ v0.23, kept on the device until cleared)* Ticking off ingredients / steps while cooking (conflicts with "nothing records progress" unless kept in memory).
- Tab names "Marked" / "Written here" in a serverless build.

## Audit of 2026-10-10 (v0.29 build) — not taken into v0.30
From two fresh audits (v0.30's file lists what was taken). Still open from the 2026-10-04 audit: 4, 5 (editor), 19, 26/31 in part.
- The book's header: only the top bar scrolls away; tabs, search and chips stay (~250 dp at 1×, ~58% of a 320×640 screen at 2×) — let search and chips collapse with the list. M.
- The editor's Done top-right and "Add ingredient" / "Add step" at the end of long lists — out of thumb reach (2026-10-04 item 5). M.
- Equipment items are inline fields with a trash icon and ⋮ — a third editing pattern beside the ingredient and step sheets; at 2× its hint wraps a word per line. M.
- Rows read as tappable only by their outline and drag strip — a trailing edit cue; hide the handle when a list has one row. S.
- The free-text mark repeats under every free-text row — one line at the list head. S.
- Offers: "chicken wings" above "chicken" for "chi"; hand-built offer rows — exact/shorter first; ListItem in a surface. S.
- A frozen recipe: an empty "Tags" heading; its message gives no next step (e.g. fork). S.
- A tap anywhere on a tag chip removes it, with no Undo — remove from the × only, or offer Undo. S.
- An unnamed timer's row repeats the whole step ("Roast the chicken until golden. · 40 min") — shorten the derived name on the row. S.
- The crop uses hard-coded white and black and has no non-gesture way to adjust. S.
- Limit marks are separate rows under fields, not supportingText (2026-10-04 item 19). S.
- On Cooking an ingredient line has two targets (the amount fixes, the rest marks done) — one target per line. S.
- The book's Undo snackbar stays after switching tabs — end it on a tab change. S.
- Open product questions: which view the book opens on.


## Audit of 2026-10-13 (v0.37 build) — not taken into v0.38
From two fresh audits (v0.38's file lists what was taken). Still open from earlier audits: the editor's Done and Add reach (2026-10-04 item 5), equipment rows, the free-text mark per row, the crop, Cooking's two touch areas on an ingredient line.
- The editor's Done top-right and every Add at the end of its list — a bottom bar or a section FAB. M.
- Equipment items: inline fields with a trash icon and ⋮, cut at 2× ("Big mixing b") — rows opening a small sheet like the group sheet. M.
- The crop: only drag and pinch, hard-coded white/black — zoom −/+ and Reset, theme tokens. M.
- The checklist: a long scrolling AlertDialog cut at the bottom — a full-height sheet. S.
- Import/export failure dialogs: no title, only "OK"; "made by a newer version" offers no way to update. S.
- The unit menu: the current unit unmarked, bare abbreviations ("l" reads like "1"). S.
- Settings: one flat list with only "Backup" headed; the editor-only drag side among general settings — section headers. S.
- My kitchen: an open group's items align with its checkbox — indent them. S.
- The editor's snackbar is full-width, flush to the edges; Settings' floats with margins. S.
- Sheet actions are plain text rows; the source sheet's have icons — one sheet style. S.
- The language "Clear" under RU/EN reads as clearing the title; nothing on the cover shows a tap changes it. S.
- The free-text mark repeats under every free-text row — one line at the list head. S.
- On Cooking an ingredient line has two touch areas (the amount fixes, the rest marks done). S.
- Cooking at 2× (RU): the status line reserves ~2 blank lines for the "recalculated" legend. S.
- A collection's screen: entries show no tags; no primary action outside the ⋮; a non-lazy Column. S.
- Menus at 2×: the selection ⋮ mixes an icon item among plain ones (→ v0.38 T-269 if it fits).
