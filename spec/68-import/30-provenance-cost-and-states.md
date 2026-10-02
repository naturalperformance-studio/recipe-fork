---
slug: IMPORT
status: closed
updated: 2026-09-27
ids: [IMPORT-017, IMPORT-024, IMPORT-018, IMPORT-019, IMPORT-020, IMPORT-021]
---

# How an import shows where it came from

### IMPORT-017 — An imported recipe names its source and the model that read it

The system shall show on every imported recipe, on its card and in its catalogue
and search entry in place of the author's nickname, a source mark naming the
platform and the version of the model that produced it, and leading to the post.

**Acceptance criteria**
- Given an imported recipe, when its card or entry is shown, then the mark names
  the platform and the model version, and no nickname is shown.
- Given the mark, when it is followed, then the post opens outside the application
  and nothing is fetched by the application to open it.
- Given a TikTok import, when its mark is shown, then it also names the post's
  creator as TikTok's oEmbed endpoint returned it at import.
- Given an Instagram or a Pinterest import, when its mark is shown, then it names
  no creator, and no name read by the model out of the pasted text appears anywhere.

**Verification:** open an imported recipe from each platform as a reader and
follow the mark.
**Source:** user decision, 2026-09-27; the creator's name resolved OQ-032 the same
day: only TikTok returns it from an endpoint, and a name read out of pasted text
would be a name nobody verified.

### IMPORT-024 — A source is never checked again

The system shall make no request to a source's platform about a post after its
import is confirmed, and shall change nothing about an imported recipe because of
what later happens to the post.

**Acceptance criteria**
- Given a post deleted, made private or edited on its platform after import, when
  the imported recipe is shown, then it is unchanged, and following its mark leads
  to whatever the platform now shows.
- Given the product's outbound traffic over a month, when it is enumerated, then
  no request concerns a post whose import was already confirmed, except a
  moderator's re-reading (IMPORT-019).
- Given a creator who wants the recipe gone, when they ask, then the route is
  ABUSE-007 and nothing else.

**Verification:** delete a post after importing it and reopen the recipe; traffic
review.
**Source:** user decision, 2026-09-27, resolving OQ-035: the recipe is permanent
like any other (ST-001).

### IMPORT-018 — Every descendant of an import says that its original was imported

Where a recipe descends from an imported recipe through any number of forks, the
system shall show on it a mark that its original was imported, leading to that
imported recipe inside the application.

**Acceptance criteria**
- Given a fork of a fork of an imported recipe, when it is shown, then it carries
  the mark, and following the mark opens the imported recipe, whose own mark leads
  outside.
- Given this mark, when it is read, then it is shown in addition to the direct
  parent of FORK-004, and it is the one exception to GOAL-003's single step: it
  names one ancestor, the imported one, and nothing between. See
  [ADR — the imported ancestor is named](../decisions/20260927-the-imported-ancestor-is-named.md).
- Given an imported ancestor since removed, when a descendant is shown, then the
  mark still says the original was imported and shows it as unavailable
  (ST-007).

**Verification:** fork an import twice down; follow the mark from the third
recipe.
**Source:** user decision, 2026-09-27.

## A newer model

### IMPORT-019 — A moderator may have a source read again by a newer model

Where the current model version differs from the one that produced an imported
recipe, the system shall offer a moderator to import its source again, shall
publish the result as a new imported recipe, and shall mark the earlier one as
replaced by it, as FORK-009 does for an author's replacement.

**Acceptance criteria**
- Given an import produced by an earlier model version, when a moderator opens
  it, then the action is offered; given one produced by the current version, then
  it is not.
- Given the action, when it completes, then a new recipe exists with the current
  model version on its mark, and the earlier recipe states that a newer reading
  exists and leads to it.
- Given the earlier recipe's forks, when they are shown, then they still name the
  earlier recipe as parent; nothing moves to the new one.
- Given the earlier recipe, when its ratings, favourites and fork count are read,
  then none changed (FORK-006).
- Given an Instagram or Pinterest source, when it is read again, then the model
  receives the text the moderator approved the first time.
- Given any account without the moderator role, including one that shares the
  source, when it looks for the action, then it is not offered.

**Verification:** change the model version, re-read one TikTok and one Instagram
import, and compare the two recipes and their forks.
**Source:** user decision, 2026-09-27: a published recipe cannot be corrected
(CON-003), so a newer reading can only be a new recipe; allowing anybody to
trigger it would turn every model update into a wave of duplicates and calls.

## Cost and connection

### IMPORT-020 — The model has a budget of USD 50 a month

The system shall keep the cost of the language model within USD 50 per calendar
month, and if that budget is reached, then it shall refuse every import until the
month ends, stating that imports resume next month.

**Acceptance criteria**
- Given a month whose model cost has reached the budget, when anyone shares a
  post, then the import is refused with that statement before any call.
- Given the budget, when the product's infrastructure cost is summed under
  CON-002, then the model's cost is not part of the USD 20 and is summed
  separately against this figure.
- Given the refusal, when it applies, then IMPORT-004 still works: a source that
  already has a recipe still leads to it.

**Verification:** set the counter at the budget and share a new and an already
imported post; monthly review of the model provider's invoice.
**Source:** user decision, 2026-09-27. See
[ADR — the model has its own fifty dollars](../decisions/20260927-the-model-has-its-own-fifty-dollars.md).

### IMPORT-021 — Import needs the server

If the server cannot be reached or does not answer, then the system shall refuse
an import as FLOW-003 refuses a submission, and shall queue nothing.

**Acceptance criteria**
- Given no connection, when a post is shared to the application, then it states
  that a connection is needed and keeps nothing of the link.

**Verification:** share a post in airplane mode.
**Source:** FLOW-003, applied to a new act that needs the server.

## States of an imported recipe

An imported recipe has no owner, so every transition of
[`entity-states`](../60-entity-states/00-index.md) triggered by an owner is absent. What
exists:

| From | Trigger | Guard | To | Side effects |
|---|---|---|---|---|
| — | TikTok import confirmed | IMPORT-002, IMPORT-020 | `published` | No queue entry; counted toward ABUSE-001. |
| — | Instagram or Pinterest import confirmed | IMPORT-002, IMPORT-020 | `pending` | Queue entry with link and pasted text. |
| `pending` | moderator approves | actor is a moderator | `published` | Enters the catalogue. |
| `pending` | moderator rejects | actor is a moderator | `rejected` | Source free again (IMPORT-006). |
| `published` | third counted report | ABUSE-003 | `reported_hidden` | As ST-006. |
| `published` | moderator removes | actor is a moderator | `removed` | With `foreign_content`, the source is closed for good (IMPORT-007); with `health_risk`, it is closed except to a moderator's re-reading by a newer model (IMPORT-022). |

**Disallowed from every state:** any transition an owner triggers — withdrawal,
edit, resubmission, deletion, a private link, a replacement mark; any edit by
anybody.
**Disallowed from `rejected`:** leaving it; it is a record for the moderator.
