---
slug: MOD
status: closed
updated: 2026-09-20
ids: [MOD-001, MOD-002, MOD-003, MOD-004, MOD-005, MOD-006, MOD-007]
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
