---
slug: FLOW
status: closed
updated: 2026-10-01
ids: [FLOW-008, FLOW-066]
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

### FLOW-066 — A sending the server already holds changes nothing

If the owner sends a recipe whose content — every field and every media item — is
the same as the content the server holds for it, then the system shall write
nothing to the recipe, shall create no fork, shall mark the recipe on the device as
sent with every change on the server (FLOW-005), and shall show on the device the
state the server holds it in — unless the same sending already made a fork under
FLOW-008, which criterion 4 governs and which is checked first.

**Acceptance criteria**
- Given a sending the server stored whose answer never reached the device, when the
  owner sends the recipe again, then no fork is created, no notice of FLOW-008 (3)
  is shown, and the recipe is marked as sent.
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
