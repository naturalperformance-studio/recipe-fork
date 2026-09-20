---
slug: CAT
status: blocked
updated: 2026-09-20
ids: [CAT-001, CAT-002]
---

# Catalogue and search

The public catalogue is where a reader meets a recipe they did not already know
about. Searching works over the catalogue and, by the same rules, over the
reader's own favourites (draft section 7).

The screens themselves — their empty, loading, error and offline states — are in
[`flows`](70-flows.md); what is here is the ordering and the filters.

### CAT-001 — The reader chooses how results are ordered

The system shall order the catalogue and the results of a search by a field the
reader chooses, and shall apply that choice to both.

**Acceptance criteria**
- Given the catalogue with no query, when the reader chooses an ordering, then
  the recipes are ordered by it.
- Given a query, when the results are shown, then they are ordered by the same
  chosen field, and choosing another reorders them without repeating the query.
- Given the reader's favourites, when they are searched, then the same orderings
  are offered there.
- Given a chosen ordering, when the reader leaves the catalogue and returns
  within the same session, then the choice is still in force.

**Verification:** compare the catalogue and a query's results under each offered
ordering.
**Source:** user decision, 2026-09-20: the reader picks the ordering rather than
the product picking one for them, which is also why rating may take part in it
without becoming the only way a recipe is ever found.
**Blocked by:** OQ-014.

### CAT-002 — Filtering by an ingredient orders by how much of it there is

Where the reader filters by an ingredient, the system shall order the results by
the quantity of that ingredient for one serving, largest first.

**Acceptance criteria**
- Given a filter on chicken, when two recipes both use it, then the one using
  more chicken per serving appears first.
- Given a recipe authored for 4 servings with 500 g of the ingredient and one
  authored for 2 servings with 300 g, when both match, then the second appears
  first, because 150 g per serving exceeds 125 g.
- Given a filter on an ingredient, when the order is computed, then neither the
  author nor the moderator has marked anything as a main ingredient: the
  quantity is taken from the recipe as stored.

**Verification:** filter on one ingredient across recipes authored for different
serving counts and compare against hand computation.
**Source:** user decision, 2026-09-20. The draft asked for a filter by main
ingredient (section 7); rather than add a field the author must fill and can
never correct after publication (CON-003), the product infers importance from
how much of the ingredient a portion actually contains.
**Blocked by:** OQ-015.

## Open

- OQ-014 — which fields the reader may order by, and which one is in force
  before they choose.
- OQ-015 — how recipes are ordered by an ingredient's quantity when the same
  ingredient is expressed in unit types that cannot be compared.
