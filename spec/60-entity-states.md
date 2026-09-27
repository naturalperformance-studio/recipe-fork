---
slug: ST
status: closed
updated: 2026-09-27
ids: [ST-001, ST-002, ST-003, ST-004, ST-005, ST-006, ST-007, ST-008,
  ST-009, ST-010]
---

# Entity states

## Recipe

| State | Who can see it | Editable |
|---|---|---|
| `private` | owner | yes |
| `pending` | owner, moderator | no |
| `rejected` | owner, moderator | yes |
| `link_shared` | owner, moderator, anyone holding an active private link | no |
| `published` | everyone | no |
| `reported_hidden` | owner, moderator | no |
| `removed` | owner, moderator | no |

An imported recipe (not in the first release) has no owner and so none of the
owner's transitions; its own table is in
[`import`](68-import/30-provenance-cost-and-states.md).

`link_shared` is a state of the recipe; whether a particular private link opens
it is a property of that link (`active`), not of the recipe.

### Transitions

| From | Trigger | Guard | To | Side effects |
|---|---|---|---|---|
| — | owner creates a recipe | — | `private` | Empty recipe owned by the creator, no parent. |
| `published` | any user forks it | — | `private` (new recipe) | New recipe owned by the forking account, `parent` set to the forked recipe. The forked recipe is unchanged. |
| any state | owner forks their own recipe | actor is the owner | `private` (new recipe) | As above. |
| `private` | owner submits for a private link | DOM-006 met; owner not publishing-banned | `pending` | Submission created with trigger `private_link`; recipe frozen; text auto-filter runs. |
| `private` | owner submits for publication | DOM-006 met; owner not publishing-banned | `pending` | Submission created with trigger `publication`; recipe frozen; text auto-filter runs. |
| `pending` | owner withdraws | — | `private` | Submission closed as withdrawn; recipe editable again. |
| `pending` | moderator rejects | actor is a moderator | `rejected` | Reason recorded; owner told in the application (MOD-015). |
| `pending` | moderator approves, trigger `private_link` | actor is a moderator | `link_shared` | An active private link is issued (FLOW-054); content frozen permanently; owner told in the application (MOD-015). |
| `pending` | moderator approves, trigger `publication` | actor is a moderator | `published` | Recipe enters the catalogue and search; content frozen permanently; owner told in the application (MOD-015). |
| `rejected` | owner edits | — | `rejected` | Content changes; the recorded rejection stays attached to the closed submission. |
| `rejected` | owner resubmits | DOM-006 met; owner not publishing-banned | `pending` | New submission. |
| `private` | owner deletes it | actor is the owner | — (the recipe ceases to exist) | Recipe destroyed with every submission record attached to it; its media left to MEDIA-012 (ST-010). |
| `rejected` | owner deletes it | actor is the owner | — (the recipe ceases to exist) | As above; the recorded rejection goes with the submission it was attached to. |
| `link_shared` | owner publishes | owner not publishing-banned | `published` | Enters the catalogue immediately, with no new moderation: the content is frozen and is byte-for-byte what was approved. |
| `link_shared` | owner revokes the link | — | `link_shared` | The active link becomes inactive; the recipe stops opening for its holders and no link is active. |
| `link_shared` | owner issues a new link | — | `link_shared` | A new active link; the previous one becomes inactive in the same act; at most one link is ever active; no new moderation. |
| `link_shared` | third counted report is recorded | 3 distinct reporters, each with at least one published recipe (ABUSE-003) | `reported_hidden` | Hidden from link holders; moderator queue entry; owner told it is hidden, with no further detail. |
| `published` | third counted report is recorded | 3 distinct reporters, each with at least one published recipe (ABUSE-003) | `reported_hidden` | Hidden from the catalogue, search and direct access; moderator queue entry; owner told it is hidden, with no further detail. |
| `reported_hidden` | moderator dismisses the reports | actor is a moderator | previous state (`published` or `link_shared`) | Reports closed; visibility restored. |
| `reported_hidden` | moderator upholds the reports | actor is a moderator | `removed` | |
| `published`, `link_shared` | moderator removes it | actor is a moderator | `removed` | Recipe leaves the catalogue, search and every direct path; forks show their parent as unavailable. |

