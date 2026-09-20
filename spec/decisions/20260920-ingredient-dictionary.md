# The ingredient dictionary

**Status:** accepted

**Context:** CAT-002 filters recipes by an ingredient and orders them by how much
of it a serving contains. It rested on an assumption the domain model did not
support: that the product can tell that two recipes use the same ingredient.
`Ingredient.name` was free text, so nothing linked `курица`,
`куриное филе бедра` and `крылышки`, nothing separated a spelling variant from a
different ingredient, and a filter on `курица` would both miss recipes that use
chicken and match recipes that only mention it. Because a published recipe can
never be corrected (CON-003), every such mistake would be permanent, so the
defect accumulates in the catalogue with no path back.

**Decision:** an ingredient names an entry of an ingredient dictionary maintained
by the product owner, exactly as tags already are (DOM-008), and may carry the
author's own qualifier for the cut or part (`филе бедра`). An ingredient that
matches no entry is still accepted as free text, with a warning that filters will
not find the recipe by it and that publication makes that permanent (DOM-009,
FLOW-022). A filter matches the entry only — never a qualifier, never free text
(CAT-006).

**Consequences:**
- A filter on `курица` finds every cut of it, which is what makes CAT-002 mean
  anything at all.
- The dictionary is hundreds to thousands of entries, and the product owner
  maintains it alone while also being the only moderator (`abuse`). That is the
  real price of this decision, and it is paid continuously, not once.
- The dictionary is localised like tags, so a second language means translating
  every entry (`localization`).
- The divisibility flag moves onto the entry and the separate dictionary of
  indivisible ingredients disappears (`scaling-rules`); a free-text `piece`
  ingredient has no entry, so its author sets divisibility by hand.
- The editor now needs the dictionary to name an ingredient at all, which is why
  FLOW-001 holds it on the device.
- Recipes with free-text ingredients are invisible to ingredient filters forever
  once published. The author is warned; nobody is blocked.
- Qualifiers are free text and are not searchable, so `крылышки` cannot be
  filtered on precisely. That is the cost of a one-level dictionary and is the
  reason OQ-015 remains open.

**Alternatives considered:**
- *Keep free text and match it as text.* Rejected: the filter would work by luck,
  permanently, and CAT-002's precise ordering would sit on top of an imprecise
  match — accuracy over garbage.
- *A two-level dictionary of ingredient and part (`курица` → `крылышки`).*
  Rejected by the product owner for the MVP: it would make filtering by a part
  precise and would improve comparability between pieces of the same part, but it
  doubles the dictionary, adds a hierarchy to maintain and makes the author choose
  twice.
- *Refuse an ingredient that is not in the dictionary.* Rejected: it blocks
  authoring on the product owner's backlog.
- *A queue of requests to add an entry.* Deferred past the MVP as OQ-019: every
  request lands on the one person who is also the moderator.
- *A built-in table of average piece weights, to make quantities comparable.*
  Rejected: DOM-003 forbids it, and an invented weight produces a wrong order
  wearing the appearance of an exact one.
