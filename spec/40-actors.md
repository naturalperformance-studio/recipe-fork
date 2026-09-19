---
slug: ACT
status: closed
updated: 2026-09-20
ids: [ACT-001, ACT-002, ACT-003, ACT-004, ACT-005, ACT-006, ACT-007]
---

# Actors and permissions

Two actors exist. There is no unauthenticated actor: an account is created
automatically at first launch (ACT-001), so every person using the application
holds one from their first screen onwards. "Owner" and "reader" are roles an
account holds with respect to a particular recipe, not separate actors.

| | Account holder | Owner of the recipe | Moderator |
|---|---|---|---|
| Browse and search the public catalogue | yes | yes | yes |
| Open a published recipe | yes | yes | yes |
| Open a private draft or fork | no | yes | no |
| Open a recipe awaiting moderation | no | yes | yes |
| Create and fork a recipe | yes | yes | yes |
| Edit a recipe before it becomes externally visible | no | yes | no |
| Edit a recipe after it becomes externally visible | no | no | no |
| Submit a recipe for moderation | yes, unless banned | yes, unless banned | yes, unless banned |
| Approve or reject a submission | no | no | yes |
| Remove a published recipe from the catalogue | no | see OQ-007 | yes |
| Report a recipe or an author | yes | yes | yes |
| Block an author's recipes from own catalogue and search | yes | yes | yes |
| Ban an account from publishing | no | no | yes |

### ACT-001 — An account exists from first launch

When the application is launched on a device for the first time and no key file
has been imported, the system shall create an account, generate its identifier
and a random three-word nickname, and sign the user in to that account.

**Acceptance criteria**
- Given a device with no prior installation, when the application is opened,
  then the user is signed in to a newly created account without entering
  anything.
- Given that account, when its public representation is displayed, then it shows
  the generated three-word nickname and no other identifying value.

**Verification:** manual check on a clean device; inspection of the created
record for absence of the fields forbidden by CON-001.
**Source:** draft section 2.1; user decision, 2026-09-20.

### ACT-002 — Ownership is fixed at creation

The system shall treat the account that created a recipe as that recipe's owner
for the lifetime of the recipe, and shall provide no means of transferring
ownership to another account.

**Acceptance criteria**
- Given a recipe, when its owner is resolved at any point in its lifetime, then
  it is the account that created it.
- Given any interface of the application, when it is enumerated, then it offers
  no action that changes a recipe's owner.

**Verification:** interface review and data model review.
**Source:** draft sections 2.3, 4.1.

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

### ACT-005 — A publishing ban stops submissions, not the account

When a moderator bans an account from publishing, the system shall reject every
subsequent moderation submission from that account.

**Acceptance criteria**
- Given a banned account, when it submits a recipe for publication or requests a
  private link, then the submission is rejected and the recipe stays private.
- Given a banned account, when it creates, forks, edits a private recipe, scales
  a recipe, searches or uses favourites, then those actions succeed unchanged.

**Verification:** functional test on a banned account covering both lists above.
**Source:** user decision, 2026-09-20; obligation origin CON-006.

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

When a banned account attempts to submit a recipe for moderation, the system
shall state that the account may not publish.

**Acceptance criteria**
- Given a banned account, when a submission is rejected, then the interface says
  the account cannot publish, rather than reporting a generic failure.

**Verification:** manual UI check on a banned account.
**Source:** user decision, 2026-09-20 (there is no contact channel outside the
application — CON-001 — so the application is the only place a ban can be
communicated).
