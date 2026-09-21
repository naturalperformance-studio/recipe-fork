---
slug: MOD
status: closed
updated: 2026-09-21
ids: [MOD-001, MOD-002, MOD-003, MOD-004, MOD-005, MOD-006, MOD-007,
  MOD-008, MOD-009, MOD-010, MOD-011, MOD-012, MOD-013]
---

# Moderation

Moderation decides whether a recipe may become visible to users other than its
owner. It is the only gate on that transition, and after it the content can
never change (CON-003), so a recipe is reviewed once and never re-reviewed for
the same content.

What the moderator may and may not do is in the permission matrix
([`actors`](40-actors.md)); the states involved are in
[`entity-states`](60-entity-states.md).

### MOD-001 — Moderation is triggered by first external visibility

When the owner of a recipe submits it for publication or for a private link, the
system shall place that recipe in the moderation queue.

**Acceptance criteria**
- Given a private recipe, when its owner requests a private link, then it enters
  the queue and no link exists until a moderator approves it.
- Given a private recipe, when its owner submits it for publication, then it
  enters the queue and it does not appear in the catalogue until approved.
- Given an approved recipe, when its owner publishes it from `link_shared` or
  issues a further private link, then it does not enter the queue again.

**Verification:** functional test over both triggers and both follow-on actions.
**Source:** draft section 6.1; a private link is a distribution channel and is
checked on the same footing as publication.

### MOD-002 — The text filter routes, it never rejects

When a recipe enters the moderation queue, the system shall run the automatic
text filter over its title, description, steps and tags, and shall use the
result only to mark the queue entry for the moderator's attention.

**Acceptance criteria**
- Given a submission the filter flags, when it is processed, then it is present
  in the moderator's queue, marked, and no decision has been recorded.
- Given a submission the filter flags, when the moderator approves it, then it
  becomes visible exactly as an unflagged submission would.
- Given a submission the filter does not flag, when it is processed, then it is
  still present in the queue for human review.

**Verification:** submit a recipe containing a flagged term and one without, and
compare the queue entries and the outcomes.
**Source:** user decision, 2026-09-20, resolving OQ-008. A filter that rejected
on its own would silently lose recipes over ordinary culinary vocabulary, and
the author would have no channel to appeal through.

### MOD-003 — Media is reviewed by a human only

The system shall not classify images or video automatically, and shall present
every media item of a submitted recipe to the moderator for review.

**Acceptance criteria**
- Given a submission with media, when the moderator opens the queue entry, then
  every image and every video attached to the recipe is shown.
- Given the product's running costs, when they are reviewed, then no automated
  media classification service appears in them (CON-002).

**Verification:** queue entry review; cost review.
**Source:** draft section 6.2; NGOAL-005.

### MOD-004 — A decision is approve or reject, and a rejection carries a reason

When a moderator decides a submission, the system shall record either an
approval or a rejection, and shall require a reason for a rejection.

**Acceptance criteria**
- Given a queue entry, when the moderator rejects it, then a reason must be
  entered before the rejection is recorded.
- Given a submission whose text is misspelled, clumsy or simply bad cooking, when
  the moderator reviews it and it breaks no rule, then poor quality is not a ground
  for rejection (NGOAL-007).
- Given a rejected submission, when its owner opens the recipe, then the reason
  and the date of the decision are shown.
- Given a recipe with several past submissions, when its owner opens it, then
  each decision is shown with its own date.

**Verification:** manual moderation of a submission through both outcomes.
**Source:** draft section 6.4; ADR — immutability trigger (a decision is
recorded against a submission, not against the recipe, because the recipe may be
edited after a rejection).

### MOD-005 — The expected wait is stated before submission

When the owner of a recipe opens the submission action, the system shall state
the expected waiting time for review before the submission is confirmed.

**Acceptance criteria**
- Given the submission action, when it is opened, then the stated expectation is
  visible without scrolling and before the confirming control.
- Given a submitted recipe, when its owner opens it, then it shows that it is
  awaiting review.

**Verification:** manual UI check.
**Source:** draft section 6.3 ("publication may take up to a week"). The figure
itself is a service commitment with a measurement method, specified in section
`quality`.

### MOD-006 — Queue entries are ordered by submission time

The system shall present the moderation queue in the order submissions were
received, oldest first.

