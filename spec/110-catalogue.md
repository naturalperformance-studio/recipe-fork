---
slug: CAT
status: closed
updated: 2026-09-21
ids: [CAT-001, CAT-002, CAT-003, CAT-004, CAT-005, CAT-006, CAT-007, CAT-008, CAT-009]
---

# Catalogue and search

The public catalogue is where a reader meets a recipe they did not already know
about. Searching works over the catalogue and, by the same rules, over the
reader's own favourites (draft section 7).

The screens themselves — their empty, loading, error and offline states — are in
[`flows`](70-flows/00-index.md); what is here is the ordering, the filters and what a
listed recipe shows. Two of these requirements reach beyond the catalogue by
design: the fork marker and the fork number are shown both where a recipe is
listed and on its card, because they are one number with one meaning
(CAT-004, CAT-005). One filter is specified elsewhere because it exists for a
reason outside this section: the filter on a recipe's declared language is
L10N-005, and every listing here holds both languages until a reader sets it. One
statement on a listed recipe is specified elsewhere for the same reason: a recipe
whose author has published a replacement says so wherever it is shown, and that
is FORK-009.

### CAT-001 — The reader chooses how results are ordered

The system shall order the catalogue and the results of a search by a field the
reader chooses from `newest`, `highest rated`, `most rated` and `most forked`,
shall apply that choice to both, and shall order by `newest` for a reader who
has not chosen a field.

**Acceptance criteria**
- Given a reader who has never chosen an ordering, when the catalogue opens, then
  it is ordered by `newest`.
- Given the catalogue with no query, when the reader chooses an ordering, then
  the recipes are ordered by it.
- Given the ordering `most forked`, when two recipes are ordered, then the one
  with the larger *published* fork count of CAT-005 appears first, and the count
  that includes unpublished forks takes no part in the ordering.
- Given a query, when the results are shown, then they are ordered by the same
  chosen field, and choosing another reorders them without repeating the query.
- Given the reader's favourites, when they are searched, then the same orderings
  are offered there.
- Given a chosen ordering, when the reader leaves the catalogue and returns
  within the same session, then the choice is still in force.
- Given the ordering `newest`, when two recipes are ordered, then the one that
  entered the catalogue later comes first: the instant a moderator's approval
  published it, and not the instant it was created or last changed.
- Given a recipe approved for a private link in March and published in
  September, when it is ordered by `newest`, then the instant used is the one it
  entered the catalogue, so it is new to the catalogue on the day it arrives
  there and not on the day its author wrote it.
- Given the own-recipes view, when it is ordered by `newest`, then FLOW-039
  governs instead and orders by the time each recipe was last changed, because
  nothing there need ever have been published.
- Given two recipes equal under any of the four orderings, when they are
  ordered, then the equality is broken by the chain RATE-010 already defines —
  the more recent creation instant, then the title in ascending alphabetical
  order, then the recipe's identifier — so the order of any two recipes is
  decided under every ordering and not only under the two about rating.
- Given an unchanged catalogue, when the reader pages through any ordering to
  the end (FLOW-041), then every recipe appears exactly once and none is
  skipped.

**Verification:** compare the catalogue and a query's results under each of the
four orderings, and open the catalogue on a fresh install to confirm the default;
build two recipes published in one millisecond with one title and page through
all four orderings twice.
**Written 2026-09-21:** what `newest` orders by, and the tie-breaking chain for
`newest` and `most forked`. Both were missing: FLOW-039 defined `newest` for the
own-recipes view and said in passing that a published recipe has a publication
date, RATE-010 and RATE-011 gave a total order to the two rating orderings, and
the default ordering of the catalogue was left to whoever implemented it. See
[ADR — newest is the moment a recipe reached the catalogue](decisions/20260921-newest-is-when-the-catalogue-got-it.md).
**Source:** user decision, 2026-09-20: the reader picks the ordering rather than
the product picking one for them, which is also why rating may take part in it
without becoming the only way a recipe is ever found. `newest` is the default
because an ordering by rating becomes nearly static as the catalogue grows —
the same recipes stay on top and a new one is never seen, so it is never rated.
`most forked` is offered although what a high fork count means is genuinely
ambiguous — it may mark a recipe that needs fixing or one that adapts to any
taste; the product owner accepted that ambiguity on 2026-09-20 because the
ordering harms no reader and it is the one ordering that rewards the mechanic the
product is built on. See
[ADR — lineage is visible in discovery](decisions/20260920-lineage-visible-in-discovery.md).

