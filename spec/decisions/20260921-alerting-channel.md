# One outbound alerting channel, carrying numbers only

**Status:** accepted; widens EXT-001

**Context:** EXT-001 closed the list of external dependencies at three services
and required that any addition be a recorded decision. Observability then hit the
gap that closure left: the product has no way to tell its operator anything. It
sends nothing to a device (EXT-009), holds no contact detail for anybody
(CON-001), and its operator is one person who is also its moderator. Without a
channel, the storage ceiling of CON-002 is learned about when the application is
next opened, which may be days after it mattered.

The product owner proposed a messaging bot on 2026-09-21, on the grounds that
they have built a far more complicated one before and that a notification-only
bot costs essentially nothing to run.

**Decision:** the dependency list becomes four. A single outbound alerting
channel receives one message when a signal crosses its threshold (OBS-004). Two
restrictions make it compatible with what the rest of the specification
guarantees: a message carries a signal's name and its value and nothing else, so
no recipe content, nickname or identifier ever reaches it (EXT-010); and the
channel is one-way, so nothing arriving on it can act on the product (OBS-005).
When it cannot be reached, alerts are abandoned and the moderator's screen
remains the record (OBS-006).

**Consequences:**
- The operator learns about a crossed threshold when it happens rather than when
  they next look.
- A third party now receives a stream of aggregate numbers about the product.
  Nothing in it is about a person, so OQ-029 is untouched, but the service can
  see how large the product is and how busy its queue is.
- Whoever obtains the channel can read past alerts. Because it is one-way, that
  is the whole of what they get.
- The product carries a credential for that service. It is the second secret the
  server holds, after the deletion key verifiers.
- EXT-001's list is now four, and it stays closed at four by the same rule.

**Alternatives considered:**
- *No alerting at all; the signals live only on the moderator's screen.* The spec
  author's recommendation, rejected by the product owner: the two signals that
  matter most — the storage ceiling and a queue that stopped moving — are
  precisely the ones nobody looks at while nothing is wrong.
- *A local notification on the moderator's own device, triggered by the
  application itself.* Rejected although it adds no dependency: it only fires
  while that one device is reachable and the application is installed on it, and
  it puts an operational duty on a phone.
- *Electronic mail.* Rejected: it needs a sending service, an address to send to,
  and an address is the one kind of personal data the product has refused
  everywhere else.
- *A bot that also accepts commands.* Rejected as a second control surface for
  the product, outside everything `auth` exists to protect.