**Acceptance criteria**
- Given several pending submissions, when the moderator opens the queue, then
  they appear oldest first.
- Given a recipe hidden by reports, when it enters the queue, then it takes its
  place by the time it was hidden.

**Verification:** queue inspection with several submissions.
**Source:** draft section 6.3 (a single moderator, no staffing); ordering by age
is what makes the stated waiting time in MOD-005 meaningful.

### MOD-007 — Approval is final for that content

If a moderator attempts to re-decide a submission that has already been decided,
then the system shall reject the attempt.

**Acceptance criteria**
- Given an approved submission, when the moderator opens it, then no approve or
  reject control is offered.
- Given a published recipe a moderator wishes to act on, when they act, then the
  available action is removal (ST-002), not a reversal of the approval.

**Verification:** interface review of a decided queue entry.
**Source:** CON-003 and draft section 4.2: the content cannot have changed since
the decision, so there is nothing to re-decide; acting on published content is
removal, which is a different decision with a different record.

### MOD-011 — A decision on a submission the owner withdrew is refused and says so

If a moderator records a decision on a submission its owner has already
withdrawn, then the system shall refuse the decision, shall take the entry out of
the queue, and shall state that the owner withdrew it.

**Acceptance criteria**
- Given a withdrawn submission, when a decision on it is recorded, then nothing
  is stored — no approval, no rejection and no rejection reason — and the recipe
  stays where the withdrawal put it, editable by its owner.
- Given that refusal, when the moderator reads it, then it says the owner
  withdrew the submission, so that the moderator does not retry.
- Given the entry, when the queue is next shown, then it is absent and does not
  return; if the owner submits again it arrives as a new entry, ordered by its
  own submission time (MOD-006).
- Given a run in progress (FLOW-050), when a decision is refused for this reason,
  then the run continues to the next entry exactly as it does after a decision
  that was recorded.
- Given the moderator had read the recipe before it was withdrawn, when the
  refusal is shown, then nothing they read is retained as a decision or as a
  reason, because a withdrawn submission was never decided.

**Verification:** withdraw a submission from the owner's device, then decide it
from the moderator's, in the list and inside a run.
**Source:** user question, 2026-09-21; the ordering rule is ST-008. This is the
same shape as MOD-007 — an attempt to decide something that is no longer open —
and it is separate from it because the cause is different and so is what the
moderator must be told.

### MOD-012 — A submission whose owner deleted their account leaves the queue

When an account is deleted, the system shall take every submission it owned out
of the moderator's queue and shall make no decision on them possible.

**Acceptance criteria**
- Given an account with a submission awaiting a decision, when the account is
  deleted, then the recipe is destroyed with it (RET-001) and its queue entry is
  gone at the same moment.
- Given a moderator holding such an entry open from before the deletion, when
  they record a decision, then it is refused as MOD-011 refuses a withdrawn one,
  and the refusal states that the submission no longer exists.
- Given a run in progress (FLOW-050), when it reaches such an entry, then the
  entry is not offered and the run continues.
- Given the deletion, when it is performed, then it is not delayed, queued behind
  or blocked by a submission awaiting a decision: RET-004 makes deletion
  immediate and this is no exception to it.
- Given the queue afterwards, when its count is read, then the entry is not
  counted in the signals of OBS-001.

**Verification:** submit a recipe, delete the owning account, then attempt a
decision on the entry from the list and inside a run.
**Source:** user decision, 2026-09-21. RET-001 already destroyed the recipe; what
was missing was the queue entry that pointed at it, which would otherwise have
been an item a moderator could open and could not decide.

### MOD-013 — A moderator may decide a submission they made themselves

Where an account holds the moderator role, the system shall allow it to decide a
submission it owns, and shall route that submission through the queue exactly as
it routes any other.

**Acceptance criteria**
- Given a moderator submitting their own recipe, when it is submitted, then it
  enters the queue as an ordinary entry, ordered by its submission time
  (MOD-006), and the text filter runs on it (MOD-002).
- Given that entry, when its owner decides it as moderator, then the decision is
  recorded like any other and MOD-007 governs it afterwards.
- Given a rejection of one's own submission, when it is recorded, then the recipe
  becomes editable again exactly as ST-003 requires of any rejection.
- Given the moderator role granted to a second account later, when this
  requirement is read, then it still permits deciding one's own submission, and
  forbidding it is a separate decision to be taken when there is somebody to hand
  the work to (ACT-003).

