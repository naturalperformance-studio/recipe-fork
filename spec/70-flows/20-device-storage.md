---
slug: FLOW
status: closed
updated: 2026-10-03
ids: [FLOW-009, FLOW-010, FLOW-011, FLOW-060, FLOW-012, FLOW-058, FLOW-051, FLOW-052]
---

# The recipe book and what the device keeps

Favourites — the recipes a reader found and marked, which with the recipes they
wrote make up the section the interface calls "My recipe book" (FLOW-034) — are the
main thing the reader keeps; a list of blocked authors is the other. One setting
governs whether media comes along, and one action deletes what media the device
can fetch again.

### FLOW-009 — Every favourite is readable without a network connection

The system shall hold on the device the full text of every recipe in the
account's favourites — its title, tags, serving count, ingredients, steps,
timers and provenance — and shall keep it there for as long as the recipe stays
in favourites.

**Acceptance criteria**
- Given no network connection, when the reader opens any recipe in their
  favourites, then it opens in full, can be scaled by `scaling-rules`, and its
  timers run.
- Given a recipe is added to favourites while a connection is available, when
  the addition completes, then its text is on the device without the reader
  asking for it.
- Given an account with several hundred favourites, when they are held, then no
  favourite's text is dropped to make room for another: text is never evicted.
- Given a recipe is removed from favourites, when the removal completes, then
  its held text and media are removed from the device.
- Given a favourite that stops being accessible, when the device next reaches
  the server, then its held content is deleted from the device and the favourite
  itself stays in the list (FLOW-010).
- Given a recipe the reader opened but never added to favourites, when the
  reader opens it with no connection, then it is not available (FLOW-003).

**Verification:** favourite a recipe, go offline, cook from it end to end
including timers; then have a moderator remove it and confirm the held copy
disappears on the next connection.
**Source:** user decision, 2026-09-20. Favouriting is the act of taking a recipe
with you; nothing is held that the reader did not mark, because a recipe's text
is a few kilobytes and the bound that matters is the reader's own choice.

### FLOW-010 — A favourite that becomes unavailable stays in the list

If a recipe in an account's favourites stops being accessible to that account,
then the system shall keep it in the favourites list marked as unavailable,
shall delete its held content from the device, shall state why only where it was
removed as dangerous to health, and shall not notify the reader.

**Acceptance criteria**
- Given a favourite that reports have hidden, when the device next reaches the
  server, then the recipe stays in the favourites list, is marked unavailable,
  cannot be opened, and its held text and media are gone from the device.
- Given that recipe, when a moderator dismisses the reports and it returns to
  `published`, then it becomes openable again and its content returns to the
  device without the reader doing anything.
- Given a favourite a moderator has removed, when the device next reaches the
  server, then it is marked unavailable and never becomes openable again.
- Given a favourite removed as dangerous to health, when the reader looks at it,
  then that reason is stated, because the reader has cooked from it and may cook
  from it again.
- Given a favourite removed for using someone else's content, or one hidden by
  reports, when the reader looks at it, then no reason is given and the two
  cases are indistinguishable.
- Given any of these, when they happen, then no notification was delivered.
- Given an unavailable favourite, when the reader removes it from favourites,
  then it leaves the list like any other.
- Given no network connection, when the reader opens a favourite that has been
  hidden or removed since the device last reached the server, then it still
  opens, because the device cannot know.

**Verification:** hide a favourite by reports and reconnect; dismiss the reports
and reconnect; remove another favourite as a moderator and reconnect; inspect
the list and the device's held content at each step.
**Source:** user decision, 2026-09-20. The reader marked the recipe and the
reader unmarks it; the product does not tidy the list on their behalf and does
not spend a notification on it. A shelf in a shop shows that an item ran out
rather than rearranging itself.

### FLOW-011 — One setting governs whether media is held

The system shall offer a single setting that governs whether the media of
favourited recipes and of the sent recipes the account owns (FLOW-038) is held on
the device, shall hold the text of every one of them regardless of that setting,
and shall always hold the media of a recipe that was never sent or holds media not
sent, because the device holds its only copy.

**Acceptance criteria**
- Given the setting is on, when a recipe is added to favourites, then its cover,
  its step photos and its step video are held on the device.
- Given the setting is off, when a recipe is added to favourites, then only its
  text is held and its media requires a connection.
- Given held media, when the reader turns the setting off, then the held media
  is deleted from the device and every favourite keeps its text.
- Given the setting is on and the device has no space left, when media is being
  held, then the download fails, the recipe stays readable, its media requires a
  connection, and no other favourite's media is deleted to make room.
- Given any number of favourites, when media is held, then nothing is capped or
  evicted by count or by size: the setting and the action of FLOW-060 are the only
  controls.
- Given a recipe never sent, or media added to a sent recipe and not sent, when
  the setting is turned off, then that media stays on the device.

