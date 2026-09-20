# The editor teaches its author without inspecting the recipe

**Status:** accepted

**Context:** the product deliberately checks nothing about a recipe's craft — a
moderator never rejects for quality, and readers' ratings do the sorting
([ADR — quality is sorted by readers](20260920-quality-sorted-by-readers.md),
NGOAL-007). That leaves a gap this spec had not filled: publication is
irreversible (ST-001, CON-003), several authoring mistakes cannot be repaired
afterwards, and nothing required the product to tell the author any of it
beforehand. The most irreversible act in the product was happening with no
statement at all.

**Decision:** the editor teaches, and it does so without ever reading the
author's recipe to decide what to say. Three separate mechanisms, with
deliberately different rules (section `author-guidance`):

- A statement of what becomes permanent, shown before every submission and
  before publication from `link_shared`, confirmed explicitly, never suppressible
  (GUIDE-001, GUIDE-002). Its wording differs by act, because the three acts
  differ in what exactly cannot be undone.
- A checklist of seven fixed pieces of advice, opened from a control in the
  editor, read-only, storing nothing and blocking nothing (GUIDE-003,
  GUIDE-004).
- Hints shown only at a moment the author's own action creates, holding no state
  and needing no dismissal (GUIDE-005–GUIDE-008).

**Consequences:**
- Every author sees the irreversibility statement on every submission, including
  the author publishing their fiftieth recipe. That is a deliberate cost.
- The checklist is advice nobody has to read, so a recipe that ignores all seven
  items publishes normally. The product's answer to a badly written recipe stays
  the readers' ratings and nothing else.
- Because no hint holds state, a hint may be shown more than once to the same
  author. GUIDE-006 in particular reappears whenever a long step with a timer is
  left, by design: it shows the notification's actual text, so it is a preview
  and not a reprimand.
- Nothing about guidance is held per account, so nothing has to sync between
  devices and nothing depends on an account carrying personal data (CON-001).
- An author who writes their first step before entering any ingredient never sees
  GUIDE-008, and finds the same advice only in the checklist.
- The checklist's advice on media quality is provisional until OQ-003 sets the
  re-encoding limits.

**Alternatives considered:**
- *Show only the checklist items the recipe actually fails.* Rejected by the
  product owner: detecting a failed item means inspecting the recipe's craft,
  which is precisely what NGOAL-007 forbids, and the fact that the inspection
  would be advisory does not change what it is.
- *Show the checklist for an author's first few publications and then stop.*
  Rejected: it needs a published-recipe count per account and a rule about what
  happens on a second device, and the count is a poor proxy for whether the
  author knows the advice.
- *A "do not show again" control on the checklist.* Rejected together with the
  above: a dismissal flag is per-account state to store and keep in step for no
  gain, once the checklist is behind a control the author opens on purpose.
- *Put the checklist inside the submission dialogue.* Rejected: it arrives when
  the author is finished and wants to press the button, which is when they are
  least willing to read it and least able to act on it.
- *Tick boxes on the checklist, with submission blocked until all are ticked.*
  Rejected: that is quality control wearing the author's own hand, and it would
  make the product refuse recipes it has no business judging.
- *Tick boxes that block nothing.* Rejected as the worst of both: ticks that
  change nothing are ticked without reading.
- *A permanent line of advice under each field.* Rejected: a line that is always
  there stops being read by the third recipe.
- *Show the irreversibility statement only on the author's first publication.*
  Rejected: an author can press the button on the wrong recipe or before they
  have finished at any point, and there is no way back afterwards.
