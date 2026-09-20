---
slug: EXT
status: closed
updated: 2026-09-20
ids: [EXT-001, EXT-002, EXT-003, EXT-004, EXT-005, EXT-006, EXT-007, EXT-008,
  EXT-009, EXT-010]
---

# External interfaces

What crosses the product's boundary, what the product may depend on, and what
each dependency does when it is not there. The rule behind all of it: a
dependency may degrade the product, and none may stop it from opening.

Three services are outside and are depended upon — a device attestation service,
an object storage service and the distribution channel. Two artefacts cross the
boundary in the user's own hands — the account key and a private link. One surface
exists outside the application because the distribution channel requires it — the
account deletion page.

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
attestation service, an object storage service and the distribution channel, and
shall treat the addition of another as a product decision recorded as an ADR.

**Acceptance criteria**
- Given the released application, when its network destinations are enumerated,
  then each one belongs to the product's own server or to one of the three named
  services.
- Given any screen, when it is used, then no content is fetched from a service
  not on that list.
- Given a proposed dependency not on the list, when it is considered, then it
  reaches the product only through a recorded decision.

**Verification:** traffic capture of a full session — first launch, authoring,
publication, cooking — checked against the list.
**Source:** user decision, 2026-09-20. The list is closed rather than open
because every addition is a place where content or an identifier can leave, and
CON-001 is only as strong as the shortest list.

### EXT-002 — The attestation service may be absent

Where the device attestation service cannot be reached or returns no verdict, the
system shall continue to operate as AUTH-004 requires and shall not prevent the
application from opening.

**Acceptance criteria**
- Given the service is unreachable, when the application is opened and used
  without an account, then everything that needs no account works (ACT-001).
- Given the service is unreachable, when an account is asked for, then a muted
  account is created (AUTH-004) and the application keeps working.
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
keep media added meanwhile on the device (FLOW-004).

**Acceptance criteria**
- Given the service is unreachable, when a recipe is opened, then its text,
  ingredients, steps and timers work and only its media is absent.
- Given the service is unreachable, when the author adds a photograph, then it is
  held on the device and the recipe is marked as not accepted by the server
  (FLOW-005).
- Given the service is unreachable, when the author submits for moderation, then
  FLOW-006 refuses, because the server does not hold the content.
- Given the service returns, when the application next reaches it, then the
  queued media is uploaded without the author asking.

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
  limits the rate at which keys may be tried (AUTH-017).
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
- Given the attestation service, when it is called, then it receives what the
  service itself requires and no recipe content.
- Given `analytics`, when its measurements are defined, then they obey this
  requirement rather than qualifying it.

**Verification:** traffic capture of a full session, including a forced crash.
**Source:** CON-001, NGOAL-006, and the decision of 2026-09-20 to gate accounts
by a device rather than by an identity precisely so that nothing personal exists
to send.
