---
slug: FORK
status: closed
updated: 2026-09-20
ids: [FORK-001, FORK-002, FORK-003, FORK-004, FORK-005]
---

# Forking

Who may fork what is specified in [ST-004](60-entity-states.md); this section
specifies what a fork contains and what it shows about its origin.

The split between what is inherited and what is not follows the law rather than
convenience: a sequence of cooking operations and a list of ingredients are a
method and a set of facts, which are not objects of copyright (Civil Code of the
Russian Federation, art. 1259 §5 —
[ConsultantPlus](https://www.consultant.ru/document/cons_doc_LAW_64629/be05678dc42ddc67aae5be9ba9beebd367fb9a3f/),
checked 2026-09-20). Photographs, video and the author's own prose are protected
expression. The fork therefore carries the former and never the latter.

### FORK-001 — What a fork inherits

When a user forks a recipe, the system shall create a new private recipe
containing the parent's title, tags, serving count, ingredients and steps, and a
reference to the parent.

**Acceptance criteria**
- Given a forked recipe, when it is opened, then its ingredients match the
  parent's in dictionary entry, qualifier or free-text name, unit type, unit,
  quantity, piece weight and divisibility,
  and in order.
- Given a forked recipe, when its steps are opened, then their text, timers and
  ingredient references match the parent's and resolve within the fork
  (DOM-007).
- Given a forked recipe, when it is created, then its state is `private`, its
  owner is the forking account, and its parent is the forked recipe.

**Verification:** fork a recipe covering all five unit types and compare field by
field.
**Source:** draft section 4.1; user decision, 2026-09-20.

### FORK-002 — What a fork never inherits

The system shall create a fork with no media and an empty description, and shall
not copy the parent's ratings, favourites, reports or moderation history.

**Acceptance criteria**
- Given a parent with a cover photo and photos on its steps, when it is forked,
  then the fork has no media at all, in its draft and afterwards.
- Given a parent with a description, when it is forked, then the fork's
  description is empty.
- Given a parent with likes, favourites and a moderation history, when it is
  forked, then the fork has none of them and has never been submitted.

**Verification:** fork a recipe that has media, a description, ratings and a
moderation history, and inspect the result.
**Source:** user decision, 2026-09-20. See
[ADR — a fork carries the method, not the expression](decisions/20260920-fork-carries-method-not-expression.md).

### FORK-003 — The parent's media stays visible in the parent

Where a fork has a parent that is accessible to the viewer, the system shall
offer navigation to the parent, where the parent's own media is shown.

**Acceptance criteria**
- Given a fork of a published recipe, when its card is opened, then the parent
  is named and can be opened, and the parent's photos are visible there.
- Given a fork whose parent was removed, when its card is opened, then the
  parent is shown as unavailable and cannot be opened (ST-007).

**Verification:** manual check on a fork of a published recipe with media.
**Source:** user decision, 2026-09-20: the author of a fork can see how the dish
should look by opening the original, which does not require copying the photo
into the fork.

### FORK-004 — Only the direct parent is shown

Where a recipe's parent is or has been `published`, the system shall show that
direct parent on its card, and shall not show, compute or load its full fork
tree.

**Acceptance criteria**
- Given a recipe that is a fork of a published fork, when its card is opened,
  then exactly one parent is named.
- Given that parent, when it is opened, then its own parent is named in turn,
  with no limit on the depth reachable this way.
- Given any recipe, when its card is opened, then it lists no descendants and no
  count of them.

**Verification:** build a chain three deep and walk it in both directions.
**Source:** draft section 4.3; GOAL-003; DOM-004. The condition on the parent is
FORK-005.

### FORK-005 — A parent that was never published is not mentioned at all

While a recipe's parent has never been `published`, the system shall show the
recipe with no parent and shall give no indication that a parent exists.

**Acceptance criteria**
- Given a published recipe forked from the author's own `private` recipe, when
  its card is opened by anyone, then no parent is named, no placeholder is
  shown, and the card is indistinguishable from that of a recipe with no parent.
- Given a published recipe whose parent is `link_shared` and has never been
  published, when its card is opened, then no parent is named.
- Given such a recipe, when its parent is published later, then the parent
  becomes named on the card from that moment, by FORK-004.
- Given such a recipe, when the chain is walked upwards from one of its own
  forks, then the chain stops at it and does not reveal its parent.
- Given the owner of both recipes, when they open either one, then the parent is
  not named to them either — the card is the same for everyone.

**Verification:** publish a fork of an own private recipe and inspect the card
as owner, as another account and as a moderator; then publish the parent and
inspect again.
**Source:** user decision, 2026-09-20. A parent nobody could ever open tells a
reader nothing and discloses that the author holds an unpublished recipe. The
recipe's stored parent is unchanged, so immutability is untouched: what depends
on the parent's state is the display, exactly as it already does in ST-007. See
[ADR — provenance follows the parent's publication](decisions/20260920-provenance-follows-publication.md).
