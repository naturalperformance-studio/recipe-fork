---
slug: FLOW
status: closed
updated: 2026-09-27
ids: [FLOW-033, FLOW-034, FLOW-035, FLOW-036, FLOW-037, FLOW-038, FLOW-039,
  FLOW-040, FLOW-041, FLOW-042, FLOW-043]
---

# Browsing: the catalogue and the favourites section

Two sections, not one. The catalogue is where a reader meets recipes other
people wrote. The favourites section is where a reader keeps what they took
with them and where an author finds what they wrote themselves. The rules the
two share — orderings, filters, what an entry shows — were decided in
[`catalogue`](../110-catalogue.md) and are referenced here, never restated.

### FLOW-033 — The reader's own recipes are a filter of the catalogue, off by default

The system shall offer in the catalogue a filter that includes the requesting
account's own recipes in the listing, shall leave that filter off until the
reader turns it on, and shall apply it wherever the catalogue's filters apply.

**Acceptance criteria**
- Given an account with a published recipe, when it browses the catalogue with
  the filter off, then that recipe is absent from the listing under every
  ordering of CAT-001.
- Given the same account, when it turns the filter on, then its own published
  recipes take their place in the listing by the ordering in force, beside
  everyone else's.
- Given the filter off, when the owner searches for their own recipe's exact
  title or filters by an ingredient it holds, then it is not among the results,
  and turning the filter on brings it into the same results without repeating
  the query.
- Given the list of a recipe's forks (CAT-008), when it is opened with the filter
  off and one of those forks belongs to the reader, then that fork is absent
  while the count of CAT-005 still includes it, and turning the filter on makes
  the list and the count agree.
- Given any other account, when it browses, then the reader's recipes are listed
  as CAT-003 requires and this filter has no effect on what it sees.
- Given the filter is on and the reader leaves the catalogue and returns within
  the same session, then it is still on, as CAT-001 keeps a chosen ordering.
- Given a recipe the account owns that is not published, when the filter is on,
  then it is still absent, because the catalogue holds only what is externally
  visible and the own-recipes view (FLOW-034) is where the rest lives.

**Verification:** browse, search and filter the catalogue from the owning account
with the filter in both positions, and open the fork list of a recipe the reader
has forked.
**Source:** user decision, 2026-09-20. The product owner first excluded own
recipes outright; that rule then needed an exception for the fork list of
CAT-008, and the exception was the evidence that the rule was in the wrong place.
A filter does the same work in one mechanism the reader can see and undo, and it
needs no special case anywhere: a number that disagrees with a list is then the
reader's own filter, which is true of every other filter in the product.
It is off by default because an author's own recipe is noise in the one place
built for meeting recipes they do not know.

### FLOW-034 — The favourites section holds two views

The system shall present the favourites section as two views — the recipes the
account has marked, and every recipe the account owns — and shall let the reader
switch between them inside the section without leaving it.

**Acceptance criteria**
- Given an account that has marked recipes and owns recipes, when it opens the
  section, then both views are reachable from it and the switch between them is
  visible without scrolling.
- Given the view of marked recipes, when it is shown, then it lists exactly what
  FLOW-009 holds and FLOW-010 governs, and nothing else.
- Given the view of the account's own recipes, when it is shown, then it lists
  every recipe the account owns in every state, including states no other account
  can see.
- Given a recipe the account owns, when either view is shown, then it appears in
  the own-recipes view alone, because FLOW-040 gives it no way into the other,
  whatever position the catalogue filter of FLOW-033 is in.
- Given a switch between the views, when the reader leaves the section and
  returns within the same session, then the view they were in is still in force,
  as CAT-001 keeps a chosen ordering.
- Given a session in which the reader has not chosen a view, when they open the
  section, then it opens on the view of marked recipes, whatever the account holds.
- Given a device holding no account, when the section is opened, then it shows
  the view of marked recipes alone and no switch; the own-recipes view and the
  switch appear once the device holds an account.

**Verification:** open the section on an account that owns recipes in several
states and has marked recipes of other authors, and inspect both views; open it
in a new session and on a device with no account.
**Completed 2026-09-27:** which view the section opens on and what a device with no
account sees were decided nowhere (SD-004). By user decision of that date it opens
on the marked recipes, and a device with no account has that view alone.
**Source:** user decision, 2026-09-20: finding one's own recipe must be simple,
and the author's own work is not discovery, so it lives beside favourites rather
than in the catalogue. Two views rather than two sections, because a state of a
recipe is not a reason for a screen.

