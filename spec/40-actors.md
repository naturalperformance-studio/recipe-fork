---
slug: ACT
status: closed
updated: 2026-10-01
ids: [ACT-001, ACT-002, ACT-003, ACT-004, ACT-005, ACT-006, ACT-007, ACT-008]
---

# Actors and permissions

Three actors exist. A person with no account is one of them: most of what the
product is for — finding a recipe, cooking from it, writing and forking one,
keeping it on the device — needs no account at all, and one appears only when an
action requires it (ACT-001, AUTH-001). What a person with no account writes is
in the pool of their device (AUTH-026), and they are its owner there for every row
below that speaks of the owner, on that device. "Owner" and "reader" are roles an account holds with
respect to a particular recipe, not separate actors.

An account is full or muted. A muted account is what the product creates when the
device cannot be attested (AUTH-004); it is not a fourth actor but an account
that uses nothing of the server but carrying its favourites, and it becomes full
as soon as a verdict and a free slot allow (AUTH-005). The columns below state
what each may do while the server runs; in serverless mode every row that needs
the server is gone for everyone (FLOW-062).

| | No account | Account holder | Owner of the recipe | Moderator | Muted account |
|---|---|---|---|---|---|
| Browse and search the public catalogue | yes | yes | yes | yes | yes |
| Open a published recipe | yes | yes | yes | yes | yes |
| Open a private draft or fork | its own, on this device | no | yes | no | its own |
| Open a recipe awaiting moderation | no | no | yes | yes | no |
| Create and fork a recipe | yes, on this device (FLOW-002) | yes | yes | yes | yes, on this device |
| Edit a recipe before it becomes externally visible | its own, on this device | no | yes | no | its own, on this device |
| Send a recipe to the server (FLOW-059) | no (AUTH-001) | its own | yes | its own | no (AUTH-004) |
| Edit a recipe after it becomes externally visible | no | no | no | no | no |
| Submit a recipe for moderation | no | yes, unless banned | yes, unless banned | yes, unless banned | no (AUTH-004) |
| Approve or reject a submission | no | no | no | yes | no |
| Delete a recipe that was never externally visible | its own, on this device | no | yes, in `local`, `private` and `rejected` (ST-010) | no | its own, on this device |
| Remove a published recipe from the catalogue | no | no | no (ST-001) | yes, including their own (MOD-014) | no |
| Rate a published recipe | no (AUTH-001) | yes | no (RATE-004) | yes | no (AUTH-004) |
| Add a recipe to favourites | yes, on this device only (FLOW-051) | yes | no (FLOW-040) | yes | yes |
| Carry favourites beyond this device | no (FLOW-051) | yes (FLOW-052) | yes | yes | yes |
| See the own-recipes view of the recipe book (FLOW-034) | yes, the pool's | yes | yes | yes | yes |
| Hold several accounts on the device and switch between them (AUTH-011) | — | yes | yes | yes | yes |
| Export a recipe to a file, import one (serverless mode only, FLOW-063, FLOW-064) | yes | yes | yes | yes | yes |
| Report a recipe or an author | yes (ABUSE-002) | yes | yes | yes | yes |
| Block an author's recipes from own catalogue and search | yes, on this device only | yes | yes | yes | yes |
| Ban an account from publishing | no | no | no | yes | no |

The moderator column applies to the moderator's own recipes too: the role is not
suspended by ownership, it decides its holder's own submissions (MOD-013) and
acts on their own published recipes (MOD-014). What that reaches is the
moderator's own actions and nothing else — approving, rejecting, removing,
dismissing or upholding reports, clearing a safety flag, banning an account and
lifting a ban. In every other row an account holding both roles has what the
owner column gives it: it opens, edits and deletes its own unpublished recipes
(ST-003, ST-010, FLOW-034), and it neither rates nor favourites a recipe it owns
(RATE-004, FLOW-040).

**Corrected 2026-09-21:** this paragraph read that the moderator column wins
wherever the two disagree. Taken literally it let a moderator rate and favourite
their own recipe, against RATE-004 and FLOW-040, and it took their own drafts
away from them — no opening, no editing, no deleting — against ST-003, ST-010
and FLOW-034. The rule it was written for is
[ADR — the moderator is not exempt from their own content](decisions/20260921-the-moderator-is-not-exempt-from-their-own-content.md),
which is about moderator actions and says nothing about the rest of the matrix.

