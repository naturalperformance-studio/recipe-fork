# Done marks live on the device

**Status:** accepted, narrows [20260921-the-cooking-card-is-one-scrolling-screen.md](20260921-the-cooking-card-is-one-scrolling-screen.md)

**Context:** Since 2026-09-21 the card was one scrolling screen on which nothing
recorded which steps were done. Planning v0.23 on 2026-10-04, the product owner
asked to mark ingredients and steps done while cooking, and chose where the marks
live from the options offered.

**Decision:** the reader marks an ingredient or a step done with a tap (FLOW-068).
Marks are kept on the device per recipe until the reader clears them, and are
never part of the recipe, its file, a fork or a sending. The card stays one
scrolling screen: there is still no current step and nothing advances by itself.

**Consequences:**
- A reader who leaves the card mid-recipe finds their place by the marks as well
  as by the scroll position (FLOW-012).
- RET-010 holds: the marks serve the reader on their own device and are sent
  nowhere.
- Marks left on a recipe stay until cleared; cooking it again starts with
  clearing them.

**Alternatives considered:** the other options offered at the planning of v0.23
are recorded in that increment (`docs/increments/v0.23.md`); the owner chose the
recommended one, marks kept until cleared.
