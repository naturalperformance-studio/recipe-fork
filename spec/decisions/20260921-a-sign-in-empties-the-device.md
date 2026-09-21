# A sign-in empties the device, including what no account held

**Status:** accepted; widens AUTH-012 and AUTH-013

**Context:** FLOW-051 lets a person with no account keep favourites and blocked
authors on the device. FLOW-052 says that when an existing account then signs in
there, those do not merge into it, and sends the reader to AUTH-012 for what
does happen. AUTH-012 deletes what "the previous account held" — and in this
case there is no previous account, so the one requirement that was supposed to
answer the question did not mention the case at all. A developer could
reasonably have left them on the device.

**Decision:** signing in deletes them with everything else. AUTH-012 reaches
what the device held with no account, and AUTH-013's statement names them before
anything is destroyed.

**Consequences:**
- AUTH-012's own promise stays true: a device sold or handed on carries nothing
  of whoever held it before. Left unwritten, the exception would have been the
  people who never made an account — the ones the product is most careful about
  everywhere else.
- Someone who used the product for months without an account and then signs in
  with a key loses a list they built. They are told first, in numbers, which is
  the same protection AUTH-013 already gives unsent drafts.
- Creating an account on that device still keeps them (FLOW-052). The two acts
  differ because one says "this device is mine and now has an account" and the
  other says "this device now belongs to a different account".

**Alternatives considered:**
- *Leave them on the device beside the account's own.* Rejected: the device grows
  a second, ownerless layer of favourites that belongs to nobody the product can
  name, and a resold phone shows its new owner the previous person's taste.
- *Merge them into the signing-in account.* Rejected by FLOW-052 before this
  question was asked: an account that already carries favourites from its own
  devices would silently acquire a stranger's.
