# A rating given without a connection is refused, not queued

**Status:** accepted

**Context:** every favourite is readable without a connection (FLOW-009), so the
rating controls on a held card are reachable in aeroplane mode — which is also
the mode the product is written for, since cooking happens where the network is
worst. The product already has both answers in it: FLOW-004 queues media added
offline and uploads it later, while FLOW-003 refuses submission, the catalogue
and every moderator action outright.

**Decision:** the controls stay visible offline, activating one states that a
connection is needed, and nothing is recorded or queued (RATE-012). The counts
shown are the ones from the last synchronisation, unmarked (RATE-013).

**Consequences:**
- A reader who cooks somewhere without a connection will not rate that recipe,
  because by the time they are back on the network they have stopped thinking
  about it. Some ratings are simply lost.
- No rating can arrive for a recipe that was removed or hidden while the device
  was offline, so no rating has to be discarded on arrival and no reader has to
  be told that the thing they did yesterday did not happen.
- The controls are visible although they refuse, which costs one wasted tap and
  buys a card that does not change shape when the network does.

**Alternatives considered:**
- *Queue the rating as FLOW-004 queues media.* Rejected by the product owner: a
  queued rating is a silent promise, and the recipe it is aimed at may be gone
  by the time the queue drains — the reader would either learn nothing or be
  told about a rating they no longer remember giving. Media is queued because
  refusing it would make the editor useless in the kitchen; a rating has no such
  cost.
- *Hide the controls entirely while offline.* Rejected: the card would differ
  from itself online by one more absent block, and the reader would not learn
  that rating exists on a recipe they only ever open offline.
