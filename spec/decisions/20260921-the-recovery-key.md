# The recovery key, and what it does to "no recovery"

**Status:** accepted; narrows NGOAL-003

**Context:** An account was held by one secret. If that secret leaked, its holder
could do nothing: the thief could publish under their nickname for ever, and the
only escape was deleting the account, which destroys everything unpublished and
leaves the published recipes standing under that same nickname (ST-001, RET-002).

The first answer drafted was to let a signed-in account be issued a new key. The
product owner rejected it, correctly: the operation is symmetric, so it is a race
between two parties holding the same secret, and the thief wins it every time —
they know they have access and act at once, while the rightful holder does not
know anything has happened. Without recovery a leaked key gives a *shared*
account; with a symmetric replacement it gives a *stolen* one, permanently. The
mechanism meant to answer theft would have handed the account over in one action.

The root cause is that the account has one authenticator and no identity behind
it (CON-001), so the server cannot tell two holders of one secret apart. Nothing
symmetric can fix that. A delay with a right of cancellation does not: the thief
can cancel too, and the holder would only learn of it by opening the application,
since nothing is delivered to a device (EXT-009). The deletion key does not
either: AUTH-017 carries it inside the account key's own file and shows it on the
account screen, so whoever holds the account key holds it as well.

A user-chosen passphrase was considered and set aside by the product owner:
chosen phrases are weak and forgotten.

**Decision:** An account carries three keys. The account key signs in. The
deletion key destroys the account from outside the application. The recovery key
is issued at creation as its own artefact, shown once, never displayed again by
the application, and is the only thing that can retire an account key
(AUTH-020, AUTH-021). Presenting it stops the old account key, deletion key and
recovery key from being accepted, signs out every device, and issues a new set.

The asymmetry that defeats the race is that the recovery key existed before the
theft and is kept somewhere else. A thief holding the account key file does not
have it and cannot obtain it from any screen.

**Consequences:**
- NGOAL-003 is narrowed but its reason is untouched. Nobody can restore an
  account on the holder's behalf, there is no support channel that could, and the
  product still stores nothing that would let it. What changes is that a holder
  who kept their recovery key can retire a lost or stolen account key themselves.
- The product now asks a person to keep two artefacts, in two places, at the
  moment they create an account. If they keep both in one place the scheme buys
  them nothing, and the product can say so but cannot enforce it.
- The recovery key is as valuable as the account key: whoever holds it can take
  the account. Two things to keep safe instead of one.
- It does not stop a thief destroying the account, because the deletion key
  travels with the account key and EXT-006 is deliberately easy to use. Published
  recipes survive that (RET-002); unpublished work does not.
- Retiring the keys empties every device signed in to the account, including one
  the rightful holder is using, and unsent work on it is lost. There is no way to
  warn that device first: nothing reaches a device from outside it (EXT-009).
- It contradicts AUTH-019's reasoning for one key on purpose. A key that can
  never be seen again is lost with the note it was written on — that is exactly
  the cost, and it is paid because a recovery key visible to a signed-in session
  is a recovery key the thief reads first.
- It cannot be added later. An account created before this exists would have no
  recovery key, and one could not be granted afterwards without an authenticated
  path to grant it, which is the problem this solves. It is therefore a decision
  for the first release or never.

**Alternatives considered:**
- *No replacement at all.* Rejected. It was coherent and cost nothing to build,
  and it left a leaked key as a permanently shared account with deletion as the
  only exit.
- *Symmetric replacement from a signed-in session.* Rejected by the product
  owner for the reason above; it is strictly worse than doing nothing.
- *Replacement after a delay, cancellable with the old key.* Rejected: symmetric
  again, and a holder learns of the attempt only by opening the application.
- *A user-chosen passphrase set at creation.* Rejected by the product owner: a
  generated key is not chosen badly and not invented under pressure. It would
  have been stronger against a leaked file, since a remembered phrase sits in no
  file at all, and that is the one advantage given up here.
- *Requiring the recovery key to delete an account too.* Rejected: EXT-006 is
  deliberately a low-friction path because the distribution channel obliges the
  product to offer one.