### ACT-001 — Most of the product needs no account

The system shall allow a person holding no account to browse and search the
catalogue, open and cook from any published recipe, write, edit, fork and delete
recipes on the device, keep favourites on the device, report content and block an
author, and shall create an account only when an action requires one (AUTH-001).

**Acceptance criteria**
- Given a clean installation, when the application is opened, then it is usable
  at once and nothing is created, asked for or generated.
- Given a person with no account, when they favourite a recipe or write one,
  then it is kept on the device and is not carried anywhere else (FLOW-051).
- Given a person with no account, when they use a control that needs one, then
  account creation is offered and the action proceeds once the account exists.
- Given an account, when its public representation is displayed, then it shows
  the generated three-word nickname and no other identifying value.
- Given a person who never creates an account, when everything they can reach is
  enumerated, then it matches the first column of the table above.

**Verification:** walk the whole account-less surface on a clean install, then
trigger creation from each action that requires it.
**Source:** draft section 2.1; user decision, 2026-09-20, replacing the earlier
decision that an account is created at first launch. See
[ADR — an account is created on demand](decisions/20260920-account-on-demand.md).
**Changed 2026-09-28:** writing, editing, forking and deleting on the device need
no account, by user decision of that date
([ADR — the device first](decisions/20260928-the-device-first.md)).

### ACT-002 — Ownership is fixed at creation, or when a pool recipe first gets an account

The system shall treat the account in whose recipe book a recipe was created as
that recipe's owner for the lifetime of the recipe; shall treat a recipe created in
the pool (AUTH-026) as having no owner until either the pool is moved into an
account or the recipe's own sending creates an account (FLOW-059 (1)), that account
becoming its owner; and shall provide no other means of transferring ownership.

**Acceptance criteria**
- Given a recipe created while an account was active, when its owner is resolved
  at any point in its lifetime, then it is that account.
- Given a recipe created in the pool, when the pool is moved into account A, then
  A is its owner from then on, and it can be sent only by A.
- Given a recipe in the pool whose sending, with no account active, creates an
  account, when that account exists, then that account is the recipe's owner
  whether the offer of AUTH-026 is accepted or declined and whether or not the
  recipe was then sent — the account muted (AUTH-004) or the sending refused
  (FLOW-003) — and the rest of the pool stays without an owner if the offer is
  declined.
- Given a recipe the server holds, when its owner is resolved, then it is the
  account that sent it, which is always the account it belonged to on the device.
- Given any interface of the application, when it is enumerated, then it offers
  no action that changes a recipe's owner.