**Verification:** switch the setting both ways with favourites already held, and
fill the device to confirm the failure is contained to the recipe being held.
**Source:** user decision, 2026-09-20. One setting rather than separate controls
for photo and video, because video here is a short clip showing a technique —
how something is cut, how one ingredient goes into another — and not a lesson,
so its weight is close to a photo's. The split into two settings is a change
that can be made later without changing anything else.
**Note:** the setting is on by default. What video is for is recorded in OQ-003,
which sets the limits that make this true.
**Changed 2026-09-28:** media the device holds as its only copy is outside the
setting, since recipes are sent only when their owner sends them (FLOW-059); by
user decision of that date
([ADR — the device first](../decisions/20260928-the-device-first.md)).

### FLOW-060 — Media the device can fetch again is deleted on request

The system shall offer, beside the setting of FLOW-011, an action that deletes
from the device the held media of every favourite and of every sent own recipe in
the recipe book in use — the active account's, or the pool's — and that deletes
no media the device holds as its only copy and none of another book on the device,
which AUTH-026 and AUTH-027 keep apart.

**Acceptance criteria**
- Given held media, when the action is used, then the media of every favourite
  and of every own recipe whose media the server holds is gone from the device,
  and every one of those recipes keeps its text.
- Given a recipe never sent, or media added to a sent recipe and not sent, when
  the action is used, then that media stays.
- Given the action used, when a recipe whose media was deleted is opened with a
  connection, then its media is shown from the server as FLOW-011 (2) shows it
  with the setting off; the setting itself is unchanged and holds media for
  recipes added afterwards.
- Given two recipe books on the device, when the action is used in one, then the
  media held for the other is untouched.
- Given serverless mode (FLOW-061), when the action is chosen, then it states
  first that the deleted media cannot be fetched again, and deletes nothing until
  the person confirms.

**Verification:** hold media for favourites, sent recipes and an unsent draft;
use the action and inspect what is left; open a cleared recipe with a connection;
repeat in serverless mode.
**Source:** user decision, 2026-09-28: the setting stays and an action to clear
media from the device is added beside it. What is cleared is what can be fetched
again, since the product owner called it clearing a cache. Amended 2026-10-03
(sync v0.3–v0.14): the action acts on the book in use, as AUTH-026 and AUTH-027
keep each book apart — req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03.

### FLOW-012 — Leaving the application does not lose the recipe being read

When the reader returns to the application after leaving it, or opens it again
after the operating system ended it, the system shall show the recipe that was
open, at the scale it was showing, at the place in it the reader had scrolled to.

**Acceptance criteria**
- Given a recipe open at a changed serving count with a fixed ingredient set,
  when the reader leaves the application and returns to it, then the same recipe
  is shown at the same scale with the same fixed ingredient, whether or not it
  is in favourites and whether or not there is a connection.
- Given a running timer, when the reader leaves the application and returns,
  then the timer shows the time remaining by the clock, not the time it held
  when the application was left.
- Given the operating system ended the application while it was away, when the
  reader opens it again, then the same recipe is shown if its content is
  available — held on the device, or fetchable over the network — at the same
  serving count, with the same fixed ingredients and at the same place.
- Given the operating system ended the application, the recipe is not in
  favourites, and there is no connection, when the reader opens the application
  again, then it states that the recipe needs a connection and offers to retry,
  and does not silently drop the reader into the catalogue.

- Given the reader had scrolled so that a given ingredient line or step was the
  first one at the top of the screen, when they return, then that line or step is
  again the first at the top, even if the text scale (A11Y-004), the orientation
  of the screen or a timer that ended while they were away changed the layout.

**Verification:** scale a recipe, fix an ingredient, scroll to its middle, start a
timer, leave the application for several minutes and return; repeat with the
process ended by the operating system, in both the favourited and the
non-favourited case, and once with the text scale changed and once with the
device rotated while away.
**Correction, 2026-09-27:** the statement restored the place and no criterion
observed it, and whether scale and place survive the operating system ending the
application was read two ways (SD-026). By user decisions of that date the place
is the line or step at the top of the screen, and the recipe, its scale and its
place all survive the process being ended; the device keeps them for that purpose
only (RET-010).
**Source:** user decision, 2026-09-20. Cooking is interrupted constantly, and an
interruption is not a reason to lose the page: leaving the application and
closing it are different events and only the second can cost the recipe.
**Note:** whether a running timer announces itself while the application is not
in the foreground is decided with the recipe card flow, not here.
**Corrected 2026-09-21:** this requirement said "at the step the reader had
reached", which promised a current step the card does not have — the card is one
scrolling screen (`30-cooking`) and nothing in the product advances through
steps. What is restored is the scroll position.

### FLOW-058 — A session ends only when the reader closes the application

The system shall treat one session as lasting from a start of the application
that has nothing to restore until the next such start, shall start a new session
only when the reader closed the application — removed it from the list of recent
applications — or the device was restarted, and shall not start one when the
operating system ended the application's process while it was away.

