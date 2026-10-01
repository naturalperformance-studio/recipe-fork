---
slug: FLOW
status: closed
updated: 2026-10-01
ids: [FLOW-001, FLOW-002, FLOW-059, FLOW-003, FLOW-004, FLOW-005, FLOW-006, FLOW-007]
---

# The device first, and sending to the server

What the device holds, what is written on it with no account and no connection,
how a recipe reaches the server — only when its owner sends it — and what is
refused outright. What happens when two devices diverge, and when a sending
reaches a server that already holds it, is in [15-divergence.md](15-divergence.md).

The product exists so that a person can cook from the recipe (GOAL-001), and
that happens in a kitchen, where the network is worst and the hands are busy. An
application that needs a connection to show a recipe fails at the moment it was
written for. The decisions below apply to every screen in this section and are
the reason most of them have a meaningful offline state at all.

The device therefore holds: the recipe book of every account signed in on it and
of the pool that belongs to no account (AUTH-011, AUTH-026), both fixed
dictionaries, and every recipe its holder wrote, in every state (FLOW-038). The
catalogue itself is not held, and neither is a recipe the reader merely opened:
what goes on the device is what the reader marked and what they wrote themselves.

Since 2026-09-28 the device comes first
([ADR — the device first](../decisions/20260928-the-device-first.md)): a recipe is
written, edited and forked on the device by anyone, with or without an account and
with or without a connection, and it reaches the server only when its owner sends
it. Nothing is sent by itself. This is also what keeps the product useful once the
server is switched off (FLOW-061).

### FLOW-001 — Both dictionaries are held on the device

The system shall hold the tag dictionary (DOM-008) and the ingredient dictionary
(DOM-009) on the device, and shall refresh them whenever a network connection is
available.

**Acceptance criteria**
- Given no network connection, when the author opens the tag editor, then the
  dictionary entries are offered exactly as they are with a connection.
- Given no network connection, when the author adds a `piece` ingredient whose
  dictionary entry is marked indivisible, then its divisibility is set from the
  entry and the author is not asked to set it by hand.
- Given no network connection, when the author adds a `piece` ingredient as free
  text, then the author sets its divisibility, because there is no entry to take
  it from.
- Given no network connection, when the author looks for an ingredient, then the
  dictionary is searched on the device and offers the same entries it offers with
  a connection.
- Given no network connection, when the reader filters their favourites by an
  ingredient, then the held dictionary supplies both what the filter matches
  (CAT-006) and the comparison factors the ordering needs (CAT-009).
- Given a connection returns after the product owner has changed a dictionary,
  when the application next reaches the server, then the held copy is replaced.
- Given a dictionary entry was removed upstream, when a held recipe already uses
  it, then the recipe keeps it and the editor stops offering it for new use.

**Verification:** open the editor in aeroplane mode on a device that has synced
at least once; change a dictionary upstream and confirm the refresh.
**Source:** user decision, 2026-09-20. Both dictionaries are labels and flags
only, with no media and no weights, and both are maintained by the product owner
alone, so holding them costs little and removes hand-set divisibility flags from
offline authoring. Holding the ingredient dictionary is also what keeps the
editor usable offline at all, now that naming an ingredient means searching it
(DOM-009). Holding only the entries an account has used was considered and set
aside on the arithmetic — see OQ-021.

### FLOW-002 — A recipe is written on the device, with no account and no connection

The system shall allow a person, whether or not an account is active and whether
or not the server can be reached, to create a recipe and to edit, fork and delete
any recipe of theirs that `entity-states` makes editable, on the device, and shall
keep every change on the device and send nothing of it until the owner sends that
recipe (FLOW-059).

**Acceptance criteria**
- Given a clean installation with no account and no network connection, when the
  person creates a recipe and fills its title, servings, ingredients and steps,
  then every change is kept and is present after the application is closed and
  reopened, and no account was created or asked for.
- Given no account, when the person forks a recipe held in their recipe book or
  open on its card, then the fork is created on the device, in the pool of
  AUTH-026, and opens in the editor.