### CAT-002 — Filtering by an ingredient orders by how much of it there is

Where the reader filters by an ingredient, the system shall order the results by
the comparable quantity (CAT-009) of that ingredient for one serving, largest
first, summed as CAT-007 requires.

**Acceptance criteria**
- Given a filter on chicken, when two recipes both use it, then the one using
  more chicken per serving appears first.
- Given a recipe authored for 4 servings with 500 g of the ingredient and one
  authored for 2 servings with 300 g, when both match, then the second appears
  first, because 150 g per serving exceeds 125 g.
- Given a filter on an ingredient, when the order is computed, then neither the
  author nor the moderator has marked anything as a main ingredient: the
  quantity is taken from the recipe as stored.

**Verification:** filter on one entry across recipes authored for different serving
counts and compare against hand computation.
**Source:** user decision, 2026-09-20. The draft asked for a filter by main
ingredient (section 7); rather than add a field the author must fill and can
never correct after publication (CON-003), the product infers importance from
how much of the ingredient a portion actually contains.

### CAT-003 — What a catalogue entry shows

The system shall show for every recipe listed in the catalogue or in search
results its title, its cover image where it has one, its author's nickname, its
rating in the form section `ratings` defines, and the serving count it was
authored for.

**Acceptance criteria**
- Given a recipe with a cover image, when it is listed, then the title, the
  image, the author's nickname, the rating and the authored serving count are
  all present in the entry.
- Given a recipe with no cover image, when it is listed, then the entry omits
  the image and shows no placeholder in its place, as FLOW-019 requires of the
  recipe card.
- Given a recipe nobody has rated, when it is listed, then the entry omits the
  rating rather than showing a zero.
- Given a recipe carrying the safety flag, when it is listed, then the notice
  FLOW-018 requires is present in the entry as well.
- Given any entry, when it is shown, then it carries nothing that identifies the
  author beyond the nickname (CON-001).

**Verification:** list four recipes — one complete, one without a cover image,
one unrated, one flagged — and compare each entry against this field list.
**Source:** user decision, 2026-09-20. The entry is the reader's first contact
with a recipe; what it does not show cannot influence what they open.

### CAT-004 — A fork is visible as a fork before it is opened

Where a recipe's parent is shown by FORK-004, the system shall mark that recipe
as a fork wherever it is listed in the catalogue or in search results and on its
card.

**Acceptance criteria**
- Given a recipe whose parent is or has been published, when it is listed, then
  its entry carries the fork marker, and when it is opened, then its card carries
  the same marker.
- Given a recipe with no parent, when it is listed, then its entry carries no
  marker and no space reserved for one.
- Given a recipe whose parent has never been published, when it is listed, then
  its entry carries no fork marker, because FORK-005 forbids any indication that
  a parent exists.
- Given a recipe whose published parent was later removed by a moderator, when
  it is listed, then its entry still carries the marker, because the parent was
  published and is shown as unavailable rather than hidden (ST-007, FORK-003).

**Verification:** list four recipes — no parent, published parent,
never-published parent, removed parent — and compare the marker on each.
**Source:** user decision, 2026-09-20: the fork is the mechanic the product is
built on, so it is shown rather than hidden, and hiding it until a recipe is
opened would leave the one thing no competing product has invisible at the point
of first contact (MKT-F3). See
[ADR — lineage is visible in discovery](decisions/20260920-lineage-visible-in-discovery.md).

### CAT-005 — The number of direct forks is stated

The system shall state, wherever a recipe is listed in the catalogue or in search
results and on its card, how many recipes name it as their direct parent, and
shall state separately how many of those are published at that moment whenever
the two numbers differ.

**Acceptance criteria**
- Given a recipe with five forks of which two are published, when it is listed or
  opened, then both numbers are stated — five forks, two of them open.
- Given a recipe with two forks, both published, when it is listed or opened, then
  one number is stated, because the two counts do not differ.
