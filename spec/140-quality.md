---
slug: QUAL
status: closed
updated: 2026-09-21
ids: [QUAL-001, QUAL-002, QUAL-003, QUAL-004, QUAL-005, QUAL-006, QUAL-007,
  QUAL-008, QUAL-009, QUAL-010]
---

# Quality attributes

Every requirement here carries a metric, a method of measuring it and a
threshold. A quality statement without all three is not in this section.

One limitation governs the whole of it, and it is a consequence of a decision
taken deliberately elsewhere: RET-010 and OBS-007 forbid measuring anything about
a person, so the product collects no timings from real devices in real hands.
Every threshold below is therefore verified on a bench, against the reference
configurations of QUAL-001, before a release — and a device that is slower than
the bench says nothing to anyone. The thresholds are set with that in mind: they
are what the product must clear where it can be observed, not an estimate of what
a stranger's phone does.

Two requirements are not bench measurements and say so where they stand.
Availability is the one attribute the product cannot verify about itself at all,
because the failure it measures is the failure that silences the measurement; it
is observed from outside instead (EXT-012). And QUAL-010, the only commitment
this section makes to a person rather than to a device, is computed after the
fact from what the store already holds — no bench can measure how long a human
took to read something.

## Time behaviour

### QUAL-001 — Two reference configurations

Time behaviour shall be measured on the two configurations below, and every
threshold in this section shall name which of them it applies to.

| Profile | Device | Network |
|---|---|---|
| `modest` | an Android phone running the lowest version the product supports (QUAL-005), with no more than 4 GB of memory, of a model first sold at least three years before the measurement | a mobile connection constrained to 1.5 Mbit/s downstream, 0.5 Mbit/s upstream and 300 ms round trip |
| `typical` | a mid-range Android phone of a model first sold within two years of the measurement | a stable connection of at least 10 Mbit/s downstream and no more than 60 ms round trip |

**Acceptance criteria**
- Given a measurement of any threshold in this section, when it is recorded, then
  it names the profile it was taken on and the exact device model and connection
  used.
- Given a threshold that names a profile, when it is verified, then it is
  verified on that profile and a result from the other profile does not satisfy
  it.
- Given a measured operation, when the time is taken, then it starts at the
  user's action and ends when the content asked for is on screen and readable —
  not when a placeholder, a spinner or a partially drawn list is shown.
- Given a measurement, when it is taken, then it is the median of at least ten
  runs on a device that has been running the application for at least a minute,
  so that a first-launch cost is not measured as ordinary behaviour.

**Verification:** review of the release measurement report against this table.
**Source:** user decision, 2026-09-21 — both profiles are measured, because the
cheap phone and the mid-range phone are the common cases and a flagship that
clears them is not in question. The device classes are the product owner's; the
numeric network figures are the spec author's concretisation of "slow mobile
internet" and "stable 4G", written down so that two engineers measure the same
thing, and open to being replaced by figures the product owner prefers.

### QUAL-002 — Response times on `typical`

While the product is measured on the `typical` profile, the system shall complete
each operation below within its threshold.

| Operation | Threshold |
|---|---|
| Open a recipe held in favourites, with no connection | 1 s |
| Recalculate a recipe to a different serving count | 200 ms |
| Show the first screen of catalogue results | 2 s |
| Show the results of a search | 2 s |

**Acceptance criteria**
- Given the `typical` profile, when each operation is run as QUAL-001 requires,
  then its median is at or below the threshold above.
- Given the recalculation threshold, when it is measured, then it is measured on
  a recipe at the caps of DOM-012 — 50 ingredients and 50 steps — and not on a
  small one.
- Given the catalogue and search thresholds, when they are measured, then the
  store holds enough recipes that a result set has to be selected rather than
  returned whole.
- Given any operation above the threshold, when the release is reviewed, then it
  is a defect and not a known limitation.

