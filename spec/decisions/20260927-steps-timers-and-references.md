# A second timer is attempted and explained; every ingredient can be referenced; the notification preview renders at the authored count

**Status:** accepted, corrects FLOW-027, FLOW-031, FLOW-032, GUIDE-006 and GUIDE-007

**Context:** GUIDE-007 answered an attempt FLOW-027 left no path to, and closed a
statement GUIDE-005 says there is nothing to dismiss (SD-005). A reference could
point at an ingredient with no quantity, and nothing said what it shows (SD-014).
GUIDE-006 did not say at which serving count its preview renders a reference, and
a short step could be cut for a reader without its author ever being shown
(SD-015).

**Decision:**
- The timer control stays on a step that has a timer; choosing it creates nothing
  and shows GUIDE-007's statement, which lasts until the author's next action.
- Any ingredient of the recipe can be referenced. A `taste_unmeasured` one renders
  as "to taste"; one with no quantity yet renders as an empty placeholder that is
  removed like any reference. Switching a referenced ingredient to or from "to
  taste" is allowed and keeps its references.
- GUIDE-006 renders at the authored count, says quantities follow the reader's
  count, and fires on any step with an unnamed timer whose first 60 characters
  hold a reference.

**Consequences:** The author meets the one-timer rule when they try to break it. A
step such as `посолите [по вкусу]` reads as a sentence. Authors who write steps
before quantities are not stopped. Some short steps now get the preview although
their text fits at the authored count.

**Alternatives considered:**
- *No path to a second timer, GUIDE-007 retired.* Rejected: the rule would be
  learnt only from the checklist.
- *A close control on the statement.* Rejected: GUIDE-005.
- *No reference to an ingredient without a quantity.* Rejected: it orders how an
  author must write.
- *Dropping references on a switch to "to taste", or refusing the switch.*
  Rejected: both break or block the author's text.
- *Preview at the authored count only, with no extra trigger.* Rejected: the
  author would not see a cut their readers get.
