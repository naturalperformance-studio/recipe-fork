# What an interactive element is

**Status:** accepted — completes A11Y-003 (statement, criteria 5 to 7).

**Context:** A11Y-003 gives every interactive element a 48 by 48 dp touch target
and defined the term nowhere. Gate 2 of T-044
(`docs/product/verdicts/20261002-T-044-gate2.md`, item b) named three cases it
could not read: a container that scrolls, a disabled control, and text whose only
answer is focus, selection or copying. None changed the screens built so far; each
would change the result on the first screen holding one under 48 dp.

**Decision:** an interactive element is anything that answers a tap, a long press,
a drag or a swipe with an action of the product, a link inside text and a disabled
control included, and any container that scrolls, which is measured across its
scroll direction. Text whose only answer is focus, selection or copying is not one.

**Consequences:** a sideways strip, such as a row of tags, is at least 48 dp tall
and can be caught by a thumb. A disabled control keeps its size, so it does not
jump when enabled and can be touched to learn why it is disabled. Body text keeps
its typography; a separate copy control is a control and is measured. Focus for
the screen reader stays with A11Y-005, not with size.

**Alternatives considered:**
- *A scrolling container not measured.* Rejected: a narrow sideways strip is hard
  to catch, and the cost of the rule is close to nothing because such strips are
  usually taller already.
- *A disabled control not measured.* Rejected: the product explains refusals on
  the control itself (FLOW-003 (3)), so a disabled control is still touched,
  and a size that changes on enabling is easy to get wrong.
- *Selectable text measured.* Rejected: every one-line caption would become a
  48 dp block, for a touch that is not an action of the product.
