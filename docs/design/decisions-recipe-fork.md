# Recipe Fork — design decisions

This file is the source of truth for /team-lead and its developers on how recipe-fork looks and
reads, for decisions made from 2026-10-22 on. Mock-ups and proposals in `docs/design/` only
illustrate it; where they differ, this file wins.

The earlier identity and redesign decisions (direction «Своя версия», the mark, the redesign
fixes of 2026-10-15) are in `docs/brand/identity/decisions.md`. They still hold; this file does
not repeat or change them.

Colour roles are the Material 3 roles in `app/src/main/res/values/app_theme.xml` (see
`docs/theme.md`); type sizes are the theme's type roles where one matches, otherwise given in sp.
Touch targets follow `docs/architecture/ui-conventions.md` (at least 56 dp on the card).

## 2026-10-22 — Nutrition on the recipe card, T-293 (decided by the owner via /team-lead (F-2))

Proposal: `docs/design/proposals/recipe-fork-T-293.html` (option C, words table, states).
The owner's answer: layout «C — отдельный раздел»; wording and empty case «Как у дизайнера
(Recommended)», that is W1 and N1. Sample figures below (сырники, 2 servings) are the
proposal's illustration, not dictionary values.

1. **Nutrition is its own section on the card's View tab, between the ingredients panel and the «Шаги» heading; it does not appear on Cooking.**
   Order inside the section, top to bottom: heading; the per serving / whole dish switch; four
   tiles; the «Не учтены: …» line (only when a row was left out); the «Подробнее ›» text button.
   The section has no panel or background of its own; it sits in the card's content column
   (16 dp side padding, 8 dp gap between blocks, as the rest of the card). Opening the card does
   not move anything above it.
   - Heading: «Пищевая ценность, примерно» / "Nutrition, approximate"; titleMedium (16 sp, Medium),
     onSurface, the same style and top spacing as «Шаги». Semantics: heading.
   - No daily norms, percentages, goals, rings, bars, charts or colour judgement anywhere in the
     section or the sheet.
   *Why:* the owner's choice. The proposal's case for C: the numbers read best, and the section is
   easy to find, with its own heading for the screen reader. Its cost, as the proposal stated it:
   the heaviest of the three options (new section, switch, four tiles, a sheet); big numbers on
   tiles are the visual language of fitness trackers, which the app must not resemble; the switch
   is an extra state; at 200 % the section takes almost the whole screen; on the card the left-out
   rows show only by name, their reasons are in the sheet. No principle in the designer's list is
   broken. *Rejected:* A — a two-line row under the servings counter opening a sheet (pushes the
   ingredients down by 56–64 dp on every opening); B — the totals at the end of the ingredients
   panel, both columns side by side with no switch, breakdown expanding in place (the designer's
   recommendation).

2. **The switch is a two-segment single-choice control, «На порцию» | «Всё блюдо» / "Per serving" | "Whole dish", opening on «На порцию».**
   - Component: M3 `SingleChoiceSegmentedButtonRow`, full content width, two equal segments, as
     the editor's recipe-language control (`editor/TagsAndLanguage.kt`). Drawn height 40 dp with
     8 dp above and below; the touch target of each segment is at least 56 dp tall (card rule).
   - Selected segment: secondaryContainer fill, onSecondaryContainer text, check icon 18 dp before
     the label. Unselected: outline border, onSurface text. Labels labelLarge (14 sp, Medium).
   - At large font the segments grow in height (minimum 40 dp drawn) and a label wraps rather than
     truncates.
   - The segment label is «Всё блюдо» without the servings count; the count is on the servings
     counter just above the ingredients, in the screen-reader phrase (decision 3) and in the
     sheet's breakdown caption (decision 5).
   - State: the choice is not remembered between openings of a card and is not stored per recipe;
     every opening of a card starts on «На порцию». While the card stays open (rotation, switching
     to Cooking and back) the choice is kept. The proposal did not draw this; it is the simplest
     rule, the same one the proposal used for B's breakdown ("collapses when the card is left").
   - A recipe without a serving count (no servings control): no switch. In its place one label
     line «Всё блюдо» / "Whole dish", labelMedium (12 sp, Medium), onSurfaceVariant, and the tiles show
     the whole dish. Per serving is never invented. (The proposal drew this state for B and stated
     that A and C follow it in meaning.)
   *Why:* option C as drawn; one value set at a time keeps the tiles to four. *Rejected:* both
   sets at once without a switch (B); a per serving / whole dish table only in a sheet (A).

