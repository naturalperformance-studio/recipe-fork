---
slug: GUIDE
status: closed
updated: 2026-09-20
ids: [GUIDE-001, GUIDE-002, GUIDE-003, GUIDE-004, GUIDE-005, GUIDE-006,
  GUIDE-007, GUIDE-008]
---

# What the editor teaches its author

The product refuses to judge a recipe's craft: a moderator never rejects for
quality and readers' ratings do the sorting (NGOAL-007,
[ADR — quality is sorted by readers](decisions/20260920-quality-sorted-by-readers.md)).
This section is the other half of that position. Because nothing is inspected
afterwards, everything the author needs to know has to reach them beforehand,
and it has to reach them without the product reading their recipe to decide what
to say.

Three things live here, and they are deliberately different from one another:

- **The statement before submission** (GUIDE-001, GUIDE-002). Submission is the
  most expensive act in the product and the only one with no way back. It is
  shown every time, for every author, and cannot be turned off.
- **The checklist** (GUIDE-003, GUIDE-004). Fixed advice on a screen the author
  opens when they want it, from a control in the editor. It names nothing in
  their recipe, stores nothing, and blocks nothing.
- **Hints** (GUIDE-005–GUIDE-008). Short statements at the moment the author's
  own action makes them relevant.

Advice that belongs to one field stays with that field and is not repeated here:
the free-text ingredient warning is FLOW-022, the minimum and the limits are
FLOW-028, and what a fork carries away is FLOW-030.

## Before submission

### GUIDE-001 — Submission is confirmed against what it will cost

When the owner submits a recipe for publication or for a private link, or
publishes a recipe that already holds a private link, the system shall state
what becomes permanent and shall carry the act out only after the owner
explicitly confirms.

**Acceptance criteria**
- Given a submission for publication, when the statement is shown, then it says
  that on the moderator's approval the recipe can never be edited and can never
  be taken out of the catalogue by its owner (CON-003, ST-001).
- Given a submission for a private link, when the statement is shown, then it
  says that on approval the recipe can never be edited, and that the link can be
  revoked while the recipe itself stays frozen — the two are not the same act
  (`entity-states`, `link_shared`).
- Given a recipe in `link_shared` that the owner publishes, when the statement is
  shown, then it says the recipe enters the catalogue at once with no further
  moderation and can never be taken out of it.
- Given any of the three statements, when it is shown, then it also says that the
  submission can be withdrawn until a moderator decides and that withdrawal makes
  the recipe editable again (ST-003).
- Given any of the three statements, when it is shown, then it says that the
  recipe itself can never be changed afterwards but can be forked into a new
  recipe the author then builds on (ST-004, CON-003), so the author is told what
  remains possible and not only what is lost.
- Given that part of the statement, when it is shown, then it says that a fork of
  one's own recipe brings the photographs and the description with it (FORK-007),
  because an author who believes a correction costs them their media will not
  make one.
- Given the statement, when the owner dismisses it without confirming, then
  nothing is submitted, nothing is published and the recipe keeps its state.
- Given the statement, when the owner confirms, then the transition happens as
  `entity-states` requires.
- Given a submission that DOM-006, DOM-012 or FLOW-006 refuses, when the owner
  submits, then the refusal is shown and this statement is not, because there is
  nothing to confirm.

**Verification:** submit for publication, submit for a private link, and publish
from `link_shared`; dismiss and confirm each; compare all three statements
against ST-001, ST-003 and CON-003 clause by clause.
**Source:** user decision, 2026-09-20. All three acts are irreversible in a way
nothing else in the product is, and they differ from one another in what exactly
cannot be undone, so one statement for all three would be wrong about two of
them.

### GUIDE-002 — The statement is shown every time

The system shall show the statement GUIDE-001 requires on every submission and
on every publication from `link_shared`, and shall offer no way to suppress it.

**Acceptance criteria**
- Given an account submitting its fiftieth recipe, when it submits, then the
  statement is shown exactly as it was on the first.
- Given the statement, when it is shown, then it carries no control that stops it
  appearing again, and no setting anywhere turns it off.
- Given a recipe a moderator rejected, when the owner edits and resubmits it,
  then the statement is shown again (ST-003).
- Given a recipe the owner withdrew and resubmitted unchanged, when it is
  resubmitted, then the statement is shown again.

**Verification:** submit ten recipes from one account and confirm the statement
on each; search the settings for anything that suppresses it.
**Source:** user decision, 2026-09-20: the author can press the button on the
wrong recipe or before they are finished, and after approval there is no way
back, so this is the one thing the product repeats however experienced the
author is. The checklist fades because advice goes stale for the person who
knows it; a warning about an irreversible act does not.

## The checklist

### GUIDE-003 — The checklist is a control in the editor, open to the author at any time

