# A draft can be deleted; nothing that was ever visible can

**Status:** accepted; adds ST-010 and FLOW-053

**Context:** Two requirements already assumed an author could delete their own
unpublished recipe. MEDIA-010 names deleting an unpublished recipe as one of the
two ways to free the 150 MB allowance, and MEDIA-011 tells the author so at the
moment they hit the cap; MEDIA-012's first acceptance criterion is a draft being
deleted. Nothing anywhere specified the act: no requirement, no transition in the
recipe state machine, no row in the permission matrix, no control in any screen.

Without it the allowance has one exit instead of two, and MEDIA-011's message
names an action the product does not offer.

**Decision:** The owner may delete a recipe in `private` or in `rejected`. The
recipe and every submission record attached to it are destroyed; its media is
left to MEDIA-012, which deletes each file nothing else holds. The owner deletes
a recipe in no other state: `pending` is withdrawn first, and `link_shared`,
`published`, `reported_hidden` and `removed` were seen by someone other than the
owner and are out of their hands.

*Corrected 2026-09-21:* this paragraph, and ST-010's second criterion with it,
said that no recipe in any other state "can be deleted by anyone". RET-001 says
otherwise and has said so since 2026-09-20: deleting an account destroys every
recipe it owns that was never published, which reaches a `link_shared` recipe and
one that reached `removed` or `reported_hidden` without ever being published. The
rule this decision draws is about what the *owner* may delete recipe by recipe.
What nothing at all destroys is a recipe that was published (ST-001, RET-002).

**Consequences:**
- The unpublished allowance has the two exits MEDIA-011 already promises.
- A published fork of a deleted recipe is unaffected: FORK-005 already shows no
  parent for a parent that was never published, so the deletion is invisible to
  every reader.
- A rejection disappears with the recipe it was recorded against. That is
  consistent with MOD-004 — a decision belongs to a submission, not to a recipe —
  and with MOD-012, which takes a deleted account's entries out of the queue.
- The boundary is the same one CON-003 and ST-001 already draw. Deletion is not a
  second, weaker kind of permanence: it exists only where nothing external ever
  depended on the recipe.
- Deletion needs a connection, because the server orders every transition
  (ST-009) and a recipe deleted on the device alone would return at the next
  synchronisation.

**Alternatives considered:**
- *Nothing is ever deleted; publication is the only way to free the allowance.*
  Rejected by the product owner: an author who fills 150 MB with drafts they
  never meant to publish would have to publish them to keep writing, which turns
  a storage limit into a publication incentive and puts work in front of the
  moderator that was never meant for anyone.
- *A `pending` recipe can be deleted outright, in one action.* Rejected: the
  moderator may already be reading it, and MOD-011 and ST-008 settle that race
  for withdrawal only. Withdrawing first costs the author one action and leaves
  the ordering rule that already exists to decide who won.