3. **Four tiles in one row, in this order: kcal, protein, fat, carbohydrates; each a large number over a small word, showing the switch's selection and following the servings counter and a fixed amount as they change.**
   - Tile: surfaceContainer fill, shape medium (12 dp corners), padding 10 dp top and bottom, 8 dp
     at the sides; 8 dp gap between tiles; equal widths. Tiles are display only: not tappable, no
     ripple, no focus stop of their own.
   - Value: 20 sp, line height 26 sp, Medium, onSurface (no theme type role matches; nearest is
     titleLarge 22 sp). Label: bodySmall (12 sp), onSurfaceVariant.
   - Contents (RU / EN): «680» over «ккал» / "680" over "kcal"; «37 г» over «белки» / "37 g" over
     "protein"; «37 г» over «жиры» / "37 g" over "fat"; «50 г» over «углеводы» / "50 g" over
     "carbs". The kcal tile carries no «≈» and no unit in the value; "approximate" is said by the
     heading. Full words on the tiles in both languages: the W1 abbreviations «Б · Ж · У» do not
     appear in option C.
   - Values: totals summed from exact values and rounded once, kcal to the nearest 10, grams to
     whole grams. Per serving does not change with the servings (only through the rounding of
     scaled amounts); the whole dish follows the counter and a fixed amount.
   - Large text and narrow screens: at 200 % (drawn at 320 dp) the tiles stand 2 × 2 (kcal,
     protein / fat, carbohydrates) and the section takes almost the whole screen height. Rule for
     the developer: four in a row whenever every value and label fits on one line; otherwise 2 × 2.
     Text in the tiles is in sp and grows with the font.
   - Screen reader: the four tiles are one node read as one phrase, with no live region (the
     servings counter already announces itself).
     RU per serving: «На порцию: примерно 680 килокалорий, белки 37 граммов, жиры 37 граммов,
     углеводы 50 граммов». RU whole dish: «Всё блюдо, 2 порции: примерно 1360 килокалорий, белки
     73 грамма, …»; with a fixed amount «Всё блюдо, примерно 3 порции: …»; with no serving count
     «Всё блюдо: …». Number agreement through plurals resources.
     EN: "Per serving: about 680 kilocalories, protein 37 grams, fat 37 grams, carbs 50 grams";
     "Whole dish, 2 servings: about 1360 kilocalories, …".
   *Why:* option C as drawn; the reader's phrase is from the proposal's common rules. *Rejected:*
   the compact «Б 37 · Ж 37 · У 50 г» line (A, B); W2 (full words everywhere, which in C changes
   nothing on the tiles).

4. **Under the tiles, «Не учтены: соль, сметана» names the left-out rows by name only; below it, the «Подробнее ›» text button opens the sheet.**
   - «Не учтены: …» / "Not counted: …": bodyMedium (14 sp), onSurfaceVariant, 8 dp above; the
     rows' ingredient names as the card writes them, comma-separated, wrapping as needed. Absent
     when every row was counted. Reasons are not on the card; they are in the sheet.
   - «Подробнее» / "Details": M3 text button, primary text, labelLarge (14 sp, Medium), trailing
     chevron 20 dp; touch target at least 56 dp tall. This is the section's only accent colour.
     Screen-reader name: its visible text, role button.
   *Why:* option C as drawn. *Rejected:* the full «Не учтены: соль (по вкусу), сметана (без
   количества)» line on the card (B); a count only, «не учтены: 2» (A).

