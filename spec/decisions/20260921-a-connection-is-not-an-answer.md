# A connection is not an answer

**Status:** accepted; widens FLOW-003

**Context:** FLOW-003 is the closed list of what the product refuses, and every
one of its criteria began "Given no network connection". The product runs on one
server with no redundancy, and QUAL-006 accepts that it is unavailable for up to
about 36 hours a month — so the state where the device is online and the server
is not answering is not an edge case, it is the state QUAL-006 budgets for. It
was specified in exactly two places: FLOW-043 for a listing that fails to load,
and RATE-014 for a rating that cannot be recorded. Submission, account creation,
signing in, deleting a recipe, revoking a link and every moderator action said
nothing about it, so each implementer would have invented an answer, and the
likeliest invention is the message that is already written — "a connection is
needed" — said to somebody whose connection is fine.

**Decision:** FLOW-003 covers both. The refusals are the same and nothing is
queued in either case; what differs is the statement. No connection: a
connection is needed. A connection but no answer: the product could not be
reached, try again.

**Consequences:**
- The person is told which of the two things to do — restore a connection, or
  wait — and the product stops blaming their phone for its own downtime.
- Two statements instead of one, in both languages (L10N-001). That is the whole
  cost, and it buys the one thing an error message is for.
- ST-009 already guarantees the other half: a refused act leaves nothing
  half-done at the server, so retrying is always safe.

**Alternatives considered:**
- *One statement for both cases.* Rejected: it is fewer strings and it is wrong
  half the time. A reader told to check their connection while their connection
  works will go and fix a phone that is not broken.
- *Queue the act and replay it when the server returns.* Rejected: it is the
  decision the product already refused for ratings
  ([ADR — a rating is not queued](20260920-rating-is-not-queued.md)) and for every
  transition, because the server orders them (ST-009) and a queued act aimed at a
  recipe that has since changed state is a promise the product cannot keep.
