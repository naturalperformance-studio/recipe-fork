# An unanswered sending keeps the mark the recipe had

**Status:** accepted — corrects FLOW-059 (9); adds FLOW-005 (8).

**Context:** a sending the server stored whose answer did not reach the device is
indistinguishable on the device from one the server never stored (FLOW-066). FLOW-059
(9) said the recipe "still" shows "holding changes not sent", which on a first
sending it never showed; read literally it put the mark "sent" on a recipe that may
never have left the phone (SD-043, 2026-09-29).

**Decision:** after a sending that was refused or got no answer, the recipe carries
the mark it carried before that sending — never sent after a first sending, holding
changes not sent after a later one. Sending again settles it (FLOW-066).

**Consequences:** the three marks of FLOW-005 stay three. The cost: a recipe the
server does hold can show "held on this device alone and lost with it", while
another device of the same account already shows it as sent. The error leans to
the safe side: it asks the owner to send again, and sending again changes nothing
the server holds.

**Alternatives considered:**
- *A fourth mark, "not confirmed whether it arrived; send again".* Rejected: more
  honest, but a fourth state in FLOW-005 and two more interface strings for a case
  that one more sending resolves.
- *The mark "sent, holding changes not sent" after any unanswered sending.* Rejected:
  where the sending did not arrive, it tells the owner a copy exists on the server
  when none does — the error in the dangerous direction.
