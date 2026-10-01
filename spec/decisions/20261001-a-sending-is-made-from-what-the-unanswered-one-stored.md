# A sending is made from what the unanswered one stored

**Status:** accepted — adds FLOW-067 and FLOW-008 (8), narrows FLOW-066 (1) and FLOW-059 (9); generalises 20261001-a-sending-with-no-revision-is-made-from-the-first to every sending.

**Context:** after a later sending whose answer is lost, the device still holds the
revision it had before that sending (FLOW-005 (8)). When the owner edits the recipe
and sends it, FLOW-008 finds the server's version changed since — by the lost
sending itself — and the content different, and forks the owner's own recipe with a
notice; FLOW-066 (1) said a sending again after a lost answer creates no fork and
named no content condition. The two answered the edited case two ways. Found by
gate 1 of T-038 (`docs/product/verdicts/20261001-T-038-gate1.md`, item 7), and the
same outcome had already been rejected for a first sending in
20261001-a-sending-with-no-revision-is-made-from-the-first.

**Decision:** a sending made after one or more of the device's own sendings of the
recipe went unanswered is taken as made from what the latest of them that the
server stored left there — the revision it wrote, or the fork it created. FLOW-059
(4), FLOW-008 and FLOW-066 then answer it as any other sending. A first sending is
the case where what it left is the first revision (FLOW-059 (14)).

**Consequences:** a lost answer costs the owner nothing beyond sending again, with
or without an edit in between; a change made by another device is still a
divergence and still forks. The device keeps, per recipe, what it sent without an
answer until it next learns what the server holds. Where the unanswered sending
made a fork, the edit lands on that fork, so the owner holds one fork, not two.

**Alternatives considered:**
- *A fork, as FLOW-008 read.* Rejected: after any lost answer followed by an edit,
  the owner gets a copy of their own recipe and a notice about a divergence that
  never happened — what FLOW-066 and the first-revision ADR were written to prevent.
