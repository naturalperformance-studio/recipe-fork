---
slug: FLOW
status: closed
updated: 2026-10-15
ids: [FLOW-053, FLOW-054, FLOW-055]
---

# An own recipe's actions, and the author's screen

Reached from the listings of [50-browsing.md](50-browsing.md): where an owner
deletes a recipe and handles its private link, and the screen a nickname leads to.
Split from `50-browsing.md` on 2026-09-27; no identifier changed.

### FLOW-053 — Deleting a recipe is immediate and can be undone for a moment

Where a recipe written in the active recipe book is in `local`, `private` or
`rejected`, the system shall offer to delete it from that recipe's entry in the
own-recipes view and from its card; shall delete it at once, asking for no
confirmation; shall then offer to undo the deletion for as long as its notice is
shown; and shall make the deletion final only when the notice goes.

**Acceptance criteria**
- Given a recipe in `local`, `private` or `rejected`, when its entry in the
  own-recipes view or its card is opened, then deletion is offered there; given a
  recipe in any other state, then no deletion control is present and no space is
  reserved for one (ST-010).
- Given deletion asked for, when it is done, then the recipe leaves the view at
  once, no confirmation is asked, and a notice says the recipe was deleted and
  offers to undo it; asked for from the card, the card closes to the view it came
  from.
- Given that notice, when the owner undoes the deletion, then the recipe returns
  with everything it held — its photographs and video, its state, its changes not
  sent.
- Given that notice, when it goes without undo, then the deletion is final: the
  recipe's media are deleted from the device, and for a sent recipe the server
  deletes it then (ST-010) and the unpublished allowance it held is freed
  (MEDIA-010, MEDIA-011).
- Given the view now empty, when the recipe leaves it, then FLOW-042 states so
  and offers to write a recipe.
- Given a timer of that recipe running or ringing, when the deletion is done, then
  the timer ends and announces nothing further, and an undo does not restart it.
- Given an imported recipe, when its card is opened, then removing it is offered
  there as on its entry.
- Given several recipes chosen in the book (FLOW-078), when they are deleted, then
  this requirement applies to them as one deletion: one notice, one undo that puts
  every one back with its collections (DOM-028), final for all when the notice
  goes.
- Given no network connection, when deletion of a sent recipe is asked for, then
  FLOW-003 refuses it before anything leaves the view; a recipe in `local` is
  deleted with or without a connection.

**Verification:** delete a draft carrying media and unsent changes from the card
and undo it; delete it again and let the notice go; look for the control on a
`pending` entry and on a `published` one; attempt deleting a sent recipe in
aeroplane mode.
**Source:** user decision, 2026-09-21, which created the act ST-010 specifies.
Where the control sits follows FLOW-036: a state is not worth a screen, so
neither is deleting one recipe. The statement follows the pattern the product
already uses for every irreversible act it offers — GUIDE-001 before submission,
AUTH-013 before an account is removed from the device, RET-005 before an account is deleted —
and it names the freed allowance because MEDIA-011 has just told the author that
deleting a draft is one of the two ways to get it back.
**Correction, 2026-09-27:** criterion 7 grounded itself on "the card is read",
which read as forbidding the edit action FLOW-029 now places in the card screen's
bar (SD-031); it now names the bar and keeps deletion off it.
**Changed 2026-09-28:** a recipe never sent is deleted on the device alone, with or
without a connection, and frees no allowance, since recipes reach the server only
when sent (user decision of that date).
Amended 2026-10-03 (sync v0.3–v0.14): deleting a recipe ends its timers — req-forge proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03.
**Changed 2026-10-04 (sync v0.15–v0.24):** deletion is offered on the card as well
as on the entry (user decision, v0.19), and it asks for no confirmation: it is
immediate and can be undone while its notice shows, final only when the notice
goes (user decision at the planning of v0.23). The statement before deletion is
gone with the confirmation; that a sent recipe reaches the server's deletion only
when final is a req-forge proposal accepted by the user at the sync of 2026-10-04.
**Changed 2026-10-15 (sync v0.31–v0.38):** several recipes are deleted as one
(v0.37, FLOW-078).


### FLOW-054 — The private link is shown, copied, revoked and reissued in one place

Where a recipe the account owns is in `link_shared`, the system shall show its
active private link on that recipe's entry in the own-recipes view, shall copy
it in full on request, and shall offer there to revoke it and to issue a new one.

