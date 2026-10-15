---
slug: FLOW
status: closed
updated: 2026-10-15
ids: [FLOW-061, FLOW-062, FLOW-063, FLOW-064, FLOW-065, FLOW-073]
---

# Serverless mode and recipe files

What the application is when the product owner's server is gone for good: what a
release built for that removes, what it keeps, and the one new way a recipe passes
from one person to another — a file, exported by one and imported by another, at
their own risk.

The mode exists because the product owner may stop paying for the server
([ADR — serverless mode and recipe files](../decisions/20260928-serverless-mode-and-recipe-files.md)).
It is built into the first release and switched on by a later one (MVP-001). What
it keeps is what the device already does alone every day
([ADR — the device first](../decisions/20260928-the-device-first.md)), so the mode
adds two actions and takes the rest away; it does not introduce a second way of
writing or cooking.

### FLOW-061 — Serverless mode is switched on by a release, never by the application

The system shall have a serverless mode that is on only in a release built with it
on, and shall offer no control, and take no signal from the server or the network,
that switches it on or off.

**Acceptance criteria**
- Given a release built with the mode off, when the server cannot be reached for
  any length of time, then the mode is never entered and FLOW-003 governs every
  refusal.
- Given a release built with the mode on, when it is first started after being
  installed, then the mode is on and the application makes no request to the
  product's server from then on.
- Given any release, when every screen and setting is enumerated, then none
  switches the mode.

**Verification:** run a release built with the mode off against an unreachable
server for several days of device clock; install a release built with it on over
it and capture traffic; enumerate the screens of both.
**Source:** user decision, 2026-09-28: the application cannot tell a server closed
for good from one that does not answer, so the final update says which.

### FLOW-062 — What serverless mode takes away and what it keeps

While serverless mode is on, the system shall offer no catalogue, no search over
the catalogue, no private link, no sending of a recipe, no submission, no rating,
no report, no account creation, no signing in with a key not yet used on this
device, no account deletion and no moderator action, and shall keep everything the
device does alone: writing, editing, forking and deleting recipes, the recipe book
with its found recipes, scaling, timers, the accounts already on the device and the
switch between them (AUTH-011).

**Acceptance criteria**
- Given the mode on, when every screen is enumerated, then none of the removed
  controls is present, and no statement that a connection is needed stands in
  their place: they are gone, not refused.
- Given the mode on, when the application opens, then it opens on the recipe book
  rather than on the catalogue.
- Given a recipe in the recipe book, when it is opened, then it opens exactly as
  it did before the mode was on, with its scaling and its timers.
- Given a found recipe whose media was not held on the device (FLOW-011), when it
  is opened, then its text opens and its media is stated to be unavailable, not
  to need a connection.
- Given an own recipe in `pending`, `link_shared`, `published`, `reported_hidden`
  or `removed`, when the own-recipes view is shown, then it is listed with the
  last state the device knew, is not editable, and can be forked by its owner.
- Given an own recipe in `private` or `rejected`, when it is opened, then it is
  editable, as a recipe in `local` is.
- Given the accounts signed in on the device before the mode was on, when the
  account list is opened, then each of them is there, and switching between them
  works as AUTH-011 states.
- Given the export and import of FLOW-063 and FLOW-064, when the mode is on, then
  both are offered; when it is off, then neither exists anywhere.
- Given the mode on, when the editor offers a tag or an ingredient, then the
  dictionaries are those shipped with the application, and a device gets a newer
  copy only with an application update
  ([ADR — the serverless dictionary ships with the application](../decisions/20261003-the-serverless-dictionary-ships-with-the-app.md)).
- Given the mode on, when the checklist (GUIDE-004) or any editor warning is read,
  then none of them speaks of publication, because nothing is published (FLOW-022).

**Verification:** install a release with the mode on over one holding two
accounts, own recipes in every state and found recipes with and without held
media; enumerate every screen, open each recipe, switch accounts.
**Source:** user decision, 2026-09-28. The catalogue and everything that needs
the server go; the core the device runs alone stays, because it is the part a
person installed the application for (GOAL-001). Amended 2026-10-03 (sync
v0.3–v0.14): the dictionaries ship with the application, and nothing in the editor
speaks of publication — req-forge proposal from the sync of v0.3–v0.14, accepted by
the user, 2026-10-03.

### FLOW-063 — A recipe is exported to a file

Where serverless mode is on, when a person chooses to export a recipe — from its
entry in the recipe book or from its card's menu — the system shall write a file
holding that recipe's identity, title, description, declared language, tags,
serving count, equipment, ingredients with
their groups and notes, steps with their authors' notes, timers and
every media item the device
holds for it, the nickname of its author, and the identity of its direct parent
with that parent's title and author's nickname where the device holds them, and
shall hand the file to the operating system to be saved or shared.