**Acceptance criteria**
- Given a view chosen in the favourites section (FLOW-034), when the reader
  leaves the application by the home action or the task switcher and returns
  while its process is alive, then it is the same session and the view is still
  in force.
- Given the same choice, when the operating system ends the application's
  process while it is away and the reader returns to it, then it is the same
  session and the view is still in force, exactly as the recipe being read is
  restored by FLOW-012.
- Given the same choice, when the reader removes the application from the list
  of recent applications and opens it again, then a new session has started and
  the section opens as FLOW-034 (6) states.
- Given the same choice, when the device is restarted and the reader opens the
  application, then a new session has started.
- Given a new session, when anything held for the length of a session is looked
  at — the ordering of CAT-001, the filter of FLOW-033, the view of FLOW-034 and a
  count the reader set on a card (FLOW-017) — then each is back at its default.

**Verification:** choose the own-recipes view, a non-default ordering and the
own-recipes filter; return after the home action, after ending the process from
the developer options while away, after removing the application from the recent
list and after restarting the device, and inspect all three each time.
**Source:** user decision, 2026-09-27 (SD-028). The line falls where the product
already put it: leaving the application and the system ending it are not the
reader's acts and cost nothing (FLOW-012); closing it is, and a fresh start is
what FLOW-017 already meant. See
[ADR — a session is ended by the reader](../decisions/20260927-a-session-is-ended-by-the-reader.md).

### FLOW-051 — What is kept with no account lives on the device and nowhere else

The system shall allow a person with no account active to add recipes to
favourites, to write recipes (FLOW-002) and to block authors, shall keep all three
on the device, in the pool of AUTH-026, under the same rules as an account's, and
shall send none of them anywhere.

**Acceptance criteria**
- Given no account, when a recipe is favourited, then it is held on the device
  and opens without a connection exactly as FLOW-009 requires.
- Given no account, when favourites are kept, then which recipes this device
  holds is never sent, so nothing identifies the device or the person (CON-001).
- Given no account, when a recipe is added to favourites, then the only thing
  that reaches the server is an increment against that recipe, carrying no
  identifier (ANL-005), and a later removal sends nothing at all.
- Given no account, when an author is blocked, then the block is kept on the
  device, is sent nowhere, and hides that author's recipes from this device's
  catalogue and search exactly as ABUSE-005 requires of an account's block.
- Given no account, when the application is reinstalled or the device is
  replaced, then those favourites, recipes and blocks are gone and nothing
  restores them.
- Given the pool, when an account becomes active on the device, then AUTH-026
  offers to move the pool into it; declined, the pool stays on the device and is
  shown again whenever no account is active.
- Given no account, when the recipe book is opened, then it states that what it
  holds lives on this device and offers to create an account to carry it, without
  requiring it.

**Verification:** favourite recipes with no account, reinstall, and confirm the
loss; capture traffic to confirm nothing was sent.
**Source:** user decision, 2026-09-20; amended 2026-09-21 so that an addition
counts toward the author's own figure (ANL-005) without the list itself leaving
the device; widened 2026-09-28 to recipes written with no account, and the loss
at a sign-in replaced by the offer of AUTH-026, by user decision of that date. Accounts are for the people who publish;
a reader who only cooks should not have to hold one, and the price of that is
stated plainly rather than discovered after a lost phone.

### FLOW-052 — An account carries favourites beyond the device

The system shall carry the favourites of an account to every device that account
is signed in to, and shall take into the account the favourites the device was
already holding when it was created.

**Acceptance criteria**
- Given an account signed in on two devices, when a recipe is favourited on one,
  then it is in the favourites of the other once it reaches the server.
- Given favourites and blocked authors kept in the pool, when an account is
  created or signs in on that device, then AUTH-026 offers to move them into it,
  with the pool's recipes; accepted, they become the account's and none is lost.
- Given a muted account (AUTH-004), when it favourites a recipe, then it is
  carried like any other account's, because this is the one thing a muted
  account carries beyond the device (it cannot send a recipe, AUTH-004).
- Given favourites, when they are carried, then they reach the server by
  themselves, without the sending of FLOW-059, which is for recipes the account
  wrote.
- Given a removed favourite, when the removal reaches the server, then it is gone
  on the other device too.

**Verification:** favourite on two devices under one account; create an account
on a device whose pool holds favourites and accept the offer; sign in to an
existing account on another such device, decline, and compare.
**Source:** user decision, 2026-09-20: the reason a reader would want an account
at all is that favourites survive the phone. Favourites keep travelling by
themselves after sending became manual, by user decision of 2026-09-28: they are
references, not recipes the account wrote. The pool replaced the rule that a
sign-in deletes what the device held, by user decision of the same date
([ADR — several accounts on one device](../decisions/20260928-several-accounts-on-one-device.md)).
