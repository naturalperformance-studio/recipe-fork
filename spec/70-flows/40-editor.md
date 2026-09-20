---
slug: FLOW
status: open
updated: 2026-09-20
ids: [FLOW-022, FLOW-023, FLOW-024, FLOW-025, FLOW-026, FLOW-027, FLOW-028,
  FLOW-029, FLOW-030, FLOW-031, FLOW-032]
---

# Writing a recipe

The editor: one screen, three fields per ingredient, timers inside steps, warnings
at both bounds, and an explicit statement of what a fork will carry away.

### FLOW-022 — An ingredient outside the dictionary is accepted with a warning

When the author names an ingredient that matches no entry of the ingredient
dictionary, the system shall accept the ingredient as free text and shall state
that no filter on that ingredient will find the recipe and that this cannot be
changed once the recipe has been published.

**Acceptance criteria**
- Given the ingredient editor, when the author types a name and the dictionary
  offers nothing that matches, then adding it as free text is offered and is not
  refused.
- Given the author adds it, when the warning is shown, then it states both that
  filters will not find the recipe by this ingredient and that publication makes
  it permanent (CON-003).
- Given a recipe that holds a free-text ingredient, when the author later finds a
  matching entry while the recipe is still editable, then the ingredient can be
  replaced by the entry and the warning is gone.
- Given a recipe that holds a free-text ingredient, when it is submitted for
  moderation, then submission is not blocked by the warning.
- Given a `piece` ingredient added as free text, when it is added, then the
  author is asked for its divisibility, because no entry supplies it (FLOW-001).

**Verification:** add an ingredient absent from the dictionary, confirm the
warning's two statements, then replace it with an entry and confirm the warning
disappears.
**Source:** user decision, 2026-09-20: a missing entry never blocks authoring,
and the author is told what it costs rather than protected from it. See
[ADR — the ingredient dictionary](../decisions/20260920-ingredient-dictionary.md).

### FLOW-023 — An ingredient is three fields and nothing more

The system shall compose an ingredient in the editor from exactly three fields —
the ingredient, the quantity and the unit — and shall never ask the author for the
unit type, which follows from the unit chosen.

**Acceptance criteria**
- Given the editor, when the author adds an ingredient, then exactly three fields
  are offered: `крылышки` · `500` · `г`, or `тушка` · `2` · `шт`.
- Given the ingredient field, when the author types, then dictionary entries are
  offered whether they are leaves or parents (DOM-010), so `курица · 500 · г` is an
  ordinary row.
- Given the ingredient field, when both a leaf and its parent could match what the
  author typed, then the leaves are offered before the parent, so that a qualifier
  is a last resort and not a substitute for picking the right entry.
- Given the author picks the entry `филе` and keeps typing `без кожи`, when the row
  is read, then it reads `филе без кожи`, the ingredient holds that entry and that
  qualifier, and no fourth field has appeared.
- Given such an ingredient, when a reader filters on `курица`, then the recipe
  matches through the entry, and the qualifier plays no part in the match
  (CAT-006).
- Given the ingredient field, when nothing in the dictionary matches, then free
  text is offered with the warning FLOW-022 requires.
- Given the unit field, when the author picks `шт`, then the ingredient's unit type
  is `piece` and the author is never shown a unit type at all.
- Given the unit field, when the author picks the unit that means "to taste", then
  the quantity field is withdrawn and the ingredient is stored with no quantity
  (DOM-002).
- Given a `piece` ingredient whose dictionary entry supplies its divisibility, when
  the row is shown, then divisibility is not one of the three fields (FLOW-001).

**Verification:** author one ingredient of each unit type through the editor and
confirm that no row ever presents a fourth field or asks for a unit type.
**Source:** user decision, 2026-09-20. Three fields is also why a parent entry has
to be usable on its own: a row is an ingredient, a number and a unit, and there is
no place in it for the product to demand a narrower entry than the author has. The
qualifier survives as the tail of the first field rather than as a field of its
own, because the dictionary will always lag behind a kitchen: without it an author
whose leaf is missing would have to choose between a recipe that can be found and
a row that says what they mean (OQ-020).

### FLOW-024 — The editor is one screen in a fixed order

The system shall present the editor as one scrolling screen holding, in this order,
the title with the cover, the tags, the serving count, the ingredients, the steps
and the description, and shall not require the author to complete one part before
reaching another.

**Acceptance criteria**
- Given a new recipe, when the editor opens, then every part is present and
  reachable by scrolling, in the order above.
- Given a half-filled recipe, when the author scrolls to the steps without having
  entered a serving count, then the steps accept input.
- Given a recipe created by forking, when the editor opens, then the inherited parts
  arrive already filled in this same screen (FORK-001).
