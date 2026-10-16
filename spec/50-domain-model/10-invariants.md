---
slug: DOM
status: closed
updated: 2026-10-15
ids: [DOM-001, DOM-002, DOM-003, DOM-004, DOM-005, DOM-006, DOM-007, DOM-008, DOM-015]
---

# Invariants over the entities

The rules that hold over the entities described in the index: what must be
present, what must never be, and what a submission is refused for.

### DOM-001 — A nickname is unique and permanent

The system shall assign each account a nickname that no other account holds, and
shall provide no means of changing it.

**Acceptance criteria**
- Given any two accounts, when their nicknames are compared, then they differ.
- Given any interface of the application, when it is enumerated, then it offers
  no action that changes a nickname once the account exists.
- Given the account creation screen, when a nickname is rerolled before the
  account is created, then no nickname is assigned yet and none is consumed
  (AUTH-018).

**Verification:** uniqueness check over the account store; interface review.
**Source:** draft section 2.1; user decision, 2026-09-20.
**Note:** because a nickname never changes, a published recipe needs no snapshot
of its author's name — the current nickname is always the one that was shown at
publication. Behaviour when the nickname space is exhausted is OQ-009.

### DOM-002 — A quantity exists exactly when the unit type carries one

The system shall hold a quantity for every ingredient whose unit type is
`exact`, `piece`, `household` or `taste_measured`, and shall hold no quantity
for an ingredient whose unit type is `taste_unmeasured`.

**Acceptance criteria**
- Given an ingredient of type `taste_unmeasured`, when it is stored, then it has
  no quantity and no unit, the editor offers no quantity field for it, and its unit
  field shows "to taste" (FLOW-023).
- Given an ingredient of any other type, when it is submitted without a
  quantity, then the recipe cannot be submitted for moderation.

**Verification:** editor check per unit type; validation test on submission.
**Source:** draft section 3.2; user decision, 2026-09-20.
**Correction, 2026-09-27:** criterion 1 said the editor offers no field for either
quantity or unit, while FLOW-023 has the author pick "to taste" in the unit field;
the unit field stays (SD-008 item 3, user decision).

### DOM-003 — A household quantity is never expressed as mass

The system shall not convert a `household` quantity to mass or volume, and shall
not display a mass or volume equivalent for it.

**Acceptance criteria**
- Given an ingredient measured in tablespoons, when the recipe is displayed at
  any scale, then its quantity is shown in tablespoons only.
- Given a recipe calling for 2 cups («2 стакана») of flour, when it is read in
  either unit system, then it reads 2 cups, and no gram amount appears beside it
  (L10N-008).

**Verification:** manual UI check on a recipe using household units, in both unit
systems.
**Source:** user decision, 2026-09-20. The product holds no ingredient weight
table, and inventing one per ingredient would be a separate product with its own
moderation burden. The dictionary's comparison factors are approximate masses;
they are never used in scaling and never shown as such (DOM-011).
**Confirmed 2026-10-15 (sync v0.31–v0.38):** since v0.35 a factor may turn a mass
into cups for a US reader, marked "≈" — the opposite direction, which this
requirement does not forbid. The owner chose at that sync that the household units
belong to both unit systems, so a cup is never turned into grams for a metric
reader; the v0.35 build still does so (a known departure). See
[ADR — one unit system, converted on display](../decisions/20261015-one-unit-system-converted-on-display.md).

### DOM-004 — A parent reference is fixed and shallow

The system shall set a recipe's parent at creation, shall provide no means of
changing it, and shall hold no reference from a recipe to its descendants or to
the root of its chain.

**Acceptance criteria**
- Given a forked recipe, when its parent is resolved, then it is the recipe it
  was forked from, at every point in its lifetime.
- Given any recipe, when its stored fields are enumerated, then none of them
  lists descendants or names a chain root.

**Verification:** data model review; interface review.
**Source:** draft section 4.3; GOAL-003.

### DOM-005 — Media is owned by an account from upload

When a recipe's media is uploaded by sending the recipe (FLOW-059), the system
shall bind each item to the owner's account at that moment, before the recipe
becomes externally visible.

**Acceptance criteria**
- Given a sent, unpublished recipe with media, when the same account is signed in
  on another device, then that media is available there.
- Given a recipe never sent, when another device of the same account is looked
  at, then neither the recipe nor its media is there (FLOW-059).
- Given a media item belonging to an unpublished recipe, when any other account
  requests it, then it is not served.

**Verification:** two-device test with one account key, before and after sending;
access test from a second account.
**Source:** draft section 5.2; upload moved to the sending of the recipe by user
decision, 2026-09-28.

### DOM-006 — Submission minimum

If a recipe lacks a title, at least one ingredient, at least one step, a serving
count, or a declared language, then the system shall reject its submission for
moderation.

A step holds text, media, or both; a step with neither does not exist, and so does
not count towards the minimum.

