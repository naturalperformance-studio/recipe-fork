# The cooking card is one scrolling screen

**Status:** accepted; corrects FLOW-012 and A11Y-003. Its consequence that nothing about a reader's progress is stored is narrowed by [20260927-the-card-survives-the-process.md](20260927-the-card-survives-the-process.md).

**Context:** Two requirements referred to a mechanism the specification never
contained. FLOW-012 promised to return the reader "at the step the reader had
reached", and A11Y-003 gave a touch-target figure to "the step advance" on the
cooking card. Nothing in `flows` defines a current step, a way to move from one
step to the next, or a record of which steps are done — `30-cooking` covers
timers, the screen that stays awake, the serving count, the safety flag and the
card's empty, loading and error states, and nothing else.

A developer meeting those two phrases had to invent a cooking mode, and would
then have had to decide alone what it does offline (FLOW-038), what it does with
the screen reader (A11Y-005), what it restores after the operating system ends
the application (FLOW-012), and whether progress is held per reader — which
RET-010 forbids recording.

**Decision:** the card is one scrolling screen. There is no current step, nothing
advances between steps and nothing records which are done. What the product
restores on return is the scroll position (FLOW-012), and the one place it moves
a reader within a recipe is a timer's announcement leading to its own step
(FLOW-013).

**Consequences:**
- The two phantom phrases are gone rather than implemented, and the card stays
  what the whole of `30-cooking` already describes.
- A reader with a long recipe scrolls, and the product remembers where. That is
  also what keeps the screen honest under A11Y-004, where 200 per cent text makes
  every screen a scrolling one anyway.
- Nothing about a reader's progress is stored, which RET-010 already required and
  which a step tracker would have had to argue with.
- If a cooking mode is wanted later it is a new decision with its own
  requirements, not a detail an implementer fills in from two adjectives.

**Alternatives considered:**
- *Write the cooking mode: a current step, forward and back, a done marker.*
  Rejected by the product owner for the first release. It is a real feature with
  offline, screen-reader and restore behaviour of its own, on the product's
  central screen, and neither the draft nor any decision had asked for it.
- *Leave both phrases and let the implementer decide.* Rejected: it is exactly
  the shape of gap this specification exists to close — two requirements that
  read as settled while the behaviour under them is unwritten.
