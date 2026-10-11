---
phase: market
updated: 2026-10-10
sources:
  - analytics/market/competitors.md
  - analytics/reports/20261010-paid-features.md
---

# Hypotheses

### H-001 — A whole-book backup is a must-have before release
**Status:** adopted
**Claim:** Without a way to save and restore the whole recipe book, a share of recipe-fork's Play reviews will report recipes lost on a new or reset phone, as they do for the offline recipe keepers in the set.
**Evidence:**
- 54 of the 1,000 most recent reviews of My Recipe Box (RecetteTek) mention backup, restore or lost recipes — play.google.com/store/apps/details?id=fr.recettetek, checked 2026-10-10
- 20 of 1,000 at Recipe Keeper, 19 at COOKmate, 11 at Paprika mention the same — their Play listings, checked 2026-10-10
- Every offline keeper in the set offers a whole-collection backup or sync (RecetteTek free; Paprika and Pestle sell it as sync) — analytics/market/competitors.md
**Rests on:** GOAL-001; FLOW-061 / FLOW-062 (serverless mode keeps everything on the device)
**Requirements:** FLOW-063 exports one recipe; the whole book needs new requirements in flows (a book file, its restore, what a restore does to a book already holding recipes)
**Measurement:** share of recipe-fork's Play reviews mentioning lost recipes or backup, via the reviews export, over the first 3 months after release — settled below 1% (against 5.4% at RecetteTek)

**Recommendation:** adopt — the failure it prevents is the most common complaint found and the one that loses a user's data for good; it extends what FLOW-063 already does, so it is buildable within the vision.
**Ruling:** adopted
**Date:** 2026-10-10
**User's words:** chose «Принять (рекомендую)» in the batch put at session close.