- Given a published fork that a moderator then removes, when the parent is listed
  again, then the open count has fallen by one and the total is unchanged,
  because the removed fork still names it as parent (ST-007) and is no longer
  open to anybody.
- Given a recipe that nobody has forked, when it is listed, then its entry states
  no number at all rather than a zero.
- Given a recipe whose fork has a fork of its own, when the first recipe is
  listed, then the grandchild is not counted: only direct forks are.
- Given any recipe, when either number is shown, then nothing about any individual
  fork — its title, its author, when it was made — is derivable from a number
  (CON-001).
- Given a recipe listed and the same recipe opened, when both numbers are read,
  then they agree.

**Verification:** build a recipe with two published forks and three unpublished
ones, plus a recipe whose forks are all published, and compare both entries and
both cards against the rule.
**Source:** user decision, 2026-09-20; the open count narrowed 2026-09-21 from
"are or have been published" to "are published", so that it always equals the
length of the list CAT-008 opens. The alternative — keeping a count that includes
removed forks and letting the list be shorter than the number beside it — was
rejected because FLOW-033 was deliberately built so that the one thing which ever
makes a count disagree with a list is the reader's own filter, which they can see
and undo. The cost accepted in exchange: the open count can fall, so a reader who
saw five yesterday may see four today, exactly as RATE-002 already accepts for
the rating counts. The count is derived from the parent references DOM-004
already holds; whether it is stored as a counter or computed is an engineering
choice, and DOM-004's ban on holding a descendant list stands either way. See
[ADR — lineage is visible in discovery](decisions/20260920-lineage-visible-in-discovery.md).
The two numbers exist because one of them answers two different questions badly:
the reader wants to know how often the recipe was adapted, while the ordering and
the list of CAT-008 can only work on what was published. Counting unpublished
forks in the ordering would let one account fork its own recipe privately as
often as it likes and climb `most forked` without the moderator seeing anything
(ABUSE).

### CAT-006 — A filter on an ingredient matches the dictionary entry, not the words

Where the reader filters by an ingredient entry, the system shall match a recipe
that holds an ingredient of that entry or of any entry whose parent it is, and
shall match neither a `qualifier` nor a `free_text_name`.

**Acceptance criteria**
- Given a dictionary where `крылышки` and `филе бедра` are children of `курица`,
  when the reader filters on `курица`, then recipes holding either child and
  recipes holding `курица` itself all match.
- Given the same dictionary, when the reader filters on `крылышки`, then only
  recipes holding `крылышки` match, and a recipe holding `курица` itself does not,
  because a parent is not evidence of any particular child.
- Given a recipe whose chicken was added as free text because no entry matched,
  when the reader filters on `курица`, then it does not match — exactly what
  FLOW-022 warned the author about.
- Given a recipe holding the entry `бульон` qualified `куриный` and no chicken of
  its own, when the reader filters on `курица`, then it does not match.
- Given a published recipe whose ingredient name was misspelled as free text,
  when any filter runs, then no filter finds it and no correction is possible,
  because publication is permanent (CON-003).

**Verification:** build five recipes — two child entries, the parent itself, one
free text, one qualifier mentioning the filtered word — and run a filter on the
parent and on one child.
**Source:** user decision, 2026-09-20. Free text cannot carry identity: the same
ingredient is routinely written with a cut attached, and those cuts are counted in
different units. The author picks the leaf they mean and the dictionary supplies
the parent (DOM-010). See
[ADR — the ingredient dictionary](decisions/20260920-ingredient-dictionary.md) and
[ADR — comparison factors](decisions/20260920-comparison-factors.md).

### CAT-007 — Several lines of one ingredient are summed

Where a recipe holds more than one ingredient the filter matched, the system shall
take the quantity for ordering (CAT-002) as the sum of those ingredients'
comparable quantities (CAT-009) for one serving.

**Acceptance criteria**
- Given a recipe authored for 4 servings holding 500 g of `филе бедра` and 300 g
  of `крылышки`, when the reader filters on `курица`, then the recipe is ordered
  on 200 on the family's scale for one serving.
- Given a recipe holding exactly one matched ingredient, when it is ordered, then
  the sum is that one comparable quantity.
