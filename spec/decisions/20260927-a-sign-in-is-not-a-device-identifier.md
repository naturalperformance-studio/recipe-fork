# A sign-in is a server secret, not a device identifier

**Status:** accepted
**Context:** AUTH-015 forbids storing "a device identifier of any kind" and never
defined one. QUAL-009 counts every presentation of an account key against ten a
day per source, successful ones included, and AUTH-021 (3) signs out every device
holding an account. Together they make a device-held secret other than the account
key the evident way to build sign-in, and such a secret — one per device, presented
on every request — was read both as a device identifier and as not one (SD-035).
Nothing said how long its record may outlive the device.
**Decision:** A device identifier is anything derived from a device, its hardware
or its operating system, anything from a device verdict, and any identifier a
third party issues to a device (AUTH-015). A random secret the server mints at a
sign-in is not one. The server keeps a record of it from which the secret cannot
be rebuilt, and deletes the record after six calendar months unused; the device
then signs in again, silently, with the account key it holds, and if QUAL-009
refuses that sign-in the device stays signed in, behaves as with no connection and
tries again when the period has passed (AUTH-025).
**Consequences:** QUAL-009 and AUTH-021 stand as decided, and AUTH-015 now has a
test a data-model review can apply. Records of devices that were reset, lost or
moved to another account do not accumulate beyond six months, so the store does not
keep a lifetime tally of a holder's sign-ins. The cost is one scheduled deletion on
the server, and one of a source's ten daily attempts spent when a long-unused
device returns.
**Alternatives considered:**
- *The same secret, kept until the keys are replaced or the account is deleted.*
  Rejected: abandoned records accumulate without bound, and their number is a trace
  of how a person uses the product.
- *No secret: the account key presented on every request.* Rejected: QUAL-009 would
  exhaust a device within a day unless rewritten to count only the sign-in and
  recovery screens, which reopens a decision of 2026-09-21 and sends the account key
  over the wire on every call.
- *Signing a device out when its silent sign-in is refused by the limit.* Rejected:
  it punishes the holder for someone else trying keys from the same network.