The system shall offer in the editor a control that opens a checklist of advice
on writing a recipe, shall make it available whatever the recipe holds and
whatever state it is in, and shall let the author do nothing there but read and
close it.

**Acceptance criteria**
- Given a new and empty recipe, when the editor opens, then the control is
  present.
- Given a recipe being edited, when the author opens the checklist, then the
  recipe is not left and closing the checklist returns to the same place in it.
- Given the checklist, when it is open, then it offers no tick boxes and no
  controls that change the recipe.
- Given the checklist, when the author closes it, then nothing was recorded about
  it having been opened or read.
- Given a recipe whose author never opened the checklist, when it is submitted,
  then submission behaves identically to one whose author read it through.
- Given the statement of GUIDE-001, when it is shown, then the checklist is not
  part of it: the checklist is reached from the editor, where the author can still
  act on it.

**Verification:** open and close the checklist from an empty recipe and from a
full one; submit two recipes, one with the checklist read and one without, and
compare.
**Source:** user decision, 2026-09-20. Advice attached to the submission dialogue
arrives when the author is finished and wants to press the button, which is the
moment they are least willing to read it and least able to act on it; a control
in the editor is reachable while there is still something to change. Reading is
never a condition of submitting, because a condition would be the product judging
the recipe through the author's own tick marks.

### GUIDE-004 — What the checklist says

The system shall present in the checklist the advice below, as advice, naming no
part of the author's own recipe.

The seven items:

