# The moderator is not exempt from their own content

**Status:** accepted; widens MOD-013, removes an exemption from FLOW-047

**Context:** FLOW-047 ended with a criterion saying that no moderator action is
offered on a moderator's own recipe. MOD-013, written the day before, says the
opposite for one action: a moderator may decide a submission they made
themselves, because at the first release the product owner is the only author and
the only moderator (`mvp-boundary`).

Taken together the two left states with no exit. Only a moderator removes a
`published` recipe (ST-002) and its owner never can (ST-001), so a moderator's
own published recipe could be removed by nobody at all. Only a moderator leaves
`reported_hidden`, by dismissing the reports or upholding them, so a moderator's
own recipe hidden by three counted reports would stay hidden permanently. With
one person in the role, "permanently" is the literal outcome and not a corner
case.

**Decision:** Every moderator action is available on the moderator's own recipes
on the same terms as on anyone else's: deciding a submission (MOD-013), removing
a published recipe with its category, dismissing or upholding reports, and
clearing a safety flag. Ownership suspends nothing.

**Consequences:**
- No state in the recipe machine has an exit that depends on a second person
  existing.
- Nothing in the product checks a moderator acting on their own work. This is
  stated rather than hidden, and it is not new: MOD-013 already accepted it for
  approvals, which is the more consequential direction — a moderator who approves
  their own submission puts content in the catalogue, while one who removes their
  own recipe only takes their own work out of it.
- The answer to that, when it matters, is a second moderator. ACT-003 already
  makes the role assignable, and forbidding self-moderation becomes worth writing
  on the day there is somebody to hand the work to.
- The permission matrix in `actors` now says which column wins where the owner
  column and the moderator column disagree. *Corrected 2026-09-21:* it first
  said the moderator column always wins, which reached rows this decision never
  touched — it would have let a moderator rate and favourite their own recipe
  (against RATE-004 and FLOW-040) and taken their own drafts away from them
  (against ST-003, ST-010 and FLOW-034). What ownership does not suspend is a
  moderator's own actions; the rest of the matrix is read from the owner column
  as it always was.

**Alternatives considered:**
- *The moderator decides their own submissions but may not act on their own
  published recipes.* Rejected: it is exactly the combination that produced the
  dead ends, and it would need a further invented rule — an automatic return from
  `reported_hidden` after some period — to give the states an exit. That rule
  would apply to every author, not only the moderator, and it would let anyone
  wait out a report.
- *Keep FLOW-047 and narrow MOD-013 instead, so the moderator acts on nothing of
  their own.* Rejected: at a launch with one person it means nothing can ever be
  published, which is what MOD-013 was written to prevent.
