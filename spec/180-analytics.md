---
slug: ANL
status: closed
updated: 2026-09-28
ids: [ANL-001, ANL-002, ANL-003, ANL-004, ANL-005, ANL-006, ANL-007, ANL-008]
---

# What the product learns about its own use

The product measures itself, never its users. Everything a product owner — or a
buyer — normally asks for is available from three places that require tracking
nobody: what is already stored, the distribution channel's own statistics, and
three counters kept against a recipe rather than against a person.

What is deliberately unavailable: funnels, session length, cohort retention by
action, and any account of what a particular person cooked. Those need a record
of individual behaviour, and the product holds none (RET-010, OBS-007). The
trade is stated rather than regretted: the asset here is the fork graph and a
product that arrives with no personal data attached to it.

### ANL-001 — Nothing is measured about a person

The system shall derive every measurement in this section from stored content,
from aggregate counters, or from the distribution channel, and shall create no
record of an individual's behaviour for the purpose of measuring anything.

**Acceptance criteria**
- Given every measurement named in this section, when its inputs are
  enumerated, then none is a history of what one account or one device did.
- Given the counters of ANL-004, ANL-005 and ANL-008, when their stored form is
  inspected, then they are numbers against the product or against a recipe, and
  carry no account identifier and no device identifier (CON-001).
- Given a proposed measurement that would need such a record, when it is
  considered, then it is refused rather than built.

**Verification:** data model review of every input this section uses.
**Source:** CON-001, RET-010, OBS-007; user decision, 2026-09-21.

### ANL-002 — The product can be described from what it already holds

The system shall be able to report, from stored state alone and without holding
any further record, the following: published recipes in total and over a period;
the share of the catalogue that is forks, forks per recipe and the length of the
longest chain; accounts in total, the share that have published at least once,
and the share that have published more than once; ratings given over a period
and the share of recipes carrying at least one; favourites per recipe;
submissions over a period with the share approved and the time taken to decide;
reports per thousand published recipes, removals by category and rights holder
claims; and stored bytes per published recipe.

**Acceptance criteria**
- Given the stored data, when each figure above is asked for, then it can be
  computed without any record kept specially for it.
- Given a figure above, when it is computed twice over the same period, then it
  gives the same answer.
- Given the data model, when it changes, then each figure above still resolves or
  the change is recorded as a decision.

**Verification:** compute every figure against a seeded store and against the
same store a day later.
**Source:** user decision, 2026-09-21. The list is what the product owner would
have to answer if the product were being valued: how large it is, whether its
one mechanic is actually used, whether authors come back, what it costs to run
per recipe, and what liability it carries. The bytes per recipe are the figure
that turns CON-002 from a constraint into an argument.

### ANL-003 — Reach is taken from the distribution channel, not measured here

The system shall measure no installation, no uninstallation and no active
device, and shall take such figures from the distribution channel's own
statistics.

**Acceptance criteria**
- Given the released application, when its measurements are enumerated, then
  none counts installations or devices.
- Given the product owner needing reach figures, when they look for them, then
  they are in the distribution channel's console and not in the product.

