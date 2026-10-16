---
slug: FORK
status: closed
updated: 2026-10-15
ids: [FORK-001, FORK-002, FORK-003, FORK-004, FORK-005, FORK-006, FORK-007,
  FORK-008, FORK-009, FORK-010]
---

# Forking

Who may fork what is specified in [ST-004](60-entity-states/00-index.md); this section
specifies what a fork contains and what it shows about its origin.

The split between what is inherited and what is not follows the law rather than
convenience: a sequence of cooking operations and a list of ingredients are a
method and a set of facts, which are not objects of copyright (Civil Code of the
Russian Federation, art. 1259 §5 —
[ConsultantPlus](https://www.consultant.ru/document/cons_doc_LAW_64629/be05678dc42ddc67aae5be9ba9beebd367fb9a3f/),
checked 2026-09-20). Photographs, video and the author's own prose are protected
expression. The fork therefore carries the former and never the latter.

### FORK-001 — What a fork inherits

When a user forks a recipe, the system shall create a new private recipe
containing the parent's title, tags, serving count, equipment, ingredients and
steps, and a reference to the parent.

**Acceptance criteria**
- Given a forked recipe, when it is opened, then its ingredients match the
  parent's in dictionary entry, qualifier or free-text name, unit type, unit,
  quantity, piece weight, divisibility, group and note (DOM-022, DOM-023),
  and in order.
- Given a forked recipe, when its equipment is opened, then it matches the
  parent's item by item, in entry, qualifier or free text, and in order (DOM-025).
- Given a forked recipe, when its steps are opened, then their text, timers and
  ingredient references match the parent's and resolve within the fork
  (DOM-007).
- Given a parent whose step carries a timer, when the recipe is forked by
  anyone, then the fork's timer holds the parent's duration.
- Given a parent whose timer its author named by hand, when the recipe is forked
  by anyone, then the fork's timer holds that same name, and the name stays as it
  is however the fork's author rewrites the step (FLOW-016).
- Given a parent whose timer its author never named, when the recipe is forked,
  then the fork's timer has no name of its own either, so its name derives from
  the fork's own step text — identical at creation, and following that text
  wherever the fork's author takes it (FLOW-016).
- Given a fork by an account other than the owner, when its timers are opened,
  then their names travelled with them, because a timer's name says what the
  wait is for and is part of the method the fork exists to carry, not of the
  author's expression that FORK-002 withholds.
- Given a forked recipe, when it is created, then its state is `local`, it is
  in the forking person's recipe book — the active account's, or the pool's
  (AUTH-026) — and its parent is the forked recipe.

**Verification:** fork a recipe covering all five unit types, one step with a
named timer and one with an unnamed timer; compare field by field, then rewrite
both steps in the fork and compare the two timers' names again.
**Source:** draft section 4.1; user decision, 2026-09-20.
**Changed 2026-10-11 (sync v0.22–v0.30):** equipment (DOM-025) is part of what
every fork carries, as built in v0.27 — a list of what a dish needs is method,
not expression.

### FORK-002 — A fork by another account carries no media, no description and no step notes

When anyone other than the owner forks a recipe — another account, a person with
no account, or anyone forking a recipe imported from a file — the system shall
create the fork with no media, with an empty description and with no author's
note on any step (DOM-027).

**Acceptance criteria**
- Given a parent with a cover photo and photos on its steps, when another
  account forks it, then the fork has no media at all, in its draft and
  afterwards.
- Given a parent with a description, when another account forks it, then the
  fork's description is empty.
- Given a parent whose steps carry the author's notes, when another account forks
  it, then the fork's steps carry none, and keep their text, timers and
  references.
- Given such a fork, when its author wants a photograph, then they supply their
  own or publish without one, because media is not part of the minimum
  (DOM-006).
- Given the owner forking their own recipe, when the fork is created, then this
  requirement does not apply and FORK-007 governs.

**Verification:** fork a recipe that has media and a description from a second
account, and inspect the result.
**Source:** user decision, 2026-09-20. See
[ADR — a fork carries the method, not the expression](decisions/20260920-fork-carries-method-not-expression.md).
**Changed 2026-10-11 (sync v0.22–v0.30):** a step's author's note, added in v0.29,
is the author's prose and stays behind like the description (user decision,
2026-10-10).

### FORK-003 — The parent's media stays visible in the parent

Where a fork has a parent that is accessible to the viewer, the system shall
offer navigation to the parent, where the parent's own media is shown.

