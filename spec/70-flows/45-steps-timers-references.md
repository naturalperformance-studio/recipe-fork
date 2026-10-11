---
slug: FLOW
status: closed
updated: 2026-10-11
ids: [FLOW-027, FLOW-031, FLOW-032]
---

# Steps: timers and ingredient references

Split on 2026-09-27 from [40-editor.md](40-editor.md); no identifier changed.

### FLOW-027 — A step carries up to five timers, attached inside it

The system shall let the author attach up to five timers to a step and enter each
one's duration there, shall hold the timers as part of that step, and shall not
create a sixth timer on a step that already carries five (DOM-024).

**Acceptance criteria**
- Given a step reading `в духовку на 190 °C на 30 минут`, when the author attaches
  a timer and enters 30 minutes, then the timer belongs to that step.
- Given a step that already carries a timer, when the author chooses the control
  for attaching a timer, then a further timer is added to the step with its own
  duration and name.
- Given a step that already carries five timers, when the author chooses that
  control, then no sixth timer is created and the statement of GUIDE-007 is
  shown (DOM-024).
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
- Given a step with timers, when the step is deleted, then its timers are
  deleted with it.
- Given a step with timers, when the step is moved, then the timers move with it
  (FLOW-025).
- Given a duration the author has not entered, when the recipe is saved, then the
  timer is not stored, because a timer without a duration is not a timer.
- Given a timer being attached, when its duration is entered, then it is entered
  as hours and whole minutes, from 0 to 99 hours and from 0 to 59 minutes, and no
  seconds are offered.
- Given a duration of 0 hours and 0 minutes, when the recipe is saved, then it is
  a duration the author has not entered, and criterion 11 governs: the timer is
  not stored and nothing is refused.
- Given the duration field, when the author tries to enter more than 99 hours
  and 59 minutes, then the field cannot hold it, and the longest timer a recipe
  can carry is 99 hours 59 minutes.
- Given an editable step carrying timers, when the author looks at it, then
  each timer has a control that removes it alone in one action, together with
  any name the author gave it.

**Verification:** attach a timer, try to attach a second through every path the
editor offers, delete the step, move the step, and save a recipe with an
unfinished timer, with a timer of 0 hours 0 minutes and with one of 99 hours 59
minutes; try to enter 100 hours and 60 minutes.
**Completed 2026-09-27:** what the duration field accepts was stated nowhere
(SD-030, item 1). By user decision of that date it is hours and minutes up to
99:59, and zero is not a duration. See
[ADR — a timer is hours and minutes, and a hint outlives a scroll](../decisions/20260927-timer-duration-and-the-next-action.md).
**Source:** user decision, 2026-09-20. One timer per step, because FLOW-016 names
a timer by its step and two timers on one step would carry one name between them;
and two waits in sequence are already two steps in the kitchen. The name is shown
rather than asked for, and corrected rather than filled in, so that the author who
is satisfied with the derived name does nothing at all.
**Correction, 2026-09-27:** criterion 2 said a second timer was "not offered at
all", which left GUIDE-007 with no attempt to answer (SD-005). By user decision of
that date the control stays, and choosing it on a step that has a timer is the
attempt GUIDE-007 explains.
**Changed 2026-10-02:** criterion 15 added by user decision at the v0.2 demo —
emptying the duration was the only way to remove a timer, and it was not found.
**Changed 2026-10-04 (sync v0.15–v0.24):** a step carries up to five timers
instead of one, by user decision at the planning of v0.24; the cap of five is a
req-forge proposal accepted by the user at the sync. Criteria 2, 3, 9, 10 and 15
changed accordingly. See
[ADR — a step may carry several timers](../decisions/20261004-a-step-may-carry-several-timers.md).
**Note:** what the author is told about the first 60 characters of the step
mattering is in `author-guidance`, not here — this requirement is what the editor
accepts, that one is what the editor teaches.

### FLOW-031 — A reference is inserted by a trigger character or by a control on the step

The system shall offer the recipe's own ingredients for insertion into a step's text
both when the author types a trigger character and when the author uses a visible
control on that step, and shall offer no ingredient of any other recipe. A
reference stands for the whole ingredient or for a part of it.

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
- Given an ingredient with a quantity, when the author inserts a reference to it,
  then they may choose the whole ingredient or a part of it; a part is typed in the
  ingredient's own unit, read as the quantity field reads it (DOM-019), and held
  only if it lies on that unit type's grid and is above zero (DOM-017).
