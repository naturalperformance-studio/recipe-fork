# Availability is measured from outside the product

**Status:** accepted, widens the boundary of EXT-001

**Context:** The product owner set a monthly availability target of 95 per cent
(QUAL-006). A target with no verification method is not a requirement, and the
product cannot verify this one about itself. The only outbound channel it has is
the alerting channel of OBS-004, which carries a signal's value from the running
product to its operator; OBS-006 already accepts that a channel which cannot be
reached costs alerts and nothing more. The failure being measured — the server
is not answering — is exactly the failure that stops any measurement taken
inside the product from leaving it. Silence on the alerting channel is therefore
indistinguishable from health, and no arrangement of internal signals changes
that.

The product owner also weighed the cost against the budget: an external probe
sufficient for one host costs nothing in money (CON-002 is untouched), so the
price is paid entirely in the length of the dependency list, which EXT-001 keeps
short deliberately.

**Decision:** One availability probe outside the product requests a single
health endpoint at a fixed interval, records each result, and notifies the
product's operator through its own channel when the endpoint stops answering.
The product exposes that endpoint (EXT-011) and discloses nothing through it.
The probe is an observer, not a dependency: it calls the product, the product
never calls it, and the product behaves identically whether the probe exists, is
misconfigured or is itself down.

**Consequences:**
- QUAL-006 gains a verification method it can actually be held to: the probe's
  own monthly record, produced by something that keeps working while the product
  does not.
- The operator gains a second channel that can wake them, separate from OBS-004
  and with its own failure modes. Two channels mean two places to look and two
  things that can cry wolf.
- A health endpoint is reachable from the internet by anyone, with no account.
  It becomes a thing to keep silent: EXT-011 requires it to answer with liveness
  and nothing else, because an endpoint that reports counts or versions is a
  disclosure surface that no user ever asked for.
- The probe can be wrong in both directions. It can call the product down when
  its own network path is broken, and it cannot see a product that answers the
  endpoint while every real operation fails. The endpoint is therefore specified
  to depend on the store the product actually needs, so that a hollow answer is
  not possible.
- EXT-001's list of what sits on the boundary grows by one entry for the second
  time, after the alerting channel of 2026-09-21. The list is still closed.

**Alternatives considered:**
- *No measurement at all; availability stated as a design intention.* Rejected
  by the product owner. It was a real option — nothing in the product breaks
  without it — but it would have left reliability as the one quality
  characteristic with no requirement under it, and left the 95 per cent figure as
  a sentence nobody could ever be held to.
- *The product measures its own availability and reports it through OBS-004.*
  Rejected on the argument above: the measurement dies with the thing it
  measures. An internal uptime counter can only ever report the periods during
  which there was nothing to report.
- *The product owner checks by opening the application.* Rejected. It measures
  when the owner happened to look, not when the product was down, and it produces
  no record that a monthly figure could be computed from.
- *A second server checking the first.* Rejected against CON-002 and against
  itself: a second machine bought to watch the first is a second machine to keep
  alive, and its own downtime becomes indistinguishable from the downtime it is
  supposed to be reporting.
