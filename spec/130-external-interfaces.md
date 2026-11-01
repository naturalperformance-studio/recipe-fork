---
slug: EXT
status: closed
updated: 2026-11-01
ids: [EXT-001, EXT-005, EXT-009, EXT-010]
---

# External interfaces

What crosses the product's boundary. While the product is serverless almost
nothing does: the store distributes it, and a recipe file leaves the device in the
person's own hands (FLOW-063, FLOW-064). The services a server would need —
attestation, object storage, alerting, an availability probe, a deletion page —
are later (`35-server-later.md`).

### EXT-001 — The list of external dependencies is closed

The system shall depend on no external network service but the distribution
channel, and shall treat adding one as a product decision recorded as an ADR.

**Source:** user decision, 2026-09-20: every addition is a place where content or
an identifier can leave, and CON-001 is only as strong as the shortest list.
Narrowed to the channel alone with the server, 2026-11-01.

### EXT-005 — The distribution channel's obligations are traceable

Where the channel imposes obligations, the system shall satisfy them and the
specification shall name where each one is met; an obligation with no
counterpart here is raised as an open question, not implemented silently.

**Source:** CON-006.

### EXT-009 — The product sends nothing to a device

The system shall deliver no message to a device from outside it, and shall drive
every timer and announcement from the device's own clock and notification
facility.

**Source:** user decision, 2026-09-20: a delivery token is a per-device identifier
held by a third party, which CON-001 keeps out; and the one thing that must reach
the user on time — a timer — is a local alarm anyway (FLOW-013).

### EXT-010 — Nothing receives content or identifiers

The system shall send no recipe content and no device identifier to any service:
no advertising network, identity provider or analytics service receives anything
(NGOAL-006), and a crash or diagnostic report, if any leaves the device, carries
no recipe content. An exported file is handed to the operating system and the
product sends it nowhere.

**Source:** CON-001, NGOAL-006.
