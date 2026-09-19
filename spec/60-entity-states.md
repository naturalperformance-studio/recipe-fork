---
slug: ST
status: closed
updated: 2026-09-20
ids: [ST-001, ST-002, ST-003, ST-004, ST-005, ST-006, ST-007]
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
| `pending` | moderator rejects | actor is a moderator | `rejected` | Reason recorded; owner notified in the application. |
| `pending` | moderator approves, trigger `private_link` | actor is a moderator | `link_shared` | An active private link is issued; content frozen permanently. |
| `pending` | moderator approves, trigger `publication` | actor is a moderator | `published` | Recipe enters the catalogue and search; content frozen permanently. |
| `rejected` | owner edits | — | `rejected` | Content changes; the recorded rejection stays attached to the closed submission. |
| `rejected` | owner resubmits | DOM-006 met; owner not publishing-banned | `pending` | New submission. |
| `link_shared` | owner publishes | owner not publishing-banned | `published` | Enters the catalogue immediately, with no new moderation: the content is frozen and is byte-for-byte what was approved. |
| `link_shared` | owner revokes the link | — | `link_shared` | Every issued link becomes inactive; the recipe stops opening for its holders. |
| `link_shared` | owner issues a new link | — | `link_shared` | A new active link; previously revoked links stay inactive; no new moderation. |
| `link_shared` | third distinct account reports it | 3 distinct reporters | `reported_hidden` | Hidden from link holders; moderator queue entry; owner told it is hidden, with no further detail. |
| `published` | third distinct account reports it | 3 distinct reporters | `reported_hidden` | Hidden from the catalogue, search and direct access; moderator queue entry; owner told it is hidden, with no further detail. |
| `reported_hidden` | moderator dismisses the reports | actor is a moderator | previous state (`published` or `link_shared`) | Reports closed; visibility restored. |
| `reported_hidden` | moderator upholds the reports | actor is a moderator | `removed` | |
| `published`, `link_shared` | moderator removes it | actor is a moderator | `removed` | Recipe leaves the catalogue, search and every direct path; forks show their parent as unavailable. |

### Disallowed transitions

**Disallowed from `private`:** being seen, rated, favourited, reported or forked
by any account other than the owner; appearing in the catalogue or in search.

**Disallowed from `pending`:** any edit; withdrawal after a decision is recorded;
forking by anyone other than the owner; rating; favouriting; being reported by
anyone other than the moderator's own action.

**Disallowed from `rejected`:** publication without a new submission; visibility
to anyone but the owner and a moderator; forking by another account.

**Disallowed from `link_shared`:** any edit; re-moderation on publication or on
issuing a further link; appearing in the catalogue or in search; forking by any
account other than the owner.

**Disallowed from `published`:** any edit; unpublishing, hiding or deleting by
the owner or by any user; return to an editable state; transfer of ownership;
re-moderation when restored from `reported_hidden`.

**Disallowed from `reported_hidden`:** any edit; restoration by the owner;
un-hiding by the withdrawal of reports; forking by another account.

**Disallowed from `removed`:** any edit; restoration by the owner; forking by
anyone, including the owner; every transition out of the state — `removed` is
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
- Given a `removed` recipe, when anyone including its owner forks it, then the
  fork is rejected.

**Verification:** functional test over each state and both actor kinds.
**Source:** user decision, 2026-09-20. See
[ADR — forking from published only](decisions/20260920-forking-published-only.md).

### ST-005 — A revoked link stops opening the recipe

When the owner revokes the private link of a recipe, the system shall stop
serving that recipe to every holder of a previously issued link.

**Acceptance criteria**
- Given a link holder who could open the recipe, when the owner revokes the
  link, then opening it fails for that holder.
- Given the same recipe, when the owner issues a new link, then the new link
  opens it and the revoked one still does not.
- Given the revocation, when the recipe is inspected, then its content is
  unchanged and it is still not editable.

**Verification:** two-account functional test across revoke and re-issue.
**Source:** user decision, 2026-09-20.

### ST-006 — Three distinct reports hide a recipe

When three distinct accounts have reported the same recipe, the system shall
hide it from every account except its owner and moderators, and shall place it
in the moderation queue.

**Acceptance criteria**
- Given a published recipe with two reports from two accounts, when the catalogue
  is searched, then it is still returned.
- Given a third report from a third account, when the catalogue is searched,
  then it is not returned, and it cannot be opened directly.
- Given the same account reporting the same recipe repeatedly, when the reports
  are counted, then they count once.
- Given the recipe is hidden, when its owner opens it, then the owner is told it
  is hidden and awaiting review, and is told nothing about who reported it, how
  many did, or under which category.

**Verification:** functional test with three accounts; repeat-report test.
**Source:** user decision, 2026-09-20. See
[ADR — report and block model](decisions/20260920-report-and-block-model.md).

### ST-007 — An unavailable parent is shown without a reason

Where a recipe's parent is `removed`, the system shall show the parent as
unavailable and shall not state why.

**Acceptance criteria**
- Given a fork whose parent was removed, when its card is opened, then the
  parent is named as unavailable, cannot be opened, and no reason is given.
- Given that fork, when the chain is walked upwards, then it stops at the
  unavailable parent.

**Verification:** manual check on a fork whose parent was removed.
**Source:** user decision, 2026-09-20, resolving OQ-005.

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

Account deletion is not modelled: it is decided in section `retention`.

## Media

A media item has no independent lifecycle. Its visibility follows the recipe it
belongs to (DOM-005), and it is owned by the account that uploaded it from the
moment of upload. Retention of media belonging to recipes that were never
published is OQ-004.
