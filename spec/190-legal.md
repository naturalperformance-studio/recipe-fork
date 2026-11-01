---
slug: LEG
status: closed
updated: 2026-11-01
ids: [LEG-007, LEG-008, LEG-009, LEG-010]
---

# Legal

What the product declares about who it is for, where it is distributed, and the
texts it publishes. What publishing grants and accepting terms of use belong to a
server and are later (`35-server-later.md`). The published name is not yet cleared (OQ-016), and
what data protection law obliges in the countries distributed in needs a lawyer
(OQ-029); both hold up the store listing, not this section.

### LEG-007 — The product declares itself not directed to children

The system shall be declared in the distribution channel as not directed to
children; any change to that declaration is a product decision recorded as an ADR,
because it changes which policies bind.

**Source:** user decision, 2026-09-21.
[Google Play — target audience and app content](https://support.google.com/googleplay/android-developer/answer/9867159?hl=en),
checked 2026-09-21.

### LEG-008 — The privacy statement is reachable from outside the application

The system shall publish its privacy statement at an address reachable without
the application; it describes what the product does not collect (CON-001).

**Source:** CON-006 and user decision, 2026-09-21.

### LEG-009 — The published name is cleared before the listing is created

The published name shall be cleared in the distribution channel and against
trademark registers in the owner's jurisdiction before the store listing is
created. `Recipe Fork` is a working title.

**Source:** OQ-016.

### LEG-010 — Distribution is limited; access is not

The first release shall not be offered through the distribution channel in the
Russian Federation, and the system shall not restrict, degrade or refuse any
function on the basis of a user's location; nothing derives a location at all.

**Source:** user decision, 2026-09-21: where the product is offered is the owner's
choice in the channel; where it is reachable from is not something it polices. See
[ADR — distribution is postponed, access is not withheld](decisions/20260921-distribution-is-postponed-access-is-not-withheld.md).