**Acceptance criteria**
- Given a fork of a published recipe, when its card is opened, then the parent
  is named and can be opened, and the parent's photos are visible there.
- Given a fork whose parent was removed, when its card is opened, then the
  parent is shown as unavailable and cannot be opened (ST-007).
- Given serverless mode, when a fork of an imported recipe is opened, then its
  parent opens while it is held in the recipe book and is otherwise shown by name
  as unavailable (FLOW-065, FORK-010).

**Verification:** manual check on a fork of a published recipe with media.
**Source:** user decision, 2026-09-20: the author of a fork can see how the dish
should look by opening the original, which does not require copying the photo
into the fork.

### FORK-004 — Only the direct parent is shown

Where a recipe's parent is or has been `published`, the system shall show that
direct parent on its card, and shall not show, compute or load its full fork
tree.

**Acceptance criteria**
- Given a recipe that is a fork of a published fork, when its card is opened,
  then exactly one parent is named.
- Given that parent, when it is opened, then its own parent is named in turn,
  with no limit on the depth reachable this way.
- Given any recipe, when its card is opened, then it lists no descendants and no
  count of them.

**Verification:** build a chain three deep and walk it in both directions.
**Source:** draft section 4.3; GOAL-003; DOM-004. The condition on the parent is
FORK-005; how the parent is named is FORK-010.

### FORK-010 — The parent is named in one line; its author is on its own card

Wherever a card shows its recipe's parent — by FORK-004, to the owner of both by
FORK-005, or in serverless mode by FLOW-065 — the system shall show it as one line,
the Origin, naming the parent by its title ("after the recipe «…»", in Russian
«по рецепту «…»») and leading to the parent when it can be opened; shall name the
parent in the same words on the recipe's entry in the recipe book; and shall show
the parent's author on the parent's own card rather than on the fork's. When, in
serverless mode, the parent is not held in the recipe book, the fork's card keeps
the author the file states beside the title, marked as stated in the file and not
verified, because there is nowhere else to see it.

**Acceptance criteria**
- Given a fork whose parent is held in the recipe book, when its card is opened,
  then the parent is named in exactly one line, by its title, and one tap on the
  line opens the parent.
- Given that fork, when its card is shown, then the parent's author is not named
  on it; given the parent opened from the line, then its author is on its card —
  for a recipe imported from a file, marked as stated in the file and not
  verified (FLOW-064).
- Given serverless mode and a fork whose parent is not in the recipe book, when its
  card is opened, then the line names the parent by its title, shows it as
  unavailable, and names the author the file states, marked as stated in the file
  and not verified; given a file that names no author, then no author is shown.
- Given a fork in the recipe book whose parent is shown, when its entry is listed,
  then the entry names the parent by its title in the words the card uses.
- Given a recipe whose parent is not shown (FORK-005), when its card or its entry
  is shown, then there is no such line (FLOW-019).
- Given any card, when the line is read, then it names one recipe — the direct
  parent — and nothing beyond it (FORK-004).

How the line looks — its type, colour, the marks along it — is the team's and the
owner's design (`docs/brand/`).

**Verification:** in serverless mode, import a recipe whose file states an author,
fork it, and inspect the fork's card and entry; open the parent from the line and
inspect its card; remove the imported recipe from the book and reopen the fork.
**Source:** user decision, 2026-10-15 (`docs/brand/identity/decisions.md`, fixes to
the redesign; interface recommendation 3). See
[ADR — the Origin is one line](decisions/20261015-the-origin-is-one-line.md).

### FORK-005 — A parent that was never published is not mentioned at all

While a recipe's parent has never been `published`, the system shall show the
recipe with no parent and shall give no indication that a parent exists to
anyone except the owner of both recipes in their own recipe book.

**Acceptance criteria**
- Given a published recipe forked from the author's own `private` recipe, when
  its card is opened by anyone, then no parent is named, no placeholder is
  shown, and the card is indistinguishable from that of a recipe with no parent.
- Given a published recipe whose parent is `link_shared` and has never been
  published, when its card is opened, then no parent is named.
- Given such a recipe, when its parent is published later, then the parent
  becomes named on the card from that moment, by FORK-004.
- Given such a recipe, when the chain is walked upwards from one of its own
  forks, then the chain stops at it and does not reveal its parent.
- Given the owner of both recipes, when they open the fork in their own recipe
  book, then the parent is named to them by its title, opens while that book holds
  it, and is named as unavailable when it does not; no one else is shown it.