- Given a recipe in `local`, `private` or `rejected`, when its owner edits it with
  or without a connection, then the edit is accepted.
- Given a recipe in `pending`, `link_shared`, `published`, `reported_hidden` or
  `removed`, when its owner opens it, then it is not editable, with or without a
  connection.
- Given changes made on the device, when a connection returns, then nothing is
  sent to the server without the owner sending the recipe.
- Given a recipe in `private` edited on the device, when the server's copy is
  inspected, then it is unchanged until the owner sends the recipe, and FLOW-005
  marks the recipe as holding changes not sent.

**Verification:** on a clean installation in aeroplane mode, write and fork
recipes with no account, restart the application, restore the connection and
confirm at the server that nothing arrived; edit a sent `private` recipe and
confirm the server's copy is unchanged until it is sent.
**Source:** user decision, 2026-09-20; changed by user decision, 2026-09-28: a
recipe no longer needs an account to be written, and changes are no longer sent
when a connection returns. See
[ADR — the device first](../decisions/20260928-the-device-first.md).

### FLOW-059 — A recipe reaches the server only when its owner sends it

When the owner chooses to send a recipe, the system shall send that recipe and its
media to the server — creating it there in `private` on its first sending, and
again whenever the server no longer holds it, and replacing the server's copy on a
later one — and shall send no other recipe with it.

**Acceptance criteria**
- Given a recipe written with no account active, when the person chooses to send
  it, then account creation is offered (AUTH-001), the recipe becomes that
  account's the moment the account exists, whether the offer of AUTH-026 that
  follows is accepted or declined (ACT-002), and it is then sent if the account
  can send; criterion 12 states what happens when it cannot.
- Given a muted account (AUTH-004), when a recipe is open in the editor, then the
  send control is replaced by the statement of AUTH-006.
- Given a recipe in `local`, when it is sent, then the server holds it in
  `private`, FLOW-005 marks it as sent, and it reaches every other device the
  account is signed in to when that device next reaches the server.
- Given a sent recipe edited on the device, when it is sent again, then the
  server's copy is replaced by the device's version, unless FLOW-008 finds the two
  diverged.
- Given a recipe that holds no change the server does not hold, when its entry or
  its editor is shown, then no send control is offered.
- Given a recipe in `pending`, `link_shared`, `published`, `reported_hidden` or
  `removed`, when it is shown, then no send control is offered, because the
  server already holds it and it cannot change.
- Given a recipe whose media would take the account past the allowance of
  MEDIA-010, when it is sent, then nothing of it reaches the server, MEDIA-011
  states why, and the recipe is on the device exactly as before.
- Given a sending that fails, or no connection, or a server that does not answer,
  when the recipe is sent, then FLOW-003 refuses it, nothing is queued, and the
  server holds either the whole sending or nothing of it — never its text without
  its media, nor part of its media.
- Given a sending the server stored whose answer did not reach the device, when
  the recipe is shown, then FLOW-005 marks it exactly as it marked it before that
  sending — never sent after a first sending, holding changes not sent after a
  later one — and sending it again is answered by FLOW-066.
- Given a fork whose parent the server does not hold, when the fork is sent, then
  it is sent alone and FLOW-007 governs how it names its parent.
- Given serverless mode (FLOW-061), when any screen is shown, then no send control
  is present (FLOW-062).
- Given a recipe from the pool whose sending created an account, when that account
  is muted (AUTH-004) or the sending that follows creation is refused (FLOW-003),
  then the recipe is in that account's recipe book, marked never sent, nothing of
  it reached the server, and the send control is offered on it again once the
  account can send — at once after a refusal, once the account is full when it is
  muted (AUTH-005 (5)).
- Given a recipe the device holds as sent, when the server no longer holds it —
  its store restored from a copy older than the recipe's sending (QUAL-008), or the
  recipe deleted on another device of the account before this device learned of
  it (ST-010 (2)) — and the owner sends it, then the server creates it again in
  `private` with the same identity, owned by the sending account, as on a first
  sending, and FLOW-005 marks it as sent.
