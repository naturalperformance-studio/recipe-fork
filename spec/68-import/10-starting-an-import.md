---
slug: IMPORT
status: blocked
updated: 2026-09-27
ids: [IMPORT-001, IMPORT-002, IMPORT-003, IMPORT-004, IMPORT-005, IMPORT-006, IMPORT-007]
---

# Starting an import

### IMPORT-001 — An import starts from a shared post of one of three platforms

When a person shares a link to the application from Instagram, TikTok or
Pinterest, the system shall start an import of the post the link names, and shall
refuse a shared link that names anything else, stating which three sources it
accepts.

**Acceptance criteria**
- Given a link to a post on each of the three platforms, when it is shared to the
  application, then an import starts.
- Given a link to a platform profile, a search page or any other site, when it is
  shared, then the import is refused and the three sources are named.
- Given the refusal, when it is read, then nothing has been sent to the model.

**Verification:** share one post from each platform, a profile link and a link to
an unrelated site.
**Source:** user decision, 2026-09-27.

### IMPORT-002 — Importing needs an account and counts as a submission

The system shall require an account to import, shall count every confirmed
import toward the daily limit of ABUSE-001, and shall refuse an import to an
account under a publishing ban (ACT-005).

**Acceptance criteria**
- Given a person with no account, when they share a post, then the application
  offers to create one (AUTH-001) and starts no import until it exists.
- Given an account that has made three submissions in the preceding 24 hours,
  whether recipes or imports, when it confirms an import, then the import is
  refused until the window has passed.
- Given a TikTok import, which is published without moderation, when the limit is
  counted, then it counts all the same.
- Given an account under a publishing ban, when it shares a post, then the import
  is refused with the ban named, before any model call.

**Verification:** import with no account, with a banned account, and four times
in a day with an ordinary account.
**Source:** user decision, 2026-09-27: without an account there is no limit and
no ban, and a TikTok import reaches the catalogue with nobody having read it.

### IMPORT-003 — A post is identified by its platform and its own identifier

The system shall identify the source of an import by its platform and the post's
identifier on that platform, and where a shared link is a short link, shall learn
the post's address by following its redirection once without reading the page
it leads to.

**Acceptance criteria**
- Given two links to the same post that differ only in tracking parameters, when
  each is imported, then both identify the same source.
- Given a `pin.it` or `vm.tiktok.com` link, when it is shared, then the source it
  identifies is the same as the full link to that post.
- Given the redirection, when what the server received is inspected, then it is
  the address and nothing from the page's content.
- Given a short link whose redirection fails, when it is shared, then the import
  is refused as a link that could not be read, and nothing is sent to the model.

**Verification:** share the same post as a full link, a link with tracking
parameters and a short link; capture the server's outbound traffic.
**Source:** user decision, 2026-09-27: Pinterest's own share menu gives a short
link, so refusing short links would make Pinterest unusable.

## What a source already has

### IMPORT-004 — A source already published leads to its recipe

When a person shares a post whose source already has a published imported
recipe, the system shall open that recipe and shall not import the post again.

**Acceptance criteria**
- Given a published imported recipe, when anyone shares its source, then its card
  opens with a statement that this post was already imported, and the person can
  fork it or add it to favourites from there as from any card.
- Given that case, when the model's use is counted, then no call was made and
  nothing was counted toward ABUSE-001.
- Given a source with a published recipe and a newer one replacing it
  (IMPORT-019), when the source is shared, then the newest published one opens.

**Verification:** import a post, publish it, share the same post from another
account.
**Source:** user decision, 2026-09-27: the person should be able to take the
ready version at once.

### IMPORT-005 — A source under review is not imported a second time

If a person shares a post whose source has an import waiting for a moderator,
then the system shall refuse the import, stating that the post is already under
review.

**Acceptance criteria**
- Given an Instagram import in `pending`, when another account shares the same
  post, then the import is refused with that statement and no text is asked for.
- Given the same account sharing it again, when it does, then the same refusal
  applies.

**Verification:** submit an Instagram import and share the post again from two
accounts.
**Source:** user decision, 2026-09-27.

### IMPORT-006 — A rejected import leaves its source free

When a moderator rejects an import, the system shall accept a new import of the
same source.

**Acceptance criteria**
- Given a rejected import, when another account shares the same post and pastes
  its caption, then the import proceeds.
- Given the rejected import, when anyone other than a moderator looks for it,
  then it is nowhere, and it can be neither edited nor resubmitted, because it has
  no owner to do either.

**Verification:** reject an import, then import the same post again.
**Source:** user decision, 2026-09-27; the second criterion follows from IMPORT-015
and the decision that an import is never edited.

### IMPORT-007 — A source removed on a rights claim is never imported again

If a person shares a post whose imported recipe was removed with the category
`foreign_content`, then the system shall refuse the import permanently, stating
that this post cannot be imported.

**Acceptance criteria**
- Given a recipe removed after a claim under ABUSE-007, when its source is shared
  by anyone, at any later time, then the import is refused before any model call.
- Given the refusal, when it is read, then it names no claimant and no reason
  beyond the post not being importable.

**Verification:** remove an imported recipe with `foreign_content` and share its
source again.
**Source:** user decision, 2026-09-27: otherwise the next person to share the post
puts it back into the catalogue the claim took it out of.
**Blocked by:** OQ-034 for removal with any other category.