### Disallowed transitions

**Disallowed from `private`:** being seen, rated, favourited, reported or forked
by any account other than the owner; appearing in the catalogue or in search.

**Disallowed from `pending`:** any edit; withdrawal after a decision is recorded
(ST-008 settles which of the two came first and what the other one is told);
forking by anyone other than the owner; rating; favouriting; being reported by
anyone other than the moderator's own action; deletion by the owner, who
withdraws first and deletes from `private` (ST-010).

**Disallowed from `rejected`:** publication without a new submission; visibility
to anyone but the owner and a moderator; forking by another account; rating
(RATE-003).

**Disallowed from `link_shared`:** any edit; re-moderation on publication or on
issuing a further link; appearing in the catalogue or in search; forking by any
account other than the owner; rating, by a link holder or by anyone else
(RATE-003); deletion by the owner (ST-010).

**Disallowed from `published`:** any edit; unpublishing, hiding or deleting by
the owner or by any user; return to an editable state; transfer of ownership;
re-moderation when restored from `reported_hidden`.

**Disallowed from `reported_hidden`:** any edit; restoration by the owner;
un-hiding by the withdrawal of reports; forking by another account; rating,
although the ratings it already carries survive (RATE-003, RATE-015); deletion
by the owner (ST-010).

**Disallowed from `removed`:** any edit; restoration by the owner; forking by
any account other than the owner (ST-004); rating (RATE-003); deletion by the
owner (ST-010, RET-009); every transition out of the state — `removed` is
terminal.

### ST-001 — Publication is permanent

If the owner of a published recipe attempts to hide, unpublish or delete it,
then the system shall reject the attempt.

**Acceptance criteria**
- Given a published recipe, when its owner opens it, then the interface offers
  no action that removes it from the catalogue.
- Given a published recipe, when a removal is attempted directly against the
  service by its owner, then it is rejected and the recipe stays published.

**Verification:** interface review; service-level attempt as the owner.
**Source:** user decision, 2026-09-20. See
[ADR — publication is permanent](decisions/20260920-publication-is-permanent.md).

### ST-002 — Only a moderator removes published content

The system shall make removal of a `published` or `link_shared` recipe available
to a moderator and to no one else.

**Acceptance criteria**
- Given a published recipe, when a moderator removes it, then it leaves the
  catalogue, search and direct access, and its forks show their parent as
  unavailable.
- Given the same recipe, when any non-moderator account attempts the same
  action, then it is rejected.

**Verification:** functional test as moderator and as a non-moderator.
**Source:** user decision, 2026-09-20.

### ST-003 — Freezing happens at external visibility, not at submission

While a recipe is `pending`, the system shall reject every edit to it; and when
a submission is withdrawn or rejected, the system shall make the recipe editable
again.

**Acceptance criteria**
- Given a pending recipe, when its owner attempts an edit, then the edit is
  rejected.
- Given a pending recipe, when its owner withdraws the submission, then the
  recipe is editable and no user other than the owner can reach it.
- Given a rejected recipe, when its owner edits and resubmits it, then a new
  submission is created and no fork was required.

**Verification:** functional test across submit, withdraw, reject and resubmit.
**Source:** user decision, 2026-09-20. See
[ADR — immutability trigger](decisions/20260920-immutability-trigger.md).

### ST-004 — Forking is allowed from published recipes and from one's own

The system shall allow any account to fork a `published` recipe, shall allow an
owner to fork their own recipe in any state, and shall reject every other fork.

**Acceptance criteria**
- Given a published recipe, when any signed-in account forks it, then a new
  private recipe is created with its parent set to the forked recipe.
- Given a `link_shared` recipe, when an account holding the link forks it, then
  the fork is rejected.
- Given a `link_shared` or `private` recipe, when its owner forks it, then a new
  private recipe is created.
- Given a `removed` recipe, when its owner forks it, then a new private recipe
  is created; when any other account forks it, then the fork is rejected.
- Given such a fork of a recipe a moderator removed, when its media is resolved,
  then it carries only the media that still exists, because MEDIA-013 deletes
  what nothing else holds and FORK-007 can carry no file that is gone.

