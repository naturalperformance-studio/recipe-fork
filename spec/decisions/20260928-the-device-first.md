# The device first: a recipe lives on the device, and reaches the server only when sent

**Status:** accepted — supersedes the parts of
[an account is created on demand](20260920-account-on-demand.md) that made writing
and forking actions requiring an account, and the automatic sending of
FLOW-002 (4), FLOW-004 and AUTH-005 (5).

**Context:** the product owner expects to stop paying for the server soon if the
product does not take off, and asked on 2026-09-28 that the first release stay
useful with no server at all. Before this decision a person with no account could
cook from favourites and nothing else: writing and forking required an account,
and an account cannot be created without the server (FLOW-003, AUTH-014). A person
installing the application after the server was gone would have found nothing to
read and nothing they were allowed to write.

**Decision:**
- Anyone, with or without an account and with or without a server, writes, edits,
  forks, deletes and cooks from recipes on the device. An account and the server
  are needed only for what is shared by nature: sending a recipe to the server,
  submission for moderation, publication, private links, rating, and carrying the
  recipe book beyond the device.
- Nothing is sent to the server automatically. The owner sends one particular
  recipe by an explicit act. Sending gives three things: a copy the server keeps,
  the recipe on every other device the account is signed in to, and the
  possibility of submitting it.
- A recipe that was sent and is still editable is edited on the device in place;
  the next sending replaces the server's copy. A recipe that is externally visible
  is changed only by a fork, as before (GOAL-002).
- The 150 MB allowance of MEDIA-010 counts the media of recipes that were sent
  and are not published. What stays on the device is bounded by the device alone.
- The favourites section is named "My recipe book" in the interface: one place
  holding the recipes the person wrote and the recipes they found. The found
  recipes of an account keep reaching the server by themselves (FLOW-052); they
  are references, not recipes the person wrote.
- The setting that governs whether media is held on the device stays, and an
  action that deletes held media from the device is added beside it.

**Consequences:**
- The first-release core — writing, forking, scaling, timers, the recipe book —
  works every day without a server, so the state the product is left in when the
  server is switched off is the state it was tested in, not a mode nobody ran.
- The threshold for an author falls to nothing: no account, no slot, no nickname
  until the recipe is sent.
- A recipe that was never sent is lost with the device. The product says so on
  the recipe (FLOW-005), not in general terms.
- A muted account (AUTH-004) differs from a full one only in what uses the
  server; everything it did "on this device only" is now what everyone does.
- Deleting a recipe the server never held is a local act and needs no connection;
  deleting one the server holds still does (ST-010).
- The backlog of the first wave, which was cut around "writing asks for an account
  first", has to be cut again.

**Alternatives considered:**
- A mode entered only after the server is switched off, with everything as before
  until then. Rejected: the mode would be code nobody runs until the day it is
  needed, and it needs a reliable signal that the server is gone for good, which
  the application cannot obtain by itself.
- Keeping automatic sending and adding a serverless mode on top. Rejected by the
  product owner: drafts are not the server's to keep unless the author asks, and
  what the author has not sent is exactly what survives the server.
- Every edit of a sent recipe creating a fork. Rejected: editing a backup would
  multiply recipes; the fork is for what another person can already see.
