---
slug: FLOW
status: closed
updated: 2026-09-27
ids: [FLOW-027, FLOW-031, FLOW-032]
---

# Steps: timers and ingredient references

Split on 2026-09-27 from [40-editor.md](40-editor.md); no identifier changed.

### FLOW-027 — A step carries one timer, attached inside it

The system shall let the author attach one timer to a step and enter its duration
there, shall hold the timer as part of that step, and shall not create a second
timer on a step that already carries one.

**Acceptance criteria**
- Given a step reading `в духовку на 190 °C на 30 минут`, when the author attaches
  a timer and enters 30 minutes, then the timer belongs to that step.
- Given a step that already carries a timer, when the author looks at it, then
  the control for attaching a timer is still shown, and choosing it creates no
  second timer and shows the statement of GUIDE-007 (DOM-013).
- Given two waits in sequence, when the author writes them, then they are two
  steps with one timer each.
- Given a timer being attached, when the author is asked for anything, then a
  duration is the only thing required of them.
- Given a timer with no name of its own, when the author looks at it, then the
  name it will announce itself by is shown, derived from the step (FLOW-016), and
  correcting it by hand is offered there.
- Given the author corrects that name, when they leave it, then the timer holds
  what they wrote and stops following the step's text.
- Given a name the author corrected, when they clear it, then the timer's name
  follows the step's text again and nothing of what they had typed is kept.
- Given a name the author is typing, when it passes 60 characters, then FLOW-028
  governs — it is marked on leaving the field and refused at submission
  (DOM-012), never refused while typing.
- Given a step with a timer, when the step is deleted, then its timer is deleted
  with it.
- Given a step with a timer, when the step is moved, then the timer moves with it
  (FLOW-025).
- Given a duration the author has not entered, when the recipe is saved, then the
  timer is not stored, because a timer without a duration is not a timer.

**Verification:** attach a timer, try to attach a second through every path the
editor offers, delete the step, move the step, and save a recipe with an
unfinished timer.
**Source:** user decision, 2026-09-20. One timer per step, because FLOW-016 names
a timer by its step and two timers on one step would carry one name between them;
and two waits in sequence are already two steps in the kitchen. The name is shown
rather than asked for, and corrected rather than filled in, so that the author who
is satisfied with the derived name does nothing at all.
**Correction, 2026-09-27:** criterion 2 said a second timer was "not offered at
all", which left GUIDE-007 with no attempt to answer (SD-005). By user decision of
that date the control stays, and choosing it on a step that has a timer is the
attempt GUIDE-007 explains.
**Note:** what the author is told about the first 60 characters of the step
mattering is in `author-guidance`, not here — this requirement is what the editor
accepts, that one is what the editor teaches.

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
  ingredients of this recipe (DOM-007), including one whose quantity is not yet
  entered and one whose unit type is `taste_unmeasured`.
- Given an inserted reference, when the author deletes the character range it
  occupies, then the reference is gone and the step's text is left as the author
  typed it.
- Given a reference to an ingredient, when that ingredient is deleted from the
  recipe, then DOM-007 governs and the reference is dropped.
- Given a referenced ingredient, when the author changes its unit to "to taste" or
  from it to a measured unit, then the change is allowed and every reference to it
  stays and renders by FLOW-032.

**Verification:** insert references both ways in one step, reference a
`taste_unmeasured` ingredient and one with no quantity yet, switch a referenced
ingredient to "to taste" and back, and delete a referenced ingredient.
**Correction, 2026-09-27:** criterion 3 kept `taste_unmeasured` ingredients out of
the list, and nothing said what a reference to an ingredient with no quantity
yet, or one switched to "to taste", shows (SD-014). By user decision of that date
both are referenced and render by FLOW-032; the exclusion is lifted.
**Source:** user decision, 2026-09-20, adopting the spec author's proposal. The
trigger character is for whoever expects one; the control is why the feature is not
hidden from everybody else, and the product's readers and authors are not programmers
(`market-context`).

### FLOW-032 — A reference renders as the quantity alone

The system shall render an ingredient reference in a step as that ingredient's
quantity and unit at the reader's current scale — or as the words "to taste" where
the ingredient's unit type is `taste_unmeasured`, or as an empty placeholder where
its quantity is not yet entered — and shall render no part of the ingredient's
name.

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
- Given a reference to a `taste_unmeasured` ingredient, when the step is read at any
  scale, then the reference reads "to taste" in the interface language (`по вкусу`
  in Russian), and nothing in it is marked as derived.
- Given a reference to an ingredient whose quantity is not yet entered, when the
  step is shown in the editor or in the saved view of FLOW-029, then the reference
  shows a visibly empty placeholder, followed by the unit where one is chosen; the
  placeholder occupies a character range and is removed as FLOW-031 (4) removes
  any reference.

**Verification:** scale a recipe up, down and to a value the grid rounds, and compare
every step's reference against the ingredient list line by line; read a reference
to a "to taste" ingredient and one to an ingredient with no quantity.
**Completed 2026-09-27:** the two references that have no quantity to show (SD-014;
user decision).
**Source:** user decision, 2026-09-20, adopting the spec author's proposal. The name
is left out because a substituted name would arrive in one grammatical case and every
sentence needs its own; the author already writes the word, and only the number has
to change under scaling.
