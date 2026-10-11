---
phase: market
updated: 2026-10-10
sources:
  - https://play.google.com/store/apps/details?id=com.hindsightlabs.paprika.android.v3
  - https://www.paprikaapp.com/
  - https://paprikaapp.com/blog/2018/11/14/paprika-3-0-is-now-available-for-android/
  - https://play.google.com/store/apps/details?id=com.mealime
  - https://www.mealime.com/closing
  - https://www.mealime.com/pro
  - https://play.google.com/store/apps/details?id=com.foodient.whisk
  - https://apps.apple.com/us/app/id1133637674
  - https://news.samsung.com/us/upgraded-samsung-food-raises-bar-for-food-experiences-ifa-2024/
  - https://play.google.com/store/apps/details?id=com.recime.app
  - https://apps.apple.com/us/app/id1593779280
  - https://recime.app/help/en/articles/11596201-is-there-a-free-version-of-the-app
  - https://recime.app/help/en/articles/11630603-what-are-the-benefits-of-recime-plus
  - https://play.google.com/store/apps/details?id=com.supercook.app
  - https://play.google.com/store/apps/details?id=fr.cookbook
  - https://www.cookmate.online/pro/
  - https://play.google.com/store/apps/details?id=com.tudorspan.recipekeeper
  - https://recipekeeperonline.com/
  - https://play.google.com/store/apps/details?id=fr.recettetek
  - https://play.google.com/store/apps/details?id=com.cookbook.cbapp
  - https://crouton.app
  - https://pestlechef.app
  - https://play.google.com/store/search?q=crouton+recipe+manager&c=apps
  - https://play.google.com/store/search?q=pestle+recipe+manager&c=apps
---

# Competitor set — personal recipe apps on Google Play

The question this set answers (2026-10-10): which features do recipe apps sell, which are wanted, and which could recipe-fork give free in its serverless, offline Play build (no INTERNET permission). The distribution channel is Google Play (spec/130-external-interfaces.md EXT-005; spec/25-market-context.md MKT-F5). recipe-fork has no monetisation by design (NGOAL-006), so "what others charge for" is a set of candidates to give free, not a price to undercut.

Gathered by two research agents in this session; every row's figures were read on the live page on 2026-10-10. Play figures are the US listing (`hl=en_US&gl=US`). Review-keyword counts in `analytics/reports/20261010-paid-features.md` are keyword matches over the 1,000 most recent reviews, not hand-classified.

## The set