**Verification:** functional test over each state and both actor kinds,
including a fork of an own removed recipe whose media has already been deleted.
**Source:** user decision, 2026-09-20; the `removed` case decided 2026-09-21,
replacing the criterion that had refused an owner's fork against the requirement
it belonged to. See
[ADR — forking from published only](decisions/20260920-forking-published-only.md)
and [ADR — a removed recipe is still its author's work](decisions/20260921-a-removed-recipe-is-still-its-authors-work.md).

### ST-005 — A revoked link stops opening the recipe

When the owner revokes the private link of a recipe, the system shall stop
serving that recipe to every holder of a previously issued link.

**Acceptance criteria**
- Given a link holder who could open the recipe, when the owner revokes the
  link, then opening it fails for that holder.
- Given the same recipe, when the owner issues a new link, then the new link
  opens it and every link issued before it does not.
- Given a recipe in `link_shared`, when its links are counted, then at most one
  is active: issuing a new link retires the current one in the same act, so the
  owner never holds two strings that both work.
- Given the revocation, when the recipe is inspected, then its content is
  unchanged and it is still not editable.

**Verification:** two-account functional test across revoke and re-issue;
confirm the previous link stops opening the recipe the moment a new one exists.
**Source:** user decision, 2026-09-20; the one-active-link rule decided
2026-09-21. Several live links were weighed and buy nothing: the product records
nothing about who holds which link, so it cannot tell two recipients apart, and
revocation was always collective — the author would be keeping several strings
they cannot distinguish and cannot act on separately. One link is one thing to
copy, send and retire.
**Note:** where the owner finds, copies, revokes and reissues it is FLOW-054.

### ST-006 — Three counted reports hide a recipe

When three distinct accounts that each have at least one published recipe have
reported the same recipe, the system shall hide it from every account except its
owner and moderators, and shall place it in the moderation queue.

**Acceptance criteria**
- Given a published recipe with two counted reports, when the catalogue is
  searched, then it is still returned.
- Given a third counted report, when the catalogue is searched, then it is not
  returned, and it cannot be opened directly.
- Given three reports from accounts with no published recipe, when the threshold
  is evaluated, then the recipe is not hidden (ABUSE-003).
- Given the same account reporting the same recipe repeatedly, when the reports
  are counted, then they count once.
- Given the recipe is hidden, when its owner opens it, then the owner is told it
  is hidden and awaiting review, and is told nothing about who reported it, how
  many did, or under which category.

**Verification:** functional test with three accounts; repeat-report test.
**Source:** user decision, 2026-09-20. See
[ADR — report and block model](decisions/20260920-report-and-block-model.md).

### ST-007 — An unavailable parent is shown without a reason

Where a recipe's parent has been `published` and is now `removed` or
`reported_hidden`, the system shall show the parent as unavailable, and shall
state why only where the parent was removed as dangerous to health.

**Acceptance criteria**
- Given a fork whose published parent was removed, when its card is opened, then
  the parent is named as unavailable, cannot be opened, and no reason is given.
- Given a fork whose published parent is hidden by reports, when its card is
  opened, then it is shown as unavailable with no reason.
- Given a fork whose parent was removed as dangerous to health, when its card is
  opened, then the parent is shown as unavailable and that reason is stated.
- Given a fork whose parent was removed for using someone else's content, when
  its card is opened, then the parent is shown as unavailable with no reason,
  and this case is indistinguishable from a parent hidden by reports.
- Given a fork whose parent was never published, when its card is opened, then
  no parent is shown at all and this requirement does not apply (FORK-005).
- Given that fork, when the chain is walked upwards, then it stops at the
  unavailable parent.

**Verification:** manual check on a fork whose parent was removed.
**Source:** user decision, 2026-09-20, resolving OQ-005; amended the same day to
name the reason for one category only, see
[ADR — danger to health travels down the fork chain](decisions/20260920-danger-travels-down-the-chain.md).

### ST-010 — An owner deletes a recipe that was never externally visible

Where a recipe is in `private` or `rejected`, the system shall allow its owner to
delete it, and shall destroy the recipe together with every submission record
attached to it.

**Acceptance criteria**
- Given a recipe in `private` or in `rejected`, when its owner deletes it, then
  it is gone from the server and from every device the account holds, and no
  screen reaches it.
- Given a recipe in `pending`, `link_shared`, `published`, `reported_hidden` or
  `removed`, when its owner attempts to delete it, then the attempt is refused;
  a `pending` recipe is withdrawn first and deleted from `private`.
- Given those four other states, when everything that can still destroy such a
  recipe is enumerated, then it is the deletion of the owner's account and
  nothing else: RET-001 destroys every recipe the account owns that was never
  published, which reaches `link_shared`, and reaches `reported_hidden` and
  `removed` where the recipe passed through `link_shared` and never through
  `published`. A recipe that was once published is destroyed by nothing at all,
  and survives its author's account (ST-001, RET-002, RET-009).
- Given a deleted recipe's media, when the next deletion run happens, then
  MEDIA-012 deletes each file nothing else holds, and the account's unpublished
  allowance is freed by exactly that much (MEDIA-010).
- Given a deleted `rejected` recipe, when the moderation record of its closed
  submissions is looked for, then it is gone with the recipe, because a decision
  is recorded against a submission and the submission had nothing else to point
  at (MOD-004).
- Given a published fork of the deleted recipe, when it is opened by anyone,
  then nothing about it changes, because FORK-005 already shows no parent for a
  parent that was never published.
- Given a deleted recipe, when the moderator's queue and the operational signals
  are read, then it is counted in neither (MOD-012 does the same for a deleted
  account's submissions, OBS-001).
- Given no network connection, when deletion is attempted, then FLOW-003 refuses
  it and nothing is deleted locally either, because the server orders every
  transition (ST-009).

**Verification:** delete a recipe in each of the two states, with media, with a
closed rejection and with a published own fork naming it; attempt deletion in
each of the other five states and in aeroplane mode.
**Source:** user decision, 2026-09-21. MEDIA-010 and MEDIA-012 already assumed
this act existed — the allowance is freed by publishing or by deleting — and
without it an author who fills 150 MB with drafts has no way out but publication.
It stops at external visibility for the same reason everything else does: a
recipe another person can see is one other recipes may name (ST-001, CON-003).
**Note:** what the owner is told before the deletion is FLOW-053.
**Corrected 2026-09-21:** the second criterion said that the four states past
`rejected` "can never be deleted by anyone". RET-001 says otherwise and gives its
reason: deleting an account destroys every recipe it owns that was never
published, which includes a `link_shared` recipe and one that reached `removed`
or `reported_hidden` without ever being published. What is permanent is
publication, not every state beyond the owner's reach.

## Account

| State | Meaning |
|---|---|
| `active` | Normal. |
| `publishing_banned` | May not submit anything for moderation (ACT-005, ACT-006). |

| From | Trigger | Guard | To | Side effects |
|---|---|---|---|---|
| `active` | moderator bans publishing | actor is a moderator | `publishing_banned` | Already published recipes stay published. |
| `publishing_banned` | moderator lifts the ban | actor is a moderator | `active` | |

**Disallowed from `publishing_banned`:** submitting a recipe for publication or
for a private link; loss of access to one's own private recipes, favourites,
ratings or scaling.

Account deletion is not a state of the account: it destroys it outright and at
once, and what goes with it is RET-001.

## Media

A media item has no independent lifecycle. Its visibility follows the recipe it
belongs to (DOM-005), and it is owned by the account that uploaded it from the
moment of upload. A stored file is deleted once no surviving recipe holds it
(MEDIA-012, MEDIA-013); nothing is deleted for being old (RET-006), and what
bounds unpublished media is the 150 MB allowance of MEDIA-010.

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

The system shall order every transition of a recipe at the server, shall refuse a
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
- Given no connection, when any transition is attempted, then FLOW-003 refuses it
  and queues nothing, because the ordering exists only at the server.

**Verification:** drive each pair above from two clients in both orders and
inspect the stored state and what each actor was told.
**Source:** user decision, 2026-09-21, generalising the rule ST-008 settled for
one pair. Written once rather than per pair so that a pair nobody has thought of
yet is already answered; ST-008 stays separate because what its loser is told is
specific enough to be worth spelling out.
