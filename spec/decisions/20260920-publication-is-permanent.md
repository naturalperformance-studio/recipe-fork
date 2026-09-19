# Publication is permanent

**Status:** accepted

**Context:** The draft did not say whether an author could take a published
recipe back (its section 11). An earlier answer in the same session allowed the
author to hide a published recipe without deleting it; the product owner then
reversed that on consideration, and this ADR records the settled position.

The tension is real either way. Hiding gives an author a way out of a regretted
publication — a personal story, a photo they did not own, a recipe attached to a
person they no longer want to be associated with. Permanence gives every reader,
every fork and every favourite a reference that cannot be pulled out from under
them by one person's second thought.

**Decision:** Once a recipe is published to the catalogue it stays published.
The owner has no action that hides, unpublishes or deletes it. Only a moderator
can remove published content, and only through the report and review path.

**Consequences:**
- Provenance chains and favourites never break because of an owner's decision.
  The only way a parent becomes unavailable is moderator removal (ST-007), so
  the unavailable-parent case is rare rather than routine.
- An author who regrets a publication has no self-service remedy. The only path
  is to report their own recipe and wait for the moderator, up to a week.
- The moderator becomes the single point of removal for every case, including
  the sympathetic ones. Queue load rises with the size of the catalogue.
- Nothing about a published recipe is reversible: neither its content (CON-003)
  nor its presence. This is the strongest form of the draft's immutability
  principle and should be stated plainly in the terms of use (section `legal`).

**Alternatives considered:**
- *Owner may hide a published recipe, keeping it reachable as a fork parent and
  from favourites.* Rejected by the product owner. It would have kept chains
  intact while giving authors a remedy, at the cost of a recipe that is present
  for some paths and absent for others.
- *Owner may hide a published recipe completely.* Rejected: one button would
  retroactively break other people's chains and favourites.
- *Owner may delete a published recipe outright.* Rejected: it destroys the
  parent of every fork descended from it.