| App | What it does | Installs | Rating (n) | Last update | Monetisation | Found by | Source |
|---|---|---|---|---|---|---|---|
| Paprika 3 | Keeps your own recipes locally; grocery list that combines ingredients and sorts by aisle; pantry with expiry dates; meal planner; scaling and unit conversion; cook mode; print | 500K+ | 4.9 (20.8K) | 2025-08-02 | free with a 50-recipe cap; one-time unlock (Play IAP $4.99–$19.99) removes the cap and adds cloud sync | «recipe keeper» (#6) | play.google.com/store/apps/details?id=com.hindsightlabs.paprika.android.v3, checked 2026-10-10 |
| Mealime | Meal plans from its own recipes; grocery list combined across recipes; hands-free mode | 1M+ | 4.6 (26.8K) | 2026-09-16 | Pro $2.99/month (nutrition, notes, exclusive recipes) — **shuts down 2026-10-21, personal data deleted, no export stated** | named | play.google.com/store/apps/details?id=com.mealime; mealime.com/closing, checked 2026-10-10 |
| Samsung Food (Whisk) | 240K recipes, save from any site, meal planner → grocery list, retailer ordering, nutrition | 1M+ | 4.5 (23K) | 2026-07-29 | Food+ $6.99/month or $59.99/year: AI meal plans, smart cooking mode, **adjusting servings**, pantry ("cook with what you have") | named | play.google.com/store/apps/details?id=com.foodient.whisk; apps.apple.com/us/app/id1133637674, checked 2026-10-10 |
| ReciMe | Imports recipes from social networks; grocery lists, meal plans | 1M+ | 4.7 (109K) | 2026-10-02 | Plus ($4.99/month, $34.99/year on the App Store; Play IAP $6.99–$69.99): unlimited imports (free: 5/week), nutrition, **metric/imperial converter**, **export/print/PDF**, **cooking mode** | named | play.google.com/store/apps/details?id=com.recime.app; recime.app help articles 11596201, 11630603, checked 2026-10-10 |
| SuperCook | Suggests recipes from 11M web recipes by what is in your pantry | 1M+ | 4.6 (18.7K) | 2025-10-16 | none shown | named; #6 for «мои рецепты» | play.google.com/store/apps/details?id=com.supercook.app, checked 2026-10-10 |
| COOKmate | Keeps your recipes; shopping list from recipes; scaling; Dropbox sync; meal planner (online account) | 1M+ | 4.7 (45.9K) | 2026-09-07 | ads; Premium $6/quarter or $20/year (unlimited online recipes and lists, no ads, scaling) | named | play.google.com/store/apps/details?id=fr.cookbook; cookmate.online/pro, checked 2026-10-10 |
| Recipe Keeper | Keeps your recipes; aisle-grouped shopping list; meal planner; scaling; US↔metric conversion; PDF/print cookbooks; offline; sync | 1M+ | 4.7 (20.3K) | 2026-03-31 | one IAP $19.99; free/paid split unverified (vendor states none) | #1 for «recipe keeper» and «recipe organizer» | play.google.com/store/apps/details?id=com.tudorspan.recipekeeper; recipekeeperonline.com, checked 2026-10-10 |
| My Recipe Box (RecetteTek) | Keeps your recipes; shopping list from ingredients; recalculation; weekly plan; search by ingredient; backup/restore; Dropbox sync; offline | 1M+ | 4.6 (46.2K) | 2026-10-08 | ads; Premium (IAP $2.99–$69.99): no ads, PDF/HTML export of all recipes, Google Drive/pCloud/WebDAV sync | #2 «recipe keeper»; **#1 «мои рецепты»** (hl=ru); #2 «книга рецептов» | play.google.com/store/apps/details?id=fr.recettetek, checked 2026-10-10 |
| CookBook | Keeps your recipes; merged, aisle-sorted grocery list; meal planner; scaling; conversion; AI "what to cook with what you have"; sync | 100K+ | 4.8 (2.22K) | 2026-10-05 | free tier 20 recipes / 20 imports; subscription (IAP $6.99–$39.99) | #3 «recipe organizer»; #4 «книга рецептов» | play.google.com/store/apps/details?id=com.cookbook.cbapp, checked 2026-10-10 |

Not on Google Play (so not in the set, named because they were asked about): Crouton — iOS/macOS only (crouton.app; Play search for «crouton recipe manager», checked 2026-10-10); Pestle — iPhone/iPad, with Pro $24.99/year for sync and meal plans (pestlechef.app; Play search for «pestle recipe manager», checked 2026-10-10).

## Hygiene and differentiation (Kano)

Counted over the nine Play apps above. "recipe-fork" marks what the current build (v0.30, develop a713487) has.

| Capability | In the set | Paid where | recipe-fork | Class | Requirement IDs |
|---|---|---|---|---|---|
| Scaling by servings | 8 of 9 | Samsung Food+, COOKmate Premium | yes | hygiene | SCALE-* |
| Cook mode (screen on, cross off, current step) | ≥5 of 9 | ReciMe Plus, Samsung Food+ | yes (Cooking tab) | hygiene; given free where two charge | FLOW-068, A11Y-007 |
| Works offline, no account | Paprika, Recipe Keeper, RecetteTek state it | — | yes | hygiene among recipe keepers | FLOW-061 |
| **Shopping list from recipes** | 8 of 9 | free everywhere | **no** | **hygiene — missing** | none yet — would need requirements in flows |
| Merging the same ingredient across recipes | Paprika, Mealime, CookBook (+ aisle sorting at ReciMe) | free where present | no | differentiation candidate among lists; complaints where missing | none yet |
| **Backup / restore of the whole collection** | RecetteTek (free), Paprika & Pestle (paid sync), COOKmate & Recipe Keeper (sync) | sync is paid at Paprika and Pestle | **no** (one recipe at a time, FLOW-063) | **hygiene — missing; its failure is the most common complaint found** | FLOW-063 covers one recipe; the whole book needs new requirements |
| Meal planner | 8 of 9 | Pestle Pro only | no | hygiene, weak demand signal | none yet |
| Unit conversion metric ↔ US | Paprika, Recipe Keeper, CookBook (+ Pestle) | ReciMe Plus | no | differentiation candidate; strongest board signal found | none yet — SCALE fixes units |
| Export to PDF / print | Paprika, Recipe Keeper, COOKmate (+ RecetteTek, ReciMe paid) | RecetteTek Premium, ReciMe Plus | no | hygiene-leaning, modest demand | none yet |
| Pantry / "what can I cook with what I have" | Paprika, SuperCook (its core), CookBook (AI), Samsung Food+ | Samsung Food+ | equipment only ("My kitchen") | differentiation candidate only as a product's core; thin elsewhere | none yet |
| Nutrition | Samsung (free), ReciMe & Mealime (paid) | ReciMe Plus, Mealime Pro | no | out of this question (needs a nutrition dictionary) | — |
| Import from URL / social | most | ReciMe caps free imports | no (Play build has no INTERNET) | hygiene the serverless build cannot meet | IMPORT-* (not in first release) |
| Forking with named parent; no personal data | 0 of 9 | — | yes | differentiation (MKT-F1, MKT-F3) | GOAL-002, NGOAL-001 |

## Positioning

```
For home cooks who keep their own recipes and cook from them, recipe-fork is a recipe book that works entirely on the phone, with nothing to pay and nothing to sign up for, that scales and guides a recipe while you cook and lets you make your own version of any recipe.
Unlike Paprika, it never caps how many recipes you keep and charges for nothing, sync included — because there is nothing to sell.
```

## Notable

- Mealime closes on 2026-10-21 and deletes its users' data; its closing page names no export (mealime.com/closing, checked 2026-10-10). Its 1M+ users are looking for somewhere else this month; recipe-fork has no Mealime import and cannot fetch anything in the Play build.
- The free/paid line in this set: grocery lists and URL import are free everywhere; the paywall sits on recipe count (Paprika 50, CookBook 20, Pestle 15), sync, AI/photo import, and at ReciMe and Samsung on cook mode, conversion, scaling and nutrition.
- Not checked: Reddit (blocked to the research agents), reviews outside the English locale, Play listings in other regions.