### FLOW-035 — Both views are searched, filtered and ordered exactly as the catalogue is

The system shall offer in both views of the favourites section the orderings of
CAT-001 that FLOW-039 leaves available to that view and the filters of CAT-002
and CAT-006, and shall give each of them the same meaning it has in the
catalogue.

**Acceptance criteria**
- Given the view of the account's own recipes, when the reader filters by an
  ingredient entry, then CAT-006 decides what matches, including the free-text
  line that matches nothing.
- Given either view, when the reader filters by an ingredient, then CAT-002 and
  CAT-007 order the results by comparable quantity per serving.
- Given either view, when the reader chooses an ordering, then the choice applies
  to that view and does not silently change the other.
- Given a query, when it is run inside a view, then it searches that view alone
  and reaches no recipe outside it.
- Given the catalogue and a view of the favourites section, when the same filter
  is applied to both, then a reader needs to learn the filter once.

**Verification:** run the same ingredient filter and each ordering in the
catalogue, in the marked view and in the own-recipes view, and compare behaviour.
**Source:** user decision, 2026-09-20. One mechanism, three places; the reader
learns it once.

### FLOW-036 — An own recipe carries its state on its entry, not in a screen of its own

The system shall mark every entry in the own-recipes view with the state of that
recipe — `private`, `pending`, `rejected`, `link_shared`, `published`,
`reported_hidden` or `removed` — and shall create no separate screen or section
for any state.

**Acceptance criteria**
- Given a recipe in `private`, when the view is shown, then its entry is marked
  as a draft.
- Given a recipe in `pending`, when its entry is shown, then it states that the
  recipe is awaiting review, and the expectation MOD-005 states is not repeated
  on the entry.
- Given a recipe in `rejected`, when its entry is shown, then it states that the
  recipe was rejected, and opening it shows the reason and the date MOD-004
  records.
- Given a recipe in `reported_hidden`, when its entry is shown, then it states
  that the recipe is hidden and gives no further detail (ST-006).
- Given a recipe in `removed`, when its entry is shown, then it stays in the view
  marked as removed, because its owner may still open it (`entity-states`).
- Given a recipe the server has not yet accepted, when its entry is shown, then
  the indication FLOW-005 requires is present as well and does not replace the
  state marker.
- Given any state, when the reader looks for its recipes, then the way to see
  only them is the filter of FLOW-037 and not a separate screen.
- Given an entry in `private` or `rejected`, when it is shown, then deletion is
  offered on it and FLOW-053 governs what the author is told; given an entry in
  any other state, then it is not offered.

**Verification:** hold one account with a recipe in each of the seven states and
inspect every entry in the view.
**Source:** user decision, 2026-09-20: a marker on the entry, because a state is
not worth a screen and an author may hold many drafts at once.

### FLOW-037 — The own-recipes view narrows to a state

Where the reader is in the own-recipes view, the system shall offer a filter by
recipe state and shall combine it with the filters and orderings of FLOW-035.

**Acceptance criteria**
- Given the view with no state chosen, when it is shown, then recipes in every
  state are listed together.
- Given the state `private` chosen, when the view is shown, then exactly the
  drafts are listed.
- Given a state filter and an ingredient filter both applied, when the view is
  shown, then a recipe must satisfy both to be listed.
- Given a state filter, when the reader switches to the view of marked recipes,
  then the state filter is not offered there, because a marked recipe is always
  one the account could open.
- Given a chosen state that no recipe is in, when the view is shown, then it is
  empty and says so rather than falling back to the unfiltered list.

**Verification:** filter the view by each state in turn on an account holding
recipes in all of them, then combine a state filter with an ingredient filter.
**Source:** user decision, 2026-09-20, given together with FLOW-036: no screens
per state, but the states are worth filtering by.

### FLOW-038 — Every recipe the account owns is readable without a connection

The system shall hold on the device the full text of every recipe the account
owns, in every state, and shall hold that recipe's media under the single setting
of FLOW-011, on the same terms as a favourite.