**Verification:** bench measurement on the `typical` profile, recorded in the
release measurement report.
**Source:** user decision, 2026-09-21. These are the figures the product aims at.

### QUAL-003 — Response times on `modest`

While the product is measured on the `modest` profile, the system shall complete
each operation below within its threshold.

| Operation | Threshold |
|---|---|
| Open a recipe held in favourites, with no connection | 2 s |
| Recalculate a recipe to a different serving count | 500 ms |
| Show the first screen of catalogue results | 4 s |
| Show the results of a search | 4 s |

**Acceptance criteria**
- Given the `modest` profile, when each operation is run as QUAL-001 requires,
  then its median is at or below the threshold above.
- Given the same conditions QUAL-002 sets for the recipe size and the store size,
  when these are measured, then those conditions apply here unchanged.
- Given any operation above the threshold, when the release is reviewed, then it
  is a defect, on the same footing as a failure of QUAL-002.

**Verification:** bench measurement on the `modest` profile, recorded in the
release measurement report.
**Source:** user decision, 2026-09-21. The conservative figures are the binding
ones on the cheap phone and the slow network; the figures of QUAL-002 are what
the product aims at and are binding where the network is not the obstacle.

### QUAL-004 — The aimed-at figures are measured on `modest` too, and recorded

The system shall be measured on the `modest` profile against the thresholds of
QUAL-002 as well, and each result shall be recorded in the release measurement
report.

**Acceptance criteria**
- Given a release, when its measurement report is read, then it states, for each
  of the four operations, whether the `modest` profile met the `typical`
  threshold.
- Given a result that did not meet it, when the release is reviewed, then it is
  recorded and the release is not blocked by it, because QUAL-003 is the
  threshold that binds on this profile.
- Given the report, when it is compared with the previous release's, then the
  four results are comparable, because both were taken as QUAL-001 requires.

**Verification:** the release measurement report carries four rows for the
`modest` profile against the QUAL-002 thresholds.
**Source:** user decision, 2026-09-21, choosing measurement over a gate. The
product owner's instinct was a gate at half the figures; it was not written
because the four operations differ in difficulty by an order of magnitude — the
two local ones clear the strict figures on almost any device, and the two
network ones are bounded by a 300 ms round trip rather than by the code — so
"half of four" would have been satisfied by the same two every time and would
have measured the composition of the list rather than the quality of the build.

## Compatibility

### QUAL-005 — The lowest supported Android version

The system shall run on Android 10 and every later version, and shall not be
offered to a device running an earlier one.

**Acceptance criteria**
- Given the released package, when its declared minimum is read, then it is
  Android 10 (API level 29).
- Given a device running Android 9 or earlier, when the product is searched for
  in the distribution channel, then it is not offered as installable.
- Given a device running Android 10, when every function in this specification is
  exercised, then each one works, including the media conversion of MEDIA-002 and
  MEDIA-005 and the attestation request of AUTH-001, and none of them falls back
  to behaviour that differs from what is specified.

