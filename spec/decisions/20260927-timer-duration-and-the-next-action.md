# A timer is hours and minutes, and a hint outlives a scroll

**Status:** accepted, completes FLOW-027, GUIDE-007 and the `Timer` entity

**Context:** gate 1 found two gaps (SD-030). Nothing stated what the duration
field accepts: its unit, whether zero counts, whether it has a ceiling. Nothing
stated which interaction is "the author's next action" that ends GUIDE-007's
statement, and that answer decides whether a screen-reader user can hear it.

**Decision:**
- A duration is entered as hours and whole minutes, from 0 to 99 hours and 0 to
  59 minutes. No seconds are offered. 0:00 is no duration, so FLOW-027 (11) drops
  the timer and nothing is refused.
- The next action is a touch on any field or control of the editor, or a typed
  character. A scroll, or a move of the screen reader's focus, is not one.

**Consequences:** 99:59 covers a three-day ferment. A recipe that times in seconds
rounds to a minute. An author who leaves 0:00 loses the timer silently, as they
would by leaving the field empty. The statement stays on screen through scrolling,
so a person using the screen reader can reach it and hear it.

**Alternatives considered:**
- *Minutes only.* Rejected: a four-hour braise would have to be typed as 240.
- *Hours, minutes and seconds.* Rejected: a third field for a precision kitchen
  timers here do not need.
- *A ceiling of 24 hours.* Rejected: it forbids overnight-plus ferments and
  marinades.
- *No ceiling.* Rejected: the field needs a width, and nothing needs more than
  four days.
- *Zero refused with a message.* Rejected: it is the same act as leaving the field
  empty, which FLOW-027 already answers by dropping the timer.
- *Any interaction, a scroll included, ends the statement.* Rejected: a
  screen-reader user moving onto the statement would destroy it before hearing it.
