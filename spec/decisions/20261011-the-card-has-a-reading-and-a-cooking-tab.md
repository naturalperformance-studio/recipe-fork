# The card has a tab for reading and a tab for cooking

**Status:** accepted, supersedes [20260921-the-cooking-card-is-one-scrolling-screen.md](20260921-the-cooking-card-is-one-scrolling-screen.md)

**Context:** Since 2026-09-21 the card was one scrolling screen with no current step,
built for the bench: 56 dp lines, large targets. Touching the v0.26 build on
2026-10-09, the product owner found that interface uncomfortable when not cooking —
reading a recipe to decide on it — and asked for the card to be split into "View"
and "Cooking", always visible, switched by swipe too, with nothing on Cooking that
distracts from cooking. Done marks (FLOW-068) already existed, so a current step
could be derived without storing progress.

**Decision:** the card has two tabs (FLOW-070). View is compact and complete — the
description, the summary, the provenance, forking as its primary action and the
edit action. Cooking holds only the equipment, the ingredients and the steps, at
the cooking card's sizes (A11Y-003, A11Y-007); a tap marks a line done; the first
unmarked step is the current one and marking a step brings the next into view;
the screen stays awake there (FLOW-014). Nothing stores a current step and nothing
advances between steps.

**Consequences:**
- "The cooking card" in the specification is the Cooking tab; View is held to the
  ordinary 48 dp targets.
- The fork action is not on Cooking; a reader forks from View, where it is always
  in reach.
- The current step costs nothing to keep: it is read from the marks the reader
  already has, and RET-010 is untouched.
- The card restores a place per tab, and opens on the tab last chosen.

**Alternatives considered:**
- *Keep one screen.* Rejected by the owner after using it: what suits busy hands
  is in the way when reading.
- *A segmented button to switch views.* M3's usual control for views of one thing,
  but the two modes differ in their actions and layout, not only in presentation;
  primary tabs fit, and swiping between them is their standard behaviour (the
  lead's check, 2026-10-09).
- *A cooking mode with forward and back between steps.* Not asked for; the
  derived current step gives the guidance without a control to learn.