**Acceptance criteria**
- Given any recipe in the book, when the person looks for export, then it is
  offered both on its entry and in its card's menu, and both produce the same file.
- Given an own recipe in any state, when it is exported, then a file is produced
  and the recipe is unchanged: an editable recipe stays editable, because export
  freezes nothing.
- Given a found recipe written by another author, when it is exported, then a
  file is produced carrying that author's nickname.
- Given a recipe written with no account active, when it is exported, then the
  file names no author.
- Given a recipe whose media the device does not hold, when it is exported, then
  the file carries its text and the media the device holds, and nothing else.
- Given a recipe with no parent, when it is exported, then the file names none.
- Given a recipe imported from a file, when it is exported again, then the file
  carries the author's nickname and the parent the first file stated.
- Given the file, when the operating system's share or save sheet opens, then
  where the file goes is the person's choice and the product sends it nowhere.

**Verification:** export an own draft, an own published recipe, a found recipe
with and without held media, a pool recipe and a fork; import each on another
device and compare.
**Source:** user decision, 2026-09-28: a file carries the text and the media, any
recipe in the book can be exported, and export does not freeze it. Amended
2026-10-03 (sync v0.3–v0.14): the file carries the description and the declared
language as well — req-forge proposal from the sync of v0.3–v0.14, accepted by the
user, 2026-10-03. Amended 2026-10-15: export is offered in the card's menu as well
as on the entry — user decision of that date (interface recommendation 7). After the
server is gone the product publishes nothing, and what a person posts elsewhere is
the responsibility of the place where they post it. See
[ADR — serverless mode and recipe files](../decisions/20260928-serverless-mode-and-recipe-files.md).

### FLOW-064 — A file is imported as a found recipe

Where serverless mode is on, when a person opens a recipe file, the system shall
add the recipe it holds to the recipe book of the active account, or to the pool
when no account is active (AUTH-026), as a found recipe — readable, scalable and
cookable, and not editable — and shall show the author's nickname the file states,
marked every time it is shown as stated in the file and not verified.

**Acceptance criteria**
- Given a recipe file, when it is imported, then the recipe is in the view of
  marked recipes, opens in full, is scaled by `scaling-rules`, and its timers run.
- Given an imported recipe, when it is open, then no edit control is present and
  the fork control is (FLOW-065).
- Given an imported recipe, when its author's nickname is shown on its card or on
  its entry, then it is marked as stated in the file and not verified; given a
  file that names no author, then no author is shown.
- Given a recipe already in the active recipe book — imported before, or the
  person's own — when a file holding the same recipe is imported, then nothing is
  added and the person is told it is already in their book.
- Given a file that cannot be read — damaged, not a recipe file, or written by a
  later version of the application than the one installed — when it is imported,
  then it is refused, the person is told why, and nothing is added.
- Given a file holding a draft — ingredient rows incomplete or off the grid, parts
  of the minimum missing — when it is imported, then it is added as it is, those
  rows kept as written; only a file whose structure cannot be read is refused.
- Given a file holding a clip longer than 15 seconds — beyond the half second
  MEDIA-007 allows for how a duration is recorded — when it is imported, then the
  whole file is refused with a message naming the 15-second limit, and nothing is
  added.
- Given a collection file (FLOW-077), when it is opened, then each recipe it holds
  is imported by the rules above and the collection by FLOW-077.
- Given a file holding a photo larger than 1 MB (1,000,000 bytes), the limit the
  device's own processing keeps (MEDIA-002), when it is imported, then it is
  refused, the person is told why, and nothing is added.
- Given an imported recipe, when it is removed from the recipe book, then it
  leaves it as any other marked recipe does, with its held content (FLOW-009), and
  its running and ringing timers end.
- Given a recipe file received in another application or saved in a file
  manager, when the person opens it, then this application is offered for it and
  the file is imported as above; the application is offered only for a file named
  as a recipe file or declaring the recipe file's type, never for files of an
  unknown type — a file that carries neither is imported from within the
  application.
- Given serverless mode off, when a recipe file is opened, then nothing is
  imported.

**Verification:** import a file on a device with an active account and on one with
none; import it twice; import a damaged file, a foreign file and one from a newer
build; inspect the card, the entry and the controls.
**Source:** user decision, 2026-09-28: an imported recipe is a found recipe, not
a template; a second import of the same recipe adds nothing; an unreadable file is
refused and said to be. The nickname cannot be checked with no server, so it is
labelled rather than trusted or hidden. See
[ADR — serverless mode and recipe files](../decisions/20260928-serverless-mode-and-recipe-files.md).
Amended 2026-10-03 (sync v0.3–v0.14): a draft imports as it is, only a
structurally unreadable file is refused, a clip over 15 seconds or a photo over
1 MB refuses the file, and removing a found recipe ends its timers — req-forge
proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03.
Amended 2026-10-11 (sync v0.22–v0.30): opening a file from another application
(v0.22). Offering the application only for recipe files is the user's decision of
2026-10-09, over matching every file of an unknown type; its stated cost is that a
file manager handing over neither the name nor the type (Samsung My Files, seen
2026-10-09) does not offer the application.

