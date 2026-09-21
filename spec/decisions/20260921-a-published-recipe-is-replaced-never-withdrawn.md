# A published recipe is replaced, never withdrawn

**Status:** accepted; confirms
[publication is permanent](20260920-publication-is-permanent.md) rather than
superseding it

**Context:** The product owner asked whether permanence could be relaxed a
little — a published recipe rolled back while it has no favourites, or within 24
hours of publication — and whether the product needs that at all. The question
came from a real case: an author publishes, spots a mistake, fixes it by forking,
and the catalogue now holds both the broken recipe and the fixed one.

What the author already has: a submission can be withdrawn at any time until the
moderator records a decision (`pending` to `private`), which is hours or days;
and the editor shows the reader's own view while the recipe is still editable, so
there is no unseen surface (FLOW-029). What they do not have is a way back after
approval.

**Decision:** Permanence stands. Instead, an author forking their own frozen
recipe may mark the fork as replacing its parent (FORK-008); once that fork is
published, the replaced recipe states that its author has published a newer
version and leads to it (FORK-009). Nothing is removed, nothing is hidden, no
rating or favourite moves, and no fork is broken.

**Consequences:**
- The catalogue keeps both recipes, and the superseded one keeps the ratings it
  earned, so the orderings of CAT-001 may still place it above its replacement.
  What changes is that a reader who lands on it is one step from the version its
  author stands behind — which is why this serves GOAL-001 and survives the
  tiebreak of GOAL-003, where a fork tree would not.
- The mark is frozen with the fork, so an author who forgets to set it gets
  another fork, not an edit. That is the same remedy as for any other mistake in
  a published recipe and it costs one more entry in the catalogue.
- Nothing in moderation changes. A replacing fork is moderated as an ordinary
  submission; there is no second decision on the recipe it replaces.
- The statement is an author's claim, not the product's judgement. The product
  judges no recipe's craft (NGOAL-007) and this does not start.

**Alternatives considered:**
- *Withdrawal within 24 hours of publication.* Rejected on three counts. It
  doubles the work of the single moderator for every recipe withdrawn, edited and
  resubmitted, on the queue that `abuse` and OBS-003 exist to protect. It orphans
  forks made in that window, showing them the silent unavailable parent of ST-007
  that was designed for a moderator's removal, where withholding the reason is
  deliberate. And the window falls exactly on the peak: `newest` is the default
  ordering (CAT-001), so the first day is when a recipe collects most of the
  forks, ratings and favourites it will ever collect — the condition is placed
  where it destroys the most.
- *Withdrawal while the recipe has no favourites.* Rejected: favouriting is the
  weakest signal of engagement the product has, and is explicitly not approval, so
  a recipe can hold no favourites while holding ratings, forks and hundreds of
  openings. The condition does not measure what it is chosen for.
- *Withdrawal while nothing at all has attached — no rating, favourite, fork or
  opening by anyone but the owner, with no time limit.* Rejected, although it is
  the only coherent form of the idea: it removes both the orphaned fork and the
  destroyed rating, leaving only the re-moderation cost. It was rejected because
  with `newest` as the default ordering the gap between approval and the first
  opening is short, so the mechanism would most often refuse precisely when an
  author reached for it.
- *Doing nothing.* Rejected as the weaker half of the status quo: the catalogue
  accumulates superseded versions and gives the reader no way to tell which is
  which.
