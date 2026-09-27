---
slug: GLOSS
status: closed
updated: 2026-09-27
ids: []
---

# Glossary — The editor, language, quality and operations, import, names

Part of the [glossary](00-index.md); holds no requirements.

## What the editor tells the author

**The statement before submission** — what the product says about what becomes
permanent, shown before every submission and before publishing from a private
link, confirmed explicitly, and impossible to switch off (GUIDE-001, GUIDE-002).

**Checklist** — seven fixed pieces of advice on writing a recipe, opened from a
control in the editor, read-only, recording nothing and blocking nothing
(GUIDE-003, GUIDE-004).

**Hint** — a short statement shown only at a moment the author's own action
creates. Hints hold no state, need no dismissal, and are never triggered by
reading what the author wrote (GUIDE-005).

**Replacement** — a fork of an author's own frozen recipe that the author marked
as replacing it (FORK-008). Once the replacement is published, the recipe it
replaces says so and leads to it (FORK-009). It removes nothing: the replaced
recipe stays in the catalogue with every rating, favourite and fork it had.

**Recovery key** — the third of an account's keys, issued at creation as its own
artefact, shown once and never displayed by the application again. It is the only
thing that can retire a lost or stolen account key; presenting it retires all
three keys, signs out every device and issues a new set (AUTH-020, AUTH-021). It
is useless if kept beside the account key, which is the one thing the product
asks of its holder and cannot enforce.

## Language

**Declared language** — the language an author states a recipe is written in,
chosen in the editor, frozen at publication and never detected by the product
(L10N-004). It selects which listings the recipe appears in when a reader filters
(L10N-005) and which analysis a search query is matched under (L10N-007). It says
nothing about whether the text is actually in that language.

**Interface language** — the language the application shows its own words in.
Starts from the device setting, changed by the reader at any time, held on the
device and not on any account (L10N-002). Unrelated to the declared language of
any recipe.

## Quality and operations

**`modest` and `typical`** — the two reference configurations, each a device
together with a network, that every timing in the spec is measured on. A
threshold names the profile it binds on, and a result from the other profile does
not satisfy it (QUAL-001).

**Release measurement report** — the record produced before a release holding
every timing taken on both profiles, including the ones that bind nothing and are
kept only so that releases can be compared (QUAL-002, QUAL-003, QUAL-004).

**Health endpoint** — the one address that answers whether the server can serve
requests and discloses nothing else. No user reaches it; it exists for the
availability probe (EXT-011).

**Availability probe** — the observer outside the product that calls the health
endpoint on a schedule and tells the operator when it stops answering. It is not
a dependency: it calls the product, the product never calls it, and the product
behaves identically without it (EXT-012). Its records are what the 95 per cent of
QUAL-006 is computed from.

**Copy of the store** — the hourly copy of everything the product holds except
media, kept so that a total failure costs at most an hour of work. Restoring one
returns the whole state to the moment it began, including up to an hour of
deletions and moderator decisions (QUAL-008, RET-004).

## Import (not in the first release)

**Import** — turning a post on Instagram, TikTok or Pinterest into a published
recipe by a language model (IMPORT-001, IMPORT-010).

**Imported recipe** — a recipe produced by an import. It has no author and no
owner (IMPORT-015), no media (IMPORT-016), and is changed only by being forked.

**Source** — the post an imported recipe was read from, identified by its
platform and the post's own identifier, however the link to it was written
(IMPORT-003). One source has at most one current imported recipe (IMPORT-004).

**Source mark** — what an imported recipe shows in place of a nickname: the
platform, the model version, and the way to the post (IMPORT-017).

**Model version** — the version of the language model that produced an imported
recipe; shown in its source mark, and what makes a moderator's re-reading
possible (IMPORT-019).

**Importing account** — the account that shared the post. It is not the author,
and only a moderator sees it (IMPORT-015).

## Names

**Recipe Fork** — the working title used throughout this repository. It is not a
decided product name and has been cleared against nothing; the published name is
a release prerequisite (OQ-016).
