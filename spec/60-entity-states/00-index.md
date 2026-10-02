---
slug: ST
status: closed
updated: 2026-09-28
ids: []
---

# Entity states

## Recipe

| State | Who can see it | Editable |
|---|---|---|
| `local` | whoever holds the recipe book it is in, on this device only; the server does not hold it | yes |
| `private` | owner | yes |
| `pending` | owner, moderator | no |
| `rejected` | owner, moderator | yes |
| `link_shared` | owner, moderator, anyone holding an active private link | no |
| `published` | everyone | no |
| `reported_hidden` | owner, moderator | no |
| `removed` | owner, moderator | no |

An imported recipe (not in the first release) has no owner and so none of the
owner's transitions; its own table is in
[`import`](../68-import/30-provenance-cost-and-states.md).

`link_shared` is a state of the recipe; whether a particular private link opens
it is a property of that link (`active`), not of the recipe.

`local` is the state of a recipe that was never sent (FLOW-059). It exists on one
device, in an account's recipe book or in the pool of AUTH-026, and its
transitions happen on the device: the server orders none of them, because it holds
nothing of the recipe (ST-009). Every other state is held by the server and
ordered there. A recipe imported from a file (FLOW-064) is not an own recipe in
any state: it is a favourite, and has no transition but being forked or removed
from the book. See
[ADR — the device first](../decisions/20260928-the-device-first.md).

### Transitions

| From | Trigger | Guard | To | Side effects |
|---|---|---|---|---|
| — | a person creates a recipe, with or without an account | — | `local` | Empty recipe on the device, in the active account's book or in the pool, no parent. |
| `published` | anyone forks it, with or without an account | — | `local` (new recipe) | New recipe in the forking person's book or pool, `parent` set to the forked recipe. The forked recipe is unchanged. |
| any state | owner forks their own recipe | actor is the owner | `local` (new recipe) | As above. |
| — (a found recipe imported from a file) | anyone forks it | serverless mode (FLOW-061) | `local` (new recipe) | As above; `parent` is the imported recipe (FLOW-065). |
| `local` | owner sends it (FLOW-059) | an account is active and full; the server answers; MEDIA-010 allows its media | `private` | The server holds the recipe and its media; the owner becomes fixed (ACT-002); it reaches the account's other devices. |
| `local` | owner deletes it | — | — (the recipe ceases to exist) | Destroyed on the device; nothing reaches the server, which never held it. |
| `local` (in the pool) | the pool is moved into an account (AUTH-026) | the person accepts | `local` | The recipe belongs to that account from then on. |
| `private`, `rejected` | owner edits it on the device | — | same state | The server's copy is unchanged until the owner sends it again (FLOW-059); a divergent sending is FLOW-008. |
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

**Disallowed from `local`:** reaching the server by any act but its owner's
sending; submission, a private link, publication, rating, reporting; being seen
on any device but its own; being ordered by the server (ST-009).

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
belongs to (DOM-005). Until its recipe is sent it is on the device alone
(FLOW-004); from the sending it is owned by the account that sent it. A stored file is deleted once no surviving recipe holds it
(MEDIA-012, MEDIA-013); nothing is deleted for being old (RET-006), and what
bounds unpublished media is the 150 MB allowance of MEDIA-010.

This section is split into the parts below (2026-09-28, when it outgrew one file);
no identifier changed.

| Part | What is in it | Requirements |
|---|---|---|
| [10-recipe-rules.md](10-recipe-rules.md) | Publication, removal, freezing, who may fork, revoked links, hiding by reports, the unavailable parent, and deleting a recipe | ST-001–ST-007, ST-010 |
| [20-ordering.md](20-ordering.md) | How the server orders transitions: a withdrawal against a decision, and every other pair | ST-008, ST-009 |
