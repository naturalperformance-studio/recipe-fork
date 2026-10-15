---
slug: FLOW
status: closed
updated: 2026-10-15
ids: [FLOW-076, FLOW-077, FLOW-078]
---

# Collections, and acting on several recipes at once

Added on 2026-10-15 by the sync of v0.31–v0.38. Collections were the owner's idea of
2026-10-12, built in v0.36; selecting several recipes in the book was the owner's
scope of 2026-10-13, built in v0.37. A collection is defined in DOM-028.

### FLOW-076 — The reader groups recipes into collections

The system shall let the reader make collections in their recipe book, add any
recipe of the book to them — own or found — from its card, its entry or a selection
(FLOW-078), order, rename and delete their own collections, and act on a whole
collection at once: open it, add all its recipes to the shopping list, print it or
share it as a PDF (FLOW-075), and, in serverless mode, export it (FLOW-077).

**Acceptance criteria**
- Given a recipe, when the reader adds it to a collection, then it goes to the end
  of it; given it is already there, then nothing changes.
- Given the list of collections a recipe can be added to, when it is offered, then
  it holds the reader's own collections and a way to make a new one, never an
  imported collection.
- Given a collection, when "add all to the shopping list" is chosen, then each
  recipe goes onto the list at the count its card would open with, and a recipe
  already on the list keeps its count (FLOW-074).
- Given a collection deleted, when its notice shows, then the deletion can be
  undone.
- Given an imported collection, when it is shown, then it names the author its file
  states, marked as stated in the file and not verified, as a found recipe does
  (FLOW-064); given a file that names none, then no author is shown.

Where collections appear in the book, their order and how they look are the
team's.

**Source:** user decision, 2026-10-12 (the owner's idea, `later.md`, chosen for
v0.36 at its planning): authors group their recipes into collections and share a
whole collection, and a collection goes into the shopping list in one action. The
criteria are the lead's rules built in v0.36, accepted with it (2026-10-13).

### FLOW-077 — A collection travels as one file

Where serverless mode is on, the system shall let a person export a collection to
one file holding its name, its order, its stated author and every recipe in it as
a recipe file holds it (FLOW-063); and, when a person opens such a file, shall
import it whole or not at all — each recipe as a found recipe by FLOW-064, the
collection as an imported one (DOM-028).

**Acceptance criteria**
- Given an own collection, when it is exported, then the file names no author;
  given an imported one, then it keeps the author its file stated, and each found
  recipe keeps its stated author and parent (FLOW-063).
- Given a collection file, when it is imported, then each recipe the book does not
  hold is added as found, each recipe it holds is left untouched, and the person is
  told how many were added and how many were already there.
- Given a collection file whose collection the book already holds as imported,
  when it is imported again, then the collection takes the file's name, author and
  order, gains the recipes it lacks, and keeps the recipes the file no longer
  lists.
- Given a collection file whose collection is the reader's own — they exported it
  and it came back — when it is opened, then it is refused with a notice saying
  they already have it, and nothing is added.
- Given a collection file with one recipe or one media item that cannot be read,
  when it is imported, then the whole file is refused, the person is told why, and
  nothing is added.
- Given serverless mode off, when a collection file is opened, then nothing is
  imported.

The file's format and its limits are the team's.

**Source:** user decisions at the planning of v0.36 (2026-10-12): a collection is
shared whole; an imported collection is not editable; importing it again adds what
is missing, with the name and order from the file. The rest are the lead's rules
built in v0.36, accepted with it (2026-10-13).

### FLOW-078 — Several recipes are chosen in the book and acted on at once

The system shall let the reader choose several recipes in one view of the recipe
book and, in one action, add them to a collection, add them to the shopping list,
print them or share them as one PDF (FLOW-075), or delete or remove them; and shall
offer each of these actions on a single entry as well, from its own menu.

**Acceptance criteria**
- Given the own-recipes view, when the reader starts choosing, then the choice stays
  within that view; leaving the view ends it.
- Given several chosen recipes, when "add to collection" is chosen, then all go into
  the one collection picked, or into a new one made then.
- Given several chosen recipes, when "add to the shopping list" is chosen, then each
  goes on at the count its card would open with, and one already on the list keeps
  its count.
- Given several chosen recipes, when they are deleted or removed, then they leave
  the book in one step, and one undo, while its notice shows, puts every one back
  with its collections; the deletion becomes final when the notice goes (FLOW-053).
- Given chosen recipes of which some cannot take an action — an own recipe whose
  state forbids deletion (ST-010) — when the action is taken, then it applies to
  those that can and the notice counts only them; an action that applies to none of
  the chosen is not offered.
- Given "choose all", when it is used, then every recipe the view lists under its
  current search and filters is chosen.
- Given one entry's menu, when it is opened, then the same actions are there for that
  recipe alone.

How choosing starts and ends, and what the bar shows while it lasts, are the team's.
A single list of the book's recipes across both views is not offered (the owner,
2026-10-13: left for later).

**Source:** the owner's scope of 2026-10-13 for v0.37, and their decisions at its
planning: the book keeps its two views and a choice lives within one. The rest are
the lead's rules built in v0.37, accepted with it (2026-10-13).
