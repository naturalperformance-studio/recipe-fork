# A quantity is derived by provenance, not by value

**Status:** accepted, corrects SCALE-004 and CON-004 (1)

**Context:** Two families of wording marked a quantity as derived differently
(SD-012 item 2). CON-004's statement marked what the system derived; SCALE-004,
CON-004 (1) and the glossary marked what differs from the authored value. They part
where scaling and rounding land on the authored number: a recipe for 4 with one
indivisible egg, read at 2 servings, computes 0.5, is held at the floor, and shows
1 egg.

**Decision:** While a recipe is displayed at a scale factor other than 1, every
displayed quantity is marked as derived, including one whose displayed value equals
its authored value; where rounding moved a quantity, its direction is shown
(SCALE-004). At a factor of exactly 1 nothing is derived.

**Consequences:** One rule, testable without knowing the authored values: the
marker follows the scale, not the number. The egg in the example is marked derived
and says the exact value was lower. At any scale other than the authored one every
quantity line carries the marker, so the marker says "this view is scaled" as much
as "this number changed".

**Alternatives considered:**
- *By value — mark only what differs from the authored number.* Rejected: the egg
  would carry a rounded-up marking from SCALE-005 without being derived, and a
  number the system computed would be presented as the author's.
