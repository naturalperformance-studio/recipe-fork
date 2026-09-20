---
slug: AUTH
status: blocked
updated: 2026-09-20
ids: [AUTH-007, AUTH-008, AUTH-009, AUTH-010, AUTH-017]
---

# The key file

The artefact that is the account: how it is issued, what the product refuses to
do with it, how it is imported, and the deletion key it carries.

### AUTH-007 — The key file is issued with a warning that must be acknowledged

When an account is created, the system shall offer its key file for download and
shall display an irreversibility warning that the user must explicitly
acknowledge before the download begins.

**Acceptance criteria**
- Given a newly created account, when the key file is offered, then the download
  control stays disabled until the acknowledgement control is chosen.
- Given the acknowledgement, when the user downloads the key file, then the
  warning text stays visible on the screen.
- Given the warning, when it is read, then it states that losing the file loses
  the account, that there is no recovery, and that nobody can restore it.
- Given a user who leaves without downloading, when they next open the
  application, then the key file is offered again on the same terms.
- Given the warning, when any setting is searched for, then none switches it off
  (GUIDE-001 governs the same pattern for submission).

**Verification:** manual UI check on a fresh account, including leaving the
screen and returning.
**Source:** draft section 2.2; user decision, 2026-09-20.

### AUTH-008 — The product neither stores the key file nor recovers an account

The system shall offer no storage of the key file, no backup of it and no path
that restores access to an account whose key file is lost.

**Acceptance criteria**
- Given any screen, when it is enumerated, then none offers to keep, back up or
  mail the key file.
- Given a lost key file, when the user looks for recovery, then the product
  states there is none and names what NGOAL-003 costs them.
- Given a support channel of any kind, when it is enumerated, then it can restore
  no account, because the product holds nothing that could.

**Verification:** interface review against this list.
**Source:** draft section 2.2; NGOAL-003.

### AUTH-009 — Importing a key file signs in and costs no slot

When the user imports a key file, the system shall sign in to the account that
file holds, and shall neither consume nor require a free slot on that device.

**Acceptance criteria**
- Given a second device, when the key file is imported, then the same account is
  active on both and both hold its recipes (FLOW-038).
- Given a device with all three slots occupied, when a key file is imported, then
  the import succeeds.
- Given a key file the product cannot read or that names no account, when it is
  imported, then the import is refused, the reason is stated, and the account
  that was active stays active.
- Given the same key file imported twice in succession, when the second import
  happens, then nothing changes and no data is wiped.

**Verification:** import on a fresh device, on a device with slots full, twice in
a row, and with a damaged file.
**Source:** derived from the slot decision of 2026-09-20 and from draft section
2.2: a slot limits how many accounts a device can mint, and an import mints
none. Without this, a family sharing one tablet could not sign in to accounts
they already hold. Flagged to the product owner as the spec author's inference
rather than a stated decision.

### AUTH-010 — Importing a key file requires a connection

If the device has no network connection, then the system shall refuse to import a
key file and shall state that a connection is needed.

**Acceptance criteria**
- Given no connection, when the user imports a key file, then the import is
  refused, the reason is stated, and the active account is unchanged.
- Given the connection returns, when the user imports again, then it proceeds.

**Verification:** import in aeroplane mode, then with a connection.
**Source:** the server decides whether a key file holds a real account, and that
test is OQ-002; until it can run, the client has accepted nothing. Consistent
with FLOW-003, which refuses every action whose answer only the server holds.
**Blocked by:** OQ-002.

### AUTH-017 — The key file carries a deletion key

The system shall generate for every account a deletion key that is unique across
all accounts and not guessable, shall place it in the account's key file, shall
show it inside the application, and shall accept it as the sole proof of
ownership at the deletion page (EXT-006).

**Acceptance criteria**
- Given a key file, when it is opened, then the deletion key is readable from it
  as text that can be copied.
- Given the application, when the account's own settings are opened, then the
  deletion key is shown there too, so that a user who cannot open the file can
  still reach it.
- Given two accounts, when their deletion keys are compared, then they differ,
  and no key is ever issued twice.
- Given a deletion key, when its length and randomness are examined, then trying
  keys at the page's permitted rate cannot find a live one in any practical time.
- Given an account deleted by its deletion key, when that key is submitted again,
  then it matches nothing.
- Given a key file, when it is imported on another device, then the deletion key
  travels with it and is the same key.

**Verification:** read the key out of a file and out of the application and
confirm they match; attempt deletion twice with one key; review the key's
entropy against the page's rate limit.
**Source:** user decision, 2026-09-20. This is what keeps the rule "the key file
is never uploaded anywhere" free of exceptions, and it bounds what a counterfeit
page can achieve: a stolen deletion key destroys an account but never takes it
over. See
[ADR — deletion from outside the application uses its own key](../decisions/20260920-deletion-key.md).
**Constrains OQ-002:** whatever format the key file takes, it carries this value
as copyable text.
