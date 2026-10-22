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

## 2026-10-22 — Finished dish weight in the editor, nutrition per 100 g on the card, T-319 / T-320 (decided by the owner via /team-lead (F-1))

Proposal: `docs/design/proposals/recipe-fork-T-319.html` (options 1А–1В, 2А–2В, 3А–3В, the
common rules and the words table). The owner's answers, verbatim: «1А, но текст подсказки
странный, давай переформулируем»; «2А - подписи: 100г, порция, всё блюдо - давай попробуем
так»; «3Б — подсказка в «Подробнее» (Recommended)». The in-app wording the owner left to the
team is the lead's reading (L2), recorded as given. Sample figures (сырники, 2 servings, 560 g)
are the proposal's illustration; the weight 560 g is an assumption. This entry amends the
2026-10-22 nutrition entry (T-293) where named below; everything else there still holds.

1. **The editor gets an optional field «Вес готового блюда» directly under «Порций» (1А), with the hint reworded.**
   - Placement: the editor form, right after the «Порций» field and its supporting text, before
     «Посуда и техника» (or «Ингредиенты» when the recipe has no equipment). Same component and
     spacing as «Порций» (`ServingCount`, `editor/IngredientsSection.kt`): M3 `OutlinedTextField`,
     full width, single line, 4 dp between field and supporting text, the form's usual 16 dp gap
     to the next block.
   - Label: «Вес готового блюда» / "Finished dish weight". Suffix «г» / "g" inside the field,
     onSurfaceVariant, shown while the field is focused or filled (the field's own suffix slot).
   - Supporting text (the hint), bodySmall (12 sp), onSurfaceVariant, always shown:
     with a serving count «Сколько весит всё блюдо на %s — для КБЖУ на 100 г» /
     "What the whole dish for %s weighs — for nutrition per 100 g", where %s is the serving
     phrase from a new plurals resource: RU one «%d порцию», few «%d порции», many «%d порций»,
     other «%d порции», with «на» in the string («на 1 порцию», «на 2 порции», «на 5 порций»);
     EN one "%d serving", other "%d servings". Without a serving count (the «Порций» field empty):
     «Сколько весит всё блюдо — для КБЖУ на 100 г» / "What the whole dish weighs — for nutrition
     per 100 g". The hint follows the «Порций» field as it is typed. Join «100» and «г» / "g"
     with a no-break space (as `card_nutrition_grams` does).
   - Fit (measured with Golos Text 12 sp, the field's 16 dp inner padding): RU hint with a count
     is about 348 dp wide — 2 lines at 360 and 320 dp, 3 lines at 320 dp / 200 %, as the proposal
     drew its own hint; nothing was shortened. Without a count: 1 line at 360 dp, 2 at 320 dp, 3 at
     200 %. EN with a count: 2 lines at 360 and 320 dp, 4 lines at 320 dp / 200 %. The field and
     its text grow in height; nothing is clipped.
   - Input: whole grams, 1 or more; optional; no upper bound (none was drawn). Keyboard
     `KeyboardType.Number`. Empty means "no weight" and is not an error. A refused entry (0, or
     any character that is not a digit) behaves as «Порций» does: the character does not enter,
     the field is drawn in the error colour (`isError`), the hint stays as the supporting text; no
     separate error message.
   - Keyboard Next order: «Порций» → «Вес готового блюда» → the first item of equipment or, with
     none, the ingredient list's first item (what «Порций» led to before); with neither, Done
     closes the keyboard.
   - A recipe that cannot be edited (from a file): as «Порций» read-only — label line
     «Вес готового блюда» / "Finished dish weight" (labelMedium) over the value «560 г» / "560 g"
     or «Не указано» / "Not given" (bodyLarge), one node for the screen reader.
   - Screen reader: the text field's own label, value, suffix and supporting text; nothing added.
   *Why:* the owner's choice of 1А; the proposal's case: weight and servings are one pair («560 г
   на 2 порции»), side by side the link needs no words, no new pattern, an empty field says
   nothing. Its cost: the form above the ingredients is one field longer (~76 dp at 1×) for
   everyone, including those who never weigh. The hint is the lead's rewording after the owner
   called the proposal's hint odd. *Rejected:* the proposal's hint «Необязательно. Всё блюдо на
   2 порции, целое число граммов»; 1Б — the field at the end of the form after the steps, under
   «Готовое блюдо»; 1В — the field behind a text button «＋ Указать вес готового блюда».

2. **The card's switch takes up to three segments, «100 г» | «Порция» | «Всё блюдо» / "100 g" | "Serving" | "Whole dish", in that order, and still opens on «Порция» (2А, the owner's labels).**
   - Component, sizes, colours, check icon, wrapping rule and state keeping: as the T-293 entry,
     decision 2 (M3 `SingleChoiceSegmentedButtonRow`, full content width, equal segments, drawn
     height at least 40 dp, touch target at least 56 dp, selected = secondaryContainer + check
     icon 18 dp, labels labelLarge, a label wraps rather than truncates).
   - States, by what the recipe has:
     | Serving count | Weight | Switch | Opens on |
     |---|---|---|---|
     | yes | yes | «100 г \| Порция \| Всё блюдо» | «Порция» |
     | yes | no | «Порция \| Всё блюдо» | «Порция» |
     | no | yes | «100 г \| Всё блюдо» | «Всё блюдо» |
     | no | no | no switch; the label line «Всё блюдо» as decision 2 | — |
     The third row's opening segment is the simplest rule (there is no «Порция» to open on; it is
     what such a recipe shows today); neither the owner nor the lead stated it. If the segment
     shown is no longer offered (the weight was removed in the editor while the card stayed
     open), the switch returns to the opening segment of its new state.
   - With «100 г» selected the four tiles show per 100 g of the finished dish: the whole dish's
     exact values × 100 ÷ (the entered weight × the card's current multiplier), rounded as the
     other totals (kcal to the nearest 10, grams whole). It does not change with the servings
     counter or a fixed amount, except through rounding. Sample: 210 ккал, белки 15 г, жиры 11 г,
     углеводы 12 г. Tile layout, styles and the 4-in-a-row / 2 × 2 rule: decision 3, unchanged.
   - Screen reader, the tiles' one phrase with «100 г» selected: «На 100 граммов готового блюда:
     примерно 210 килокалорий, белки 15 граммов, жиры 11 граммов, углеводы 12 граммов» /
     "Per 100 grams of the finished dish: about 210 kilocalories, protein 15 grams, fat 11 grams,
     carbs 12 grams" (the existing `card_nutrition_spoken` pattern). The per serving phrase stays
     «На порцию: …» / "Per serving: …" (decision 3); the segment label and the spoken prefix
     become separate strings. The spoken prefix spells out «граммов» / "grams" so that «100 г» is
     not read as «сто г»; visible texts keep «100 г» / "100 g" (the lead's wording, L2, 2026-10-22;
     amends this entry's first wording «На 100 г готового блюда» / "Per 100 g of the finished
     dish").
   - Fit of the labels with the selected segment's check (measured with Golos Text Medium 14 sp,
     12 dp side padding, 18 dp icon + 8 dp gap; segment 109 dp at 360 dp, 96 dp at 320 dp):
     - 360 dp, 100 %: every label on one line, except «Всё блюдо» / "Whole dish" when selected,
       which wraps to two lines at the space.
     - 320 dp, 100 %: «Порция» / "Serving" when selected needs 79 / 78 dp and has 72 dp — a
       single word that does not fit is broken inside the word («Порц|ия»), and this is the state
       every card opens in. «Всё блюдо» wraps at the space, selected or not.
     - 200 % (360 and 320 dp): three segments break words inside the word in nearly every state
       («Порц|ия», «блю|до», «10|0 г»). The two-segment «Порция | Всё блюдо» at 320 dp / 200 % breaks
       «Порци|я» when selected; at 360 dp / 200 % it wraps at spaces only. «100 г | Всё блюдо»
       wraps at spaces only, at every width and scale.
     Breaking inside a word is not covered by decision 2's wrapping rule. How the switch behaves
     where a label cannot fit whole is not decided here; it was returned to the lead as a fork
     (T-319 record report). Until it is answered, the rest of this decision can be built.
   - Where a label cannot fit whole (decided by the owner via /team-lead (F-2), 2026-10-22):
     the label breaks inside the word, as Compose does with the label's text; the segments stay
     equal and the selected segment keeps its check icon. Nothing is added to the switch for this
     case: no shorter label, no other control at large text, no change of segment widths. The
     states measured above are the expected result (the 320 dp / 100 % default «Порц|ия»; at
     200 % «Порц|ия», «блю|до», «10|0 г»). The owner's answer, verbatim: question «Переключатель
     «100 г | Порция | Всё блюдо» не влезает на узком экране и при крупном шрифте. Как быть?» →
     «Оставить перенос в слове». *Why:* the owner's choice. Its cost: on a 320 dp phone the
     state every card opens in shows «Порция» broken across two lines, and at 200 % most labels
     break inside a word, which reads worse than a break at a space. No principle in the
     designer's list is broken: nothing is truncated or clipped, the label is whole, only broken.
     *Rejected* (the options as `docs/increments/v0.44.md`, F-2, lists them; the designer had no
     proposal file for this fork): «Equal segments with labels by width + a radio list at large
     text / width by label only / a list always».
   *Why:* the owner's choice of 2А with their own labels; the proposal's case for 2А: one visual
   language, large numbers for all three sets. Its cost, as the proposal stated it: a third state
   of the switch, and labels that wrap at 360 dp and always at 320 dp; with the owner's shorter
   labels the 360 dp default now fits, the 320 dp default and 200 % do not (above). No principle
   in the designer's list is broken; the open fit is a defect to resolve, not a choice recorded
   against a principle (resolved 2026-10-22 by the owner, F-2: the break inside the word stays,
   above). *Rejected:* 2Б — a line «На 100 г готового блюда» with the values under
   the tiles, independent of the switch (the designer's recommendation); 2В — per 100 g only in
   the «Подробнее» sheet.

3. **The «Подробнее» sheet gets one more line after «Посчитано по сырым продуктам…»: the weight per 100 g was counted from, or, without a weight, where to give it (3Б).**
   - Same style as the line above it: bodySmall (12 sp), onSurfaceVariant, 6 dp above. Not a
     button, not a link.
   - With a weight: «На 100 г — по весу готового блюда из рецепта: 560 г на 2 порции.» /
     "Per 100 g is by the finished weight the recipe gives: 560 g for 2 servings." The weight and
     the servings are those on the counter now (weight × multiplier, whole grams); with a fixed
     amount «…: 560 г на ≈3 порции.» / "…: 560 g for ≈3 servings."; without a serving count
     «…: 560 г.» / "…: 560 g.". Serving phrase: the plurals of decision 1 (and the existing
     fraction forms for fractional counts).
   - Without a weight, on a recipe the reader can edit: «Чтобы видеть «на 100 г», укажите в
     рецепте вес готового блюда.» / "To see per 100 g, give the finished dish weight in the
     recipe." On a recipe that cannot be edited (from a file): no line.
   - Nothing about per 100 g appears on the card while there is no weight: no segment, no dash, no
     text. A recipe where nothing can be counted still shows no section (decision 6); a weight
     does not make it appear.
   *Why:* the owner's choice of 3Б; the proposal's case: one line where counting is already
   explained; the card does not urge the reader to weigh. *Rejected:* 3А — nothing anywhere;
   3В — the line «Взвесьте готовое блюдо и укажите вес в рецепте — появится «на 100 г».» on the
   card under «Не учтены» (a quiet but constant prompt on every own recipe without a weight).

**Strings (RU / EN):**
| Where | RU | EN |
|---|---|---|
| Editor field label | Вес готового блюда | Finished dish weight |
| Field suffix | г | g |
| Hint, with a count | Сколько весит всё блюдо на %s — для КБЖУ на 100 г | What the whole dish for %s weighs — for nutrition per 100 g |
| Serving phrase (plurals) | %d порцию / %d порции / %d порций / %d порции | %d serving / %d servings |
| Hint, no count | Сколько весит всё блюдо — для КБЖУ на 100 г | What the whole dish weighs — for nutrition per 100 g |
| Read-only, none | Не указано | Not given |
| Segment | 100 г | 100 g |
| Segment (was «На порцию») | Порция | Serving |
| Segment | Всё блюдо | Whole dish |
| Spoken prefix, per 100 g | На 100 граммов готового блюда | Per 100 grams of the finished dish |
| Sheet, weight line | На 100 г — по весу готового блюда из рецепта: %s. | Per 100 g is by the finished weight the recipe gives: %s. |
| Sheet, no weight | Чтобы видеть «на 100 г», укажите в рецепте вес готового блюда. | To see per 100 g, give the finished dish weight in the recipe. |

**Amends the 2026-10-22 nutrition entry (T-293):** decision 2 — the switch's labels «На порцию |
Всё блюдо» / "Per serving | Whole dish" become «Порция | Всё блюдо» / "Serving | Whole dish", the
switch has two or three segments by the table above, it opens on «Порция» (was «На порцию»), and
a recipe without a serving count but with a weight gets «100 г | Всё блюдо» instead of the label
line; decision 3 — a third value set (per 100 g) and its spoken phrase; decision 5 — «Посчитано
по сырым продуктам…» is no longer the last line of the sheet.

**Behaviour changes against the current build (for /team-lead):**
- New optional recipe field: finished dish weight, whole grams ≥ 1, for the recipe's own serving
  count; scaled with the servings like any amount. Model, recipe file format, backup and whether a
  fork carries it are the lead's and /req-forge's (`spec/` says nothing about it).
- The editor's «Порций» Next now goes to the weight field.
- The card computes per 100 g from the weight; the switch gains the «100 г» segment when a weight
  is given; the per serving segment is relabelled «Порция».
- The sheet gains one line (weight used, or where to give it on an editable recipe).
- Ingredients changed after weighing: the weight stays as entered; the app does not warn.
- Open: the switch where a label cannot fit whole (320 dp default state, 200 %) — fork to the lead.
  Closed 2026-10-22 (F-2): the label breaks inside the word as Compose does; equal segments and
  the check icon stay; no behaviour change beyond decision 2.
