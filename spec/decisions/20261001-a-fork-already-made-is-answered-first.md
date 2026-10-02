# A fork already made is answered first

**Status:** accepted — completes 20260929-a-sending-the-server-already-holds; corrects FLOW-066 (statement, 2, 4) and FLOW-008 (7).

**Context:** a divergent sending made a fork F and its answer was lost. Before it is
sent again, another device makes the server's content the same as that sending.
FLOW-066's statement and (2) then answered the repeat as sent, with no notice;
FLOW-066 (4) answered it with F and a notice. The server has to check one of the
two first (SD-044, 2026-09-30).

**Decision:** the server first checks whether the same sending — the same content,
made from the same revision — already made a fork, and if so answers with that fork
and the owner is told of it once (FLOW-008 (3)); only otherwise does sameness of
content (FLOW-066) apply.

**Consequences:** a fork that exists is always announced to its owner. No cost
could be named: FLOW-066 (4) already required the server to remember which sending
made a fork.

**Alternatives considered:**
- *Sameness first.* Rejected: the recipe is marked sent and fork F stays on the
  server with nobody told of it, against FLOW-008's statement that the owner is told.
