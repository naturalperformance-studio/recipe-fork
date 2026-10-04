# A step may carry several timers

**Status:** accepted, supersedes in part [20260920-timer-named-by-its-step.md](20260920-timer-named-by-its-step.md)

**Context:** Since 2026-09-20 a step carried at most one timer (DOM-013), because
a timer was named by its step and two timers on one step would share a name; two
waits in sequence were to be written as two steps. Planning v0.24 on 2026-10-04,
the product owner chose to let a step hold more than one timer, each with its own
name and duration.

**Decision:** a step carries from none to five timers (DOM-024). Each timer may be
named by its author; an unnamed one is still named from its step's text, and
unnamed timers of one step are told apart in a way the team chooses (FLOW-016).
Each starts, rings and is stopped on its own. DOM-013 is retired; FLOW-027 and
GUIDE-007 now speak of a sixth timer, not a second.

**Consequences:**
- A step like "boil the pasta 10 minutes, meanwhile reduce the sauce 6 minutes"
  can be written as one step with two timers.
- The derived name of a timer is no longer unique within a step, so the product
  has to tell same-step timers apart; across steps it still does not.
- The recipe file and forks carry a list of timers per step; recipes with one
  timer read as before.
- The cap of five bounds what a card and a notification shade have to show for
  one step.

**Alternatives considered:**
- *Keep one timer per step.* The owner's choice overturned it.
- *No cap.* Rejected as the req-forge proposal: every other list in a recipe has
  a limit (DOM-012), and five covers any step a cook would write.
