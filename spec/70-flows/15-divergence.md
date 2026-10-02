---
slug: FLOW
status: closed
updated: 2026-10-01
ids: [FLOW-008, FLOW-066, FLOW-067]
---

# Divergence, and a sending the server already holds

What the server does with a sending that does not simply replace its copy: one
made from a version the server has since moved past, and one whose content the
server already holds — including the same sending arriving twice because its
answer was lost. Sending itself is in [10-offline-and-sync.md](10-offline-and-sync.md)
(FLOW-059). Split from that file on 2026-10-01 when it passed 400 lines; no
identifier changed.

### FLOW-008 — Two devices that diverge produce a fork, never an overwrite

If the owner sends a recipe whose version on the server has changed since the
version the device's copy was made from — in its content, or in its state alone —
and whose content differs from the content the server holds, then the system
shall keep the server's version as that recipe and turn the
divergent local version into a fork of it owned by the same account, and shall
tell the owner that it did so.

**Acceptance criteria**
- Given a device holding changes not sent to a recipe, when the server's copy
  of that recipe changes, then the device keeps its own version and does not take
  the server's over it; the difference is settled when the device sends.
- Given a recipe edited on device B while device A's edit was sent to the
  server, when device B sends it, then the server's version keeps the recipe's
  identity and device B's version becomes a `private` fork naming it as parent.
- Given that outcome, when the server's answer reaches device B, then the owner
  is told a fork was created and why at that moment if the application is open,
  or when they next open it if it is not; the notice stays until they
  acknowledge it, is not shown again afterwards, and both recipes are reachable.
- Given the server's version has since been published or otherwise frozen, when
  device B sends a divergent version, then the same rule applies and
  nothing is written to the frozen recipe.
- Given no divergence — device B's version was made from the one the server
  holds — when device B sends, then the server's copy is replaced and no fork is
  created.
- Given a recipe edited on device B while, on the server, nobody changed
  its content but it was submitted and became `pending`, `link_shared` or
  `published`, when device B sends it, then device B's version becomes a
  `private` fork naming it as parent, and the owner is told as criterion 3
  states.
- Given a sending whose content is the same as the content the server holds, when
  it arrives, then it is not a divergence whatever version it was made from, and
  FLOW-066 governs it — including a repeat of a sending that already made a fork,
  which FLOW-066 (4) answers with that fork.
- Given a server's version changed since the device's copy only by this device's
  own unanswered sending, when the device sends, then that change is not a
  divergence: FLOW-067 takes the sending as made from what that sending stored.

**Verification:** enter the same account key on a second device, edit one recipe on
both, send from one and then from the other, and inspect both recipes; repeat with
only a state change on the server.
**Correction, 2026-09-27:** the statement required an edit on each side while
criterion 4 treated any difference as divergence, and criterion 2 tied the notice
to opening the application while automatic sync does not wait for it (SD-017 item
1, SD-018). By user decisions of that date a change of state alone is divergence,
and the owner is told when the answer arrives, once, until acknowledged.
**Source:** user decision, 2026-09-20. The product's own answer to a changed
recipe is a fork (GOAL-002), and the server's version keeps its identity because
other recipes may already name it as their parent. No edit is discarded.
**Changed 2026-09-28:** sending is the owner's act (FLOW-059), so the divergence is
found when device B sends, not when it reconnects, and device B keeps its unsent
version rather than taking the server's over it. The notice of criterion 3 reaches
an owner who is sending, so it is shown at once. By user decision of that date.
**Correction, 2026-09-29:** criterion 6 and the note above said "criterion 2" for
the notice, which the 2026-09-28 renumbering had moved to criterion 3 (SD-039); by
user decision of that date they mean the whole notice of criterion 3. The same day
a sending the same as the server's stopped being a divergence (SD-041): the
statement forked "the divergent local version" and its condition also caught a
version that had not diverged. By user decision of that date it is governed by
FLOW-066. See
[ADR — a sending the server already holds changes nothing](../decisions/20260929-a-sending-the-server-already-holds.md).
**Correction, 2026-10-01:** criterion 7 also caught a repeated sending that had
already made a fork once the server's content became the same as it (SD-044); by
user decision of that date that sending is answered with its fork (FLOW-066 (4)).
The same day criterion 8 is added: an edit sent after the device's own unanswered
sending was a divergence by the statement, against FLOW-066 (1); by user decision
of that date FLOW-067 governs it.

### FLOW-066 — A sending the server already holds changes nothing

If the owner sends a recipe whose content — every field and every media item — is
the same as the content the server holds for it, then the system shall write
nothing to the recipe, shall create no fork, shall mark the recipe on the device as
sent with every change on the server (FLOW-005), and shall show on the device the
state the server holds it in — unless the same sending already made a fork under
FLOW-008, which criterion 4 governs and which is checked first.

**Acceptance criteria**
- Given a sending the server stored whose answer never reached the device, when the
  owner sends the recipe again unedited, then no fork is created, no notice of
  FLOW-008 (3) is shown, and the recipe is marked as sent; sent again edited, it is
  governed by FLOW-067.
- Given device A's edit sent to the server, when device B sends an edit that leaves
  the recipe exactly as device A left it and that has not already made a fork,
  then the server's copy is unchanged, no fork is created and device B marks the
  recipe as sent.
- Given a recipe that, on the server, was submitted and became `pending` since the
  device's copy was made, when the device sends content the same as the server's,
  then no fork is created and the device shows the recipe in `pending`.
