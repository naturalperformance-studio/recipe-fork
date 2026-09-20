---
slug: AUTH
status: blocked
updated: 2026-09-20
ids: []
---

# Accounts and the account key

Most people who use this product never need an account. Browsing, searching,
opening a recipe, cooking from it, running its timers, keeping favourites on the
device and reporting something all work without one. An account is what an
author needs, and what a reader chooses if they want their favourites to survive
the phone.

An account carries no personal data (CON-001) and is held by a key its holder
keeps. Nothing here recovers an account: NGOAL-003 is a refusal, not a missing
feature.

Two things gate an account rather than identify its holder. The first is the
key: whoever holds it holds the account. The second, decided 2026-09-20, is the
device: creating an account costs a slot on the device it is created from, and a
device has three. The gate exists because an account was otherwise free, and a
free account meant an unbounded number of submissions into a queue one moderator
works (ABUSE-001 caps submissions per account, not per person). Creating an
account on demand rather than at first launch narrows the same problem from the
other end: a person who only cooks never mints one. See
[ADR — account creation is gated by the device](../decisions/20260920-account-creation-gated-by-the-device.md)
and
[ADR — an account is created on demand, by the people who need one](../decisions/20260920-account-on-demand.md).

The gate is an attestation service: the client obtains a verdict, the server
checks it, and the service holds a few bits of state per device that survive
reinstalling the application and resetting the device. The product never learns
a device identifier and stores no part of the verdict (AUTH-015). The interface
itself belongs to [`external-interfaces`](../130-external-interfaces.md).

Whether obtaining such a verdict makes the product an operator of personal data
under the product owner's own law is OQ-029 and is not answered here.

**Not in this section:** what the key contains and how the server tells a
genuine one from a forgery — that is OQ-002, still open, which is why this
section is `blocked` and AUTH-014 carries no behaviour yet.

This section is split into the parts below. Requirement identifiers did not
change on the split and never will.

| Part | What is in it | Requirements |
|---|---|---|
| [10-creating-an-account.md](10-creating-an-account.md) | When an account appears, the device slots, the nickname, and the muted account | AUTH-001–AUTH-006, AUTH-018 |
| [20-key-file.md](20-key-file.md) | The account key and the deletion key: issuing, keeping, showing, importing | AUTH-007–AUTH-010, AUTH-017, AUTH-019 |
| [30-account-life.md](30-account-life.md) | One active account, what replacing it destroys, losing the key | AUTH-011–AUTH-016 |