- Given a recipe whose first sending the server stored and whose answer did not
  reach the device, so that the device holds no revision for it, when the owner
  sends it again, edited or not, then the server treats the sending as made from
  the recipe's first revision: it replaces the server's copy if that copy has not
  changed since (criterion 4), FLOW-008 governs if another device changed it, and
  FLOW-066 governs if the content is the same.

**Verification:** send a recipe written with no account; send a muted account's
recipe; edit and resend a sent recipe and inspect the server's copy; send with the
allowance nearly full, in aeroplane mode, and with the server refusing; send a
fork of an unsent recipe; send a pool recipe with attestation blocked, and again
with the server refusing after creation; delete a sent recipe on one device and
send it from another that has not yet learned of it; drop the answer to a first
sending, edit the recipe and send it, once with the server's copy untouched and
once after a second device changed it.
**Source:** user decision, 2026-09-28: sending is manual and per recipe, and it
gives a copy the server keeps, the recipe on the account's other devices, and the
possibility of submitting it. See
[ADR — the device first](../decisions/20260928-the-device-first.md).
**Correction, 2026-09-29:** criterion 8 said nothing of a failed sending reaches
the server, which no device and no server can promise when the server stores a
sending and its answer is lost (SD-041); by user decision of that date a sending
is stored whole or not at all, and a lost answer is settled by FLOW-066. Criterion
1 said the recipe becomes the new account's and AUTH-026 (3) with ACT-002 left it
in the pool when the offer is declined (SD-040); by user decision of that date it
becomes the account's either way. See
[ADR — the recipe that created the account is the account's](../decisions/20260929-the-recipe-that-created-the-account.md).
**Correction, 2026-10-01:** criterion 1 made the recipe the account's by being sent
and the account's offer by the account's creation, and a muted account or a refused
sending sends nothing (SD-042); by user decision of that date the creation decides,
and criterion 12 is added. Criterion 9 said "still marks it as holding changes not
sent" of a first sending, which never carried that mark (SD-043); by user decision
of that date the mark is the one the recipe had before the sending. The statement
and criterion 13 are added for a recipe the server no longer holds, which no
sending covered (SD-045); by user decision of that date it is created again. See
[ADR — the account's creation gives the owner](../decisions/20261001-the-accounts-creation-gives-the-owner.md),
[ADR — an unanswered sending keeps its mark](../decisions/20261001-an-unanswered-sending-keeps-its-mark.md) and
[ADR — a recipe the server lost is sent as new](../decisions/20261001-a-recipe-the-server-lost-is-sent-as-new.md).
Criterion 14 is added the same day by user decision: once a first sending whose
answer was lost leaves the recipe marked never sent, its next sending names no
revision for a recipe the server holds, which neither criterion 4 nor FLOW-008
answered. See
[ADR — a sending with no revision is made from the first](../decisions/20261001-a-sending-with-no-revision-is-made-from-the-first.md).

### FLOW-003 — What is refused while the server has not answered

If the device has no network connection, or if the product's own server cannot
be reached or does not answer, then the system shall refuse the catalogue,
search over the catalogue, opening a recipe the device does not hold, sending a
recipe (FLOW-059), submission for moderation, creating an account, signing in
with a key not yet used on this device (AUTH-010, AUTH-027), deleting a recipe the
server holds (ST-010), and every moderator action; shall state which of the two
happened; and shall queue nothing in either case.

**Acceptance criteria**
- Given no network connection, when the reader opens the catalogue, then it
  shows that a connection is needed and offers to retry, and does not show a
  stale catalogue.
- Given no network connection, when the reader opens a private link, then it
  shows that a connection is needed.
- Given no network connection, when the owner tries to send a recipe or to submit
  it for publication or for a private link, then the control states that a
  connection is needed and the recipe is unchanged on the device.
- Given no network connection, when a moderator opens the queue, then it shows
  that a connection is needed and no queue entry is shown from a held copy.
- Given no network connection, when a person signs in with a key not yet used on
  this device, then it states that a connection is needed and the active account
  is unchanged (AUTH-010); a key already used on this device opens its account
  as AUTH-027 states.
- Given no network connection, when a person uses a control that needs an account
  and has none, then account creation is offered and states that a connection is
  needed, and nothing is created — neither a full account nor a muted one
  (AUTH-001, AUTH-004).
- Given no network connection, when the owner deletes a recipe the server holds,
  then it is refused and the recipe is untouched on the device (ST-010,
  FLOW-053); a recipe in `local` is deleted on the device at once.
- Given a working connection and a server that does not answer or answers with a
  failure, when any act in the list above is attempted, then it is refused,
  nothing is queued, nothing changes on the device, and the statement says the
  product could not be reached and offers to try again — it does not say a
  connection is needed, because there is one.
- Given the two statements, when they are read, then they tell the person two
  different things to do: restore a connection, or try again later. That is why
  there are two and not one.
- Given either refusal, when the server is inspected afterwards, then no part of
  the act happened there either, because the server orders every transition and
  records none it did not complete (ST-009).
- Given a listing (FLOW-043) or a rating (RATE-014), when the server fails, then
  those two requirements already say this for their own surfaces and this one
  adds nothing to them.
- Given serverless mode (FLOW-061), when any screen is shown, then none of these
  refusals is shown, because the controls they refuse are not there (FLOW-062).

**Verification:** walk each refused path in aeroplane mode, then with the
connection up and the server refusing every request, and compare the two
statements; confirm nothing was queued and nothing reached the server.
**Source:** user decision, 2026-09-20; account creation and deletion added
2026-09-21, and the unanswering server the same day — every criterion here began
with "no network connection", so the commonest failure of a one-server product
(QUAL-006 allows it about 36 hours a month) was specified nowhere but in
FLOW-043 and RATE-014. See
[ADR — a connection is not an answer](../decisions/20260921-a-connection-is-not-an-answer.md). The catalogue is a live surface — a held copy would show recipes that
moderation has since removed or hidden. An account cannot be minted on the device
alone: its nickname is unique across all accounts (DOM-001) and its three keys
are tested by the server and by nothing else (AUTH-014), so neither can be issued
where the server cannot be reached.
**Changed 2026-09-28:** sending joined the list; deletion is refused only for a
recipe the server holds, and signing in only with a key not yet used on this
device, by user decision of that date
([ADR — the device first](../decisions/20260928-the-device-first.md),
[ADR — several accounts on one device](../decisions/20260928-several-accounts-on-one-device.md)).

### FLOW-004 — Media stays on the device until its recipe is sent

When the owner adds media to a recipe, the system shall keep the media on the
device, and shall upload it only as part of sending that recipe (FLOW-059).

**Acceptance criteria**
- Given a recipe, when the owner adds a photo to it with or without a connection,
  then it is displayed in the recipe at once and nothing is uploaded.
- Given a sent recipe, when media is added to it, then FLOW-005 marks the recipe
  as holding changes not sent.
- Given media added to a recipe, when a connection returns, then nothing is
  uploaded until the owner sends the recipe.
- Given a sending that fails, when it is refused, then the media stays on the
  device and the recipe keeps the mark it had.
- Given media the owner removes before the recipe is sent, when the recipe is
  sent, then nothing is uploaded for it.

**Verification:** add media with and without a connection, restore the connection
and confirm nothing is uploaded; send the recipe and confirm the upload; repeat
with the sending forced to fail.
**Source:** user decision, 2026-09-20; changed by user decision, 2026-09-28:
media travels with the recipe when it is sent and not before. See
[ADR — the device first](../decisions/20260928-the-device-first.md).

### FLOW-005 — Every recipe shows whether it was sent and what it holds unsent

The system shall display, for each recipe its holder wrote, on its entry in the
own-recipes view and in the editor, which of three it is: never sent and held on
this device alone; sent, with every change on the server; or sent, holding changes
not sent.

**Acceptance criteria**
- Given the own-recipes view, when it is displayed, then each entry carries one
  of the three marks.
- Given a recipe never sent, when its mark is shown, then it says the recipe is
  held on this device alone and is lost with it.
- Given a recipe open in the editor, when it holds changes not sent, then the
  editor states so and offers the send control of FLOW-059.
- Given a sent recipe whose text is on the server and to which media was added
  since, when it is displayed, then it counts as holding changes not sent.
- Given a recipe whose every change is on the server, when its entry or the
  editor is displayed, then it is marked as sent and not merely left unmarked.
- Given an own recipe, when it is displayed on its card or in the catalogue, then
  it carries no mark of either kind.
- Given serverless mode (FLOW-061), when the own-recipes view or the editor is
  shown, then none of the three marks is shown, because nothing can be sent.
- Given a sending that was refused or whose answer did not reach the device, when
  the recipe is shown afterwards, then it carries the mark it carried before that
  sending, whether or not the server stored it, because the device cannot tell the
  two apart (FLOW-059 (9)).

**Verification:** compare the marking against the server in four cases — never
sent, sent with nothing pending, text changed since, media added since — on the
list entry and in the editor, and confirm the card and the catalogue carry none;
drop the answer to a first sending and to a later one and compare the marks with
those shown before each.
**Correction, 2026-10-01:** criterion 8 added by user decision of that date
(SD-043): a first sending whose answer is lost leaves the recipe marked never
sent, even when the server holds it. See
[ADR — an unanswered sending keeps its mark](../decisions/20261001-an-unanswered-sending-keeps-its-mark.md).
**Correction, 2026-09-27:** criterion 4 named no surface (SD-017 item 2); by user
decision of that date the mark is on the own-recipes entry and in the editor
only.
**Source:** user decision, 2026-09-20; the three marks by user decision,
2026-09-28, when sending became the owner's act. The marking is per recipe rather
than one global indicator, so that the owner can see which recipe they would lose
by losing the phone.

### FLOW-006 — Nothing is submitted for moderation before the server holds it

If a recipe was never sent or holds changes not sent, then the system shall refuse
to submit it for publication or for a private link, and shall state that it has to
be sent first.

**Acceptance criteria**
- Given a recipe never sent, when the owner submits it for publication, then the
  submission is refused, the recipe stays as it is, and the statement points to
  the send control.
- Given a sent recipe with media added since, when the owner submits it, then the
  submission is refused in the same way.
- Given a recipe whose every change is on the server, when the owner submits it,
  then it moves to `pending` by `entity-states`.
- Given a refused submission, when the owner sends the recipe, then the
  submission control becomes available without the owner reopening the recipe.

**Verification:** submit a recipe never sent, one with media added since sending,
and one fully sent.
**Source:** user decision, 2026-09-20; the wording of sending by user decision,
2026-09-28. A moderator must review the content that will be published; content
the server does not yet hold is not that content.

### FLOW-007 — A fork may be sent before its parent, and names it once the parent is published

Where the owner sends a fork whose parent the server does not hold, the system
shall accept the fork, shall keep in it the identity of its parent unchanged, and
shall name that parent on the fork only from the moment the parent is published,
as FORK-004 and FORK-005 state.

**Acceptance criteria**
- Given a recipe in `local` and a fork of it, when the fork alone is sent, then
  the server holds the fork, does not hold the parent, and the fork's card names
  no parent (FORK-005).
- Given that fork, when its parent is later sent and published, then the fork
  names it from that moment, by FORK-004, without the fork being sent again.
- Given that fork, when its parent is deleted without ever being sent, then the
  fork never names a parent and nothing on the server shows that one existed.
- Given a fork of a published recipe, when it is sent, then it names its parent
  as any fork does.
- Given any fork, when it is sent, then the identity of its parent it holds is
  the one it was created with.

**Verification:** fork an unsent recipe, send the fork alone, inspect its card;
then send and publish the parent and inspect again; repeat with the parent deleted
unsent.
**Source:** user decision, 2026-09-28, replacing the rule of 2026-09-20 that a fork
waits for its parent: the fork travels alone, and the parent's identity it carries
restores the link once the parent is published. A recipe's identity is given when
it is created and kept when it is sent (`domain-model`), which is what makes this
possible. See
[ADR — the device first](../decisions/20260928-the-device-first.md).