5. **The «Подробнее» sheet holds the per-ingredient kcal for the whole dish, the left-out rows with their reasons, and one line on how it is counted.**
   - Component: M3 `ModalBottomSheet` as the card's other sheets: surfaceContainerLow container,
     shape extraLarge top corners (28 dp), drag handle 48 dp wide, 24 dp side and bottom padding,
     scrim; closed by Back, a swipe down or a tap on the scrim. Scrolls when it does not fit.
   - Title: «Пищевая ценность, примерно» / "Nutrition, approximate", titleLarge (22 sp), onSurface.
   - Section 1, caption «По ингредиентам, на всё блюдо (2 порции)» / "By ingredient, whole dish
     (2 servings)" — titleSmall (14 sp, Medium), onSurface; with a fixed amount «(≈3 порции)» /
     "(≈3 servings)"; with no serving count «По ингредиентам, на всё блюдо» / "By ingredient, whole
     dish". Then one row per counted ingredient in recipe order: the name at the start, «570 ккал»
     / "570 kcal" at the end (no wrap), bodyMedium (14 sp), onSurface, 4 dp vertical padding. Each
     row's kcal is rounded on its own (to the nearest 10, as drawn), so the rows may not add up to
     the total by a unit or two. The breakdown is always for the whole dish, whatever the switch
     shows.
   - Section 2, caption «Не учтены» / "Not counted" (titleSmall, 16 dp above). One row per left-out
     row in recipe order: the name at the start, the reason at the end, both onSurfaceVariant.
     Reasons: «по вкусу» / "to taste", «без количества» / "no amount", «нет в словаре» / "not in
     the dictionary", «нет данных» / "no data", «нет меры» / "no measure" (a row whose unit has no
     measure in its dictionary entry). Absent when every row was counted.
   - Last, always: «Посчитано по сырым продуктам: потери при готовке не учтены, вес штук — средний.»
     / "Counted from raw ingredients: cooking losses are ignored, piece weights are averages." —
     bodySmall (12 sp), onSurfaceVariant, 6 dp above.
   - The sheet as drawn carries no per serving / whole dish totals table; the totals are on the
     card behind the switch.
   - Screen reader: the title is announced when the sheet opens; each row reads as one item, name
     then value or reason.
   *Why:* option C as drawn, with the sheet title from the proposal's words table («заголовок блока
   / листа»), which the owner accepted with W1; the C frame's own titles («Пищевая ценность ≈» on
   the card, «Подробнее» on the sheet) give way to it. The fifth reason «нет меры» is the lead's
   behaviour rule (T-293). *Rejected:* A's sheet with a per serving / whole dish table; B's
   breakdown expanding in place on the card.

6. **A recipe where no row can be counted shows no section at all (N1).**
   No heading, no switch, no tiles, no text line; the steps follow the ingredients as today.
   *Why:* the owner accepted the designer's recommendation: the same rule as an empty description
   or a missing cover (FLOW-019). *Rejected:* N2, one muted line «Пищевую ценность не посчитать: ни
   одну строку нельзя учесть.»

7. **Both themes use the same roles; nothing in the section is coloured except the text roles, the switch's secondaryContainer selection and the button's primary.**
   Dark theme: identical layout, the dark values of the same roles. Not by colour alone: the
   selected segment also carries the check icon. Contrast pairs the section adds to
   `ui/ContrastTest`: onSurface and onSurfaceVariant on surfaceContainer (tiles),
   onSecondaryContainer on secondaryContainer (selected segment), primary on surface (button),
   onSurface and onSurfaceVariant on surfaceContainerLow (sheet), where not already listed.
   *Why:* option C as drawn in both themes; the proposal's common rule "no colour".
   *Rejected:* none proposed.

**Behaviour changes against the current build (for /team-lead):**
- New section on View only, between ingredients and steps; not on Cooking, not in print or PDF.
- The switch opens on «На порцию» every time a card opens; not stored per recipe or app-wide;
  kept while the card stays open.
- A recipe without a serving count: whole dish only, no switch.
- Totals summed exactly and rounded once (kcal to 10, grams whole); breakdown rows rounded on
  their own.
- Left-out reasons: по вкусу, без количества, нет в словаре, нет данных, нет меры.
- No section when nothing can be counted (N1).
- The «Подробнее» sheet has no per serving / whole dish totals table (see decision 5); T-293's
  criterion "«Подробнее» shows per-serving and whole-dish values" in `docs/increments/v0.40.md`
  does not match the decided design and needs the lead's reading.