**Verification:** run the full functional set on a device at API level 29 and on a
current version.
**Source:** user decision, 2026-09-21. Android 10 and later covered roughly nine
tenths of the measured Android base in April 2026 — API level 30 alone reached
86.9 per cent
([Android version market share, StatCounter](https://gs.statcounter.com/os-version-market-share/android),
checked 2026-09-21). Raising the floor to Android 12 after the MVP, once the
distribution figures for the product's own installed base can be read from the
channel, is OQ-031.

## Reliability

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
  every recipe in favourites opens, cooks and recalculates, and every timer runs
  (FLOW-009, FLOW-013, EXT-009), because none of that reaches the server.
- Given the product is unavailable, when a reader uses the application, then the
  catalogue, search, publication, moderation, rating, account creation and
  signing in with a key state that a connection is needed (FLOW-003) and none of
  them fails silently.
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
[ADR — an hourly snapshot is the whole safety net](decisions/20260921-an-hourly-snapshot-is-the-whole-safety-net.md).
The retention figures and the quarterly drill are the spec author's, on the
arithmetic that 78 copies of a store measured in tens of megabytes of text cost
cents a month at the price EXT checked, and that an untested copy is the most
common way a backup turns out not to exist.

## Security

### QUAL-007 — The rate at which deletion keys may be tried

The system shall accept at most ten deletion key submissions in any 24 hours from
one source at the deletion page, and shall reject every further submission from
that source until the period has passed.

**Acceptance criteria**
- Given one source that has submitted ten keys in 24 hours, when it submits an
  eleventh, then it is rejected without being checked against any account, and
  nothing is deleted.
- Given a rejected submission, when the page answers, then it discloses nothing
  about whether any of the ten matched, in keeping with EXT-006.
- Given the limit, when a submission is refused by it, then the refusal is
  counted in the signal OBS-001 keeps for rejected attempts at the deletion page.
- Given the source record the limit needs, when its lifetime is examined, then it
  is discarded no later than RET-008 discards a server log.
- Given a deletion key's length, when it is chosen, then ten tries a day from
  every source an attacker can obtain cannot find a live key in any practical
  time (AUTH-017).

**Verification:** submit eleven keys from one source inside 24 hours and confirm
the eleventh is refused; confirm the refusal is indistinguishable from a
non-matching key.
**Source:** user decision, 2026-09-21. Two limitations are stated rather than
hidden. The source is an IP address, which is the same thing OQ-029 has not
settled the legal status of and the reason RET-008 keeps logs for fourteen days;
the limiter holds it for no longer. And the limit is the second line of defence,
not the first: what actually protects a deletion key is its length, which depends
on the key's length, which AUTH-014 requires to hold against exactly this rate.

### QUAL-009 — The rate at which account keys and recovery keys may be tried

The system shall accept at most ten attempts in any 24 hours from one source to
present an account key or a recovery key, counted together, and shall reject
every further attempt from that source until the period has passed.

**Acceptance criteria**
- Given one source that has attempted ten sign-ins in 24 hours, when it attempts
  an eleventh, then it is rejected without being checked against any account.
- Given a rejected attempt, when the answer is read, then it discloses nothing
  about whether any key tried matches an account.
- Given a successful sign-in, when it is counted, then it counts toward the ten
  like any other attempt, so a source cannot buy attempts by holding one valid
  key.
- Given the screen that accepts a recovery key (AUTH-021), when keys are tried
  there, then they draw on the same ten, so an attacker gains nothing by moving
  between the two screens.
- Given the source record the limit needs, when its lifetime is examined, then it
  is discarded no later than RET-008 discards a server log, exactly as QUAL-007
  requires of its own.
- Given an account key's or a recovery key's length, when it is chosen, then ten
  tries a day from every source an attacker can obtain cannot find a live one in
  any practical time (AUTH-014, AUTH-020).

**Verification:** attempt eleven sign-ins from one source inside 24 hours and
confirm the eleventh is refused before any key is checked.
**Source:** user decision, 2026-09-21. The same shape and the same figure as
QUAL-007, deliberately: one rule to implement, one record to expire, and the two
places a key is presented behave alike. A guessed account key is worse than a
guessed deletion key — the first takes an account over, the second only destroys
it — so the limit is at least as tight here as there.

## The one commitment made to a person

### QUAL-010 — The time a moderation decision takes

The system shall have decided at least 95 per cent of the moderation submissions
of each calendar month within seven days of their submission, measured from the
`submitted_at` and `decided_at` the submission record already holds.

**Acceptance criteria**
- Given a calendar month, when its decided submissions are totalled, then at
  least 95 per cent of them were decided within seven days of being submitted.
- Given the measurement, when its inputs are enumerated, then they are two
  timestamps the domain model already carries, so nothing is recorded for the
  purpose and nothing about a person is measured (RET-010, OBS-007, ANL-001).
- Given a submission its owner withdrew (ST-008, MOD-011), or one destroyed with
  its owner's account (MOD-012), when the month is computed, then it counts at
  neither end: it left the queue without a decision, and counting it would
  measure the author's act rather than the moderator's.
- Given the expectation MOD-005 states before submission, when it is read, then
  it is the seven days of this requirement and not another number.
- Given a claim at the claims address (ABUSE-007), when the period it is
  answered in is stated, then it is these same seven days, although nothing in
  the product measures that one: the mailbox is outside it.
- Given a month below 95 per cent, when it is reviewed, then the cause is
  recorded, exactly as QUAL-006 requires of a month below its own figure.
- Given the age of the oldest entry in the queue, when it passes 48 hours, then
  OBS-003 marks that signal and OBS-004 alerts on it — an early warning, not this
  threshold, which is still met by a queue whose entries waited six days.

**Verification:** compute the month's figure from the stored submission records;
read the period stated before submission and the period published beside the
claims address, and confirm both are seven days.
**Source:** user decision, 2026-09-21. MOD-005 has required since 2026-09-20 that
the wait be stated before submission and said the figure itself "is a service
commitment with a measurement method, specified in section `quality`" — and this
section never carried one, so two requirements pointed at a number that existed
nowhere, ABUSE-007 joining them the same day. Seven days is the draft's own "up
to a week" (section 6.3). It is 95 per cent of a month rather than every
submission because one moderator sleeps, travels and falls ill, and a commitment
that breaks the first week the person has influenza teaches everyone to ignore
it. **Cost, stated:** an author can be the unlucky five per cent and has no
recourse and no notification — MOD-015 delivers a decision, never its absence, so
a late submission looks to its author exactly like an early one. See
[ADR — a week, and ninety-five per cent of a month](decisions/20260921-a-week-and-ninety-five-per-cent.md).

## Where the other quality characteristics live

Characteristics of ISO/IEC 25010 that carry no requirement in this section are
not thereby unaddressed; each is specified as behaviour elsewhere, or is
deliberately not committed to. This table exists so that the difference is
visible.

| Characteristic | Where it is specified |
|---|---|
| Functional suitability | The functional sections; the exact rounding of [`scaling-rules`](80-scaling-rules.md) is where correctness is measurable rather than asserted, and QUAL-010 is where the product commits to a person rather than to a device. |
| Performance efficiency | QUAL-001 to QUAL-004 for time behaviour; resource use is bounded by CON-002 and watched as a signal (OBS-001, OBS-003). |
| Compatibility | QUAL-005; the product interoperates with four services and one observer, and the list is closed (EXT-001). |
| Interaction capability | Section [`accessibility`](200-accessibility.md): a fixed list of thresholds — contrast, touch targets, text scale, the screen reader on every screen including the editor, no action that needs a drag, and the cooking card's own figures — with no conformance claim made. |
| Reliability | QUAL-006 for availability, QUAL-008 for recoverability. Fault tolerance is not committed to: there is one server and no redundancy, which is what the 95 per cent of QUAL-006 accounts for. |
| Security | QUAL-007 and QUAL-009, the two places a key is presented; the rest is structural rather than measurable — no personal data exists to lose (CON-001), the account key is never uploaded (EXT-008), and a stolen deletion key destroys an account but never takes it over (AUTH-017). |
| Maintainability | Not committed to as a measured attribute. The one maintainability property the product does buy is the replaceability of the storage provider (EXT-003). |
| Flexibility | Not committed to. The product has one client surface (CON-005) and two languages at first release, with a third a product decision of its own (L10N-001, MVP-002). |
| Safety | `n/a`. The product controls no physical process; the one place where harm is possible — a recipe that is dangerous to cook — is handled by moderation and by MOD-008, not by a quality threshold. |
