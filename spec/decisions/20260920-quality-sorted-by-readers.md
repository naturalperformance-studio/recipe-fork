# Quality is sorted by readers, not by the product

**Status:** accepted

**Context:** a recipe becomes immutable the moment anyone else can see it
(NGOAL-004), so a typo survives for ever and the only remedy is a fork. That makes
it tempting to put a quality gate in front of publication — a spellchecker the
author must satisfy, or a moderator who rejects a sloppy recipe. The product owner
ruled the opposite way and gave the reason: readers are the ones who should push
well-written recipes up, and the platform is self-moderating in that sense.

**Decision:** the product checks nothing about the craft of a recipe. No spelling
check is enforced, no plausibility is validated, no quality threshold stands in
front of the catalogue, and a moderator never rejects a submission for being badly
written (NGOAL-007, MOD-004). What the product does instead is give readers the
orderings that let quality surface (CAT-001) and the report path for content that
breaks a rule rather than a taste (ABUSE).

**Consequences:**
- The moderator's job stays narrow — legality, safety and rights — which is what
  keeps one person able to do it at all (`abuse`).
- A badly written recipe is permanent, because publication is (CON-003), and its
  author's only correction is a fork of their own recipe.
- The default ordering is `newest` (CAT-001), so an unrated recipe is shown before
  anyone has judged it. The self-sorting this decision relies on therefore lags
  behind publication by however long it takes to collect ratings, and it is weakest
  exactly when the catalogue is small. Both halves of that trade were chosen
  deliberately and on the same day.
- The case the product owner raised on 2026-09-20 falls under this decision and is
  deliberately not addressed: an author may write the whole method into the
  description, which a fork does not inherit (FORK-002), leaving steps that carry
  nothing. No detector is built, because "the description is doing the steps' work" is
  a judgement about writing, and building it would create a second, unannounced quality
  policy beside this one. What stands instead is FLOW-030, which tells the author which
  parts a fork carries away while they are writing them, and the ratings that sort such
  a recipe down. The residual case is narrow: a fork of such a recipe can still read
  the method in its parent (FORK-003, FORK-004), and only a moderator's removal of the
  parent (ST-007) leaves the fork with empty steps for good.
- Warnings that already exist are not quality gates and stay: the incomplete-part
  warning (FLOW-028) reports the minimum DOM-006 needs, and the free-text warning
  (FLOW-022) reports a consequence the author cannot see. Neither judges the
  writing.

**Alternatives considered:**
- *A spellchecker or a validator the author must satisfy before submitting.*
  Rejected: it decides what good writing is on the author's behalf, in one language
  first (`localization`), and it cannot tell a typo from a regional word for an
  ingredient.
- *Let the moderator reject low-quality recipes.* Rejected: it puts taste in the
  hands of the one person who must stay fast and uncontroversial, and it would make
  every rejection arguable.
- *Allow an author to correct typos after publication.* Rejected earlier and for
  other reasons — publication is permanent
  ([ADR](20260920-publication-is-permanent.md)) — and this decision is what makes
  living with that bearable: nobody is promised an error-free catalogue.
