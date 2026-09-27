---
slug: IMPORT
status: blocked
updated: 2026-09-27
ids: [IMPORT-008, IMPORT-009, IMPORT-010, IMPORT-011, IMPORT-012, IMPORT-013, IMPORT-014, IMPORT-015, IMPORT-016]
---

# Getting the text and turning it into a recipe

### IMPORT-008 — A TikTok post's text comes from TikTok's official endpoint

When a TikTok import starts, the system shall take the post's caption from
TikTok's official oEmbed endpoint, and shall neither fetch the post's page nor
download its video.

**Acceptance criteria**
- Given a public TikTok post, when it is imported, then its caption is the text
  the model receives, and nothing else is fetched from TikTok.
- Given a private, deleted or embed-disabled TikTok post, when it is imported,
  then the import is refused as a post that could not be read.

**Verification:** traffic capture of a TikTok import.
**Source:** user decision, 2026-09-27;
[TikTok — Embed videos](https://developers.tiktok.com/doc/embed-videos/), checked
2026-09-27.

### IMPORT-009 — An Instagram or a Pinterest post's text is pasted by the person

When an Instagram or a Pinterest import starts, the system shall ask the person to
paste the post's text, and shall fetch nothing from either platform beyond the
redirection of IMPORT-003.

**Acceptance criteria**
- Given an Instagram or a Pinterest import, when it starts, then a text field
  asks for the caption and says that it will be compared with the post by a
  moderator.
- Given an empty field, when the person tries to continue, then nothing is sent
  to the model.

**Verification:** traffic capture of an Instagram and a Pinterest import.
**Source:** user decision, 2026-09-27; Instagram forbids extracting content
through its oEmbed
([Meta — Instagram oEmbed](https://developers.facebook.com/docs/instagram-platform/oembed/),
checked 2026-09-27).

### IMPORT-010 — A model turns the text into a recipe in the text's own language

When the text of an import is available, the system shall have a language model
produce from it a recipe — title, serving count, ingredients and steps — declared
in the language of the text, without translating it.

**Acceptance criteria**
- Given a caption in any language, when it is parsed, then the recipe's declared
  language (L10N) is the caption's and its text is not translated.
- Given an ingredient the model cannot match to a dictionary entry, when the
  recipe is produced, then it is a free-text ingredient exactly as an author's
  would be (DOM), with its warning.
- Given the produced recipe, when its fields are enumerated, then it has no
  description and no media (IMPORT-016).

**Verification:** import a Russian and an English caption, one naming an
ingredient absent from the dictionary.
**Source:** user decision, 2026-09-27: a translation would be a retelling, more
expression and less source.

### IMPORT-011 — The person sees the result before it goes anywhere

When the model has produced a recipe, the system shall show it to the person who
imported it with two actions, confirm and cancel, and shall offer no editing.

**Acceptance criteria**
- Given a produced recipe, when it is shown, then it is shown as its card would
  be, with confirm and cancel, and no field can be changed.
- Given cancel, when it is pressed, then nothing is created, nothing is counted
  toward ABUSE-001, and the source is not held.
- Given a person who wants it changed, when they read the screen, then it says
  that they can fork the recipe once it is in the catalogue.

**Verification:** import and cancel; import and confirm.
**Source:** user decision, 2026-09-27: for a TikTok import this is the only
moment anybody reads the result before the catalogue does; editing it would make
the person its author.

### IMPORT-012 — A text with no recipe in it creates nothing

If the model finds no recipe in the text, then the system shall refuse the import
with a statement that the post holds no recipe it could read, shall create
nothing, and for a TikTok source shall refuse later imports of that source by the
same model version without calling the model.

**Acceptance criteria**
- Given a caption reading only "recipe at the link in my profile", when it is
  parsed, then the import is refused and nothing is created.
- Given a TikTok source refused this way, when anyone shares it again while the
  model version is unchanged, then it is refused without a model call.
- Given a TikTok source refused this way, when the model version has changed,
  then the next share calls the model again.
- Given an Instagram or a Pinterest source refused this way, when another person
  shares it, then they are asked for the text again, because they may paste a
  different one.

**Verification:** import a TikTok post with no recipe twice and count model
calls; do the same on Instagram with a different text the second time.
**Source:** user decision, 2026-09-27.

## Publishing an import

### IMPORT-013 — A confirmed TikTok import is published at once

When a person confirms a TikTok import, the system shall publish the recipe
without moderation.

**Acceptance criteria**
- Given a confirmed TikTok import, when the catalogue is next read, then the
  recipe is in it, and no queue entry was made.
- Given the published import, when it is reported, then ABUSE-002 and ST-006
  apply as to any published recipe.

**Verification:** confirm a TikTok import and look for it in the catalogue and in
the queue.
**Source:** user decision, 2026-09-27: its text is known to come from the source.

### IMPORT-014 — A confirmed Instagram or Pinterest import waits for a moderator

When a person confirms an Instagram or a Pinterest import, the system shall place
the recipe in `pending` and shall show the moderator its source link and the
pasted text beside the produced recipe.

**Acceptance criteria**
- Given a confirmed Instagram import, when the queue is opened, then its entry
  shows the link, the pasted text and the recipe.
- Given the moderator's approval, when it is recorded, then the recipe is
  `published`.
- Given the moderator's rejection, when it is recorded, then IMPORT-006 applies.

**Verification:** submit an Instagram and a Pinterest import and decide each.
**Source:** user decision, 2026-09-27: pasted text could put any words under any
blogger's link, and the moderator is what compares the two.
**Blocked by:** OQ-033 for what the importing account is told of the decision.

### IMPORT-015 — An imported recipe has no author

The system shall record no account as the author or owner of an imported recipe,
shall store which account imported it, and shall show that account to a
moderator only.

**Acceptance criteria**
- Given an imported recipe, when it is shown to a reader, then no nickname
  appears on it and nothing leads to an author's screen (FLOW-055).
- Given a moderator, when they open the recipe, then the importing account is
  shown and can be banned from there (ACT-005).
- Given the importing account, when its holder reads its own recipes, then the
  import is not among them, and no owner action — withdrawal, deletion, a
  replacement mark (FORK-008), a private link — is offered on it to anyone.
- Given the importing account deleted (RET-001), when the recipe is read, then it
  is unchanged.

**Verification:** import a recipe; view it as a reader, as the importer and as a
moderator.
**Source:** user decision, 2026-09-27.

### IMPORT-016 — An imported recipe carries no media

The system shall attach no photograph, video or frame to an imported recipe.

**Acceptance criteria**
- Given any import, when it is produced and published, then it holds no media
  item, and no cover image appears in its catalogue entry (CAT-003).
- Given a fork of it, when it is edited, then its author may add their own media
  as to any recipe of their own.

**Verification:** import from each platform and enumerate the recipe's media.
**Source:** user decision, 2026-09-27; downloading a video to take a frame from it
is what Google Play's intellectual property policy prohibits
([Google Play — Intellectual Property](https://support.google.com/googleplay/android-developer/answer/9888072?hl=en),
checked 2026-09-27).
