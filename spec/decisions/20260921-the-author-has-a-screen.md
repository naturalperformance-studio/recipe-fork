# The author has a screen, and reports about them collapse onto it

**Status:** accepted; adds FLOW-055 and FLOW-056, rewrites the last criteria of
ABUSE-006, places the control ABUSE-005 and ACT-005 never had

**Context:** Three requirements gave an act against an author and none of them
had a surface anywhere in `flows`. ABUSE-005 lets anyone block an author.
ABUSE-006 lets anyone report one and speaks of "an author's public
representation" as though it existed. ACT-005, ACT-006 and ACT-007 describe a
publishing ban in full — what it stops, what it leaves alone, what the banned
account is told — and no screen in the product applied it. FLOW-044 to FLOW-050
enumerate the moderator's lists and every decision they admit, and a ban is in
none of them. ACT-008 had meanwhile closed the escape route: there is no
administration screen and none may be invented.

A second thing was unimplementable rather than missing. ABUSE-006's last
criterion required that the queue not fill with one person's repetitions, while
the same requirement was widened on 2026-09-20 to people holding no account —
and AUTH-015 stores no device identifier, CON-001 stores nothing about a person,
FLOW-051 sends nothing from a device that holds no account. Nothing in the
product can tell two anonymous reporters apart, so nothing in the product could
satisfy that sentence.

**Decision:**
- A nickname leads to that author's screen: their published recipes listed as
  CAT-003 requires, and the acts that belong to an author rather than to one
  recipe — report (ABUSE-006), block and unblock (ABUSE-005), and, for the
  moderator role alone, ban and lift a ban (FLOW-055, FLOW-056).
- Reports about one author collapse onto one queue entry, keyed by the reported
  author and not by the reporter. A further report raises that entry's count and
  records its category; no second entry appears. Closing the entry ends it, and a
  later report opens a new one (ABUSE-006, FLOW-045).
- A queue entry about an author admits two decisions, dismiss and ban, and
  opening it leads to the author's screen (FLOW-046, FLOW-049).

**Consequences:**
- The ban becomes performable. Until now the only moderator action against a
  person rather than a recipe existed in the permission matrix and nowhere else.
- The count on an author entry counts reports, not people: one person pressing
  ten times reads exactly like ten people pressing once, and the moderator cannot
  tell those apart. That is the price of holding nothing that identifies a
  reporter, and it is the same price ABUSE-004 already pays by delivering
  uncounted reports.
- Repetition can no longer cost the moderator anything but a larger number, which
  is what the unimplementable sentence was reaching for.
- The product gains a screen that looks like a profile and is not one: it carries
  the nickname and the work, because CON-001 leaves nothing else to put on it and
  NGOAL-002 refuses the social layer. RET-003 had already required the author to
  be shown through a layer that can say something other than a nickname, and this
  is that layer.
- Blocking an author does not hide their screen from the blocker. Blocking acts
  on catalogue and search (ABUSE-005), and a screen the reader opened on purpose
  is neither.

**Alternatives considered:**
- *Decide everything on the queue entry and build no author screen.* Rejected:
  it gives the moderator a ban and leaves reporting and blocking an author with
  nowhere to be performed from, since a recipe card is about one recipe.
- *Move the ban out of the application, beside the moderator role and the two
  dictionaries (ACT-008, DOM-014).* Rejected: the role and the dictionaries are
  set once in a while by the operator, while a ban answers a report that arrived
  in the queue minutes ago, and the screen that shows the report would still have
  had to exist for ABUSE-005 and ABUSE-006.
- *Deduplicate author reports by account and refuse them from anyone else.*
  Rejected: it undoes the widening of 2026-09-21 for exactly the person it was
  made for — the owner of a stolen photograph, who holds no account here.
- *Drop the repetition rule and let every report be its own entry.* Rejected:
  one person could fill the queue of the one moderator, which is the thing
  ABUSE-001 exists to prevent on the other side.
