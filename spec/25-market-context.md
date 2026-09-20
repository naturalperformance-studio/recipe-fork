---
slug: MKT
status: closed
updated: 2026-09-20
ids: []
---

# Market context

This section holds no requirements. It records what the product owner
established about the market outside the product, because two other sections
rest on it: [`goals`](20-goals.md), where the tiebreak rule decides what gets
cut, and `legal`, where the product's name has to clear someone else's.

All findings below were supplied by the product owner on 2026-09-20 as the state
of the market in September 2026, with the confidence levels they stated. Nothing
here was verified independently by the spec author. A finding older than six
months is re-verified when the section resting on it is touched.

## Findings

### MKT-F1 — No direct competitor found
No product was found that sells recipe forking to non-programmers.
Owner's confidence in this conclusion: medium — enough to decide to build, not
enough for an investor deck.

### MKT-F2 — The nearest prior art exists and is dead
`esenator/recipes` (https://github.com/esenator/recipes) describes exactly this
mechanic: a variation created with the parent's contents pre-filled, and the
whole parent chain retained. The feature was never activated. The stated reason
is that the fork logic sat in the database while editing an existing recipe was
never built, so forking could not be switched on. Accounts there are
login-and-password, so the privacy position of this product has no counterpart.
Owner's confidence: high on the content (quoted from the README), medium on
"dead" (inferred from the repository's age).

Bearing on this spec: that project needed an editing path it never built. This
product does not, because a recipe is immutable once visible (NGOAL-004) and a
fork is the only way it changes (GOAL-002) — the same mechanic reached without
the missing piece.

### MKT-F3 — Recalculation and timers are hygiene, not a differentiator
Scaling by servings, scaling from a fixed quantity of one ingredient, and timers
detected in steps are all shipped elsewhere: Recipe Scaler (scaling from what
the cook actually has), Paprika (https://www.paprikaapp.com — timers in steps
plus scaling by servings), and ReciMe, CookBook and Useful Units (scaling).
Owner's confidence: high for the first two, medium for the rest.

Bearing on this spec: sections [`scaling-rules`](80-scaling-rules.md) and
[`flows`](70-flows/00-index.md) deliver a baseline the market already has. What has no
counterpart is the combination of forking, the chain of provenance, the absence
of personal data, and moderation.

### MKT-F4 — Self-hosted recipe managers are an adjacent segment
Mealie, Tandoor, KitchenOwl, `fork.recipes`
(https://github.com/mikebgrep/fork.recipes) and `dolph/recipes`
(https://github.com/dolph/recipes) are self-hosted, single-household, with no
social layer and no forking. Owner's confidence: high.

### MKT-F5 — The name `Forked` is taken on iOS
An application named `Forked` exists in the Apple App Store: swipe-based recipe
discovery, meal plans, shopping lists, a cooking mode with timers, no ads and no
tracking. Its description shows no fork-editing mechanic, so the overlap is the
name only. Owner's confidence: high that it exists, medium on its features (the
store description was the only source).

Bearing on this spec: the MVP ships on Android only
([ADR — native Android MVP](decisions/20260920-native-android-mvp.md)), so this
is not a launch blocker, but it is the reason OQ-016 exists.

## Not checked

The product owner states these were not covered, and they are the gaps to close
before any claim of a free niche is used outside this repository:

- Russian-language market and locally distributed applications — the search ran
  in English only. This matters more than the rest, because the first release is
  Russian-only (section `localization`).
- Google Play. Only the Apple App Store was examined, and Google Play is the
  MVP's only distribution channel.
- Startups with this mechanic that have already shut down.
- Trademarks and name registrations.