**Verification:** review of what the application reports.
**Source:** user decision, 2026-09-21. The channel already reports installs,
uninstalls, active devices, retention and crashes without the product collecting
anything, and a figure produced by the channel is also a figure a buyer does not
have to take on trust. Checked 2026-09-21:
[Play Console — view app statistics](https://support.google.com/googleplay/android-developer/answer/139628?hl=en&co=GENIE.Platform%3DDesktop).

### ANL-004 — Application starts are counted, and nothing else about them

The system shall count the number of times the application is started each day,
and shall record nothing that distinguishes one start from another.

**Acceptance criteria**
- Given a day's starts, when the count is read, then it is a single number for
  that day.
- Given the stored counter, when it is inspected, then it holds no identifier of
  any kind and nothing that could group starts by device or by account.
- Given the same device starting the application five times in a day, when the
  count is read, then it has risen by five, because the product cannot tell it
  was one device.
- Given a start with no network connection, when the connection later returns,
  then the count has not risen for it and nothing was held on the device to be
  sent later, exactly as ANL-005 leaves an opening without a connection
  uncounted.
- Given the counter, when it is presented anywhere, then it is named as starts
  and never as people, and it states that starts without a connection are not
  counted (ANL-007).

**Verification:** start the application repeatedly from one device and read the
counter; inspect its stored form.
**Source:** user decision, 2026-09-21; the offline case decided the same day.
It is the coarsest possible measure of whether the product is in use at all, and
it is the most the product can offer without beginning to tell its users apart.
Holding unsent starts on the device and sending them later was weighed and
rejected: a running tally of what this device did, kept so that the product can
collect it, is a record of one device's behaviour, and ANL-001 and RET-010 were
both written on the premise that no such record exists. The undercount is
systematic and lands hardest where the product is most used — the kitchen, where
the network is worst (GOAL-001) — which is why ANL-007 makes the figure carry it.

### ANL-005 — A published recipe counts its opens and its favourites

The system shall count, for each published recipe, the number of times it has
been opened and the number of times it has been added to favourites, in both
cases by an account other than its owner or by a person holding no account.

**Acceptance criteria**
- Given a published recipe opened by its owner, when the count is read, then it
  has not risen.
- Given a published recipe opened by anyone else, when the server is reached,
  then the count has risen by one.
- Given a favourite opened with no network connection, when the device next
  reaches the server, then that opening is not counted, because nothing about it
  was kept (ANL-001).
- Given the same reader opening one recipe ten times, when the count is read,
  then it has risen by ten, because the product cannot tell it was one reader.
- Given a reader holding no account who favourites a recipe, when the server is
  reached, then the count has risen by one, although which recipes that device
  holds is still never sent (FLOW-051).
- Given a reader who removes a recipe from favourites, when the count is read,
  then it has not fallen: the figure counts additions, not holders.
- Given one reader who adds, removes and adds the same recipe again, when the
  count is read, then it has risen by two.
- Given a recipe hidden by reports and then restored, when its counts are read,
  then they are as they were.

**Verification:** open a recipe as owner, as another account and with none; add
it to favourites with and without an account; compare each count.
**Source:** user decision, 2026-09-21. Owner opens are excluded because an author
cooks from their own recipe, and a number an author can raise by using their own
work is not a measurement. Favourites are counted as additions rather than as
current holders because a reader with no account cannot report a removal — their
favourites vanish with the application — so a stock figure would drift upward and
mean two different things at once. Additions are also the figure the author
actually wants: openings say how well the cover and the description catch
somebody, additions say how well the content does.

### ANL-006 — Those two numbers belong to the recipe's owner alone

The system shall show the counts of ANL-005 and the fork count of ANL-008 to the
recipe's owner and to nobody else, and shall let them take part in no ordering, no ranking and no catalogue
entry.

**Acceptance criteria**
- Given a published recipe, when a reader opens it, then neither count is shown
  anywhere on the card or in any listing.
- Given the same recipe, when its owner opens it, then the openings, the
  additions to favourites and the forks made (ANL-008) are shown.
- Given the orderings of CAT-001, when they are computed, then neither count
  takes part in any of them.
- Given a moderator, when they open a recipe, then they see what a reader sees
  and not the owner's counts.

**Verification:** compare the card as owner, as a reader, and as a moderator.
**Source:** user decision, 2026-09-21. A number visible to readers becomes a
signal about the recipe's worth that the product did not intend to make, and one
that CAT-001's orderings would then be asked to use; kept private, it tells an
author whether their work is being cooked and does nothing else.

### ANL-007 — Every number says what it counts

The system shall label each figure it shows with what it actually counts, and
shall state where a figure is known to undercount.

**Acceptance criteria**
- Given the opens count, when it is shown to the owner, then it is named as
  openings and not as readers, and it states that openings without a connection
  are not counted; the same holds for an addition made without a connection.
- Given the favourites count, when it is shown, then it is named as additions to
  favourites, and it states that it never falls when a reader removes the recipe
  again.
- Given the starts counter of ANL-004, when it is reported, then it is named as
  starts and not as users.
- Given the fork count of ANL-008, when it is shown, then it is named as forks
  made by others, and it states that forks made without a connection are not
  counted and that a fork deleted afterwards is still counted.
- Given any figure of ANL-002, when it is quoted outside the product, then its
  definition is the one written in that requirement.

**Verification:** read every label against what the requirement says the figure
is.
**Source:** CON-004 and user decision, 2026-09-21. The undercount is not a
defect to be hidden: cooking happens where the network is worst (GOAL-001), so
the product systematically fails to count exactly the use it was built for, and
an author told otherwise would draw the wrong conclusion about their own recipe.

### ANL-008 — A published recipe counts the forks others make of it

When anyone other than a published recipe's owner creates a fork of it while the
server can be reached — with an account or without one, whether or not the fork
is ever sent — the system shall add one to that recipe's count of forks made, and
shall send nothing with that increment but the recipe it counts against; and the
system shall report to the product owner the forks made across the product, in
total and per day, and the number of published recipes forked at least once.

**Acceptance criteria**
- Given a published recipe, when a person with no account forks it on their
  device with a connection, then its count of forks made has risen by one, and
  the fork itself stays in the pool, unsent (FLOW-002).
- Given the increment, when what reached the server is inspected, then it names
  the recipe and nothing else: no account, no device, no fork (CON-001, ANL-001).
- Given the owner forking their own published recipe, when the count is read,
  then it has not risen.
- Given a fork created with no connection or while the server does not answer,
  when the device next reaches the server, then it has not been counted and
  nothing was held on the device to be sent later (ANL-004 (4)).
- Given a reader who declines the offer of FLOW-015 and creates a second fork of
  the same recipe, when the count is read, then it has risen by two in all,
  because the product cannot tell it was one reader.
- Given a fork of a published fork, when it is created, then only its direct
  parent's count rises; nothing further up the chain is counted (GOAL-003).
- Given a fork later deleted, or never sent, when the count is read, then it has
  not fallen: the figure counts forks made, not forks kept.
- Given a recipe hidden by reports and restored, or removed by a moderator, when
  its count is read, then it is as it was.
- Given a published recipe no one else has forked, when its owner opens it, then
  the count is shown as zero.
- Given the product owner's report, when it is read, then it gives the forks made
  in total, the forks made on each day, and how many published recipes have a
  count of one or more, each computed from these counts alone.
- Given serverless mode (FLOW-061), when a recipe is forked, then nothing is
  counted, because there is no server to count against.

**Verification:** fork a published recipe as another account, with no account,
offline, as its owner, twice after declining FLOW-015, and as a fork of a fork;
capture what each increment sends; read the recipe's count and the product
owner's report after each.
**Source:** user decision, 2026-09-28. The product owner's measure of having
finished includes recipes forked by others, and after
[ADR — the device first](decisions/20260928-the-device-first.md) a fork that is
never sent is invisible to the server; this counter is what makes the measure
readable without tracking anyone. It follows ANL-005 in every choice: no offline
holding, the owner excluded, additions rather than holders, shown to the owner
alone (ANL-006), and labelled with what it misses (ANL-007).
