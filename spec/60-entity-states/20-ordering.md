---
slug: ST
status: closed
updated: 2026-09-28
ids: [ST-008, ST-009]
---

# How transitions are ordered

Which of two transitions wins, and who orders a transition at all: the server for a recipe it holds, the device alone for a recipe in `local`.

### ST-008 — A withdrawal and a decision are settled by whichever reached the server first

When the owner withdraws a submission and a moderator records a decision on the
same submission, the system shall accept whichever reached the server first,
shall refuse the other, and shall tell whoever was refused what has actually
become of the recipe.

**Acceptance criteria**
- Given one submission, when a withdrawal and a decision both reach the server,
  then exactly one of them is recorded and the other changes no state at all.
- Given the decision was recorded first, when the owner's withdrawal arrives,
  then it is refused and the owner is told what the recipe now is — published, or
  reachable by a private link, or rejected — and never told merely that the
  withdrawal failed.
- Given the withdrawal was recorded first, when the moderator's decision arrives,
  then it is refused and MOD-011 states why.
- Given the owner lost the race to an approval for publication, when they open
  the recipe, then it is published and permanent (ST-001), and correcting it is a
  fork like any other correction (GOAL-002).
- Given the owner has no connection, when they attempt to withdraw, then FLOW-003
  refuses the attempt and queues nothing, because whether the submission is still
  undecided is something only the server knows.
- Given a moderator holding the entry open from before the withdrawal, when the
  withdrawal is recorded, then nothing reaches their device to say so (EXT-009);
  they learn it when they record their decision and it is refused.

**Verification:** send a withdrawal and a decision against one submission from
two clients in each order and confirm exactly one takes effect and the loser is
told what happened; attempt a withdrawal in aeroplane mode.
**Source:** user question, 2026-09-21. The transition table already forbade
withdrawal after a decision is recorded, which fixes who wins; it never said what
the loser sees, and a refusal that says only "that did not work" leaves an author
guessing whether their recipe is public. The server is the single point that
orders the two events, which is why neither client may decide the outcome
locally.

### ST-009 — Every transition is ordered by the server

The system shall order every transition of a recipe the server holds at the
server, shall refuse a
transition whose guard no longer holds at the moment it is recorded, and shall
state to the actor what state the recipe is actually in, as far as that actor is
already entitled to see it.

**Acceptance criteria**
- Given the owner publishing a recipe from `link_shared` and a third counted
  report arriving together, when both reach the server, then whichever is
  recorded first takes effect; if the report did, the publication is refused and
  the owner is told the recipe is hidden and nothing further (ST-006).
- Given a moderator dismissing the reports on a hidden recipe and a further
  report arriving together, when both reach the server, then one is recorded and
  the other changes nothing.
- Given any refused transition, when its effect is inspected, then no part of it
  happened: there is no state half-changed and no side effect recorded.
- Given an actor not entitled to see the recipe's state, when their transition is
  refused, then they are told the action is no longer possible and are told no
  state.
- Given no connection, when any transition of a recipe the server holds is
  attempted, then FLOW-003 refuses it and queues nothing, because the ordering
  exists only at the server.
- Given a recipe in `local`, when it is created, edited, forked or deleted, then
  the device performs it alone, because the server holds nothing of it to order.

**Verification:** drive each pair above from two clients in both orders and
inspect the stored state and what each actor was told.
**Source:** user decision, 2026-09-21, generalising the rule ST-008 settled for
one pair. Written once rather than per pair so that a pair nobody has thought of
yet is already answered; ST-008 stays separate because what its loser is told is
specific enough to be worth spelling out.