**Acceptance criteria**
- Given a recipe that has just been approved with the trigger `private_link`,
  when its entry is opened, then the link is present and can be copied in one
  action, and nothing else in the application is the place to find it.
- Given the copy control, when it is used, then what reaches the clipboard is the
  link a recipient can open, and the product neither sends it anywhere nor offers
  to (EXT-010).
- Given the revoke control, when it is used, then the author is told that every
  person holding the link stops being able to open the recipe and that the recipe
  itself stays frozen (ST-005), and it is carried out only after an explicit
  confirmation.
- Given a revoked link, when the entry is opened, then it states that no link is
  active and offers to issue a new one.
- Given the control that issues a new link, when it is used, then the author is
  told first that the link they have already sent stops working, because at most
  one link is ever active (ST-005).
- Given a link the author never revoked, when a new one is issued, then the entry
  shows the new link alone and never both.
- Given the entry, when it is read, then it says nothing about who opened the
  recipe or how often, because the product records neither (RET-010, ANL-006).
- Given the recipe published afterwards from `link_shared`, when the entry is
  opened, then the link is gone from it: a published recipe is reached from the
  catalogue and the private link has nothing left to do (MOD-001).
- Given no network connection, when the link is copied, then it is copied from
  what the device holds (FLOW-038); when it is revoked or reissued, then
  FLOW-003 refuses it, because only the server can retire a link.

**Verification:** approve a recipe for a private link, copy the link and open it
from a second device, revoke it and confirm the copy stops working, issue a new
one and confirm the first stays dead; then publish the recipe and look at the
entry again.
**Source:** user decision, 2026-09-21. The link existed only as a side effect in
the transition table of `entity-states` — issued on approval, revoked and
reissued by ST-005 — with no screen anywhere that showed it to the person who
asked for it. It lives on the entry for the same reason deletion does (FLOW-053):
the own-recipes view is where a recipe of one's own is managed, and a state is
not worth a screen (FLOW-036).

### FLOW-055 — An author's screen is reached from their nickname

Where a nickname is shown, the system shall lead from it to that author's own
screen, and that screen shall list the author's published recipes and carry the
actions that belong to an author rather than to one recipe.

**Acceptance criteria**
- Given a nickname on a recipe card or on an entry in any listing (CAT-003),
  when the reader activates it, then the author's screen opens.
- Given that screen, when what it shows is enumerated, then it is the nickname
  and the author's `published` recipes, entry by entry as CAT-003 requires,
  ordered by the field the reader has chosen (CAT-001) — and nothing else about
  the person: no date, no totals, no description, and nothing to follow or
  subscribe to (CON-001, NGOAL-002).
- Given that screen, when the actions on it are enumerated, then they are
  reporting the author (ABUSE-006), blocking and unblocking them (ABUSE-005),
  and, for an account holding the moderator role alone, banning and lifting a
  ban (FLOW-056).
- Given the catalogue filter of FLOW-033, when the reader opens their own
  screen, then it lists their published recipes whatever position that filter is
  in, because this screen is not the catalogue; everything else they own stays in
  the own-recipes view (FLOW-034).
- Given a reader who has blocked that author, when they reach the screen, then it
  opens and lists the recipes, because blocking hides an author from catalogue
  and search and not from a screen the reader deliberately opened (ABUSE-005).
- Given the reader's own screen, when it is shown, then it offers neither
  reporting nor blocking of themselves, because both acts are aimed at somebody
  else's work; what a moderator is offered on their own screen is FLOW-056.
- Given an author whose account was deleted, when the reader activates what
  stands in place of the nickname (RET-003), then no screen opens and nothing
  about that account is disclosed.
- Given no network connection, when the screen is reached, then FLOW-003 governs
  it, because its listing is a catalogue view and no part of it is held on the
  device.

**Verification:** open the screen from a card, from a listing entry and from a
fork's named parent; open it as the author themselves, as someone who has
blocked them, as a moderator and for a deleted author; then cut the connection.
**Source:** user decision, 2026-09-21. Three requirements gave an act against an
author and none of them had a surface: ABUSE-005 lets anyone block one,
ABUSE-006 lets anyone report one and speaks of "an author's public
representation", and ACT-005 gives a moderator the ban. RET-003 had already
required the author to be shown through a layer that can say something other
than a nickname; this is that layer. It is not a profile — NGOAL-002 refuses the
social layer, and CON-001 leaves nothing to put on the screen but the nickname
and the work. See
[ADR — the author has a screen, and reports about them collapse onto it](../decisions/20260921-the-author-has-a-screen.md).
