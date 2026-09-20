# Two counts on the card, net score in the ordering

**Status:** accepted

**Context:** the draft asked for a binary like and dislike and said nothing
about what a reader sees or how `highest rated` (CAT-001) orders. Three closed
requirements were already deferring to this decision: CAT-003 shows "the rating
in the form section `ratings` defines", FLOW-019 requires the controls on an
unrated card, and CAT-001 offers `highest rated` and `most rated` without saying
what either computes.

The display and the ordering were decided together on purpose. A displayed
share of approval next to an ordering by net score would put a recipe at
`89%` above one at `96%` with no visible reason, because the number the reader
compares would not be the number the list sorts by.

**Decision:** a rating is displayed as two counts, likes and dislikes, side by
side, with no derived figure in their place (RATE-007). `highest rated` orders
by likes minus dislikes, largest first (RATE-010); `most rated` orders by likes
plus dislikes (RATE-011). Both break an equality by the more recent creation
instant, then by title ascending, then by identifier, so that the order of any
two recipes is decided and paging is stable.

**Consequences:**
- The reader sees the raw material and draws their own conclusion: `120 / 95` and
  `25 / 0` do not collapse into one figure, and a divisive recipe reads as one.
- The top of `highest rated` ages. A recipe that has accumulated 400 likes is not
  caught by a better one published this month, so the ordering becomes a hall of
  fame rather than a view of what is good now. This is survivable only because
  `newest` is the default (CAT-001) and the reader reaches `highest rated` on
  purpose.
- A recipe nobody has rated sits at zero next to one with 50 likes and 50
  dislikes, and the equality rules alone separate them.
- Net score can be moved by creating accounts, since an account costs one
  reinstall and RATE-005 counts every account. No gate is built; the ratio of
  ratings from accounts that have published nothing belongs among the signals of
  `observability`.
- The reader must do the arithmetic themselves to compare `24 / 3` against
  `30 / 12`.

**Alternatives considered:**
- *Share of approval with a count beside it (`89% · 27 ratings`).* The product
  owner preferred it as a display and rejected it because the chosen ordering is
  by net score: the figure shown and the figure sorted on would disagree, and a
  recipe with two likes would read `100%`.
- *A single net figure (`+21`).* Rejected: `21/0` and `300/279` render
  identically, and the second is a fight the first never had.
- *Ordering by share of approval with a minimum number of ratings.* Rejected: a
  threshold is a cliff — a recipe one rating short of it does not exist in that
  ordering at all.
- *Ordering by share with no minimum.* Weighed by the product owner and set
  aside in favour of net score, on the grounds that a reader who deliberately
  opens `highest rated` is looking for proven recipes, not for a recipe that is
  one-for-one.
- *A confidence bound (Wilson) instead of a threshold.* Rejected: it cannot be
  explained to a reader in one sentence and cannot be checked by hand — the only
  way to verify the order is to recompute it.
- *Total ratings as the first tie-breaker.* Rejected in favour of the creation
  instant, which is recorded to the millisecond and therefore rarely collides.