- Given serverless mode (FLOW-061), when a recipe imported from a file, or a fork
  of one, is opened, then this requirement does not apply: nothing is published
  any more, and the parent is shown as FLOW-065 states.
- Given a fork sent before its parent (FLOW-007), when the parent is published
  later, then the parent becomes named on the fork from that moment, by FORK-004.

**Verification:** publish a fork of an own private recipe and inspect the card
as owner, as another account and as a moderator; then publish the parent and
inspect again.
**Source:** user decision, 2026-09-20. A parent nobody could ever open tells a
reader nothing and discloses that the author holds an unpublished recipe. The
recipe's stored parent is unchanged, so immutability is untouched: what depends
on the parent's state is the display, exactly as it already does in ST-007. See
[ADR — provenance follows the parent's publication](decisions/20260920-provenance-follows-publication.md).
**Changed 2026-09-28:** serverless mode and a fork sent before its parent added, by
user decisions of that date
([ADR — serverless mode and recipe files](decisions/20260928-serverless-mode-and-recipe-files.md)).
**Changed 2026-10-02:** criterion 5 said the parent was hidden from the owner too,
so that every viewer saw the same card. The v0.1 build names the parent of an own
fork on the device, and the user accepted it; by user decision at the sync of
that date the owner keeps seeing it once there is a server. The reason for hiding
— a parent no reader can open, and a disclosure that the author holds an
unpublished recipe — does not apply to the person who holds it. See
[ADR — the owner sees the parent of their own fork](decisions/20261002-the-owner-sees-their-own-parent.md).

### FORK-006 — No fork carries what readers gave the parent

The system shall create every fork with none of the parent's ratings,
favourites, reports or moderation history, whoever made the fork.

**Acceptance criteria**
- Given a parent with likes, dislikes, favourites and a moderation history, when
  any account forks it, then the fork has none of them and has never been
  submitted.
- Given the owner forking their own recipe, when the fork is created, then it
  too carries none of them, although it carries the parent's media and
  description (FORK-007).
- Given a parent that reports have hidden, when its owner forks it, then the
  fork carries no report and is not hidden.

**Verification:** fork a rated, favourited, reported and moderated recipe both as
its owner and from a second account, and inspect both results.
**Source:** user decision, 2026-09-20. These are readers' own acts and a
moderator's record about one particular recipe; they are not content, so they
are not what a fork copies, and the owner's own fork is no exception.

### FORK-007 — A fork of one's own recipe carries everything else

When the owner of a recipe forks that recipe, the system shall create the fork
holding the parent's media, description and the author's notes on its steps as
well as everything FORK-001 requires.

**Acceptance criteria**
- Given the owner forks their own recipe that has a cover image and photos on
  its steps, when the fork opens, then the same media is present and attached at
  the same places — the cover with the title, each step's photo at its own step
  (FLOW-026).
- Given the owner forks their own recipe that has a video on a step, when the
  fork opens, then the video is on that step.
- Given the owner forks their own recipe with a description, when the fork
  opens, then the description is present and identical.
- Given the owner forks their own recipe whose steps carry notes, when the fork
  opens, then each step's note is present and identical (DOM-027).
- Given that fork, when the owner deletes or replaces its media, then the
  parent's media is untouched, because the parent is frozen (CON-003) and the two
  are separate recipes.
- Given a recipe the owner themselves forked from another account, when the owner
  forks it again, then the fork carries the media and description the owner added
  to it, which are the only ones it holds (FORK-002).
- Given any state ST-004 allows the owner to fork from, when they fork, then this
  requirement applies in that state too.
- Given no account is active, when the person forks a recipe written in the pool
  (AUTH-026), then the recipe counts as their own and this requirement applies; a
  recipe imported from a file does not, and FLOW-065 governs it.
- Given an account that is not the owner, when it forks the same recipe, then
  FORK-002 governs and neither media nor description travels.

**Verification:** fork an own published recipe carrying a cover, two step photos,
a step video and a description; fork the same recipe from a second account;
compare the two results field by field.
**Source:** user decision, 2026-09-20. Publication is permanent (ST-001), so
forking their own recipe is the only way an author can ever correct it — and
under FORK-002 alone that correction cost them every photograph they had taken
and every word they had written about the dish. What FORK-002 protects is one
author's expression from another author; in an own fork there is no other author.
See
[ADR — an own fork carries everything](decisions/20260920-own-fork-carries-everything.md).
**Completed 2026-10-02:** who owns a pool recipe for this requirement was not
stated; the v0.1 build treats a recipe written on the device with no account as
the person's own, adopted at the sync of that date.
**Note:** the fork holds Media entities of its own, owned by the same account
(DOM-005), so the parent's media and the fork's are separable and each follows
its own recipe's visibility. The bytes behind them are stored once: MEDIA-012
deletes a file when no surviving recipe holds it, and MEDIA-010 counts distinct
stored files against the unpublished allowance, so an author forking their own
draft to correct it pays for the same photographs once and not twice. Corrected
2026-09-21 — this note previously said the question was an engineering choice
no requirement depended on, while two criteria of MEDIA-012 and one of MEDIA-013
were already written for a shared file.

## Replacing one's own recipe

A published recipe is never withdrawn (ST-001), so an author who wants to correct
one forks it. The two requirements below let that fork say what it is, so that a
reader who lands on the superseded recipe learns there is a newer version instead
of finding out by chance. Nothing here removes, hides, reorders or unrates
anything: the replaced recipe stays exactly where it was, with everything readers
gave it.

### FORK-008 — An owner may mark a fork of their own recipe as replacing it

Where an account forks a recipe it owns and that recipe is frozen (ST-003), the
system shall offer to mark the fork as replacing its parent, shall store the mark
on the fork, and shall freeze it with the rest of the fork's content.

**Acceptance criteria**
- Given an owner forking their own frozen recipe, when the editor opens, then the
  mark is offered and is off until the author sets it.
- Given an owner forking their own recipe while it is still `private`, when the
  editor opens, then the mark is not offered, because a `private` recipe is still
  editable and there is nothing to replace.
- Given an account forking a recipe it does not own, when the editor opens, then
  the mark is not offered at all: only an author may declare their own work
  superseded.
- Given a marked fork before submission, when the author changes their mind, then
  the mark can be cleared or set again.
- Given a marked fork that has been published, when any change to the mark is
  attempted, then it is refused like any other change to frozen content
  (CON-003).
- Given the mark, when what it affects is enumerated, then it is FORK-009 and
  nothing else: no rating, favourite or fork count moves anywhere (FORK-006), and
  the fork is an ordinary recipe in every other respect.

**Verification:** fork one's own published recipe, one's own private recipe and
another account's recipe, and compare what the editor offers; publish a marked
fork and attempt to clear the mark.
**Source:** user decision, 2026-09-21. See
[ADR — a published recipe is replaced, never withdrawn](decisions/20260921-a-published-recipe-is-replaced-never-withdrawn.md).

### FORK-009 — A replaced recipe says so and leads to the replacement

Where a recipe has a published fork marked as replacing it, the system shall
state on that recipe, wherever it is shown, that its author has published a newer
version, and shall lead from that statement to the most recently published such
fork.

**Acceptance criteria**
- Given a marked fork that is not published, when the parent is shown, then it
  says nothing, exactly as FORK-005 refuses to mention a parent that was never
  published: a statement would disclose that an unpublished recipe exists.
- Given a marked fork that is published, when the parent is shown on its own card
  or as an entry in any listing, then the statement is present in both places.
- Given two marked forks both published, when the parent is shown, then the
  statement is one statement and leads to the one published most recently.
- Given the statement, when the reader follows it, then the replacement opens and
  names the replaced recipe as its parent in the ordinary way (FORK-004).
- Given a replacement that has itself been replaced, when the first recipe is
  shown, then it leads to its own direct replacement and no further: a reader
  walks the chain one step at a time, as GOAL-003 limits lineage everywhere else.
- Given the replacement removed by a moderator (ST-002), when the parent is
  shown, then the statement is gone, and if another marked fork is published
  later the statement returns naming that one.
- Given the replaced recipe, when its ordering position, ratings, favourites and
  fork count are read, then none has changed: it keeps what readers gave it and
  competes in the catalogue on it (CAT-001, FORK-006).
- Given the statement, when it is read, then it reports what the author declared
  and asserts nothing about which recipe is better, because the product judges no
  recipe's craft (NGOAL-007).

**Verification:** publish two marked forks of one recipe and confirm the
statement names the newer; remove that one and confirm it names the other; mark a
fork and leave it unpublished and confirm the parent says nothing.
**Source:** user decision, 2026-09-21. It serves cooking rather than lineage — a
reader on the superseded version is one step from the version its author stands
behind — which is why it survives GOAL-003 while a fork tree does not. See
[ADR — a published recipe is replaced, never withdrawn](decisions/20260921-a-published-recipe-is-replaced-never-withdrawn.md).