- Given the author leaves the editor at any point, when they return, then they
  return to a screen in the same order with everything they had entered.

**Verification:** open the editor on a new recipe and on a fork, and fill the parts
out of order.
**Source:** user decision, 2026-09-20: an author needs to see the whole recipe at
once, and a walk-through would send them back and forth for a change of one line.

### FLOW-025 — Steps and ingredients are inserted anywhere and reordered by dragging

The system shall let the author insert a step or an ingredient at any position,
including between two existing ones, and reorder either by dragging it up or down.

**Acceptance criteria**
- Given a recipe with five steps, when the author inserts a step between the second
  and the third, then it takes that position and the ones after it move down.
- Given a recipe with five steps, when the author drags the fourth above the second,
  then the order is as dragged and no step's content changed.
- Given a step that references an ingredient, when either is reordered, then the
  reference still resolves to the same ingredient (DOM-007).
- Given a timer inside a step, when the step is moved, then the timer moves with it
  (FLOW-027).
- Given an ingredient that a step references, when the author deletes that
  ingredient, then the reference is dropped as DOM-007 already requires.

**Verification:** insert and drag steps and ingredients in a recipe that has
references and timers, and confirm positions and references afterwards.
**Source:** user decision, 2026-09-20.

### FLOW-026 — The cover is attached with the title, step media with the step

The system shall offer the cover image where the title is entered and shall offer
media for a step at that step.

**Acceptance criteria**
- Given the top of the editor, when the author looks at the title field, then
  attaching a cover image is offered there and is described as optional.
- Given a step, when the author looks at it, then attaching an image or a video to
  that step is offered at that step and nowhere else.
- Given a recipe with no cover, when it is saved, then it saves, because a cover is
  not part of the minimum (DOM-006).
- Given media attached with no connection, when it is attached, then FLOW-004
  governs and the recipe is marked as not accepted by the server (FLOW-005).
- Given the limits of OQ-003, when they are set, then how many items a step may
  carry follows from them and not from this requirement.

**Verification:** attach a cover and step media online and offline, and save a
recipe with neither.
**Source:** user decision, 2026-09-20: media is attached where the thing it belongs
to is written, so a step's photo is never separated from its step.

### FLOW-027 — A timer is attached inside the step it belongs to

The system shall let the author attach a timer to a step and enter its duration
there, and shall hold the timer as part of that step.

**Acceptance criteria**
- Given a step reading `в духовку на 190 °C на 30 минут`, when the author attaches a
  timer and enters 30 minutes, then the timer belongs to that step.
- Given a step with a timer, when the author adds a second timer to the same step,
  then both belong to it (FLOW-016).
- Given a timer, when the author enters its label or leaves it empty, then FLOW-016
  governs what the reader sees.
- Given a step with a timer, when the step is deleted, then its timers are deleted
  with it.
- Given a duration the author has not entered, when the recipe is saved, then the
  timer is not stored, because a timer without a duration is not a timer.

**Verification:** attach one timer and two timers to a step, delete the step, and
save a recipe with an unfinished timer.
**Source:** user decision, 2026-09-20.

### FLOW-028 — The minimum and the maximum are shown on leaving a field, as a warning

When the author leaves a part of the recipe that is below the minimum DOM-006 requires
or above a limit of DOM-012, the system shall mark that part without treating it as an
error, and shall not prevent further editing.

**Acceptance criteria**
- Given an empty ingredient list, when the author moves from it to the steps, then
  the ingredient list is marked as still needing at least one ingredient.
- Given a warning of that kind, when the author keeps typing anywhere in the recipe,
  then nothing is blocked and no input is refused.
- Given a part that was marked, when the author completes it, then the mark is gone
  without the author asking for it.
- Given a recipe still below the minimum, when the author tries to submit it, then
  submission is refused as DOM-006 requires — the warning informs, the minimum
  decides.
- Given a step that passes 2 000 characters, when the author leaves it, then it is
  marked as over its limit and the text they typed is kept in full, not truncated.
- Given a recipe over a limit, when the author tries to submit it, then submission is
  refused as DOM-012 requires — again the warning informs and the limit decides.
- Given a field the author has not yet visited, when the editor opens, then it is
  not marked, because nothing has been left.

**Verification:** leave each part of a recipe below the minimum in turn, then
complete it, then attempt submission.
**Source:** user decision, 2026-09-20: a warning on leaving a field, never an error
while typing in it, and the same treatment at both ends — a part that is too small and
a part that is too large are both reported the same way and both decided at
submission.

### FLOW-029 — There is no preview; saving shows the reader's view

The system shall show the author their saved recipe exactly as a reader sees it, and
shall offer no separate preview inside the editor.