- Given 200 g of flour, when the author types a part of 250 g, then the part is
  refused at entry; given 50 g, it is held.
- Given an ingredient that is "to taste" or has no quantity yet, or a `piece`
  ingredient whose divisibility is not yet answered (FLOW-001), when the author
  inserts a reference to it, then no part is offered, only the whole.
- Given three parts of one ingredient in different steps, when they are entered,
  then each is held on its own and their sum is never checked against the whole —
  an author may use garlic three ways.
- Given a part, when the author later edits the ingredient's quantity below it,
  then the edit is allowed and the editor marks the part as larger than the whole;
  nothing is refused.
- Given a part, when the author changes the ingredient's unit to another `exact`
  unit of the same dimension (g ↔ kg), then the part is kept, read in the new unit;
  given any other unit change, then the part becomes a reference to the whole
  ingredient, and the author is told so once.

**Verification:** insert references both ways in one step, reference a
`taste_unmeasured` ingredient and one with no quantity yet, switch a referenced
ingredient to "to taste" and back, and delete a referenced ingredient; insert parts
of one ingredient in several steps, try a part above the whole and one off the
grid, lower the whole below a part, and change the unit g → kg and g → шт.
**Correction, 2026-09-27:** criterion 3 kept `taste_unmeasured` ingredients out of
the list, and nothing said what a reference to an ingredient with no quantity
yet, or one switched to "to taste", shows (SD-014). By user decision of that date
both are referenced and render by FLOW-032; the exclusion is lifted.
**Source:** user decision, 2026-09-20, adopting the spec author's proposal. The
trigger character is for whoever expects one; the control is why the feature is not
hidden from everybody else, and the product's readers and authors are not programmers
(`market-context`). Amended 2026-10-03 (sync v0.3–v0.14): a reference may stand for a
part of the ingredient, criteria 7–12 (req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03, asked by the user, v0.9). See
[ADR — a reference to part of an ingredient](../decisions/20261003-a-reference-to-part-of-an-ingredient.md).
Amended 2026-10-11 (sync v0.22–v0.30): a `piece` ingredient whose divisibility is
unanswered offers no part either, as built in v0.30 — a part of it could not be
put on its grid.

### FLOW-032 — A reference renders as the quantity alone

The system shall render an ingredient reference in a step as that ingredient's
quantity and unit at the reader's current scale — or, for a reference to a part,
as the part's own quantity at that scale (SCALE-007); or as the words "to taste"
where the ingredient's unit type is `taste_unmeasured`, or as an empty placeholder
where its quantity is not yet entered — and shall render no part of the
ingredient's name.

**Acceptance criteria**
- Given a recipe authored for 4 servings with 200 g of flour and a step reading
  `всыпьте [200 г] муки`, when the reader asks for 8 servings, then the step reads
  `всыпьте 400 г муки` and the word `муки` is the author's own text, untouched.
- Given the same step at any scale, when it is read, then the value in a reference
  to the whole ingredient equals the value the ingredient list shows for that
  ingredient (DOM-007).
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
- Given a reference to a row the card cannot scale — one with no quantity yet, or
  one whose quantity is off its grid — when the step is read on the card, then the
  reference reads exactly as that row reads in the ingredient list.
- Given a reference to a part, when the step is read at any scale, then it shows
  the part multiplied by the scale factor and rounded on its own by the grid, with
  its own derived marker and direction (SCALE-004, SCALE-005), not the list line's;
  in the editor it shows the part as authored. How the card shows that a reference
  is a part is the team's.

**Verification:** scale a recipe up, down and to a value the grid rounds, and compare
every step's reference against the ingredient list line by line; read a reference
to a "to taste" ingredient and one to an ingredient with no quantity; on the card,
read a reference to an off-grid row and one to an incomplete row against the list;
scale a part up, down and to its floor.
**Completed 2026-09-27:** the two references that have no quantity to show (SD-014;
user decision).
**Source:** user decision, 2026-09-20, adopting the spec author's proposal. The name
is left out because a substituted name would arrive in one grammatical case and every
sentence needs its own; the author already writes the word, and only the number has
to change under scaling; amended 2026-10-03 (sync v0.3–v0.14): criterion 8, a reference to a row the
card cannot scale (req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03, v0.3); criterion 9, a reference to a part (req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03,
asked by the user, v0.9). See [ADR — a reference to part of an ingredient](../decisions/20261003-a-reference-to-part-of-an-ingredient.md).
