# A week, and ninety-five per cent of a month

**Status:** accepted; writes QUAL-010, completes MOD-005

**Context:** MOD-005 required the expected wait for review to be stated to the
author before they submit, and said the figure itself "is a service commitment
with a measurement method, specified in section `quality`". Section `quality`
carried nine requirements and none of them was that one. The number lived in the
draft ("publication may take up to a week", section 6.3) and in no requirement,
which meant the one commitment the product makes to a person was unwritten while
two requirements leaned on it — ABUSE-007 joined MOD-005 on 2026-09-21 by
answering claims within the same period.

**Decision:** the period is seven days. It is stated before every submission
(MOD-005) and beside the claims address (ABUSE-007), and it binds as a monthly
figure: at least 95 per cent of a calendar month's submissions decided within
seven days of being submitted, computed from the `submitted_at` and `decided_at`
the submission record already holds (QUAL-010). Submissions withdrawn by their
owner or destroyed with their owner's account count at neither end. The 48-hour
threshold on the age of the oldest queue entry (OBS-003) stays what it always
was: an early warning, not this commitment.

**Consequences:**
- The measurement needs nothing new. Two timestamps already exist on every
  submission, so RET-010, OBS-007 and ANL-001 are untouched and no record about
  a person is created to watch a promise.
- Unlike every other threshold in `quality`, this one cannot be verified on a
  bench before release: it is about how long a human took. It is therefore
  computed after the fact, monthly, and the section says so rather than letting
  the bench sentence stand for everything.
- An author can be in the five per cent, and nothing tells them so: MOD-015
  delivers a decision and never its absence, so a late submission looks exactly
  like an early one until it is decided.
- A monthly figure below 95 per cent has its cause recorded, on the same footing
  as a month below the availability figure (QUAL-006), and a signal carries it
  (OBS-001, OBS-003) because the cost here is accepted rather than prevented.

**Alternatives considered:**
- *48 hours, matching the signal.* Rejected: one number for the whole product is
  attractive, but the signal exists to warn the operator early, and turning an
  early warning into a promise to authors means promising that one person with a
  job never sleeps through a weekend. The promise would break first and teach
  everyone to disbelieve it.
- *Two figures on the screen — "usually 48 hours, never more than a week".*
  Rejected: an author remembers the smaller number and reads the second day as a
  broken promise, while `quality` would still have to bind exactly one of them.
- *Seven days for every submission, with no percentage.* Rejected: it is broken
  by a single week of influenza, and a commitment that a holiday can falsify is
  one the product owner would quietly stop meaning.
- *No commitment at all; state the wait as a guess.* Rejected: MOD-005 has
  required a stated expectation since 2026-09-20, ABUSE-001 exists to protect it,
  and MOD-006 orders the queue by age to make it achievable. Three requirements
  were already built around a promise, so the promise had to be written.
