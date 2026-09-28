# A recipe file is a ZIP holding a versioned JSON document and its media

**Status:** accepted
**Date:** 2026-09-28
**Requirements:** FLOW-063, FLOW-064, FLOW-065, MEDIA-001, MEDIA-006

**Context:** In serverless mode a recipe is exported to a file and imported from one (FLOW-063, FLOW-064). The file carries the recipe's text and every media item the device holds for it. A step may carry a video of up to 6 MB, so a recipe with a video on every step weighs tens of megabytes. A file written by a later version of the application than the one installed must be refused and said to be (FLOW-064 (5)). A fork of an imported recipe names it by its identity, which the file carries (FLOW-065).

**Decision:**
- A recipe file is a ZIP archive with the product's own extension and MIME type, so that the operating system routes it to the application.
- The archive holds `recipe.json` and a `media/` directory with one entry per media item, stored as the device holds it (no re-encoding).
- `recipe.json` carries `format_version`, an integer. An importer refuses a file whose `format_version` is greater than the one it supports, and a file that is not a ZIP, has no `recipe.json`, or whose document does not parse.
- The document's schema lives in `:core` (`app.recipefork.core.file`), beside the recipe model it serialises. Export and import use `java.util.zip` and read and write each media entry as a stream straight to and from disk.
- Export hands the file to the Android share and save sheet through a `FileProvider`. Import is an `ACTION_VIEW` intent filter on the product's MIME type and a document picker.

**Consequences:**
- No new dependency. The ZIP reader is in the standard library.
- Memory use at import does not grow with the size of the media, because each entry is streamed to disk.
- The file adds almost nothing to the size of its media, and media does not compress further.
- `recipe.json` is readable and editable by anyone who unzips the file, including the stated author and the stated parent. This is what the specification assumes: the nickname is labelled as stated in the file and not verified every time it is shown (FLOW-064 (3)).
- A later change of container (for example to a format of the product's own) is a new `format_version`; older applications refuse such files by the rule above, and newer ones can keep reading ZIP.

**Alternatives considered:**
- *One JSON document with media embedded as base64.* Rejected: base64 adds a third to the size of every item, and a JSON parser that reads the document whole needs around 130 MB of heap for a 49 MB recipe, which fails with OutOfMemory on low-end devices within `minSdk` 29. A streaming parser would avoid that at the cost of code the ZIP reader does not need. Discussed with the user on 2026-09-28, who chose ZIP for now.
- *A binary format of the product's own.* Deferred by the user on 2026-09-28. A format alone does not stop the stated author being edited: the application that reads it can be decompiled, and the specification rejected a signature for want of a key model (spec ADR `20260928-serverless-mode-and-recipe-files.md`). Whether authorship must be protected is a question for `spec/`, not for the format.
- *A directory of files.* Rejected: it cannot be shared as one attachment.
