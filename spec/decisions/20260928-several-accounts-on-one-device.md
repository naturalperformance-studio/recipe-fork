# Several accounts on one device, each with its own content

**Status:** accepted — supersedes
[replacing the account wipes the device](20260920-replacing-the-account-wipes-the-device.md)
and [a sign-in empties the device](20260921-a-sign-in-empties-the-device.md); closes
OQ-028.

**Context:** AUTH-011 held one account on a device and AUTH-012 wiped everything
the previous account held when another key signed in. Once recipes live on the
device first ([the device first](20260928-the-device-first.md)), wiping would destroy
the only copy of every recipe the author never sent. The product owner's case on
2026-09-28: two people cooking from one phone, each with their own key.

**Decision:**
- A device holds any number of accounts that have signed in on it. The content each
  one holds on the device — its recipes, its unsent changes, its recipe book, its
  blocked authors — is kept apart from every other account's and is shown only
  while that account is the active one.
- Accounts signed in on the device are listed, and the active one is switched by
  picking another from the list. Switching deletes nothing.
- What is made while no account is active — recipes, found recipes, blocked authors
  — belongs to no account and is kept in one pool on the device.
- The first time each account becomes active on the device while the pool holds
  anything, the product offers once to move the pool into that account. Accepted,
  the pool becomes that account's. Declined, the pool stays where it is and is not
  shown in that account; a menu action makes the same offer later.
- A key already used on this device opens that account's content on the device
  even when the server cannot be reached.
- How the content is kept apart on the device is the implementation's choice; the
  product owner's sketch was one folder per account named by a digest of its key.

**Consequences:**
- The promise that a device handed on carries nothing of the previous holder is
  gone. Whoever holds the phone reaches every account listed on it. An action that
  removes an account and its content from the device takes its place.
- Everything the device holds is multiplied by the number of accounts on it —
  the reason OQ-028 had been deferred.
- The device slots of AUTH-002 are unchanged: they bound how many accounts a device
  creates, not how many it holds.

**Alternatives considered:**
- Everything local belongs to the device, not to an account; an account is only a
  key for sending. Simpler, proposed on 2026-09-28, and not chosen by the product
  owner, who wanted each person's book to be their own.
- Separate content per account, merged into one pool when the server is switched
  off. Rejected: the separation is already there, and a merge has to decide what a
  recipe in two books is.
- Switching by entering the key every time. Rejected: a key is long, and the case
  is two people sharing one phone, who would reach each other's books anyway.
- Asking about the pool at every sign-in, or once and never again. Rejected for
  one offer per account with a menu action behind it.
