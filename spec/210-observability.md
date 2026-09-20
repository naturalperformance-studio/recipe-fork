---
slug: OBS
status: closed
updated: 2026-09-21
ids: [OBS-001, OBS-002, OBS-003, OBS-004, OBS-005, OBS-006, OBS-007]
---

# Operational signals

The product is run by one person who is also its only moderator. Everything here
exists so that the things that decide slowly — a dictionary falling behind, a
storage bill creeping toward its ceiling, a queue that stopped being emptied —
are visible before they become decisions taken in a hurry.

Most of these signals were promised elsewhere as the price of a decision taken
without a guard. Each row below names where it was promised, so that a signal
that stops being needed can be found and dropped with the decision that asked
for it.

Nothing measured here is about a person: every signal is a total or a share, and
RET-010 keeps the product from holding the material a per-person measurement
would need.

### OBS-001 — What the product measures

The system shall measure and report the following signals:

| Signal | Promised by | What it decides |
|---|---|---|
| Share of the nickname space consumed | OQ-009 | when the word lists must be extended |
| Share of ingredients entered as free text, per week | OQ-019 | how far the ingredient dictionary is behind |
| Transfer size of the ingredient dictionary | OQ-021 | when holding only part of it becomes the right answer |
| Unpublished media held by accounts dormant twelve months | RET-007 | whether abandoned storage is becoming real |
| Share of ratings from accounts that have published nothing | RATE-005 | whether score is being farmed |
| Entries in the moderation queue, and the age of the oldest | `abuse`, `moderation` | whether one moderator is still enough |
| Failed media uploads, as a share of attempts per day | `media` | whether conversion or storage is broken |
| Recalculation errors | `scaling-rules` | whether a recipe scales to something impossible |
| Stored bytes against the ceiling of CON-002 | CON-002 | whether the product still fits its budget |
| Rejected attempts at the deletion page, per day | EXT-006 | whether deletion keys are being guessed |
| Share of accounts created muted | AUTH-004 | whether the attestation service is failing quietly |
| Verdict requests against the service's free quota | EXT-002 | whether the gate is about to start costing money |
| Time since the last completed copy of the store | QUAL-008 | whether the safety net is still there |

**Acceptance criteria**
- Given the running product, when the signals are enumerated, then each row above
  is present and carries a current value.
- Given any signal, when its value is read, then it is a total or a share and
  never a value about one identified person (RET-010).
- Given a decision that stops applying, when its row is reviewed, then the signal
  can be dropped because the row names what asked for it.
- Given a new decision taken with a cost accepted rather than prevented, when it
  is recorded, then a row is added here.

**Verification:** compare the reported set against this table after a seeded run.
**Source:** assembled 2026-09-21 from the decisions of 2026-09-20 and 2026-09-21,
each of which named the signal it needed. Two rows are the spec author's
additions and are flagged as such: a muted account is invisible to its holder as
a fault, and the free quota is the point where a free dependency becomes a bill.
The last row was added on 2026-09-21 by QUAL-008, which is the rule this
requirement states: a decision whose cost is accepted rather than prevented
brings a signal with it.

### OBS-002 — The signals are shown to the moderator inside the application

The system shall show the signals of OBS-001 on a screen reachable only by an
account holding the moderator role.

**Acceptance criteria**
- Given a moderator, when they open the application, then the signals screen is
  reachable from the same place as the moderation queue.
- Given any account without the moderator role, when the application is
  enumerated, then no such screen exists for them.
- Given the screen, when it is opened, then each signal shows its current value
  and whether it has crossed its threshold (OBS-003).
- Given no network connection, when a moderator opens the screen, then it states
  that a connection is needed, as FLOW-003 requires of everything the server
  holds.

**Verification:** open the screen as a moderator and as an ordinary account.
**Source:** user decision, 2026-09-21. The signals live where the work already
happens, and the product builds no separate operational surface: a second web
console would be a second thing to secure for an audience of one.

### OBS-003 — Thresholds say when a signal has become a decision

The system shall mark a signal as requiring attention when it crosses the
threshold below, and shall leave every other signal unmarked.