**Acceptance criteria**
- Given no network connection, when the owner opens the own-recipes view, then
  every recipe they own is listed, carries its state marker, and opens in full
  with its steps, ingredients and timers.
- Given no network connection, when the owner scales an own published recipe,
  then `scaling-rules` applies to it exactly as to a favourite.
- Given the setting of FLOW-011 is off, when an own recipe is opened offline,
  then its text opens and its media states that it needs a connection.
- Given the device has no space left, when the media of an own recipe is being
  held, then the failure is contained to that recipe, its text stays readable,
  and no other recipe's held media is deleted to make room (FLOW-011).
- Given an account that owns hundreds of recipes, when they are held, then no
  own recipe's text is evicted to make room for another.
- Given a recipe a moderator removed, when the owner opens the view offline,
  then it is still listed and still opens, because its owner may read it.
- Given the account's own recipes and its favourites, when both are held, then
  the two sets never overlap, because FLOW-040 keeps an own recipe out of
  favourites.

**Verification:** publish several recipes from one account, cut the connection,
and walk the whole own-recipes view including scaling and timers; then repeat
with the media setting off and with the device filled.
**Source:** user decision, 2026-09-20, choosing that an own recipe occupies the
device on the same footing as a favourite so that the view works offline whole.
The alternative — holding only drafts and unsent recipes, and marking the
published ones as needing a connection — was rejected because an author cooks
from their own recipes in the same kitchen as from anyone else's (GOAL-001).

### FLOW-039 — What `newest` means for a recipe that was never published

The system shall order the own-recipes view under `newest` by the time each
recipe was last changed, and shall offer in that view neither `highest rated` nor
`most rated`.

**Acceptance criteria**
- Given a draft created a month ago and edited today and a recipe published
  yesterday, when the own-recipes view is ordered by `newest`, then the draft is
  first.
- Given a published recipe, when it is ordered in that view, then the time used
  is the last time its content changed, which for a frozen recipe is the last
  edit before it became externally visible (ST-003).
- Given the own-recipes view, when the reader opens the list of orderings, then
  `newest` and `most forked` are offered and the two rating orderings are absent.
- Given the catalogue or the view of marked recipes, when the reader opens the
  list of orderings, then all four of CAT-001 are offered, because every recipe
  there is published.
- Given a draft that has never been edited since it was created, when it is
  ordered, then its creation is the change the ordering uses.

**Verification:** build an account with a freshly edited old draft, a new draft
and a published recipe, order the view by `newest`, and open the ordering list in
all three places.
**Source:** user decision, 2026-09-20. An author looking for a draft is looking
for what they last touched, not for what they first started; an unpublished
recipe has no rating and no publication date, so the orderings that need one are
not offered rather than shown empty.

### FLOW-040 — An own recipe is not marked as a favourite

The system shall not offer the favourite control on a recipe owned by the
account viewing it, and shall reject any attempt to add an own recipe to
favourites.

**Acceptance criteria**
- Given the owner opens their own recipe, when the card is shown, then no
  favourite control is present and no space is reserved for one.
- Given another account opens that recipe, when the card is shown, then the
  favourite control is present as for any other published recipe.
- Given an account that owns recipes, when it opens the view of marked recipes,
  then none of its own recipes is listed there in any state.
- Given a recipe in favourites whose author later — by any route — becomes the
  viewing account, when the views are built, then the recipe is listed once, in
  the own-recipes view.

**Verification:** open an own recipe and a foreign one and compare the controls;
then inspect both views on an account that owns and marks recipes.
**Source:** user decision, 2026-09-20: an own recipe is always in the
own-recipes view, so a second way in would only duplicate it and would make the
same recipe answer to two different removal rules (FLOW-009, FLOW-038).

### FLOW-041 — A long listing continues as the reader scrolls

Where a listing holds more recipes than one screen, the system shall load the
next recipes as the reader scrolls towards the end, without the reader asking,
in the catalogue and in both views of the favourites section.

**Acceptance criteria**
- Given a catalogue of several hundred recipes, when the reader scrolls to the
  end of what is loaded, then the following recipes are loaded and appended in
  the ordering in force, with no control to press.
