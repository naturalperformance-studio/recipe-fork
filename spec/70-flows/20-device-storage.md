---
slug: FLOW
status: open
updated: 2026-09-20
ids: [FLOW-009, FLOW-010, FLOW-011, FLOW-012]
---

# Favourites and what the device keeps

Favourites are the only thing the reader keeps, and one setting governs whether
their media comes along.

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
favourited recipes is held on the device, and shall hold the text of every
favourite regardless of that setting.

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
  evicted by count or by size: the setting is the only control.

**Verification:** switch the setting both ways with favourites already held, and
fill the device to confirm the failure is contained to the recipe being held.
**Source:** user decision, 2026-09-20. One setting rather than separate controls
for photo and video, because video here is a short clip showing a technique —
how something is cut, how one ingredient goes into another — and not a lesson,
so its weight is close to a photo's. The split into two settings is a change
that can be made later without changing anything else.
**Note:** the setting is on by default. What video is for is recorded in OQ-003,
which sets the limits that make this true.

### FLOW-012 — Leaving the application does not lose the recipe being read

When the reader returns to the application after leaving it, the system shall
show the recipe that was open, at the scale it was showing, at the step the
reader had reached.

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
  available — held on the device, or fetchable over the network.
- Given the operating system ended the application, the recipe is not in
  favourites, and there is no connection, when the reader opens the application
  again, then it states that the recipe needs a connection and offers to retry,
  and does not silently drop the reader into the catalogue.

**Verification:** scale a recipe, fix an ingredient, start a timer, leave the
application for several minutes and return; repeat with the process ended by the
operating system, in both the favourited and the non-favourited case.
**Source:** user decision, 2026-09-20. Cooking is interrupted constantly, and an
interruption is not a reason to lose the page: leaving the application and
closing it are different events and only the second can cost the recipe.
**Note:** whether a running timer announces itself while the application is not
in the foreground is decided with the recipe card flow, not here.
