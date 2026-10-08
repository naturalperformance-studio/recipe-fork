---
slug: ST
status: closed
updated: 2026-10-04
ids: [ST-001, ST-002, ST-003, ST-004, ST-005, ST-006, ST-007, ST-010]
---

# Recipe rules

The requirements that the recipe's state machine in [the section index](00-index.md) rests on.

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
[ADR — publication is permanent](../decisions/20260920-publication-is-permanent.md).

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
[ADR — immutability trigger](../decisions/20260920-immutability-trigger.md).

### ST-004 — Forking is allowed from published recipes and from one's own

The system shall allow anyone, with or without an account, to fork a `published`
recipe, shall allow an owner to fork their own recipe in any state, shall allow
anyone in serverless mode to fork a recipe imported from a file (FLOW-065), and
shall reject every other fork.

**Acceptance criteria**
- Given a published recipe, when anyone forks it, with or without an account,
  then a new recipe in `local` is created with its parent set to the forked
  recipe.
- Given a `link_shared` recipe, when an account holding the link forks it, then
  the fork is rejected.
- Given a `local`, `link_shared` or `private` recipe, when its owner forks it,
  then a new recipe in `local` is created.
- Given a `removed` recipe, when its owner forks it, then a new recipe in `local`
  is created; when any other account forks it, then the fork is rejected.
- Given such a fork of a recipe a moderator removed, when its media is resolved,
  then it carries only the media that still exists, because MEDIA-013 deletes
  what nothing else holds and FORK-007 can carry no file that is gone.

**Verification:** functional test over each state and both actor kinds,
including a fork of an own removed recipe whose media has already been deleted.
**Source:** user decision, 2026-09-20; the `removed` case decided 2026-09-21,
replacing the criterion that had refused an owner's fork against the requirement
it belonged to. See
[ADR — forking from published only](../decisions/20260920-forking-published-only.md)
and [ADR — a removed recipe is still its author's work](../decisions/20260921-a-removed-recipe-is-still-its-authors-work.md).
**Changed 2026-09-28:** a fork is created in `local` and needs no account, and a
recipe imported from a file can be forked in serverless mode, by user decision of
that date.

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
[ADR — report and block model](../decisions/20260920-report-and-block-model.md).

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
  no parent is shown at all and this requirement does not apply (FORK-005);
  in serverless mode a parent that is not held is shown by name as FLOW-065
  states, and this requirement does not apply either.
- Given that fork, when the chain is walked upwards, then it stops at the
  unavailable parent.

**Verification:** manual check on a fork whose parent was removed.
**Source:** user decision, 2026-09-20, resolving OQ-005; amended the same day to
name the reason for one category only, see
[ADR — danger to health travels down the fork chain](../decisions/20260920-danger-travels-down-the-chain.md).

### ST-010 — An owner deletes a recipe that was never externally visible

Where a recipe is in `local`, `private` or `rejected`, the system shall allow its
owner to delete it, and shall destroy the recipe together with every submission
record attached to it.

**Acceptance criteria**
- Given a recipe in `local`, when its owner deletes it, with or without a
  connection, then it is gone from the device at once and nothing reaches the
  server, which never held it.
- Given a recipe in `private` or in `rejected`, when its owner deletes it, then
  it is gone from the server and from every device the account holds, and no
  screen reaches it; a device that still holds it and sends it before learning of
  the deletion creates it again (FLOW-059 (13)).
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
- Given no network connection, when deletion of a recipe in `private` or
  `rejected` is attempted, then FLOW-003 refuses it and nothing is deleted
  locally either, because the server orders every transition of a recipe it
  holds (ST-009).

**Verification:** delete a recipe in each of the two states, with media, with a
closed rejection and with a published own fork naming it; attempt deletion in
each of the other five states and in aeroplane mode.
**Source:** user decision, 2026-09-21. MEDIA-010 and MEDIA-012 already assumed
this act existed — the allowance is freed by publishing or by deleting — and
without it an author who fills 150 MB with drafts has no way out but publication.
It stops at external visibility for the same reason everything else does: a
recipe another person can see is one other recipes may name (ST-001, CON-003).
**Note:** how deletion is offered, undone and made final is FLOW-053.
**Changed 2026-09-28:** a recipe in `local` is deleted on the device alone, since
the server never held it (user decision of that date,
[ADR — the device first](../decisions/20260928-the-device-first.md)).
**Corrected 2026-09-21:** the second criterion said that the four states past
`rejected` "can never be deleted by anyone". RET-001 says otherwise and gives its
reason: deleting an account destroys every recipe it owns that was never
published, which includes a `link_shared` recipe and one that reached `removed`
or `reported_hidden` without ever being published. What is permanent is
publication, not every state beyond the owner's reach.
**Changed 2026-10-01:** criterion 2 names the device that sends a recipe deleted
elsewhere before it learned of the deletion, by user decision of that date (SD-045;
[ADR — a recipe the server lost is sent as new](../decisions/20261001-a-recipe-the-server-lost-is-sent-as-new.md)).
