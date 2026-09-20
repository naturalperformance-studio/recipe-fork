# Account creation is gated by the device, not by an identity

**Status:** accepted; narrows ACT-001

**Context:** an account was created on first launch with nothing asked and
nothing verified (ACT-001). That kept CON-001 absolute, and it also made an
account free. ABUSE-001 caps submissions at three per day **per account**, so
with unlimited accounts the real cap on submissions into a queue that one
moderator works is unlimited. RATE-005 counts a rating from every account, so
the same applies to score.

The product owner raised this on 2026-09-20, having taken the key file model from
a browser game whose save file holds the whole state. That analogy does not carry:
there the server stores nothing, so an extra player costs the operator nothing,
while here the server stores recipes, media and the attention of one moderator.

The mass-report vector the product owner feared was already closed by ABUSE-003,
which counts a report toward automatic hiding only from an account that has
published something. What was open was the submission queue and score.

**Decision:** creating an account requires a verdict from a device attestation
service, and consumes one of three slots the service keeps per device. A slot is
free again six calendar months after the month it was occupied (AUTH-001,
AUTH-002). Importing a key file consumes no slot (AUTH-009). Where no verdict can
be obtained, an account is still created but is muted: it may read, cook and keep
favourites, and may not create, fork, edit, submit or rate, while reporting stays
available (AUTH-004). A muted account becomes full the moment a verdict and a
free slot allow it (AUTH-005). Nothing from a verdict is stored (AUTH-015).

The mechanism exists on both mobile platforms and needs no personal data: on
Android, Play Integrity with device recall — three bits per device with
month-granularity write dates, held by Google, surviving reinstallation and a
device reset, free within 10 000 integrity requests a day; on Apple, DeviceCheck
gives two bits and a last-update date through the same server-to-server shape.
Checked 2026-09-20:
[device recall](https://developer.android.com/google/play/integrity/device-recall),
[integrity verdicts](https://developer.android.com/google/play/integrity/verdicts),
[DeviceCheck](https://developer.apple.com/documentation/devicecheck/accessing-and-modifying-per-device-data).

**Consequences:**
- An attack on the moderation queue now costs devices rather than minutes.
- CON-001 and NGOAL-001 are untouched: the product learns no device identifier
  and stores no verdict. This is the reason this route was taken over an identity
  provider.
- The product depends on an external service at the moment an account is created,
  and on Android that service's recall feature is in beta. AUTH-004 is what keeps
  an outage from being a dead application.
- A sideloaded build, an emulator and a device without the store's services get
  muted accounts. For the MVP this is acceptable: CON-005 makes the store the only
  distribution channel.
- A person who loses their key file can start again on their own phone, but at
  most three times in six months.
- A second-hand device may arrive with slots occupied by its previous owner. The
  six-month rule is what eventually clears them.
- Whether receiving such a verdict makes the product an operator of personal data
  under the product owner's law is not settled here: OQ-029.

**Alternatives considered:**
- *Leave account creation free and handle abuse downstream.* Rejected by the
  product owner: ABUSE-001's cap is per account, so the only real limit on the
  moderator's queue was the attacker's patience.
- *Sign in with Google.* Rejected on price rather than on principle. An opaque
  Google subject identifier is personal data under GDPR Recital 26, and under
  152-ФЗ it relates to an indirectly identifiable person, which makes the product
  owner an operator: notification of Roskomnadzor before processing begins,
  localisation of Russian citizens' data in databases inside Russia (242-ФЗ), and
  leak liability that since 30 May 2025 starts in the millions of roubles. The
  whole economy of CON-002 rests on there being nothing to localise and nothing
  to leak. Checked 2026-09-20.
- *One account per device, forever.* Rejected: draft section 2.3 promises that a
  person who loses their key file can fork their own published recipe from a new
  account, and on their own phone they never could again.
- *Seven accounts per device for its lifetime, never freed.* Rejected for the same
  reason, deferred by a factor of seven, and it leaves a resold device permanently
  exhausted.
- *Refuse to start without a verdict.* Rejected: an outage at the attestation
  service would make the application dead for every new user, and the product
  owner would learn of it last.
