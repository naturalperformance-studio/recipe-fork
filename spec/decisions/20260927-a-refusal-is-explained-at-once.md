# A refused notification request is explained at once

**Status:** accepted, completes FLOW-013; qualifies [20260927-the-card-survives-the-process.md](20260927-the-card-survives-the-process.md)

**Context:** The earlier decision placed the first request at the first timer and
the application's own explanation where the platform will not ask. A reader who
refused the platform's request at a timer's start was then told nothing, while
A11Y-007 (3) says the reader has been told, and FLOW-013 (5) still carried the
phrase "asked for again", which the new criteria had replaced. The re-check of
SD-021 found both.

**Decision:** When the reader refuses the platform's request made at a timer's
start, the application's explanation — the timer will not be heard while the
application is closed, with a control to the system's settings — follows at once.
The timer starts when the reader starts it and never waits for any answer.

**Consequences:** Every reader who ends up without notifications has been told so
at the timer where it happened, before the oven is on. A reader who has just
refused sees one more message; that is the price of the promise.

**Alternatives considered:**
- *Nothing after the platform's request; the explanation only once the platform
  stops asking.* Rejected: the reader would learn why the timer was silent only
  after it had been.
