# A timer is the application's own and rings like an alarm

**Status:** accepted, adds FLOW-013 (14–16)

**Context:** FLOW-013 required a timer to announce itself with the application
closed but said nothing about how it sounds. The v0.2 build announced an ended
timer with an ordinary notification sound — one short beep, easy to take for a
messenger message in a kitchen. At the demo of 2026-10-02 the user asked whether
timers should be set in the device's clock application instead, so that they ring
properly.

**Decision:**
- Timers stay the application's own: counted down on their step, announced by
  the application, the announcement opening the card at the step.
- An ended timer rings like an alarm clock — the device's alarm sound, repeating,
  with vibration — until the reader stops it, and falls silent by itself
  2 minutes after it began.
- Two ended timers ring until each is stopped or falls silent.

**Consequences:** the ringing cannot be missed or mistaken for a message, and
the timer keeps its name, its step and its countdown on the card. Keeping a timer
alive with the application closed rests on a platform mechanism the store has to
accept before release (OQ-039). A ringing timer that nobody stops goes quiet
after 2 minutes; a cook out of earshot for longer finds it finished on its step.

**Alternatives considered:**
- *Hand each timer to the device's clock application.* Rejected: the step would
  lose its countdown, the timer its recipe and step name, and the announcement
  could not open the card at the step — FLOW-013 (3, 4, 6) and FLOW-016 would go.
- *Keep the application's timers and add an optional "set in clock" action.*
  Not taken; the alarm-like ringing answered the actual complaint.
- *A full-screen alarm screen over the lock screen.* Rejected: it needs a
  permission the store restricts to alarm and calling applications.
