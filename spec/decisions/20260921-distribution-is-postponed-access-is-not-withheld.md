# Distribution is postponed, access is not withheld

**Status:** accepted

**Context:** OQ-029 asked whether obtaining a device attestation verdict makes
the product an operator of personal data under the law of the product owner's own
jurisdiction, and what that obliges before release. The duties that follow in
that jurisdiction — notification before processing, localisation of citizens'
data, and the leak liability raised in 2025 — are what made the answer expensive
rather than academic, and the age mark required of a platform that hosts content
it does not classify in advance had been folded into the same review.

The product owner then settled the question from a different direction: the
product is aimed at Russian-speaking people living outside the Russian Federation
and at English speakers, and the Russian Federation is not a market the first
release goes into.

**Decision:** The first release is not offered through the distribution channel
in the Russian Federation. Distributing there is a separate decision, to be taken
on its own and recorded when it is taken. Meanwhile the product restricts access
to nobody: it derives no location from a network address, blocks none, and treats
a request from anywhere exactly as it treats any other (LEG-010).

**Consequences:**
- The governing legal frame for the first release becomes the law of the
  countries it is actually distributed in — predominantly the European and United
  Kingdom data protection regimes, given where the audience lives. That is a
  different review, not the absence of one, and OQ-029 is rewritten around it
  rather than closed.
- One thing gets easier and is worth recording: an IP address is personal data
  under that frame without argument, which is what RET-008's fourteen days were
  already chosen for. The reasoning that was cautious becomes the reasoning that
  is correct.
- The age mark obligation of the excluded jurisdiction does not apply to the
  first release. It returns in full with any decision to distribute there, which
  is one of the things that decision has to weigh.
- Nothing in the product changes for a person who installs it from outside the
  listed countries. The product owner stated plainly that people who reach it by
  their own means are welcome to; LEG-010 writes that down so that it is a
  requirement rather than an omission somebody later 'fixes'.
- `market-context` was written about a Russian-language market examined in
  English only. Its "not checked" list now matters differently, because the
  market the product launches into is not the one that section describes.

**Alternatives considered:**
- *Distribute everywhere and accept the risk.* Rejected by the product owner for
  the first release. It would have left the expensive obligations in force while
  the product has one author and no revenue of any kind (NGOAL-006).
- *Declare the product not aimed at that market without excluding it from the
  listing.* Rejected: it is a statement, not a mechanism. An application with a
  Russian interface, listed and installable there, collects users there
  regardless of who it was aimed at, and the obligations follow the users.
- *Block access by network address.* Rejected outright. It would require deriving
  a location from an address, which is precisely the kind of processing CON-001
  and RET-010 keep out of this product, and it would be aimed at people whose
  only act was installing an application.
