---
slug: QUAL
status: closed
updated: 2026-09-27
ids: [QUAL-010]
---

# The one commitment made to a person

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
[ADR — a week, and ninety-five per cent of a month](../decisions/20260921-a-week-and-ninety-five-per-cent.md).
