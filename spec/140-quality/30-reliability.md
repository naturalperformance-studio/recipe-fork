---
slug: QUAL
status: closed
updated: 2026-09-28
ids: [QUAL-006, QUAL-008]
---

# Reliability

### QUAL-006 — Availability of what needs the server

The system shall be available for at least 95 per cent of each calendar month,
measured as the share of the observations of EXT-012 in which the endpoint of
EXT-011 answered as healthy.

**Acceptance criteria**
- Given a calendar month, when the probe's records are totalled, then at least 95
  per cent of its observations answered as healthy.
- Given a month below 95 per cent, when it is reviewed, then the cause is
  recorded, because the figure allows roughly 36 hours and a month that spends
  them is a month with an unexamined fault in it.
- Given the product is unavailable, when a reader uses the application, then
  every recipe in the recipe book — favourites and recipes written on the device
  — opens, cooks and recalculates, recipes are written, edited and forked, and
  every timer runs (FLOW-002, FLOW-009, FLOW-013, EXT-009), because none of that
  reaches the server.
- Given the product is unavailable, when a reader uses the application, then the
  catalogue, search, sending, publication, moderation, rating, account creation
  and signing in with a key not yet used on the device are all refused, each of them stating that the product
  could not be reached rather than that a connection is needed (FLOW-003), and
  none of them fails silently.
- Given the figure, when it is quoted anywhere the user can read it, then it is
  not, because the product makes no availability promise to a user and this
  threshold is an operating target.

**Verification:** the probe's monthly record for EXT-012.
**Source:** user decision, 2026-09-21. The figure was chosen against the
architecture rather than against an ambition: one server, no redundancy, one
operator who sleeps. The hosting provider's own commitment for a single cloud
server is 99.9 per cent monthly
([Hetzner Cloud SLA](https://docs.hetzner.com/general/company-and-policy/slas-cloud/),
checked 2026-09-21), so the provider is not the binding constraint — a deployment
that breaks at 23:00 on a Friday and is noticed on Saturday morning is. Eleven
hours is already outside 99 per cent and comfortably inside 95.

### QUAL-008 — The store is copied every hour and the copy is known to work

The system shall copy its own store to the object storage service at least once
an hour, shall be restorable from the most recent completed copy into an empty
environment, and shall lose at most one hour of accepted work when it is
restored.

**Acceptance criteria**
- Given any hour of operation, when the copies are enumerated, then at least one
  completed within it, and its completion and its size are recorded.
- Given the most recent completed copy, when it is restored into an empty
  environment, then every recipe, account, nickname, rating, report, moderation
  record and ingredient dictionary entry the product had accepted when that copy
  began is present and usable.
- Given that restore, when it completes, then nothing accepted after the copy
  began is present, so the work lost is at most one hour of it.
- Given a copy, when its contents are enumerated, then no media object is in it:
  media is protected by the durability of the object storage service and by
  nothing else, and a media object that service loses is lost permanently,
  because the recipe carrying it can never be edited (CON-003).
- Given the copies, when their retention is examined, then every hourly copy of
  the last 48 hours and one copy per day of the last 30 days is available.
- Given any quarter of operation, when the record is read, then at least one
  restore into an empty environment was performed and checked within it, and its
  result was recorded; a copy that has never been restored from does not satisfy
  this requirement.
- Given a copy that fails or does not run, when the operational signals are read,
  then it is visible there (OBS-001) and crosses the threshold of OBS-003 before
  a day's worth of copies has been missed.
- Given a restore, when it completes, then the whole state returns to the moment
  the copy began — including accounts deleted since (RET-004), recipes a
  moderator removed since (ST-002), private links revoked since (ST-005) and
  reports closed since — and this is accepted as the cost of recovery rather than
  a path the product offers anyone.

**Verification:** restore the most recent copy into an empty environment and
compare it against a record of the store as it was when the copy began;
enumerate a copy's contents and confirm no media object is present; confirm the
quarterly drill is recorded.
**Source:** user decision, 2026-09-21. See
[ADR — an hourly snapshot is the whole safety net](../decisions/20260921-an-hourly-snapshot-is-the-whole-safety-net.md).
The retention figures and the quarterly drill are the spec author's, on the
arithmetic that 78 copies of a store measured in tens of megabytes of text cost
cents a month at the price EXT checked, and that an untested copy is the most
common way a backup turns out not to exist.