1. **A step that carries a timer opens with what it is waiting for.** The
   notification carries the step's first 60 characters, so `курица в духовку на
   30 минут` announces itself and `нарежьте лук полукольцами, обжарьте… и в
   духовку` does not. Where the opening cannot be the point of the wait, the
   timer can be named by hand instead (FLOW-016).
2. **One action, one step.** Steps are not joined together; two waits in
   sequence are two steps, and a step carries one timer (DOM-013).
3. **A photograph or a clip belongs at the step it shows,** and is worth
   attaching at the best quality the camera gives, because it is what the reader
   checks their own pan against (FLOW-026).
4. **Quantities inside a step go in as references, not as typed numbers.** A
   reference follows the reader's serving count; a typed number does not, so a
   reader cooking a double portion reads 400 g in the list and 200 g in the step
   (FLOW-031, FLOW-032).
5. **An ingredient is picked from the suggestions wherever one fits.** What is
   typed as free text is never found by an ingredient filter, and after
   publication that cannot be changed (FLOW-022, CAT-006).
6. **The method goes in the steps, not in the description.** When somebody else
   forks the recipe they carry the ingredients and the steps away and leave the
   description behind, so a method written in the description is a method nobody
   can build on (FORK-002, FLOW-030).
7. **The serving count is the number you actually cooked for.** Every quantity a
   reader ever sees is computed from it (SCALE-001).

**Acceptance criteria**
- Given the checklist, when it is displayed, then it holds these seven items.
- Given any item, when it is read, then it says what a good recipe does and
  refers to nothing in the recipe the author is writing.
- Given the checklist, when it is displayed, then no item is a verdict on the
  author's recipe, because the product inspects no recipe for craft (NGOAL-007).
- Given a recipe that follows none of the seven, when it is submitted, then it is
  accepted, provided DOM-006, DOM-012 and FLOW-006 are satisfied.
- Given item 1, when it is read, then the 60 characters it names are the same 60
  characters FLOW-016 cuts at, and the two are never allowed to disagree.

**Verification:** read the checklist against NGOAL-007 item by item; submit a
recipe that follows none of the advice and confirm it is accepted.
**Source:** user decision, 2026-09-20. Items 1, 2 and 3 are the product owner's
own; items 4, 5, 6 and 7 were proposed by the spec author and adopted. Each one
is a consequence of a decision recorded elsewhere, so the checklist states no
rule that is not already true of the product.
**Note:** item 3 says "the best quality the camera gives" rather than naming a
resolution, because what survives upload is decided by the client-side
re-encoding that OQ-003 has not yet settled. When OQ-003 closes, this item is
re-read against it.

## Hints

### GUIDE-005 — A hint appears only where the author's own action gives it a reason

The system shall show a hint in the editor only at a moment created by the
author's own action, and shall show no hint that is present regardless of what
the author is doing.

**Acceptance criteria**
- Given an author who never does the thing a hint is about, when they write a
  whole recipe and submit it, then that hint is never shown.
- Given a hint, when it is shown, then it blocks nothing, refuses nothing and
  delays nothing the author was doing.
- Given a hint, when it is shown, then no record is kept of its having been seen:
  whether it is shown is recomputed from the recipe and the author's action every
  time, and there is nothing to dismiss.
- Given a second device holding the same key file, when the author writes a
  recipe there, then hints behave identically, because nothing about them is held
  per account or per device.
- Given a hint and a warning of FLOW-028 that apply at the same moment, when both
  apply, then both are shown and neither replaces the other.
- Given any hint, when it is shown, then it reports a fact about how the product
  behaves and never a judgement about the recipe (NGOAL-007).

**Verification:** write one recipe that triggers no hint and one that triggers
every hint; repeat the second on a freshly installed second device and confirm
the hints are identical.
**Source:** user decision, 2026-09-20. A hint that is always on the screen stops
being read by the third recipe, and a hint that has to be dismissed is a flag to
store per account and keep in step across devices, bought for nothing. Deriving
it fresh each time costs nothing and behaves the same everywhere.

### GUIDE-006 — A long step carrying an unnamed timer is shown what its notification will say

When the author leaves a step that carries a timer whose name they have not set
and whose text holds more than 60 characters, the system shall show what that
timer's notification will read and shall offer to correct the name there.

**Acceptance criteria**
- Given such a step, when the author leaves it, then what is shown is the text
  the notification will carry, cut exactly as FLOW-016 cuts it, with the
  ellipsis.
- Given that display, when the author reads it, then it states that this is what
  the notification will say and offers to name the timer differently, and it does
  not tell the author to rewrite the step.
- Given that display, when the author names the timer from it, then the timer
  holds that name and the display does not appear for this step again while the
  name is set (FLOW-016).
- Given a step of 60 characters or fewer carrying a timer, when the author leaves
  it, then nothing is shown, because nothing will be cut.
- Given a step of more than 60 characters carrying no timer, when the author
  leaves it, then nothing is shown, because no notification will be built from
  it.
- Given a timer the author already named, when they leave its step however long,
  then nothing is shown, because the step's length no longer decides anything.
- Given an unnamed timer whose step the author rewrites so the decisive words
  come first, when they leave it again, then the new opening is shown, because
  this reports a fact and not an error that was corrected.
- Given the display, when it is shown, then nothing is refused and the step is
  stored exactly as typed: submission does not depend on it (FLOW-027, DOM-012).

**Verification:** write steps of 59, 60 and 200 characters, each with and without
a timer, and each with the timer named and unnamed; compare what is shown against
the announcement FLOW-016 produces.
**Source:** user decision, 2026-09-20: the author must not learn where the cut
falls by having a timer announce the wrong half of a step while their hands are
full. Showing the result rather than the rule is deliberate — the author sees
whether their own opening survives without counting characters — and the
correction is offered in the same place, because the author who has just seen the
problem is the one able to fix it.

### GUIDE-007 — A second timer on a step explains itself

If the author attaches a timer to a step that already carries one, then the
system shall state that a step carries one timer and that two waits in sequence
are written as two steps.

**Acceptance criteria**
- Given a step that already carries a timer, when the author attempts to attach
  another, then the statement is shown and no second timer is created (DOM-013).
- Given that statement, when it is shown, then it names splitting the step as
  what to do instead, so the author is not left with a refusal and no way
  forward.
- Given a step with no timer, when a timer is attached, then nothing is shown.
- Given the statement, when the author closes it, then the step is unchanged and
  the timer it already had still holds its duration.

**Verification:** attempt a second timer through every path FLOW-027 leaves open
and confirm both the statement and that the step is untouched.
**Source:** user decision, 2026-09-20.

### GUIDE-008 — References are offered where the first step is written

When the author begins the first step of a recipe that already holds at least one
ingredient, the system shall state that a quantity can be inserted as a reference
and that a reference follows the reader's serving count while a typed number does
not.

**Acceptance criteria**
- Given a recipe holding at least one ingredient, when the author begins its
  first step, then the statement is shown.
- Given the statement, when it is shown, then it names both ways of inserting a
  reference that FLOW-031 offers — the trigger character and the control on the
  step.
- Given a recipe that holds no ingredient yet, when the author begins a step,
  then nothing is shown, because there is nothing to reference.
- Given the statement, when the decision to show it is taken, then no step's text
  was read to take it: the trigger is that the first step is being written and
  that the recipe has ingredients, never what the author has typed (NGOAL-007,
  GUIDE-005).
- Given the statement, when the author ignores it and types a number by hand,
  then the number is accepted, nothing is marked, and the recipe submits normally.

**Verification:** begin the first step of a recipe with ingredients and of one
without; type a quantity by hand and confirm nothing objects.
**Source:** user decision, 2026-09-20. The trigger is the first step rather than
anything in the text, because a trigger that watched what the author typed would
be the product reading their recipe to decide whether it is written well, which
is exactly what NGOAL-007 forbids. The cost is accepted: an author who writes
their first step before adding any ingredient never sees this, and reaches the
same advice through the checklist (GUIDE-004, item 4).