**Verification:** submit and approve one recipe from a single account holding the
moderator role; reject another and confirm it returns to an editable state.
**Source:** user decision, 2026-09-21. At the first release the product owner is
the only author and the only moderator, so a prohibition would mean nothing could
be published at all. The alternative — a second account on a second device,
because AUTH-011 allows one active account per device — was rejected as pure cost
at a launch with one person.

## When a removal reaches the forks

A recipe removed for using someone else's content takes nothing with it: what a
fork inherits is the method and the ingredients, which are not the protected
work (see `forking`). Danger to health is the opposite case — an unsafe
preserving time or the wrong mushroom lives in the very text every fork carries
down. The three requirements below exist for that one category.

### MOD-008 — Removing a recipe as dangerous flags every descendant

When a moderator removes a recipe in the category danger to health, the system
shall set the safety flag on every descendant of that recipe, to any depth.

**Acceptance criteria**
- Given a removed recipe with a chain of forks three deep, when the removal is
  recorded, then all three carry the safety flag.
- Given a removal in the category use of someone else's content, when it is
  recorded, then no descendant is flagged and nothing about them changes.
- Given a flagged descendant that is `published`, when the flag is set, then it
  stays published: the flag alone never withdraws a recipe.
- Given a flagged recipe, when anyone forks it, then the fork carries the flag
  as well.
- Given a descendant in state `private`, when the flag is set, then it carries
  the flag and its owner sees it.

**Verification:** build a chain four deep from one recipe, remove the root in
each category in turn, and inspect every descendant.
**Source:** user decision, 2026-09-20. The danger is in the text, and the text is
what a fork inherits; a direct fork is no more suspect than a fork of a fork, so
the flag follows the whole chain.
**Note:** this is the one place where the product walks the fork tree downwards.
FORK-004 refuses to build a tree for a card; a moderator's removal is not a card.

### MOD-009 — Flagged recipes are reviewed from the youngest upwards

The system shall present flagged recipes to the moderator as a list separate
from the submission queue, ordered from the deepest descendant upwards.

**Acceptance criteria**
- Given a flagged chain, when the moderator opens the list, then the deepest
  descendant appears first, so the chain can be walked towards its origin until
  the danger stops being present.
- Given the submission queue, when it is ordered, then MOD-006 governs it and
  this list changes nothing about it.
- Given a flagged recipe in state `private`, when the list is built, then it is
  absent, because a moderator cannot see a private recipe; its flag is weighed
  when the recipe is submitted for moderation (MOD-001).
- Given a flagged recipe the moderator opens, when they decide it, then the
  actions available are clearing the flag and removing the recipe.
- Given a flagged recipe that has never been published, when its owner submits
  it, then the moderator sees the flag on the queue entry.

**Verification:** flag a chain of five, confirm the order, and confirm a private
member is absent until it is submitted.
**Source:** user decision, 2026-09-20. Walking upwards from the youngest is how
the moderator finds the point at which the unsafe instruction stopped being
copied forward.
**Note:** a single removal can add an unbounded number of entries to this list,
which is the cost of not withdrawing the descendants automatically. They stay
readable while they wait.

### MOD-010 — Clearing a flag clears one recipe and no other

When a moderator clears the safety flag of a recipe, the system shall clear it
on that recipe alone and shall leave every other flagged recipe as it is.

**Acceptance criteria**
- Given a flagged chain of three, when the moderator clears the middle one, then
  the deepest stays flagged and stays in the list.
- Given a flagged recipe the moderator finds dangerous, when they remove it,
  then it is removed by ST-002 and its own descendants are flagged afresh by
  MOD-008.
- Given a recipe whose flag was cleared, when anyone forks it, then the fork
  carries no flag.
- Given a recipe whose flag was cleared, when a further ancestor is later
  removed as dangerous, then it is flagged again.
- Given a cleared recipe, when its card is opened, then nothing states that it
  ever carried a flag.

**Verification:** clear the middle of a flagged chain and inspect the whole
chain; then remove a flagged member and inspect its descendants.
**Source:** user decision, 2026-09-20. A fork that a moderator has read is safe
on its own evidence; its descendants have not been read, and a rewrite anywhere
down the chain can reintroduce what was cleared above it.
