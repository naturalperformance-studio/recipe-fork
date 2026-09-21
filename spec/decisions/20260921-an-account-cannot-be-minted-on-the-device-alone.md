# An account cannot be minted on the device alone

**Status:** accepted; narrows AUTH-004, widens FLOW-003

**Context:** AUTH-004 creates a muted account where a device verdict cannot be
obtained, and AUTH-005's first acceptance criterion spoke of "a muted account
created while offline". FLOW-003, which is the closed list of what the product
refuses without a connection, did not mention account creation at all, while
QUAL-006 asserted that account creation states a connection is needed. Three
requirements, three readings.

An account is not local state. Its nickname is unique across every account that
has ever existed (DOM-001) and is fixed at creation (AUTH-018); its three keys
are issued once and tested by the server, which must hold nothing from which any
of them could be reconstructed (AUTH-014). None of that can be produced on a
device that cannot reach the server.

**Decision:** Creating an account requires a connection to the product's own
server, and joins the list FLOW-003 refuses without one. A muted account covers
exactly one case: the server answers and the attestation service does not.

**Consequences:**
- QUAL-006 was already right and needs no change.
- The muted account keeps the purpose it was created for — an outage at the
  attestation service does not stop a reader taking their favourites beyond the
  device — and loses a meaning it could never have had.
- A person with no connection keeps everything ACT-001 gives them: browsing what
  the device holds, cooking, timers, favourites on the device. What they cannot
  do is begin an account, and they are told why rather than left with a control
  that does nothing.
- EXT-002 now distinguishes the two outages it was silently conflating.

**Alternatives considered:**
- *An account is created locally and is issued its nickname and keys at the first
  contact with the server.* Rejected. AUTH-007 does not complete creation until
  the person has confirmed they saved a key the product would not yet have; the
  nickname could not be guaranteed unique until the server saw it, so AUTH-018's
  promise that the accepted name is permanent would hold only provisionally; and
  AUTH-020 shows the recovery key once, which cannot be done at a moment the
  person is not looking. It would have meant rewriting the whole of `auth` to buy
  account creation in aeroplane mode.
