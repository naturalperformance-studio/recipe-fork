# The card survives the process, the place is the line at the top, and a refused notification is said at the timer's start

**Status:** accepted, corrects FLOW-012, FLOW-013 and A11Y-007; narrows a
consequence of [20260921-the-cooking-card-is-one-scrolling-screen.md](20260921-the-cooking-card-is-one-scrolling-screen.md)

**Context:** FLOW-012 restored the place scrolled to and no criterion observed it,
and whether scale and place survive the operating system ending the application
was read two ways (SD-026). FLOW-013 asked for the notification permission
"again" where the platform often cannot ask, placed no first request, and
disagreed with A11Y-007 on whether a timer with notifications refused is heard
(SD-021).

**Decision:**
- The place is the ingredient line or step at the top of the screen, restored at
  the top whatever changed the layout meanwhile.
- The recipe, its serving count, its fixed ingredients and its place survive the
  process being ended; the device holds them for that purpose and sends them
  nowhere (RET-010).
- The permission is first requested at the first timer start. Where the platform
  will not ask again, the application says the timer will not be heard while it
  is closed, and leads to the system's settings. With notifications refused,
  nothing sounds while the application is closed.

**Consequences:** A reader who switched to the camera mid-recipe and lost the
process comes back to the same line at the same scale. The device now holds a
little reading state — which recipe, at what scale, where — which the earlier ADR
had said nothing stores; it is state on the reader's own device, not a record kept
by the product. A reader who refuses notifications is told what it costs before
the oven is on, not after.

**Alternatives considered:**
- *Restore the scroll offset in pixels.* Rejected: a different text scale or a
  rotation lands the reader elsewhere.
- *Restore only the recipe after the process ends.* Rejected: processes are
  ended exactly when a cook switches applications.
- *The platform's request only, nothing where it will not ask.* Rejected: the
  reader would never learn why the timer was silent.
- *Ask at first launch.* Rejected: the reader has no reason to agree yet.
- *Sound the timer by another facility when notifications are refused.* Rejected:
  a second path around the reader's own refusal, and a platform dependency EXT-009
  does not name.