| Signal | Threshold |
|---|---|
| Stored bytes against the ceiling of CON-002 | 70 per cent |
| Age of the oldest entry in the moderation queue | older than 48 hours |
| Verdict requests against the free quota | 50 per cent in a day |
| Share of the nickname space consumed | 50 per cent |
| Share of ingredients entered as free text | 20 per cent in a week |
| Failed media uploads | 5 per cent of attempts in a day |
| Time since the last completed copy of the store | longer than three hours |

**Acceptance criteria**
- Given a signal below its threshold, when the screen is shown, then it is
  present and unmarked.
- Given a signal at or above its threshold, when the screen is shown, then it is
  marked and OBS-004 sends an alert.
- Given a signal with no threshold in this table, when it is shown, then it is
  never marked and never alerts.
- Given a threshold crossed and then cleared, when the value falls back, then the
  mark is removed and no further alert is sent.

**Verification:** drive each signal across its threshold and back against a
seeded store.
**Source:** user decision, 2026-09-21. The thresholds are deliberately early
rather than tight: each one is the point at which there is still time to decide
something, not the point at which the product breaks.

### OBS-004 — Alerts leave by one outbound channel and carry only numbers

When a signal crosses its threshold, the system shall send one message to a
single alerting channel, and that message shall carry the signal's name and its
value and nothing else.

**Acceptance criteria**
- Given a crossed threshold, when the alert is sent, then it names the signal and
  its value.
- Given any alert, when its contents are inspected, then it carries no recipe
  title, no recipe content, no nickname, no account identifier and no device
  identifier (EXT-010).
- Given a signal that stays above its threshold, when time passes, then no
  further alert is sent for the same crossing.
- Given several signals crossing at once, when the alerts are sent, then each is
  its own message.

**Verification:** cross a threshold with the outbound traffic captured and read
the message that leaves.
**Source:** user decision, 2026-09-21: without an alert the storage ceiling is
learned about when the application is next opened, which may be days. The
restriction to numbers is what keeps this channel compatible with EXT-010; see
[ADR — one outbound alerting channel](decisions/20260921-alerting-channel.md).

### OBS-005 — The alerting channel is one-way

The system shall accept no instruction, command or request through the alerting
channel, and shall treat anything arriving on it as nothing.

**Acceptance criteria**
- Given a message sent to the channel from outside, when it arrives, then the
  product takes no action of any kind.
- Given the channel, when the product's control surfaces are enumerated, then it
  is not among them.
- Given someone who obtains access to the channel, when they attempt to act,
  then they can read past alerts and nothing more.

**Verification:** send commands to the channel and confirm nothing in the product
changes.
**Source:** user decision, 2026-09-21, taking the spec author's condition. A
channel that accepts commands is a second way to operate the product, with its
own access problem, sitting outside everything `auth` was built to protect.

### OBS-006 — An unavailable channel costs alerts, nothing else

If the alerting channel cannot be reached, then the system shall continue to
measure and to show every signal, and shall not retry indefinitely.

**Acceptance criteria**
- Given the channel is unreachable, when a threshold is crossed, then the signal
  is marked on the screen of OBS-002 and the product keeps working.
- Given the channel is unreachable, when the alert fails, then it is abandoned
  after a bounded number of attempts and nothing queues up without limit.
- Given the channel returns, when the next threshold is crossed, then that alert
  is sent.
- Given a crossing whose alert was never delivered, when the moderator opens the
  screen, then the mark is there, because the screen and not the channel is the
  record.

**Verification:** block the channel, cross a threshold, and inspect both the
screen and the retry behaviour.
**Source:** the alerting channel is the fourth external dependency and obeys the
rule that governs the other three (EXT-002, EXT-004): it may degrade the product
and may not stop it.

### OBS-007 — Nothing is measured about a person

The system shall derive every signal from totals and shares, and shall create no
record of an individual account's behaviour in order to measure anything.

**Acceptance criteria**
- Given the stored data behind the signals, when it is enumerated, then it holds
  no per-account history and no per-device history (RET-010, CON-001).
- Given the share of ratings from accounts that have published nothing, when it
  is computed, then it is computed from what the product already holds and not
  from a record kept for the purpose.
- Given any proposed signal that would need such a record, when it is considered,
  then it is refused rather than implemented.

**Verification:** data model review of every input the signals use.
**Source:** CON-001 and RET-010. A measurement is not a reason to start holding
what the product refused to hold everywhere else.
