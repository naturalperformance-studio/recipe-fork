# A sending with no revision is made from the first revision

**Status:** accepted — adds FLOW-059 (14); follows from 20261001-an-unanswered-sending-keeps-its-mark; generalised to every sending by 20261001-a-sending-is-made-from-what-the-unanswered-one-stored.

**Context:** a first sending the server stored whose answer was lost leaves the
recipe marked never sent, and the device holds no revision for it. When the owner,
having edited it or not, sends it again, the server receives a sending that names
no revision for a recipe it holds. FLOW-059 (4) replaces a copy the sending was
made from, FLOW-008 forks one that changed since, and neither has a revision to
compare (found while answering SD-043, 2026-10-01).

**Decision:** the server treats such a sending as made from the recipe's first
revision, which is the lost first sending itself: unchanged since, the copy is
replaced; changed since by another device, FLOW-008 forks; the same content,
FLOW-066 applies.

**Consequences:** a lost answer costs the owner nothing beyond sending again, and
an edit made in between is not mistaken for divergence. No cost could be named: a
recipe reaches another device only after the server holds it, so any later revision
did come from another device, and forking there is right.

**Alternatives considered:**
- *Always a fork.* Rejected: the owner gets a copy of their own recipe and a notice
  about an ordinary edit — what FLOW-066 was written to prevent.
- *Refuse.* Rejected: the recipe could never be sent again, and so never submitted.