### FLOW-065 — A fork of an imported recipe names it as its parent

When a person forks a recipe imported from a file, the system shall create the
fork on the device naming the imported recipe as its parent, shall let the person
open that parent while it is held in the recipe book, and shall show it by its
title as unavailable when it is not; and shall show the parent an imported recipe's
file names in the same way. The parent is named in one line by its title; its
stated author is shown on its own card, and on the fork's card only while the
parent is not in the book (FORK-010).

**Acceptance criteria**
- Given an imported recipe, when it is forked, then the fork is created in the
  active account's recipes, or in the pool when no account is active, is editable,
  and names the imported recipe as its parent.
- Given that fork, when it is created, then it carries none of the imported
  recipe's media and none of its description, as any fork by someone other than
  the author does (FORK-002).
- Given that fork, when the imported recipe is still in the recipe book, then the
  fork's card names it in one line by its title, without its author, and the
  parent opens from that line; the author's nickname the file states is on the
  imported recipe's own card, marked as stated in the file and not verified
  (FLOW-064).
- Given that fork, when the imported recipe has been removed from the book, then
  the parent is still named by its title, is shown as unavailable, and the fork's
  card names the stated nickname beside it, marked as stated in the file and not
  verified.
- Given an imported recipe whose file names a parent, when its card is opened,
  then that parent is named by the title the file states; it opens when a recipe
  with the same identity is in the book, whose own card then shows its author; and
  when none is, it is shown as unavailable with the author's nickname the file
  states, marked as not verified.
- Given that fork, when it is exported (FLOW-063), then its file names the
  imported recipe as its parent.

**Verification:** import a recipe, fork it, open the parent from the fork, remove
the imported recipe and reopen the fork; import a file naming a parent held in the
book and one naming a parent that is not.
**Source:** decided by the spec author at the product owner's request of
2026-09-28 ("на твоё усмотрение"), and accepted by the product owner the same day.
FORK-005 hides a parent that was never published; with no server nothing is
published, so a parent is shown as the file names it, which keeps one step of
lineage alive between people (GOAL-003). Amended 2026-10-03 (sync v0.3–v0.14):
the fork names its parent by the stated nickname as well as by title —
req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03.
**Changed 2026-10-15:** the fork's card names the parent in one line by its title;
the stated author moved to the parent's own card and stays on the fork's only while
the parent is not in the book — user decision of that date, FORK-010, see
[ADR — the Origin is one line](../decisions/20261015-the-origin-is-one-line.md).

### FLOW-073 — The whole recipe book is saved and restored

Where serverless mode is on, the system shall let a person save their whole
recipe book — every recipe with its media, own and found, its collections
(FLOW-076) and My kitchen — to one file of their choosing, and restore a book from
such a file, so that a lost or reset phone does not lose it. Restoring into a book
that holds recipes shall ask whether to add the file's recipes to it or to replace
it. This shall exist before the first public release.

**Acceptance criteria**
- Given a book of recipes with photos and clips, when it is saved and restored on
  another device, then every recipe is there, readable and cookable as before, own
  recipes still editable and found ones still found, with its collections and My
  kitchen.
- Given a book that holds recipes, when a file is restored into it, then the person
  is asked to add or to replace, and replacing asks once more before anything goes.
- Given "add", when the restore is done, then every recipe the book lacked is added
  and every recipe it already held is unchanged; the collections it lacked are
  added, and one it holds gains the file's recipes it lacks.
- Given "replace", when the restore is done, then the book holds exactly what the
  file holds.
- Given an empty book, when a file is restored, then it is restored with no
  question.
- Given a saved book, when it is restored, then nothing is sent anywhere: like
  FLOW-063, the file goes only where the person puts it.
- Given done marks, a shopping list and the settings, when a book is saved, then
  they do not travel with it.
- Given a file that cannot be read, when it is restored, then nothing changes in
  the book and the person is told why.
- Given a book file opened from another application, when the person opens it,
  then it leads to the same restore.

The file's format and where the controls sit are the team's.

**Source:** user decision, 2026-10-10: the analyst's hypothesis H-001 ("a
whole-book backup is a must-have before release", `analytics/hypotheses.md`)
adopted; it also answers fork F-1 of v0.25. Written at the sync of 2026-10-11 as
intent. **Changed 2026-10-15 (sync v0.31–v0.38):** add or replace is the owner's
answer to OQ-041 at the planning of v0.34 (2026-10-12); what travels and the rest
of the criteria are the lead's rules built in v0.34 («Сборка ок, мержи»,
2026-10-12), and collections travel since v0.36.
