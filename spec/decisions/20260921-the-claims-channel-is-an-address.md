# The claims channel is an address, and nothing in the product watches it

**Status:** accepted; writes ABUSE-007, narrows LEG-008

**Context:** ABUSE-007 required a contact channel for claims about someone
else's content and ended by saying that "the exact channel and its legal wording
are specified in section `legal`". LEG-008 required the terms to "name the
claims channel of ABUSE-007". Each pointed at the other and neither said what
the channel was, so the one route a rights holder has into this product did not
exist in any requirement.

It is not an ornament. RET-005 tells an author deleting their account that a
rights holder's claim is the only way their published recipes can ever be taken
out of the catalogue, ST-001 makes that true, and MVP-003 holds the release
until the published texts exist. The audit of 2026-09-21 found the loop.

**Decision:** the channel is one electronic mail address, named in the terms of
use, in the privacy statement and in the store listing. It needs no account and
no installed application. A claim reaching it is answered within the period the
product already states for review (MOD-005), and is acted on through what the
product already has: the moderator removes the named recipe from its card, in
the category `foreign_content` (FLOW-047).

**Consequences:**
- A rights holder writes a letter, which is what they already know how to do and
  what their lawyers already send.
- Nothing is built. No page, no form, no screen for the moderator, no record in
  the store, no new state anywhere. An unfounded claim leaves no trace in the
  product at all.
- EXT-001's list stays closed at four. The product neither sends to the address
  nor reads from it; the operator does. It stands to the product as the
  availability probe does — related to it, depended on by nothing in it
  (EXT-012).
- The mailbox is the one place anybody's personal data reaches the product
  owner: a claimant writes their own name and contact into it. CON-001 binds
  what the product collects from its users and a rights holder is not one, so
  the constraint is untouched — but the privacy statement has to describe the
  mailbox (LEG-008), and how long its contents are kept is now inside the review
  OQ-029 waits for rather than a number invented here.
- **The stated period is kept by a person and by nothing else.** No operational
  signal watches the mailbox (OBS-001) and nothing in the application knows a
  claim exists, so an unread mailbox looks exactly like an empty one. This is the
  cost of choosing an address, and it is the one thing about this decision worth
  revisiting if claims ever arrive in numbers.

**Alternatives considered:**
- *A form on the web, beside the account deletion page.* Rejected. It is the
  technically cleaner answer — the claim lands in the product's own store, the
  moderator meets it where they already work, the age threshold of OBS-003 could
  reach it, and the retention rule would be the product's own. It costs a fourth
  public surface, a screen for the moderator, a retention rule and a place for
  spam to arrive at scale, and it asks a rights holder to use a form instead of
  writing. For a product with one operator the address wins on what has to be
  built; the form becomes the better answer the day the claims stop fitting in a
  mailbox.
- *Both an address and a form.* Rejected: two places for one person to watch,
  and the one that is checked less often is the one that fails silently.
- *Reporting inside the application only.* Rejected, and it is the reason
  ABUSE-007 exists: the owner of a stolen photograph is normally not a user of
  this product and cannot be required to install an Android application to
  complain about it.
- *Naming no period at all.* Rejected by the product owner: the product tells its
  own authors how long a review takes (MOD-005), and a claimant asking for
  somebody else's work to be removed is owed at least as much.
