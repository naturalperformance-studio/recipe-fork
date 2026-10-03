# A reference to part of an ingredient

**Status:** accepted, changes FLOW-031, FLOW-032, DOM-007, SCALE-007

**Context:** a step's reference rendered the ingredient's whole quantity, so the
referenced value always equalled the list line (DOM-007, SCALE-007). The user
asked, during v0.8, for a reference to a share of an ingredient — garlic split
between the marinade and the sauce of one recipe — and v0.9 built it.

**Decision:**
- A reference names the whole ingredient or a part of it.
- A part is typed in the ingredient's own unit, on that unit type's grid, and is
  greater than zero; more than the whole is refused when it is typed. "To taste"
  and an ingredient with no quantity offer no part.
- Parts of one ingredient are independent; their sum is not checked. A whole
  edited below an existing part marks the part in the editor and refuses nothing.
- A part scales by the factor of the ingredient list and is rounded on its own,
  with its own derived marker; it is not taken from the list line.
- A unit change keeps a part where the new unit is an exact unit of the same
  dimension, and otherwise turns it into a reference to the whole, and the author
  is told.

**Consequences:** DOM-007's equality holds for a whole reference only; a part's
value is its own scaled value. Parts that summed to the whole at the authored
count may, after independent rounding, not sum to the list line at another count.
How the card shows that a reference is a part is left to the team.

**Alternatives considered:**
- *A part as a fraction of the whole (½, ⅓).* Not built: a fraction of a rounded
  line would round twice, and the part would not read in the cook's own unit.
- *Refuse parts that exceed the whole after an edit.* Rejected: it would block an
  edit of the list on a step the author is not looking at.
