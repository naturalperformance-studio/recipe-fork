---
phase: market
updated: 2026-10-10
sources:
  - analytics/market/competitors.md (every Play listing and vendor page there, checked 2026-10-10)
  - https://github.com/mealie-recipes/mealie/discussions/1899
  - https://github.com/mealie-recipes/mealie/discussions/5240
  - https://github.com/mealie-recipes/mealie/discussions/2448
  - https://github.com/mealie-recipes/mealie/discussions/2647
  - https://github.com/mealie-recipes/mealie/discussions/5812
  - https://github.com/mealie-recipes/mealie/discussions/1386
  - https://github.com/mealie-recipes/mealie/discussions/3881
  - https://github.com/mealie-recipes/mealie/discussions/4914
  - https://github.com/mealie-recipes/mealie/discussions/7446
  - https://github.com/mealie-recipes/mealie/issues/1306
  - https://github.com/TandoorRecipes/recipes/issues/345
  - https://github.com/TandoorRecipes/recipes/issues/4139
  - https://github.com/TandoorRecipes/recipes/issues/1853
  - https://github.com/TandoorRecipes/recipes/issues/79
---

# What recipe apps charge for, what is wanted, and what recipe-fork could give free — 2026-10-10

Asked by the owner: how ready is the app for release on its own (without outside material such as a recipe catalogue), which features are missing, and which features others sell that recipe-fork could give free. The build is the serverless Play build (no INTERNET permission). recipe-fork has no monetisation by design (NGOAL-006).

Market phase: `analytics/data/` holds no export, so no figure here is about recipe-fork's own performance.

## Method
Two research agents opened every Play listing and vendor page in the set on 2026-10-10 (US listing). Demand evidence: keyword matches over the 1,000 most recent Play reviews per app (647 for CookBook), not hand-classified (e.g. "pdf" also catches PDF import); and vote counts on the Mealie and Tandoor feature boards (self-hosted web apps — a weaker signal for a phone app). Reddit could not be read (blocked to the agents); reviews outside the English locale were not read.

## Demand evidence by candidate

| Candidate | Paid at | Review mentions (of 1,000) | Board signal | Reading |
|---|---|---|---|---|
| Whole-book backup / restore | sync paid at Paprika, Pestle | RecetteTek **54**, Recipe Keeper 20, COOKmate 19, Paprika 11 — mostly recipes lost on a new or dead phone | Tandoor #79: 6 reactions, 18 comments; Mealie #4914: 7 | the clearest pain in offline keepers |
| Shopping list from recipes | free everywhere | Paprika 110, Recipe Keeper 75, CookBook 66/647, RecetteTek 42 | Mealie #1899: 41, #5240: 17 | hygiene; merging is the open complaint |
| Metric ↔ US conversion | ReciMe Plus | 2–21 per app; SuperCook "imperial only" complaints | **Mealie #3881: 109 upvotes**; Tandoor #345: 12 | strongest board signal |
| Meal planner | Pestle Pro | Paprika 63, CookBook 58, Recipe Keeper 55 — rarely asked for where missing | Mealie #5812: 18; counter: #1386 (disable it): 18 | expected, weakly missed |
| Export PDF / print | RecetteTek Premium, ReciMe Plus | Recipe Keeper 14, CookBook 6, RecetteTek 6 | Mealie #1306: 20 | modest |
| Pantry / cook from what I have | Samsung Food+ | SuperCook **188** (its core); 1–8 elsewhere | Mealie #2448: 30, #2647: 8 | strong only as a product's core |

Offline use itself: 0–3 mentions per app — nobody chooses an app for it; it matters through what it prevents (data loss) and what it allows (no account).

## Findings
1. **A gap that loses data.** recipe-fork keeps everything on the phone and exports one recipe at a time (FLOW-063). The most common complaint found in offline keepers is a lost collection on a new phone (RecetteTek 5.4% of recent reviews). → H-001.
2. **A hygiene gap.** A shopping list from recipes is in 8 of 9 apps and free in all of them; recipe-fork has none. Its structured, dictionary-bound ingredients are what makes merging the same ingredient across recipes reliable — the thing reviewers complain about where it is missing. → H-002.
3. **A free differentiator within reach.** Metric ↔ US conversion: the strongest board signal, charged for by ReciMe, and the dictionary already carries comparison factors. → H-003.
4. **recipe-fork already gives free what two large apps charge for:** cook mode (ReciMe Plus, Samsung Food+), scaling by servings (Samsung Food+), and no cap on how many recipes are kept (Paprika 50, CookBook 20, Pestle 15).
5. **Not for this release on this evidence:** pantry (H-004), meal planner (H-005), PDF/print (H-006).
6. **Timing.** Mealime (1M+ installs) closes on 2026-10-21 and deletes users' data with no export named (mealime.com/closing). recipe-fork cannot import from it. No hypothesis is written on it: there is no instrument to measure a migration before release.

## What this report does not cover
No listing draft and no channel plan this session — the question was about features. No figure about recipe-fork's own use (no export exists).