**Verification:** interface review and data model review; move a pool into an
account and send one of its recipes.
**Source:** draft sections 2.3, 4.1. The pool's one move added 2026-09-28 by user
decision ([ADR — several accounts on one device](decisions/20260928-several-accounts-on-one-device.md)).
**Changed 2026-09-29:** a pool recipe whose sending creates an account becomes that
account's whatever the answer to the pool offer, by user decision of that date
(SD-040; [ADR — the recipe that created the account is the account's](decisions/20260929-the-recipe-that-created-the-account.md)).
**Correction, 2026-10-01:** criterion 3's Given read as a recipe that was sent,
which a muted account or a refused sending never does (SD-042); by user decision of
that date the account's creation, not the sending, makes it the owner
([ADR — the account's creation gives the owner](decisions/20261001-the-accounts-creation-gives-the-owner.md)).

### ACT-003 — Moderator authority is an assignable role

The system shall represent moderator authority as a role that can be granted to
or revoked from any account, and shall not tie it to a specific account.

**Acceptance criteria**
- Given a second account, when the role is granted to it, then it gains exactly
  the permissions in the matrix above without any code change.
- Given a moderator account, when the role is revoked, then its moderation
  permissions stop applying immediately.

**Verification:** grant and revoke the role on a test account and re-check the
permissions in the matrix.
**Source:** draft section 6.3.

### ACT-004 — A moderator cannot alter content

If a moderator attempts to modify the content of any recipe, then the system
shall reject the modification.

**Acceptance criteria**
- Given a recipe in any state, when a moderator acts on it, then the available
  actions are approve, reject and remove, and none of them changes the recipe's
  title, description, tags, servings, ingredients, steps or media.

**Verification:** attempted content modification as a moderator, through the
application and directly against the service.
**Source:** CON-003; draft sections 4.2, 6.2.

### ACT-005 — A publishing ban stops what would make a recipe public, not the account

When a moderator bans an account from publishing, the system shall reject every
subsequent moderation submission from that account, and shall reject every other
act of that account that would put one of its recipes into the catalogue.

**Acceptance criteria**
- Given a banned account, when it submits a recipe for publication or requests a
  private link, then the submission is rejected and the recipe stays private.
- Given a banned account holding a recipe in `link_shared`, when it publishes
  that recipe, then the publication is rejected and the recipe stays
  `link_shared`, although that act creates no submission and passes no
  moderation (MOD-001) — it is the guard the transition table already carries.
- Given a banned account holding a recipe in `link_shared`, when it revokes its
  link or issues a new one, then both succeed, because the recipe is already
  externally visible to whoever the author chose and neither act reaches the
  catalogue (ST-005).
- Given a banned account, when it creates, forks, edits a private recipe, scales
  a recipe, searches or uses favourites, then those actions succeed unchanged.

**Verification:** functional test on a banned account covering every line above,
including a recipe already in `link_shared`.
**Source:** user decision, 2026-09-20; obligation origin CON-006. Widened
2026-09-21: the requirement said "moderation submission" while the transition
table forbade a banned owner publishing from `link_shared`, which is the one
route into the catalogue that creates no submission. Left as it was, a ban could
be walked around by anyone holding an already approved recipe. See
[ADR — a ban stops the catalogue, not the queue](decisions/20260921-a-ban-stops-the-catalogue-not-the-queue.md).
**Note:** where the ban is applied and lifted is FLOW-056.

### ACT-006 — A publishing ban leaves published content in place

While an account is banned from publishing, the system shall keep that account's
already published recipes accessible in the catalogue.

**Acceptance criteria**
- Given a banned account with published recipes, when the catalogue is searched,
  then those recipes are still returned.
- Given a fork of one of those recipes, when its parent reference is opened,
  then the parent still resolves.

**Verification:** functional test before and after a ban.
**Source:** user decision, 2026-09-20. Individual published recipes are removed
one by one by a moderator; a ban is not a bulk removal, so provenance chains are
never broken by banning.

### ACT-007 — A banned account is told it is banned

When a banned account attempts an act that ACT-005 rejects, the system shall
state that the account may not publish.

**Acceptance criteria**
- Given a banned account, when a submission is rejected, then the interface says
  the account cannot publish, rather than reporting a generic failure.
- Given a banned account, when its publication from `link_shared` is rejected,
  then it is told the same thing and in the same terms.

**Verification:** manual UI check on a banned account.
**Source:** user decision, 2026-09-20 (there is no contact channel outside the
application — CON-001 — so the application is the only place a ban can be
communicated).

### ACT-008 — The moderator role is granted outside the application

The system shall offer no control, on any screen or at any address a user can
reach, that grants or revokes the moderator role, and the role shall be changed
only by direct operation on the product's own store by whoever operates it.

**Acceptance criteria**
- Given every screen of the application, including those the moderator role
  itself opens, when they are enumerated, then none grants the role, revokes it,
  lists the accounts that hold it, or asks for it.
- Given the role granted or revoked in the store, when the affected account next
  uses the application, then FLOW-044 governs what appears and disappears, with
  nothing reinstalled and nothing else changed about the account (ACT-003).
- Given the first account to hold the role, when the product is first deployed,
  then it is granted the same way as every later one: there is no bootstrap
  screen and no first-run special case.
- Given the three surfaces outside the application (EXT-006, LEG-008) and the
  claims address (ABUSE-007), when they are enumerated, then none of them is an
  administration surface either.
- Given this requirement, when it is read against CON-005, then it adds no client
  surface: operating one's own store is not a client of the product.

**Verification:** enumerate every screen as a moderator and as an ordinary
account; grant and revoke the role in the store and confirm the application
follows without being reinstalled.
**Source:** user decision, 2026-09-21. ACT-003 required the role to be
assignable and FLOW-044 described what changes when it is, but nothing said who
assigns it or through what, which left a developer to invent an administration
screen — and an administration screen is a second thing to secure for an
audience of one, sitting outside everything `auth` was built to protect (the
reasoning OBS-002 already used against a separate console).
**Cost, stated:** nothing in the product records who granted the role or when.
With one operator that record would say only what the operator already knows,
and the day a second moderator exists is the day it is worth writing.