**Acceptance criteria**
- Given a recipe the author saves, when it is opened afterwards, then it is the same
  card a reader sees (FLOW-019, FLOW-020), not an editor with reader styling.
- Given that view of a recipe that is still editable, when the author chooses to
  edit, then the editor opens on the same recipe.
- Given that view, when the author looks for an edit control on the card itself,
  then there is none: the card is read, the editor is written, and they are two
  screens.
- Given a recipe that is no longer editable by `entity-states`, when the author
  opens it, then no way into the editor is offered at all.

**Verification:** save a recipe, open it, edit it, publish it and try again.
**Source:** user decision, 2026-09-20: a preview would be a third view of the same
recipe to build and keep true, when saving already produces the reader's own view
and the recipe stays editable until it becomes visible to anyone else.

### FLOW-030 — The editor says which parts a fork will carry away

The system shall show in the editor which parts of the recipe a fork inherits and
which remain the author's own, following FORK-001 and FORK-002.

**Acceptance criteria**
- Given the editor, when the author looks at the title, the tags, the serving count,
  the ingredients and the steps, then these are shown as the parts a fork inherits
  (FORK-001).
- Given the editor, when the author looks at the description and the media, then
  these are shown as staying with the author and not travelling to a fork
  (FORK-002).
- Given that marking, when the author reads it, then it states what a fork does, not
  whether forking can be prevented — it cannot (FORK-002).
- Given a recipe opened for editing after a fork was made of it, when the author
  looks at the same marking, then it is unchanged, because what a fork inherits does
  not depend on the recipe.

**Verification:** compare the marking in the editor against FORK-001 and FORK-002
part by part.
**Source:** user decision, 2026-09-20: an author writing a description or attaching
photographs should know, while they do it, that this part is theirs and the method
is what travels. This marking is also the whole of the product's answer to an author
who writes the method into the description and leaves the steps empty: the product
warns before the fact and never inspects afterwards, because inspecting would be a
judgement on the writing (NGOAL-007,
[ADR — quality is sorted by readers](../decisions/20260920-quality-sorted-by-readers.md)).

### FLOW-031 — A reference is inserted by a trigger character or by a control on the step

The system shall offer the recipe's own ingredients for insertion into a step's text
both when the author types a trigger character and when the author uses a visible
control on that step, and shall offer no ingredient of any other recipe.

**Acceptance criteria**
- Given a step being written, when the author types the trigger character, then the
  recipe's ingredients are offered and choosing one inserts a reference at that
  position.
- Given the same step, when the author uses the control on the step instead, then the
  same list is offered and the result is the same reference, so an author who never
  discovers the trigger character loses nothing.
- Given the list of ingredients offered, when it is shown, then it holds only
  ingredients of this recipe (DOM-007) and no ingredient whose unit type is
  `taste_unmeasured`, because such an ingredient carries no quantity to show
  (DOM-002).
- Given an inserted reference, when the author deletes the character range it
  occupies, then the reference is gone and the step's text is left as the author
  typed it.
- Given a reference to an ingredient, when that ingredient is deleted from the
  recipe, then DOM-007 governs and the reference is dropped.

**Verification:** insert references both ways in one step, attempt to reference a
`taste_unmeasured` ingredient, and delete a referenced ingredient.
**Source:** user decision, 2026-09-20, adopting the spec author's proposal. The
trigger character is for whoever expects one; the control is why the feature is not
hidden from everybody else, and the product's readers and authors are not programmers
(`market-context`).

### FLOW-032 — A reference renders as the quantity alone

The system shall render an ingredient reference in a step as that ingredient's
quantity and unit at the reader's current scale, and shall render no part of the
ingredient's name.

**Acceptance criteria**
- Given a recipe authored for 4 servings with 200 g of flour and a step reading
  `всыпьте [200 г] муки`, when the reader asks for 8 servings, then the step reads
  `всыпьте 400 г муки` and the word `муки` is the author's own text, untouched.
- Given the same step at any scale, when it is read, then the value in the reference
  equals the value the ingredient list shows for that ingredient (DOM-007).
- Given a scaled value that the rounding grid changed, when the step is read, then
  the reference carries the same derived marker the list carries (SCALE-004), so the
  step never states a rounded number as though it were exact.
- Given a value held at the floor of its unit type, when the step is read, then it
  shows the floor exactly as the list does (SCALE-005).
- Given the editor, when the author inserts a reference, then what they see in place
  of it is the authored quantity, because the authored scale is the recipe's own.

**Verification:** scale a recipe up, down and to a value the grid rounds, and compare
every step's reference against the ingredient list line by line.
**Source:** user decision, 2026-09-20, adopting the spec author's proposal. The name
is left out because a substituted name would arrive in one grammatical case and every
sentence needs its own; the author already writes the word, and only the number has
to change under scaling.