- Given a divergent sending that made a fork (FLOW-008) whose answer never reached
  the device, when the same sending — the same content, made from the same
  revision — arrives again, then, whatever the server's content has become since,
  the server answers with the fork it already created and creates no second one,
  and the owner is told of that fork once, as FLOW-008 (3) states.
- Given a sending whose content differs from the server's in one character or one
  media item, when it arrives, then it is not governed here: FLOW-059 (4) or
  FLOW-008 applies.

**Verification:** send a recipe with the server's answer dropped and send it
again; make the same edit on two devices and send from both; drop the answer to a
divergent sending and send it again; inspect the server for writes and forks after
each.
**Source:** user decision, 2026-09-29 (SD-041). A server that stored a sending and
lost its answer cannot be told apart from one that did not store it, so the case
exists and the owner meets it by sending again; forking there would hand them a
copy of their own recipe and a notice about nothing. No edit is discarded, because
nothing differs. See
[ADR — a sending the server already holds changes nothing](../decisions/20260929-a-sending-the-server-already-holds.md).
**Cost, stated:** sameness is decided over the whole content, media included, so
the device must be able to tell that its version is the one the server holds.
**Correction, 2026-10-01:** a repeated sending that had made a fork, arriving after
the server's content became the same as it, was answered as sent with no notice by
the statement and criterion 2 and with the fork and a notice by criterion 4
(SD-044). By user decision of that date the fork it made is checked first, so the
owner always learns of a fork that exists. See
[ADR — a fork already made is answered first](../decisions/20261001-a-fork-already-made-is-answered-first.md).
The same day criterion 1, which named no content, also caught an edit sent after an
unanswered sending, which FLOW-008 forked; by user decision of that date it covers
an unedited sending, and FLOW-067 the edited one.

### FLOW-067 — A sending after an unanswered one is made from what that one stored

When the owner sends a recipe from a device that has sent it without getting an
answer since the device last learned what the server holds of it, the system shall
take the sending as made from what the latest of those unanswered sendings that the
server stored left there — the revision it wrote to the recipe, the fork it
created under FLOW-008, or, when FLOW-066 answered it by writing nothing, the
revision the server held at that moment — and shall answer the sending against that by FLOW-059 (4),
FLOW-008 or FLOW-066, as for any other sending.

**Acceptance criteria**
- Given a sent recipe edited and sent, the server having stored that sending and its
  answer not having reached the device, when the owner edits the recipe again and
  sends it while no other device has changed it, then the server's copy is replaced
  by the new version, no fork is created, no notice of FLOW-008 (3) is shown, and
  FLOW-005 marks the recipe as sent.
- Given the same unanswered sending, when another device of the account has sent a
  change to the recipe before this device sends its edit, then this device's
  version becomes a `private` fork as FLOW-008 states, and the owner is told as
  FLOW-008 (3) states.
- Given an unanswered sending whose content was the same as the server's, so that
  FLOW-066 wrote nothing, when the owner edits the recipe and sends it while no
  other device has changed it since, then the sending is made from the revision the
  server held when that sending arrived: the server's copy is replaced, no fork is
  created and no notice is shown — the device took the server's changes, and the
  edit lies on top of them.
- Given that revision held in `pending`, `link_shared` or `published`, when the
  owner's edit is sent, then nothing is written to it and the edit becomes a
  `private` fork as FLOW-008 (4) states, because that recipe cannot change.
- Given an unanswered sending the server did not store, when the owner sends the
  recipe again, then the sending is made from the revision the device held before
  that sending, as if it had never been made.
- Given two or more unanswered sendings in a row, of which the server stored any,
  when the owner sends the recipe, then the sending is made from what the latest
  stored one left, whichever earlier ones the server stored.
- Given an unanswered sending that made a fork under FLOW-008, when the owner edits
  the recipe and sends it, then the sending is made from that fork: the fork's copy
  is replaced if nothing changed it since, FLOW-008 forks the fork if another
  device changed it, no second fork of the original recipe is created, the device's
  recipe is that fork with the original reachable beside it, and the owner is told
  of the fork once, as FLOW-008 (3) states.
- Given an unanswered sending, when the owner sends the recipe again unedited, then
  FLOW-066 answers it as its criteria 1 and 4 state; this requirement only fixes
  what the sending is made from.
- Given an unanswered first sending, when the owner sends the recipe again, then
  what that sending stored is the recipe's first revision, and FLOW-059 (14) states
  the outcome.

**Verification:** drop the answer to a later sending, edit the recipe and send it —
once with the server's copy untouched, once after a second device sent a change;
drop two answers in a row with the server storing only the first, and send again;
drop the answer to a divergent sending, edit the recipe and send it; inspect the
server for writes and forks after each.
**Source:** user decision, 2026-10-01, on the question left by gate 1 of T-038
(`docs/product/verdicts/20261001-T-038-gate1.md`, item 7): a device whose later
sending went unanswered still held the revision before it, so FLOW-008 forked its
next edit against its own stored sending, while FLOW-066 (1) said no fork is
created. The device's edit was made on top of what it sent, so what it was made
from is what that sending stored. No edit is discarded. See
[ADR — a sending is made from what the unanswered one stored](../decisions/20261001-a-sending-is-made-from-what-the-unanswered-one-stored.md).
**Cost, stated:** the device keeps, for each recipe, what it sent without an answer
until it next learns what the server holds of that recipe.
**Correction, 2026-10-02:** the statement named a revision written and a fork made,
and not a sending FLOW-066 answered by writing nothing, so whether that sending
counted as stored was read two ways (SD-046). By user decision of that date it
does: device B takes the server's changes, and an edit after it lies on top of them
with nothing more for the owner to do. Criteria 3 and 4 are added.
