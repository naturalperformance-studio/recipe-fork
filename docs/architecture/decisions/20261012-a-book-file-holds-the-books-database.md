# A saved book is a zip of the book's own database and media

**Status:** accepted
**Date:** 2026-10-12
**Requirements:** FLOW-073 (the whole book saved and restored, before the first public release); OQ-041 answered by the owner on 2026-10-12: a restore into a book that holds recipes asks "Add to the book" or "Replace the book"

**Context:** a recipe file (`20260928-recipe-file-is-a-zip.md`) carries one recipe as found by someone else: it drops what is the owner's own — that a recipe is theirs and editable, its draft state, its place in the book, the step a fork's parent was. A book saved for a lost or reset phone must come back exactly: own recipes still editable, found ones still found, forks still pointing at their parents, every photo and clip. Each book is already one Room database with a media directory of its own (`20260928-one-database-per-recipe-book.md`).

**Decision:**
- A saved book is one zip file, extension `.recipefork-book`, holding `manifest.json` (the file format's version, the book schema's version, the app's version, when it was saved, the counts of recipes and media), `book.db` (a consistent copy of the active book's database), `media/…` (the book's media directory as it is) and `kitchen.json` (My kitchen). Done marks, the shopping list and the device's settings do not travel.
- The copy of the database is taken while nothing writes to it: a full WAL checkpoint inside a read transaction, then a file copy (`VACUUM INTO` needs SQLite 3.27, which API 29 lacks). Media is streamed into the zip; the file is written to a temporary file and moved over the place the person chose only when whole.
- A restore unpacks the file into a directory of the application's own, refuses it unread if the manifest is missing, from a newer format, or names a book schema newer than the app's, and opens `book.db` as a book database of its own — so Room's migrations bring an older book up to date exactly as they do for a book on the phone.
- From that opened copy, recipes go into the active book through one operation of the book repository, in one transaction of the active book's database: **Add** copies each recipe the book does not hold (by recipe id) with its media files, leaving every recipe already there untouched; **Replace** removes every recipe of the book and copies all of the file's. Media files are copied into place before the transaction and the replaced book's files deleted only after it commits, so a failure at any point leaves the book as it was. My kitchen is merged on Add and replaced on Replace.
- An empty book is restored into without asking. The temporary directory is deleted when the restore ends, whatever the outcome.
- Nothing is sent anywhere: the file goes where the person puts it through the system's document picker, and comes from where they pick it (FLOW-073 (2)).

**Consequences:** a book saved by any version restores in any later one, its migrations doing the work they already do; the file is as large as the book's media, so writing and reading report progress and can be cancelled. The file's inside is the database's schema, not a documented exchange format — it is for restoring this app's books, not for other apps.

**Alternatives considered:** a zip of recipe files with a richer document — rejected: a second full serialisation of everything a book holds, to keep in step with every schema change. Copying the database file over the active book — rejected: it can only replace, never add, and a half-copied file loses the book. Android's Auto Backup — rejected as the only means: it depends on the person's Google account and settings, restores only on a new install, and caps app data at 25 MB, which a book with clips exceeds.
