---
slug: MKT
status: closed
updated: 2026-09-27
ids: []
---

# Market context

This section holds no requirements. It records what the product owner
established about the market outside the product, because two other sections
rest on it: [`goals`](20-goals.md), where the tiebreak rule decides what gets
cut, and `legal`, where the product's name has to clear someone else's.

Findings MKT-F1 to MKT-F5 were supplied by the product owner on 2026-09-20 as
the state of the market in September 2026, with the confidence levels they
stated, and were not verified independently by the spec author. Findings MKT-F6
and MKT-F7 were looked up by the spec author on 2026-09-27 at the product owner's
request, from the vendors' own published pages, and each names its source. A
finding older than six months is re-verified when the section resting on it is
touched.

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

Bearing on this spec: sections [`scaling-rules`](80-scaling-rules/00-index.md) and
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

### MKT-F6 — Importing a recipe from a social network is a category of its own
Several recipe applications sell import from Instagram, TikTok and Pinterest as
their main feature, and they all work the same way: the person shares a post to
the application from the social network's own share menu, and a language model
turns it into ingredients and steps.

- ReciMe advertises itself as the application for saving recipes from Instagram,
  TikTok, Facebook, YouTube and Pinterest, and is distributed on Google Play
  ([Google Play listing](https://play.google.com/store/apps/details?id=com.recime.app&hl=en_US)).
  It reads the caption first, then tries to recognise the recipe from the video's
  audio, then looks for the author's own website
  ([ReciMe help — Import from Instagram](https://recime.app/help/en/articles/11596425-import-from-instagram)).
  It cannot read private accounts, pages behind a login, recipes written in the
  comments, or more than one recipe in a post, and in every such case tells the
  person to import a screenshot instead
  ([ReciMe help — why an import fails](https://recime.app/help/en/articles/14773584-why-didn-t-my-recipe-import-correctly)).
  The imported recipe keeps a link to its source
  ([ReciMe help — original source](https://recime.app/help/en/articles/12381412-how-to-check-the-original-recipe-source)).
  A free account parses 5 external links a week.
- Recipe Notes ([site](https://recipenotes.app/how-to-import-recipes-from-instagram)),
  Recipe Bro ([site](https://recipebro.com/import)),
  Flavorish ([site](https://www.flavorish.ai/)) and
  CookingGuru ([site](https://cooking.guru/)) offer the same mechanic; Paprika
  offers a built-in browser from which the person takes the recipe by hand.

Checked 2026-09-27. Bearing on this spec: import is hygiene in this category
rather than a differentiator, exactly as MKT-F3 found for scaling and timers, and
every product found parses with a model rather than by hand.

### MKT-F7 — Nobody found publishes an imported recipe
The products found keep what they import out of any shared space, and they say
why.

- ReciMe marks every saved recipe private, visible to the person who saved it
  only, and states that it offers no sharing between users and does not encourage
  it; it describes saving as a personal notebook, and presents that as the reason
  saving is not copyright infringement
  ([ReciMe help — recipe saving and copyright](https://recime.app/help/en/articles/11596213-recipe-saving-on-recime-and-copyright)).
- Samsung Food imports videos from Instagram, TikTok and YouTube and has public
  communities, but allows only the original recipe to be shared there and forbids
  sharing an edited one, to protect the creator's intellectual property
  ([Samsung Food help](https://support.samsungfood.com/hc/en-us/articles/18588347596692-How-Creators-Can-Link-From-Social-Channels-to-Recipes-on-Samsung-Food)).
- No product found attaches a frame of the source video as the recipe's picture,
  and none shows a person that the post they are importing has already been
  imported by someone else. The second was not searched for specifically.

Checked 2026-09-27. Bearing on this spec: a published import, open to forking,
has no counterpart among the products found. That is a differentiator and also
the part the two largest products deliberately refused, for a stated legal
reason.

## Not checked

The product owner states these were not covered, and they are the gaps to close
before any claim of a free niche is used outside this repository:

- Russian-language market and locally distributed applications — the search ran
  in English only. What this gap means changed on 2026-09-21, twice: the first
  release carries Russian *and* English (L10N-001), and it is not offered in the
  Russian Federation at all (LEG-010). The audience is Russian-speaking people
  living elsewhere and English speakers, so the market this section describes is
  no longer the market the product launches into — which makes the gap smaller
  and this whole section older than it looks.
- Google Play. Only the Apple App Store was examined, and Google Play is the
  MVP's only distribution channel.
- Startups with this mechanic that have already shut down.
- Trademarks and name registrations.
