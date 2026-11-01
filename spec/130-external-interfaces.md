---
slug: EXT
status: closed
updated: 2026-09-28
ids: [EXT-001, EXT-002, EXT-003, EXT-004, EXT-005, EXT-006, EXT-007, EXT-008, EXT-009, EXT-010, EXT-011, EXT-012]
---

# External interfaces

What crosses the product's boundary, what the product may depend on, and what
each dependency does when it is not there. The rule behind all of it: a
dependency may degrade the product, and none may stop it from opening.

Four services are outside and are depended upon — a device attestation service,
an object storage service, the distribution channel, and one outbound alerting
channel that carries operational numbers to the product's operator
([`observability`](210-observability.md)). One observer is outside and is
depended upon by nothing: an availability probe that calls the product and is
never called by it (EXT-012). Two artefacts cross the boundary in the user's own
hands — the account key and a private link — and in serverless mode a third, the
recipe file (FLOW-063, FLOW-064), which the operating system saves or shares
where the person chooses and the product never sends anywhere. Three surfaces exist outside the
application because the distribution channel requires them — the account deletion
page, the terms of use and the privacy statement (EXT-006, LEG-008) — and one
endpoint exists for the probe to call (EXT-011), which no user ever reaches. One
further thing sits outside and is not a surface at all: the address a rights
holder writes to (ABUSE-007). The product never sends to it and never reads from
it, so it is no more a dependency than the probe is.

