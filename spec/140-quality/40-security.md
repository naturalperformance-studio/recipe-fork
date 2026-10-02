---
slug: QUAL
status: closed
updated: 2026-09-27
ids: [QUAL-007, QUAL-009]
---

# Security

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