**Acceptance criteria**
- Given a recipe missing any one of those five, when the user submits it for
  publication or requests a private link, then the submission is rejected and
  the interface names what is missing.
- Given a recipe that has all five and no media, when it is submitted, then the
  submission is accepted.
- Given the declared language, when a new recipe is opened in the editor, then it
  is already set from the author's interface language (L10N-004, FLOW-024), so
  this part of the minimum is the one an author cannot reach by omission and can
  only reach by clearing it.
- Given a step holding only a photo or a video and no text, when the recipe is
  stored or submitted, then it is a step; given a step left with neither text nor
  media, when the recipe is stored, then that step is not kept.

**Verification:** validation test over each of the five omissions.
**Source:** user decision, 2026-09-20; the declared language added 2026-09-21
with L10N-004, which made it a condition of submission and pointed at FLOW-028
for the warning, while this requirement — the one that defines the minimum and
the one FLOW-028 and GUIDE-001 both defer to — still listed four parts; amended
2026-10-03 (sync v0.3–v0.14): a step is text, media or both, never neither
(req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03). Media is
deliberately not required: a mandatory photo would add a manual media review and
storage cost (CON-002) to every publication.

### DOM-007 — Step ingredient references resolve within the recipe

The system shall resolve every ingredient reference in a step to an ingredient
of the same recipe, whether the reference stands for the whole ingredient or for
a part of it (FLOW-031).

**Acceptance criteria**
- Given a step containing a reference to a whole ingredient, when the step is
  displayed, then the referenced quantity equals the quantity shown for that
  ingredient in the ingredient list at the current scale.
- Given a step containing a reference to a part of an ingredient, when the step
  is displayed, then it shows the part scaled by the same factor as the list and
  rounded on its own by the grid of the ingredient's unit type, with its own
  derived marker; it is not taken from the list line (SCALE-007, FLOW-032).
- Given an ingredient that a step references, when the author deletes that
  ingredient before the recipe becomes externally visible, then the reference is
  removed from the step text in the same action.

**Verification:** manual check of a scaled recipe, comparing every whole
reference in the steps against the ingredient list and every part against its
authored value times the factor, rounded by the grid.
**Source:** draft section 3.5; user decision, 2026-09-20 (moved from "desirable"
into the MVP, because otherwise every scaled recipe contradicts itself between
its list and its steps); amended 2026-10-03 (sync v0.3–v0.14): a reference may
stand for a part of the ingredient (req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03, asked by the user, v0.9). See
[ADR — a reference to part of an ingredient](../decisions/20261003-a-reference-to-part-of-an-ingredient.md).

### DOM-008 — Tags come from the dictionary only

The system shall accept as a recipe's tags only entries of the tag dictionary.

**Acceptance criteria**
- Given the tag editor, when the author looks for a tag, then only dictionary
  entries are offered and free text cannot be committed.
- Given a submission carrying a tag that is not in the dictionary, when it is
  received, then it is rejected.

**Verification:** editor check; service-level submission test.
**Source:** user decision, 2026-09-20. Free-text tags would become another text
surface for the single moderator to check, and would fragment search.

### DOM-015 — The authored serving count is a whole number of one or more

The system shall hold as a recipe's authored serving count only a whole number of
one or more, and shall set no upper bound on it.

**Acceptance criteria**
- Given the serving count field of the editor, when the author tries to enter 0,
  a negative number, or a number with a fractional part such as 2.5 or ½, then the
  field does not hold it.
- Given that field, when the author enters 1, 4 or 1000, then it holds that value.
- Given a recipe whose serving count field holds no value, when it is submitted,
  then DOM-006 rejects it as missing a serving count; no value of zero stands in
  for an empty field.
- Given any authored serving count, when a reader later returns the recipe to it
  (SCALE-001, FLOW-017), then it is a count the serving count control can hold
  (SCALE-011), because every whole number of one or more is one.

**Verification:** attempt 0, −1, 2.5, ½, 1, 4 and 1000 in the editor field; submit
a recipe with the field empty.
**Source:** user decision, 2026-09-27 (SD-013).
**Cost, stated:** a recipe written for less than one serving — half a portion of a
sauce — cannot say so, and its author writes it for one. The requested count
still goes below one (SCALE-011), so a reader can scale such a recipe down.

## Deliberately not modelled yet

These were named so their absence would be visible rather than taken for an
oversight. All four have since been settled, and each is kept here with the
requirement that settled it so that a reader arriving from an older draft is led
to the answer instead of to the gap.

- Whether a rating can be changed or withdrawn — settled in
  [`ratings`](../120-ratings.md), RATE-001 and RATE-002.
- Media size, duration and count limits — settled in [`media`](../90-media.md),
  MEDIA-001 to MEDIA-010.
- What a fork shows when its parent is gone — settled in
  [`entity-states`](../60-entity-states/00-index.md), ST-007, which closed OQ-005 and
  OQ-007 on 2026-09-20.
- Account deletion — settled in [`retention`](../150-retention.md), RET-001 to RET-005.