### H-002 — A shopping list built from recipes, scaled and merged
**Status:** adopted
**Claim:** A shopping list made from one or several recipes at their chosen servings, with the same ingredient merged into one line, is expected by recipe-fork's users; without it they keep a second app.
**Evidence:**
- 8 of the 9 Play apps in the set build a shopping list from recipes, free in every one — analytics/market/competitors.md
- Reviews mentioning a shopping or grocery list: Paprika 110, Recipe Keeper 75, CookBook 66 (of 647), RecetteTek 42 — their Play listings, checked 2026-10-10
- Complaints where lines do not merge: RecetteTek (3 reviews asking for one list across recipes), COOKmate (300+ items merged by hand after a week's plan), SuperCook ("1 onion" six times) — their Play listings, checked 2026-10-10
- Mealie discussions "Auto-generate Shopping List from Meal Plan" 41 upvotes (github.com/mealie-recipes/mealie/discussions/1899), "Generate the shopping list of all recipes planned" 17 (…/discussions/5240), checked 2026-10-10
**Rests on:** GOAL-001 (cooking from a structured recipe — typed units and the dictionary make merging possible where prose-based apps cannot)
**Requirements:** none exist — needs new requirements in flows (and in the domain model if a list is stored)
**Measurement:** before release, none; after release, share of reviews asking for a shopping list or a better one, via the reviews export, over 3 months — settled when lower than half of Recipe Keeper's 7.5%

**Recommendation:** adopt — it is hygiene in this set that recipe-fork lacks, it is free everywhere so not having it reads as a gap, and the dictionary-bound ingredients let recipe-fork merge lines exactly where others complain. It is a new capability, so it changes the vision.
**Ruling:** adopted
**Date:** 2026-10-10
**User's words:** chose «Принять (рекомендую)» in the batch put at session close.

### H-003 — Metric ↔ US unit conversion, free
**Status:** adopted
**Claim:** English-speaking readers will want a recipe written in grams shown in cups and spoons and the other way round; offering it free meets a request others leave open or charge for.
**Evidence:**
- Mealie discussion "Ability to convert units between metric / imperial / US imperial": 109 upvotes, 27 comments — github.com/mealie-recipes/mealie/discussions/3881, checked 2026-10-10
- Tandoor issues "Unit conversion options" 12 reactions (github.com/TandoorRecipes/recipes/issues/345), "Enhanced unit conversion" 5 (…/issues/4139), checked 2026-10-10
- ReciMe puts the converter in Plus — recime.app/help/en/articles/11596201-is-there-a-free-version-of-the-app, checked 2026-10-10
- SuperCook: three 2026 reviews complain it is imperial only — play.google.com/store/apps/details?id=com.supercook.app, checked 2026-10-10
**Rests on:** GOAL-001; the first release's audience includes English speakers (L10N-001, LEG-010 per spec/25-market-context.md "Not checked")
**Requirements:** SCALE-* fixes the units a quantity is shown in; conversion needs new requirements in scaling-rules (which units convert, volume-to-mass through the dictionary's factors, rounding)
**Measurement:** after release, use of the conversion control on the card — needs an in-app count the product does not keep (NGOAL-001 allows anonymous counts only, ANL-*) — or reviews asking for conversion, via the reviews export — settled when none in 3 months

**Recommendation:** adopt — the strongest single board signal found, cheap on top of the dictionary's existing comparison factors, and one of the features a large competitor charges for; it changes the scaling rules, so it goes through the vision.
**Ruling:** adopted
**Date:** 2026-10-10
**User's words:** chose «Принять (рекомендую)» in the batch put at session close.

### H-004 — "What can I cook with what I have" before release
**Status:** rejected
**Claim:** Extending My kitchen from equipment to food in stock, and showing which recipes are missing at most one or two ingredients, will draw users to recipe-fork.
**Evidence:**
- 188 of 1,000 SuperCook reviews mention cooking from what they have — but it is SuperCook's whole product — play.google.com/store/apps/details?id=com.supercook.app, checked 2026-10-10
- Outside pantry-first apps, 1–8 mentions per recipe keeper; one Paprika review says its pantry "doesn't really do anything" without quantities — their Play listings, checked 2026-10-10
- Mealie "Pantry Ingredients list" 30 upvotes (github.com/mealie-recipes/mealie/discussions/2448), "Food inventory and meal suggestions" 8 (…/discussions/2647), checked 2026-10-10
- Samsung Food sells it in Food+ — play.google.com/store/apps/details?id=com.foodient.whisk, checked 2026-10-10
**Rests on:** GOAL-001
**Requirements:** none — needs new requirements in flows and the domain model (a pantry, its quantities)
**Measurement:** reviews or ratings mentioning a pantry after release — settled when requested in ≥2% of reviews

**Recommendation:** reject for now — the demand is strong only where it is the product's core, and a personal book of a few dozen recipes gives few matches; better after release, once the shopping list exists to feed it.
**Ruling:** rejected
**Date:** 2026-10-10
**User's words:** chose «Отклонить (рекомендую)» in the batch put at session close.

### H-005 — A meal planner before release
**Status:** rejected
**Claim:** A weekly planner is needed for recipe-fork to be taken as a complete recipe app.
**Evidence:**
- 8 of 9 apps in the set have one — analytics/market/competitors.md
- Reviews mentioning a meal plan: Paprika 63, CookBook 58, Recipe Keeper 55 — but only one review found asking for it where it was missing (SuperCook) — their Play listings, checked 2026-10-10
- Counter-signal: Mealie "Disable Meal Planning and Shopping List (use for recipes only)" 18 upvotes — github.com/mealie-recipes/mealie/discussions/1386, checked 2026-10-10
**Rests on:** GOAL-001
**Requirements:** none — needs new requirements in flows
**Measurement:** reviews asking for a planner after release — settled when ≥2% of reviews

**Recommendation:** reject for now — hygiene on paper, but nobody in the evidence leaves an app for lacking it; it pays off mostly by feeding a shopping list, so it follows H-002 rather than preceding the release.
**Ruling:** rejected
**Date:** 2026-10-10
**User's words:** chose «Отклонить (рекомендую)» in the batch put at session close.

### H-006 — Export to PDF / print before release
**Status:** adopted
**Claim:** Readers want to print a recipe or a set of them, and two competitors charge for it.
**Evidence:**
- Mealie "Export Recipes and Cookbooks to PDF" 20 reactions, 20 comments, open — github.com/mealie-recipes/mealie/issues/1306, checked 2026-10-10
- Reviews: Recipe Keeper 14 (some are PDF import), CookBook 6, RecetteTek 6 — their Play listings, checked 2026-10-10
- RecetteTek Premium and ReciMe Plus charge for it — analytics/market/competitors.md
**Rests on:** GOAL-001
**Requirements:** none — needs new requirements in flows (Android's print service needs no network permission; to be confirmed by /team-lead)
**Measurement:** reviews asking for print/PDF after release — settled when ≥1% of reviews

**Recommendation:** reject for now — modest demand; worth doing cheaply later, not ahead of the backup and the shopping list.
**Ruling:** adopted
**Date:** 2026-10-10
**User's words:** chose «Принять — against the recommendation» in the batch put at session close.