- Given more recipes are being loaded, when the reader is at the end of the
  list, then the list states that more are coming and the recipes already shown
  stay readable and scrollable.
- Given the loading of the next recipes fails, when the failure is known, then
  the list keeps everything already loaded, states at its end that more could
  not be loaded, and offers to retry.
- Given the reader changes the ordering or a filter, when the listing is rebuilt,
  then it starts from the beginning of the new ordering and what was loaded under
  the previous one is discarded.
- Given the own-recipes view with no network connection, when the reader scrolls
  to the end, then the following recipes are loaded from the device (FLOW-038)
  and nothing states that a connection is needed.
- Given the catalogue with no network connection, when the reader reaches it,
  then FLOW-003 governs the whole screen and nothing is loaded.

**Verification:** scroll a catalogue of several hundred recipes to the end,
cut the connection mid-scroll, and repeat in the own-recipes view offline.
**Source:** user decision, 2026-09-20: scrolling continues by itself. The
rejected alternative was a control the reader presses for the next page, which
costs one action per screenful in the place a reader spends the most time.

### FLOW-042 — An empty listing names what is empty and offers the way out of it

When a listing the reader opened holds nothing, the system shall state which
listing is empty and shall offer the one action that leads out of it: clearing
the query and the filters where they caused the emptiness, writing a recipe in
the own-recipes view and in an empty catalogue, and opening the catalogue in the
view of marked recipes.

**Acceptance criteria**
- Given a query or a filter that matches nothing, when the results are shown,
  then they state that nothing matched and offer to clear the query and the
  filters, and clearing them returns the listing that was in force before.
- Given the state filter of FLOW-037 matching nothing, when the view is shown,
  then it offers to clear that filter and does not silently drop it.
- Given a catalogue holding no recipe the reader may see, when it is opened with
  no query and no filter, then it states that there is nothing in it yet and
  offers to write a recipe.
- Given an account that has written nothing, when it opens the own-recipes view,
  then the view states so and offers to write a recipe, and the offer opens the
  editor (FLOW-024).
- Given an account that has marked nothing, when it opens the view of marked
  recipes, then the view states so and offers to open the catalogue.
- Given any of these offers, when the reader takes it, then they land on exactly
  that screen and nothing else is changed by the offer.
- Given an empty listing, when it is shown, then it holds one offer and not
  several.

**Verification:** empty every listing in turn — a fresh account, a filter that
matches nothing, a state filter that matches nothing, an unmarked favourites
view — and take each offered action.
**Source:** user decision, 2026-09-20: an empty listing offers the action that
belongs to that place rather than only reporting emptiness, because the reader
who reached it has no other way forward from that screen.

### FLOW-043 — A listing loading for the first time, and a listing that failed to load

While a listing is being loaded for the first time, the system shall show that
it is loading and shall show no part of any previous listing; if the load fails,
then the system shall state that it failed and shall offer to retry.

**Acceptance criteria**
- Given the catalogue is opened, while its first recipes are being fetched, then
  the screen shows that it is loading and shows no recipes from a previous
  listing or a previous ordering.
- Given the fetch fails while a connection exists, when the failure is known,
  then the screen states that the listing could not be loaded and offers to
  retry, and the retry repeats the same query, filters and ordering.
- Given the retry succeeds, when the listing appears, then it is built from the
  beginning of the ordering in force.
- Given no network connection, when the catalogue is opened, then FLOW-003
  governs the screen and the message is about the missing connection, not about
  a failure.
- Given the own-recipes view, when it is opened, then it is built from what the
  device holds (FLOW-038), so it never shows a load failure and never waits on
  the network.
- Given the view of marked recipes, when it is opened, then it is built from what
  the device holds (FLOW-009) and the availability marks of FLOW-010 are as of
  the last time the device reached the server.

**Verification:** open the catalogue with the server refusing requests, retry,
then repeat with the connection cut; open both favourites views with the server
unreachable and confirm neither waits nor fails.
**Source:** user decision, 2026-09-20, extending the rule FLOW-003 already sets
for the catalogue: a listing that cannot be loaded says so instead of showing
something stale, and the two views built from the device do not depend on the
network at all.

Deleting a recipe of one's own, the private link and the author's screen are in
[55-own-recipe-and-author.md](55-own-recipe-and-author.md).
