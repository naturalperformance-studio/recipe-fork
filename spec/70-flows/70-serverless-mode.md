---
slug: FLOW
status: closed
updated: 2026-09-28
ids: [FLOW-061, FLOW-062, FLOW-063, FLOW-064, FLOW-065]
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

**Verification:** install a release with the mode on over one holding two
accounts, own recipes in every state and found recipes with and without held
media; enumerate every screen, open each recipe, switch accounts.
**Source:** user decision, 2026-09-28. The catalogue and everything that needs
the server go; the core the device runs alone stays, because it is the part a
person installed the application for (GOAL-001).

### FLOW-063 — A recipe is exported to a file

Where serverless mode is on, when a person chooses to export a recipe from the
recipe book, the system shall write a file holding that recipe's identity, title,
tags, serving count, ingredients, steps, timers and every media item the device
holds for it, the nickname of its author, and the identity of its direct parent
with that parent's title and author's nickname where the device holds them, and
shall hand the file to the operating system to be saved or shared.

**Acceptance criteria**
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
recipe in the book can be exported, and export does not freeze it. After the
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
- Given an imported recipe, when it is removed from the recipe book, then it
  leaves it as any other marked recipe does, with its held content (FLOW-009).
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

### FLOW-065 — A fork of an imported recipe names it as its parent

When a person forks a recipe imported from a file, the system shall create the
fork on the device naming the imported recipe as its parent, shall let the person
open that parent while it is held in the recipe book, and shall show it by its
title as unavailable when it is not; and shall show the parent an imported recipe's
file names in the same way, with its author's nickname marked as not verified.

**Acceptance criteria**
- Given an imported recipe, when it is forked, then the fork is created in the
  active account's recipes, or in the pool when no account is active, is editable,
  and names the imported recipe as its parent.
- Given that fork, when it is created, then it carries none of the imported
  recipe's media and none of its description, as any fork by someone other than
  the author does (FORK-002).
- Given that fork, when the imported recipe is still in the recipe book, then the
  parent can be opened from the fork's card.
- Given that fork, when the imported recipe has been removed from the book, then
  the parent is named by its title and shown as unavailable.
- Given an imported recipe whose file names a parent, when its card is opened,
  then that parent is named by the title and author's nickname the file states,
  the nickname marked as not verified, and it opens when a recipe with the same
  identity is in the book and is shown as unavailable when none is.
- Given that fork, when it is exported (FLOW-063), then its file names the
  imported recipe as its parent.

**Verification:** import a recipe, fork it, open the parent from the fork, remove
the imported recipe and reopen the fork; import a file naming a parent held in the
book and one naming a parent that is not.
**Source:** decided by the spec author at the product owner's request of
2026-09-28 ("на твоё усмотрение"), and accepted by the product owner the same day.
FORK-005 hides a parent that was never published; with no server nothing is
published, so a parent is shown as the file names it, which keeps one step of
lineage alive between people (GOAL-003).
