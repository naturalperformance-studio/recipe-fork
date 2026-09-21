---
slug: AUTH
status: closed
updated: 2026-09-21
ids: [AUTH-007, AUTH-008, AUTH-009, AUTH-010, AUTH-017, AUTH-019]
---

# The account key and the deletion key

The account is the key: whoever holds it holds the account. It is a string, and
the product hands it over in whichever form its holder prefers — copied, or
saved as a file. Beside it is a second, smaller secret that can only destroy
(AUTH-017).

### AUTH-007 — The key is issued once, in full, and creation waits for an acknowledgement

When an account is created, the system shall display its key in full, shall
offer both to copy it and to save it as a file, shall display a warning that
losing it loses the account with no recovery, and shall not complete creation
until the person confirms they have saved it.

**Acceptance criteria**
- Given the creation screen, when it is shown, then the key is displayed in full
  and can be copied in one action.
- Given the same screen, when the person prefers a file, then the key can be
  saved as one, and the product does not choose where.
- Given the warning, when it is read, then it states that losing the account key
  loses the account unless the recovery key is held (AUTH-021), and that nobody
  at all can restore an account whose keys are both gone.
- Given the confirmation control, when it is not chosen, then creation does not
  complete and the screen cannot be left by an ordinary back action.
- Given the warning, when any setting is searched for, then none switches it off
  (GUIDE-001 governs the same pattern for submission).
- Given a completed creation, when the screen is left, then the key is never
  shown in full again and AUTH-019 governs how it is reached.
- Given the same creation, when it completes, then the recovery key has been
  issued as its own artefact with its own confirmation (AUTH-020), and the
  warning above no longer says there is no recovery at all: it says what
  NGOAL-003 now says.

**Verification:** create an account, attempt to leave without confirming, then
take the key both ways and compare them.
**Source:** draft section 2.2; user decision, 2026-09-20. Both forms are offered
because the two ways people keep a secret are a password manager and a file, and
the product has no opinion about which is right.

### AUTH-019 — Afterwards the key is shown masked and copied whole

While an account is signed in, the system shall show its key masked in the
account's own screen, shall copy it in full on request, and shall show the
deletion key on the same terms.

**Acceptance criteria**
- Given a signed-in account, when its own screen is opened, then part of the key
  is shown and the rest is not.
- Given the copy control, when it is used, then what reaches the clipboard is the
  whole key and not the masked form.
- Given the deletion key, when the same screen is opened, then it is shown masked
  and copied whole in the same way (AUTH-017).
- Given a person with no account, when they look for these, then there is nothing
  to show.
- Given the recovery key, when this screen is opened, then it is absent: this
  requirement reaches the account key and the deletion key and no other
  (AUTH-020).

**Verification:** compare what is displayed against what is copied, for both
keys.
**Source:** user decision, 2026-09-20. A key that can never be seen again is lost
with the note it was written on, and the commonest moment to need it is moving to
a new phone while the old one is still in hand. Masking is what keeps the screen
safe to show to someone standing beside you; what the holder then does with the
copy is theirs.

### AUTH-008 — The product neither stores the key nor recovers an account

The system shall offer no storage of any of the account's three keys, no backup
of them, and no path that restores access to an account whose account key is
lost, whose recovery key is lost too, and which no device still holds signed in.

**Acceptance criteria**
- Given any screen, when it is enumerated, then none offers to keep, back up or
  send the key anywhere.
- Given a lost account key while the recovery key is held, when the person looks
  for a way back, then AUTH-021 is that way and it is reachable without an
  account.
- Given both keys lost and no signed-in device, when the person looks for
  recovery, then the product states there is none and names what NGOAL-003 costs
  them.
- Given a support channel of any kind, when it is enumerated, then it can restore
  no account, because the product holds nothing that could.

**Verification:** interface review against this list.
**Source:** draft section 2.2; NGOAL-003, as narrowed on 2026-09-21. AUTH-019
narrows what "lost" means from one side: an account key is recoverable from any
device that still holds the account signed in. AUTH-021 narrows it from the
other: an account key can be retired and replaced by whoever holds the recovery
key. What remains irrecoverable is an account whose holder has neither.

### AUTH-009 — Signing in with a key costs no slot

When a person signs in with an account key, whether pasted as text or taken from
a file, the system shall sign in to the account that key holds, and shall neither
consume nor require a free slot on that device.

**Acceptance criteria**
- Given a second device, when the key is entered there, then the same account is
  active on both and both hold its recipes (FLOW-038).
- Given a device with all three slots occupied, when a key is entered, then
  signing in succeeds.
- Given a key pasted as text and the same key taken from a file, when either is
  used, then the result is identical.
- Given a key that names no account or cannot be read, when it is offered, then
  signing in is refused, the reason is stated, and whatever was active stays
  active.
- Given the same key entered twice in succession, when the second attempt
  happens, then nothing changes and no data is wiped.

**Verification:** sign in on a fresh device, on a device with slots full, twice
in a row, and with a damaged key.
**Source:** derived from the slot decision of 2026-09-20 and from draft section
2.2: a slot limits how many accounts a device can mint, and signing in mints
none. Without this, a household sharing one tablet could not reach accounts they
already hold. Flagged to the product owner as the spec author's inference rather
than a stated decision.

### AUTH-010 — Signing in requires a connection

If the device has no network connection, then the system shall refuse to sign in
with a key and shall state that a connection is needed.

**Acceptance criteria**
- Given no connection, when a key is entered, then signing in is refused, the
  reason is stated, and whatever was active is unchanged.
- Given the connection returns, when the key is entered again, then it proceeds.

**Verification:** sign in in aeroplane mode, then with a connection.
**Source:** the server decides whether a key holds a real account (AUTH-014);
until that test can run, the client has accepted nothing. Consistent with
FLOW-003, which refuses every action whose answer only the server holds.

### AUTH-017 — Every account carries a deletion key

The system shall generate for every account a deletion key that is unique across
all accounts and not guessable, shall issue it beside the account key, shall show
it in the account's own screen for as long as the account is signed in, and shall
accept it as the sole proof of ownership at the deletion page (EXT-006).

**Acceptance criteria**
- Given account creation, when the key is issued, then the deletion key is issued
  on the same screen and is carried by the file that AUTH-007 offers.
- Given a signed-in account, when its own screen is opened, then the deletion key
  is reachable there, masked and copyable in full (AUTH-019).
- Given two accounts, when their deletion keys are compared, then they differ,
  and no key is ever issued twice.
- Given a deletion key, when its length and randomness are examined, then trying
  keys at the page's permitted rate (QUAL-007) cannot find a live one in any
  practical time.
- Given an account deleted by its deletion key, when that key is submitted again,
  then it matches nothing.
- Given an account key entered on another device, when the account opens there,
  then the deletion key is the same one.

**Verification:** read the key from the screen and from the file and confirm they
match; attempt deletion twice with one key; review the key's entropy against the
page's rate limit.
**Source:** user decision, 2026-09-20. This is what keeps the rule "the account
key is never uploaded anywhere" free of exceptions, and it bounds what a
counterfeit page can achieve: a stolen deletion key destroys an account but never
takes it over. See
[ADR — deletion from outside the application uses its own key](../decisions/20260920-deletion-key.md).
**Constrains AUTH-014:** whatever form the account key takes, the deletion key is
issued and carried alongside it as copyable text, and both are replaced together
(AUTH-020).
