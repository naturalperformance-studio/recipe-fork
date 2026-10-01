# A sending the server already holds changes nothing

**Status:** accepted — narrows FLOW-008, corrects FLOW-059 (8), adds FLOW-066; the order of its checks completed by 20261001-a-fork-already-made-is-answered-first.

**Context:** FLOW-008 forked whenever the server's version had changed since the
version the device's copy was made from, and it also called what it forked "the
divergent local version". Two cases met the first wording and not the second: a
sending the server stored whose answer never reached the device, sent again by its
owner; and device B making exactly the edit device A had already sent. FLOW-059
(8) said nothing of a failed sending reaches the server, which neither the device
nor the server can promise when only the answer is lost. Gate 2 of T-027 could
not mark the server's behaviour right or wrong (SD-041, 2026-09-29).

**Decision:**
- A sending whose content — every field and every media item — is the same as the
  server's is not a divergence, whatever version it was made from. Nothing is
  written, no fork is made, no notice is shown, and the device marks the recipe
  as sent and shows the state the server holds it in (FLOW-066).
- A divergent sending that already made a fork, arriving again, is answered with
  that fork; no second fork is made, and the owner is told of it once.
- A sending is stored whole or not at all. If its answer is lost the recipe keeps
  its mark of holding changes not sent, and sending it again settles it by the
  rule above (FLOW-059 (8), (9)).

**Consequences:** an owner whose connection dropped at the wrong moment sends
again and sees their recipe marked as sent — no copy of their own recipe, no
notice about nothing. FLOW-008's "No edit is discarded" still holds, because a
version the same as the server's carries no edit to discard. The cost is that
sameness is decided over the whole content, media included, so the device has to
be able to tell that its version is the one the server holds.

**Alternatives considered:**
- *Every sending made from an older version forks, even when its content is the
  same.* Rejected: an owner whose answer was lost gets a `private` fork identical
  to its parent and a notice that a fork was created, for a change that never
  existed; each retry would add another.
