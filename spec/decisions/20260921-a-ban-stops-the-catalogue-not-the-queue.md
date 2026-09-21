# A publishing ban stops the catalogue, not only the queue

**Status:** accepted; widens ACT-005 and ACT-007

**Context:** ACT-005 defined a publishing ban as the refusal of "every
subsequent moderation submission". The transition table in `entity-states`
carries a further guard the requirement never mentioned: the owner of a
`link_shared` recipe may publish it only while not publishing-banned. That
transition creates no submission and passes no moderation — MOD-001 says so
explicitly, because the content was already approved and is frozen.

So the two disagreed about one act, and the disagreement was not cosmetic. Read
by ACT-005 alone, a banned author holding any recipe that had ever been approved
for a private link could put it into the catalogue at will, which is the one
route into the catalogue that never passes the moderator a second time.

**Decision:** a ban stops every act of that account that would put one of its
recipes into the catalogue: submission for publication, submission for a private
link, and publication from `link_shared`. It stops nothing else. Revoking a
private link and issuing a new one stay available to a banned author, because the
recipe is already externally visible to whoever they chose and neither act
reaches the catalogue (ST-005).

**Consequences:**
- The guard already in the transition table is now the same rule as ACT-005, and
  a developer reading either arrives at the same product.
- A banned author keeps everything ACT-005 always left them — writing, forking,
  editing a private recipe, scaling, searching, favourites — and their published
  recipes stay in the catalogue (ACT-006).
- A banned author can still hand a private link to whoever they like. That is
  deliberate: a ban answers what the catalogue shows to everybody, and a private
  link is the author choosing their own recipients.
- ACT-007 has one more refusal to explain in the application, in the same words,
  because there is no channel to the author outside it (CON-001).

**Alternatives considered:**
- *Keep ACT-005 as it was and delete the guard from the transition table.*
  Rejected: an approved `link_shared` recipe would become a stored permit to
  publish that survives a ban, and an author who expected one could collect them
  before misbehaving.
- *Extend the ban to revoking and reissuing a private link as well.* Rejected:
  neither act makes anything more public, and taking the link controls away would
  leave a banned author unable to stop sharing a recipe they had shared — a
  restriction pointed the wrong way.
