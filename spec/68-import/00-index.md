---
slug: IMPORT
status: blocked
updated: 2026-09-27
ids: []
---


# Import from a social network

**Not in the first release.** This section is written ahead of the product that
will carry it: MVP-002 lists it as deliberately absent, and nothing here binds the
first release. It is the product owner's next feature, and it is specified now so
that it arrives as a decided shape rather than a wish.

A person who has found a recipe in a post on Instagram, TikTok or Pinterest
shares that post to the application. A language model turns the post into a
structured recipe, and that recipe is published as the source's: no account is
its author, it carries no media, and it names and links its source. One source is
turned into a recipe once, so the next person who shares the same post is led to
the recipe that already exists. Changing an imported recipe is forking it.

Two decisions underlie everything below:
[ADR — an import belongs to its source](../decisions/20260927-an-import-belongs-to-its-source.md)
and [ADR — where an import's text comes from](../decisions/20260927-where-an-imports-text-comes-from.md).
The model's budget is its own:
[ADR — the model has its own fifty dollars](../decisions/20260927-the-model-has-its-own-fifty-dollars.md).

What is settled elsewhere and applies to an imported recipe unchanged, because
once it exists it is a published recipe like any other: it is permanent (ST-001),
frozen (CON-003), rated (RATE), reported and hidden (ABUSE-002, ST-006), removed
only by a moderator (ST-002), forked by anyone (ST-004), and a fork of it carries
what FORK-001 names.

## Files

| File | Status | Requirements | What it is for |
|---|---|---|---|
| [10-starting-an-import.md](10-starting-an-import.md) | blocked | IMPORT-001 – IMPORT-007 | Where an import begins, who may start one, how a post is identified, and what happens when its source already has a recipe, is under review, was rejected or was removed on a claim. |
| [20-reading-and-publishing.md](20-reading-and-publishing.md) | blocked | IMPORT-008 – IMPORT-016 | Where the text comes from on each platform, what the model makes of it, what the person sees before confirming, what happens when there is no recipe in it, and how a TikTok and an Instagram or Pinterest import reach the catalogue — with no author and no media. |
| [30-provenance-cost-and-states.md](30-provenance-cost-and-states.md) | blocked | IMPORT-017 – IMPORT-021 | The source mark and the mark every descendant keeps, a moderator's re-reading by a newer model, the USD 50 model budget, the connection, and the state table of an imported recipe. |
