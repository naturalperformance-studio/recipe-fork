# No notification request where the platform has none

**Status:** accepted, corrects FLOW-013 (8)

**Context:** FLOW-013 (8) had the platform's notification request made at the
first timer on every device. Android 10 to 12, which QUAL-005 requires the product
to support, have no such request: notifications are on after installation and are
turned off only in the system's settings. The criterion could be met on none of
those devices, and there it collided with criteria 10 and 11 (SD-021, re-check
against spec/ @ 4cc2c18).

**Decision:** The request of FLOW-013 (8) is made only on a platform that has one
(Android 13 and later). On Android 10 to 12 nothing is requested: with
notifications on, nothing about them is shown (FLOW-013 (11)); with them off, the
application's explanation and its control to the settings are shown at the
timer's start (FLOW-013 (10)). On every version, the application's timer
notifications turned off alone, with its other notifications on, count as
notifications off: the reader is told at the timer's start that timer
notifications are off and the timer will not be heard (FLOW-013 (13)).

**Consequences:** Every supported version has one determinate path, and no
screen exists that only one version needs. A reader on Android 10 to 12 is never
asked anything, because there is nothing to ask.

**Alternatives considered:**
- *A request drawn by the application itself on Android 10 to 12.* Rejected: it
  would ask for something the reader already has by default, and would add a
  screen no other criterion describes.
- *Checking only the application-wide switch.* Rejected: with timer notifications
  turned off alone the timer would be silent and the reader never told, which
  A11Y-007 (3) forbids.