Prices and limits that were checked on 2026-09-20 and that the decisions rest on:
object storage without egress charges at USD 0.015 per GB-month
([R2 pricing](https://developers.cloudflare.com/r2/pricing)); a device
attestation free quota of 10 000 requests a day, with three bits of per-device
state whose write dates are recorded to the month
([Play Integrity device recall](https://developer.android.com/google/play/integrity/device-recall)).
Both are re-verified when this section is next touched if that date is more than
six months old.

### EXT-001 — The list of external dependencies is closed

The system shall depend on no external network service other than a device
attestation service, an object storage service, the distribution channel and one
outbound alerting channel, shall be observed from outside by one availability
probe it never calls (EXT-012), and shall treat the addition of another as a
product decision recorded as an ADR.

**Acceptance criteria**
- Given the released application, when its network destinations are enumerated,
  then each one belongs to the product's own server or to one of the four named
  services.
- Given the alerting channel, when what reaches it is inspected, then it is
  numbers and nothing else (OBS-004), and nothing it sends back acts on the
  product (OBS-005).
- Given any screen, when it is used, then no content is fetched from a service
  not on that list.
- Given the availability probe, when the product's outbound destinations are
  enumerated, then the probe is not among them, because the probe calls the
  product and the product never calls the probe.
- Given a proposed dependency not on the list, when it is considered, then it
  reaches the product only through a recorded decision.

**Verification:** traffic capture of a full session — first launch, authoring,
publication, cooking — checked against the list.
**Source:** user decision, 2026-09-20; widened to four on 2026-09-21 by
[ADR — one outbound alerting channel](decisions/20260921-alerting-channel.md),
and given an inbound observer the same day by
[ADR — availability is measured from outside](decisions/20260921-availability-is-measured-from-outside.md).
The list is closed rather than open because every addition is a place where
content or an identifier can leave, and CON-001 is only as strong as the shortest
list.
**Widened in advance, 2026-09-27:** when the import feature (not in the first
release) is built, the list gains TikTok's official oEmbed endpoint (IMPORT-008),
one language model service (IMPORT-010), and following a platform's short-link
redirection without reading the page (IMPORT-003). See
[ADR — where an import's text comes from](decisions/20260927-where-an-imports-text-comes-from.md).

### EXT-002 — The attestation service may be absent

Where the device attestation service cannot be reached or returns no verdict, the
system shall continue to operate as AUTH-004 requires and shall not prevent the
application from opening.

**Acceptance criteria**
- Given the service is unreachable, when the application is opened and used
  without an account, then everything that needs no account works (ACT-001).
- Given the service is unreachable while the product's own server is not, when an
  account is asked for, then a muted account is created (AUTH-004) and the
  application keeps working; given neither can be reached, then nothing is
  created (FLOW-003).
- Given the service is unreachable, when a muted account attempts an action that
  needs a verdict, then AUTH-006 states why it cannot proceed.
- Given the service is reached, when a verdict is obtained, then nothing from it
  is stored (AUTH-015).
- Given a day's usage at the expected load, when verdict requests are counted,
  then they stay within the service's free quota, because a verdict is requested
  only when an account is created or unmuted (AUTH-001, AUTH-005) and never on
  launch.

**Verification:** run account creation and unmuting with the service blocked,
then reachable; count requests over a simulated day.
**Source:** user decision, 2026-09-20. See
[ADR — account creation is gated by the device](decisions/20260920-account-creation-gated-by-the-device.md).

### EXT-003 — Object storage is used as a plain store

The system shall use the object storage service only to store, serve and delete
an object, and shall depend on no other capability of it.

**Acceptance criteria**
- Given the stored media, when the operations used against the service are
  enumerated, then they are storing, serving and deleting an object and nothing
  else.
- Given an object belonging to a recipe that is not visible to a requester, when
  that requester asks for it, then it is not served (DOM-005).
- Given a published recipe, when anyone requests its media, then it is served.
- Given a different provider offering the same three operations, when it replaces
  the current one, then no requirement in this specification changes.

**Verification:** review the operations used; attempt to fetch an unpublished
object as another account.
**Source:** user decision, 2026-09-20. The product owner is buying storage on
price (CON-002), and a product that uses a provider's particular features cannot
move when the price does.

### EXT-004 — Storage that cannot be reached costs media, not recipes

If the object storage service cannot be reached, then the system shall continue
to show recipe text, shall state that the media needs a connection, and shall
refuse the sending of a recipe carrying media until it can be reached, keeping the
recipe on the device as it is (FLOW-004, FLOW-059).

**Acceptance criteria**
- Given the service is unreachable, when a recipe is opened, then its text,
  ingredients, steps and timers work and only its media is absent.
- Given the service is unreachable, when the author adds a photograph, then it is
  held on the device as any media is until the recipe is sent (FLOW-004).
- Given the service is unreachable, when the author sends a recipe carrying media,
  then the sending is refused as FLOW-003 refuses it for an unanswering server,
  nothing of the recipe reaches the server, and the recipe keeps its mark
  (FLOW-005).
- Given the service is unreachable, when the author submits for moderation, then
  FLOW-006 refuses, because the server does not hold the content.
- Given the service returns, when the author sends the recipe again, then its
  media is uploaded; nothing is uploaded without the author sending.

**Verification:** block the storage host and walk reading, authoring and
submission.
**Source:** the product exists to be cooked from (GOAL-001), and a recipe is its
text; media is what makes it pleasant, not what makes it usable.

### EXT-005 — The distribution channel's obligations are traceable

Where the product is distributed through a channel that imposes obligations, the
system shall satisfy them and the specification shall name where each one is
implemented.

**Acceptance criteria**
- Given the release candidate, when the channel's obligations are enumerated,
  then in-app reporting of content and of authors and in-app blocking are present
  (CON-006, `abuse`).
- Given the release candidate, when they are enumerated, then an in-app path to
  delete the account and a web resource for the same are present (EXT-006,
  AUTH-017, `retention`).
- Given the store listing, when the data disclosure form is completed, then it
  names the deletion web resource.
- Given an obligation with no counterpart in this specification, when it is
  found, then it is raised as an open question rather than implemented silently.

**Verification:** obligation checklist against the release candidate.
**Source:** CON-006; the account deletion obligation was established
2026-09-20 from
[Google Play — app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).
Whether that obligation reaches an account the user never asked to create is
OQ-030.

### EXT-006 — The account deletion page takes a deletion key, never the account key

Where an account is deleted from outside the application, the system shall
accept the account's deletion key (AUTH-017) as text, shall accept nothing else
as proof, and shall delete the account immediately once the key matches.

**Acceptance criteria**
- Given the deletion page, when it is used, then the only thing it accepts is a
  deletion key typed or pasted as text, and it offers no way to upload a file.
- Given a deletion key that matches an account, when it is submitted, then that
  account is deleted at once, with no waiting period, no suspension and no way
  to undo it.
- Given the page, when it is shown, then it states before anything is submitted
  that deletion is immediate and irreversible.
- Given a deletion key that matches nothing, when it is submitted, then the page
  says so and deletes nothing.
- Given repeated submissions from one source, when they continue, then the page
  limits the rate at which keys may be tried to the figure of QUAL-007.
- Given the page, when it is read by someone holding no deletion key, then it
  discloses nothing about any account, any nickname or any recipe.

**Verification:** delete an account from the page with traffic captured; confirm
no file input exists; hammer the page with wrong keys and confirm the limit.
**Source:** user decision, 2026-09-20. Two alternatives were weighed and
rejected; see
[ADR — deletion from outside the application uses its own key](decisions/20260920-deletion-key.md).

### EXT-007 — A private link outside the application reveals nothing

When a private link is opened on a device without the application installed, the
system shall lead the person to the product's listing in the distribution
channel and shall disclose nothing about the recipe the link addresses.

**Acceptance criteria**
- Given a device with the application, when a private link is opened, then the
  application opens the recipe (CON-005).
- Given a device without the application, when the link is opened, then the
  person reaches the product's store listing.
- Given that page, when it is read, then it carries no title, no image, no
  author and no statement that the link addresses a recipe at all.
- Given a revoked link (ST-005), when it is opened without the application, then
  the page behaves exactly as for a live link and does not reveal that the link
  was ever valid.

**Verification:** open a live link and a revoked link on a device without the
application and compare what is disclosed.
**Source:** user decision, 2026-09-20. A private link addresses content its
author has not published, so a page that described it would hand that content's
existence to anyone who received or intercepted the link.

### EXT-008 — The account key is handed over and never managed

The system shall treat the account key as an artefact the user keeps by whatever
means they choose, shall depend on no storage provider for it, and shall accept
it from any source the device can read.

**Acceptance criteria**
- Given the account key, when it is issued, then the product offers no place to keep
  it and names none (AUTH-008).
- Given a key saved in any file storage the device can read, when it is
  imported, then it is accepted.
- Given any external storage provider, when the product's dependencies are
  enumerated, then none is among them.

**Verification:** enter a key copied as text, and one taken from a file and from a mounted cloud
folder.
**Source:** draft section 2.2; the product's promise is that the account depends
on nobody, which a recommended storage provider would quietly break.

### EXT-009 — The product sends nothing to a device

The system shall deliver no message to a device from outside it, and shall drive
every timer and every announcement from the device's own clock and its own
notification facility.

**Acceptance criteria**
- Given a timer, when it ends while the application is closed, then the
  announcement comes from the device itself (FLOW-013).
- Given the released application, when its registrations are enumerated, then it
  holds no registration with any message delivery service.
- Given a moderator's decision on a submission, when the owner is told about it,
  then they are told inside the application and nothing is pushed to them.

**Verification:** run a timer with the network disabled; enumerate the
application's registrations.
**Source:** user decision, 2026-09-20. A delivery token is a per-device
identifier held by a third party and matched to an account, which is precisely
what CON-001 keeps out of the product; and the one thing that must reach the user
on time — a timer — is a local alarm anyway.

### EXT-010 — Nothing receives content or identifiers

The system shall send no recipe content, no account identifier and no device
identifier to any service other than its own server and the object storage it
uses for that recipe's media.

**Acceptance criteria**
- Given the released application, when its traffic is captured, then no
  advertising network, identity provider or third-party analytics service
  receives anything (NGOAL-006).
- Given a crash or a diagnostic report of any kind, when it leaves the device,
  then it carries no recipe content and no account identifier.
- Given the alerting channel, when a message is sent to it, then it carries a
  signal's name and value and nothing else (OBS-004).
- Given the attestation service, when it is called, then it receives what the
  service itself requires and no recipe content.
- Given `analytics`, when its measurements are defined, then they obey this
  requirement rather than qualifying it.
- Given serverless mode, when a recipe is exported (FLOW-063), then the file is
  handed to the operating system and the product itself sends it to no service;
  where the person takes it is theirs.

**Verification:** traffic capture of a full session, including a forced crash.
**Source:** CON-001, NGOAL-006, and the decision of 2026-09-20 to gate accounts
by a device rather than by an identity precisely so that nothing personal exists
to send.

### EXT-011 — The health endpoint answers liveness and nothing else

The system shall expose one endpoint whose only answer is whether the server can
serve requests, shall require no account to reach it, and shall disclose nothing
else through it.

**Acceptance criteria**
- Given the endpoint, when it is requested by anyone at all, then the answer
  carries no count, no version, no identifier, no configuration value and no
  detail of any error.
- Given the server is running but cannot reach the store that holds recipes, when
  the endpoint is requested, then it does not answer as healthy, so that an
  answer cannot be hollow.
- Given the endpoint, when it is requested at any rate, then the work of
  answering it does not grow with the number of stored recipes, accounts or
  media objects.
- Given the application, when its screens are enumerated, then none of them
  reaches or names the endpoint; it exists for EXT-012 and for nothing else.

**Verification:** request the endpoint as an anonymous client and check the
answer against the list of what it may not carry; block the product's own store
and confirm the endpoint stops answering as healthy.
**Source:** user decision, 2026-09-21. See
[ADR — availability is measured from outside](decisions/20260921-availability-is-measured-from-outside.md).

### EXT-012 — Availability is observed from outside the product

The availability of the system shall be measured by a probe outside it, which
requests the endpoint of EXT-011 at a fixed interval no longer than five
minutes, records every result, and notifies the product's operator by its own
channel when the endpoint stops answering and again when it resumes.

**Acceptance criteria**
- Given the product is stopped entirely, when the probe next requests the
  endpoint, then the operator is notified by the probe's channel and not by the
  alerting channel of OBS-004, which sends nothing because there is nothing
  running to send it.
- Given a calendar month, when availability is computed for QUAL-006, then it is
  computed from the probe's own records and from nothing the product reported
  about itself.
- Given the probe is unreachable, misconfigured or stopped, when the product is
  used, then no behaviour of the product changes and no function a user reaches
  depends on it.
- Given the probe's notification, when what it carries is inspected, then it
  carries only that the endpoint stopped or resumed answering, and nothing about
  any recipe, any account or any nickname.
- Given the probe reports the product down while the product is answering other
  clients, when the month is computed, then that period counts as unavailable,
  because the probe's record is the definition and not an estimate of it.

**Verification:** stop the product and confirm the probe notifies within its
interval; block the probe and confirm a full session — reading, authoring,
publication — is unaffected.
**Source:** user decision, 2026-09-21. The interval is bounded rather than fixed
because five minutes is already finer than the 36 hours a month that QUAL-006
allows; a tighter interval buys resolution nobody needs and more chances for the
probe to be wrong about its own network path.