- Given a recipe holding one matched line in grams and one in pieces, when it is
  ordered, then both are converted by their entries' factors and summed, so no
  line is dropped for being stated in another unit.
- Given a matched line whose unit has no factor, when the sum is taken, then that
  line contributes nothing and the rest of the recipe is still ordered on what
  remains (CAT-009).
- Given a matched ingredient whose unit type is `taste_unmeasured`, when the sum is
  taken, then it contributes nothing, because it carries no quantity (DOM-002).

**Verification:** filter on a parent entry across a recipe with one line, a recipe
with a gram line and a piece line, and a recipe with a `taste_unmeasured` line, and
compare against hand computation.
**Source:** user decision, 2026-09-20: the sum, because the question a filter on
an ingredient answers is where there is more of it, and a recipe using chicken in
the marinade and in the filling does use more chicken. The rejected alternative
was the largest single line, which is steadier against a recipe mentioning an
ingredient twice incidentally.

### CAT-008 — The fork number opens the recipes forked from it

When the reader activates a recipe's fork number (CAT-005), the system shall show
the catalogue restricted to the published recipes forked directly from that
recipe, ordered by the field the reader has chosen (CAT-001).

**Acceptance criteria**
- Given a recipe with five forks of which two are published, when the reader
  activates the number, then exactly those two are listed, and the count of open
  forks CAT-005 states beside it is the length of that list.
- Given a recipe with three published forks, when the reader activates its
  number, then exactly those three are listed and nothing else.
- Given that restricted view, when the reader changes the ordering, then CAT-001
  applies to it as to any other catalogue view.
- Given a recipe that shows no number because nothing forked it, when the reader
  looks at it, then there is nothing to activate.
- Given a fork that has a fork of its own, when the reader activates the root's
  number, then the grandchild is not listed (CAT-005).
- Given one of the listed forks removed by a moderator, when the number is read
  and the list opened again, then both have fallen by one and they still agree
  (CAT-005).
- Given no network connection, when the reader activates the number, then it
  behaves as every other catalogue view does without one (FLOW-003).

**Verification:** activate the number on a recipe with several forks, on a
grandparent, and with the connection cut.
**Source:** user decision, 2026-09-20: the number is a way into the forks, not a
decoration. It lists one level, not a tree, so the narrowed GOAL-003 holds. See
[ADR — lineage is visible in discovery](decisions/20260920-lineage-visible-in-discovery.md).

### CAT-009 — The comparable quantity of an ingredient line

The system shall take the comparable quantity of a matched ingredient as its
quantity for one serving multiplied by its entry's comparison factor for its unit,
and shall leave the line out of the comparison entirely where its entry holds no
factor for that unit.

**Acceptance criteria**
- Given `крылышки` whose factor for `шт` is 80 on the `курица` family's scale, and
  a recipe authored for 4 servings with 8 pieces of it, when the comparable
  quantity is taken, then it is 160.
- Given `филе бедра` with 500 g in a recipe authored for 4 servings, and a factor
  of 1 for `g`, when the comparable quantity is taken, then it is 125, so the
  recipe with two wings per serving is ordered above it.
- Given a recipe holding `тушка` at 1 piece with a factor of 1500 and a recipe
  holding 5 g of `филе бедра`, when both are ordered under a filter on `курица`,
  then the whole carcass comes first.
- Given a matched line whose unit carries no factor on its entry, when the
  recipe's quantity is taken, then that line contributes nothing.
- Given a recipe in which no matched line contributes anything, when the results
  are ordered, then it is placed after every recipe that has a comparable
  quantity, and among those it is ordered by the reader's chosen field (CAT-001).
- Given the product owner later fills in a missing factor, when the same filter is
  run again, then the affected recipes take their place in the order and nothing
  stored in any recipe has changed (DOM-011).

**Verification:** fill a two-level family with factors, filter on the parent
across recipes stated in grams, in pieces and in a unit with no factor, and
compare against hand computation; then add the missing factor and repeat.
**Source:** user decision, 2026-09-20. Ordering by bands was rejected with the case
that killed it: a recipe using 5 g of chicken per serving would have been ordered
above one needing two whole carcasses, only because the first was stated in grams.
See [ADR — comparison factors](decisions/20260920-comparison-factors.md).
