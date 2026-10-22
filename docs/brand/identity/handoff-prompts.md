# Промпты для передачи айдентики в работу

Два промпта: сначала `/req-forge --sync` (видение), потом `/team-lead` (инкременты). Текст промптов —
по-английски, как принято для инструкций агентам; роли ответят по-русски.

---

## 1. `/req-forge --sync`

```text
--sync

Source of change: the owner's identity and interface decisions of 2026-10-15, recorded in
docs/brand/identity/decisions.md (read it in full first). These are owner decisions, already made — record
them, do not reopen them.

What to bring into the vision:

1. FLOW-065 (the version's card and the stated author of an imported parent). New rule: on the card of a
   version, the provenance is ONE line — «по рецепту «<parent title>» ›», tappable to the parent. The parent's
   stated author with the caveat «указан в файле, не проверен» is shown on the parent's own card. Exception:
   when the parent is not in the book, the version's card keeps the author line with the caveat, because there
   is nowhere else to see it. Check FLOW-064 and every other requirement that mentions the stated author or
   the caveat, and keep them consistent.

2. Go through the 13 interface decisions and the three fix decisions in decisions.md. For each one, check
   whether spec/ (especially 70-flows/, 65-forking.md, 35-cooking-with-busy-hands.md, 200-accessibility.md)
   states something different today. Change the spec only where it contradicts the decision or where the
   decision is a product rule rather than a visual detail. Candidates: the book opens on «Мои рецепты» first;
   the book entry of a version shows «по рецепту «…»»; «Текущий шаг · N из M» visible on screen in Cooking, not
   only to the screen reader; «Экспорт» also in the card's ⋮ menu; the floating «Своя версия» stays on every
   recipe (own and imported); cancelling a running timer is a separate quiet control under the timer, Undo stays.
   Pure look (colours, fonts, the mark, drop caps, margins) stays out of the spec — it lives in docs/brand/.

3. Naming: the product story is «someone else's recipe (e.g. from a blog, received as a file) → your own
   version → sometimes a friend's version». The element that leads to the direct parent is called
   «Происхождение» in design documents; FORK-004 (only the direct parent is shown) stands. If the glossary
   or goals use family/heirloom wording, align it. The product name is still OQ-016 — do not touch it.

Report at the end: which requirement IDs changed and how, which decisions you judged visual-only and left out,
and anything in decisions.md that conflicts with a requirement you could not resolve.
```

---

## 2. `/team-lead`

```text
Plan the visual redesign of the app as increments. Owner decisions are final and recorded — do not ask about
them again; anything new that is a real fork (scope, money, irreversible) comes to me as usual.

Read first, in this order:
- docs/brand/identity/decisions.md — every owner decision (direction «Своя версия», mark 2б, the 13 interface
  decisions, the three fixes). This is the source of truth where documents disagree.
- docs/brand/identity/redesign-1-recipe-fork.html — the redesigned interface: 37 screens, the inventory table
  (where each function lives now and after), architecture, components with sizes and the Compose composables
  they become, risks. Apply decisions.md over it.
- docs/brand/identity/redesign-1-fixes.html — corrections to the redesign: provenance as one line, the timer
  with a quiet «Отменить таймер» under it. Its row 3 (moving «Своя версия» to ⋮ on own recipes) was REJECTED —
  the floating «Своя версия» stays on every recipe, as in the build now.
- docs/brand/identity/port-1-rodoslovnaya.html, section «Как переносить в Compose» — ready theme_light_* /
  theme_dark_* lines for app/src/main/res/values/app_theme.xml, typography roles, shapes, font files.
- docs/brand/identity/mark-2.html, option 2б and its final block — the new mark: store icon 512, adaptive
  launcher icon (background #F6F0E4, mark 66 of 108 dp, monochrome layer), the separate thicker 16–20 px
  drawing, the «Recipe Fork» lockups.
- docs/brand/store/store-kit.html and docs/brand/store/export/ — the store graphics already exported.

Constraints:
- Function does not change: the redesign is presentation. Where the inventory marks something as a behaviour
  change, it is either covered by decisions.md or needs the spec first — the provenance line depends on the
  FLOW-065 change made by /req-forge --sync; if that is not in spec/ yet, schedule it last or treat it as blocked.
- Fonts Literata and Golos Text ship inside the APK (the app makes no network requests); subset them, measure
  the APK size change and report it.
- Update docs/architecture/ui-conventions.md with the new rules (one accent colour per role: olive for actions
  and «done», beet only for provenance, the graphite colour only for margin notes; drop caps; no Material You
  dynamic colour) and keep following the android-m3-ui skill for behaviour.
- v0.38 is in demo; start after it is accepted, or tell me if something in it should be folded in.

Suggested order (adjust if you see a better cut, keep each increment demoable):
1. Theme, type and icon: ColorScheme light/dark, Typography, Shapes, bundled fonts, the new adaptive launcher
   icon and monochrome icon (mark 2б). Every screen changes look at once; nothing moves.
2. The book: table-of-contents entries (60 dp miniature or drop cap), «Мои рецепты» first, collections as
   narrow spines, selection actions at the bottom with words, sort as a worded chip, empty states with the mark.
3. The card (View): page layout, quantity column, step numbers in the margin, the provenance line
   (after FLOW-065), «Экспорт» in ⋮, the floating «Своя версия» kept on every recipe.
4. Cooking: current step on the olive plate with «Текущий шаг · N из M» visible, round done marks, the running
   timer as one 56 dp control with the quiet «Отменить таймер» under it (also in the bottom timers bar), Undo kept.
5. Editor, shopping list, collections, My kitchen, settings: underline fields and the rest of the inventory.
6. Store: regenerate the Play screenshots from the real build (scripts/play-screenshots.sh) in the new look,
   compare with docs/brand/store/export/screenshots-*/, upload icon and screenshots together with the release
   that ships the new launcher icon; add one line each about the shopping list and My kitchen to
   docs/play/listing.md and move the graphics paths in listing.md and owner-checklist.md to docs/brand/store/export/.

Show me the increment plan before starting the first one.
```
