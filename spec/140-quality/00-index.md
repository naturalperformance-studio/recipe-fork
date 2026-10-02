---
slug: QUAL
status: closed
updated: 2026-09-27
ids: []
---

# Quality attributes

Every requirement here carries a metric, a method of measuring it and a
threshold. A quality statement without all three is not in this section.

One limitation governs the whole of it, and it is a consequence of a decision
taken deliberately elsewhere: RET-010 and OBS-007 forbid measuring anything about
a person, so the product collects no timings from real devices in real hands.
Every threshold below is therefore verified on a bench, against the reference
configurations of QUAL-001, before a release — and a device that is slower than
the bench says nothing to anyone. The thresholds are set with that in mind: they
are what the product must clear where it can be observed, not an estimate of what
a stranger's phone does.

Two requirements are not bench measurements and say so where they stand.
Availability is the one attribute the product cannot verify about itself at all,
because the failure it measures is the failure that silences the measurement; it
is observed from outside instead (EXT-012). And QUAL-010, the only commitment
this section makes to a person rather than to a device, is computed after the
fact from what the store already holds — no bench can measure how long a human
took to read something.

## Parts

This section is split into the parts below. Requirement identifiers did not
change on the split and never will.

| Part | What is in it | Requirements |
|---|---|---|
| [10-time-behaviour.md](10-time-behaviour.md) | The two reference configurations and the response times on each | QUAL-001, QUAL-002, QUAL-003, QUAL-004 |
| [20-compatibility.md](20-compatibility.md) | The lowest Android version and the narrowest supported window | QUAL-005, QUAL-011 |
| [30-reliability.md](30-reliability.md) | Availability and the hourly copy of the store | QUAL-006, QUAL-008 |
| [40-security.md](40-security.md) | The rates at which deletion, account and recovery keys may be tried | QUAL-007, QUAL-009 |
| [50-commitment-to-a-person.md](50-commitment-to-a-person.md) | The time a moderation decision takes | QUAL-010 |

## Where the other quality characteristics live

Characteristics of ISO/IEC 25010 that carry no requirement in this section are
not thereby unaddressed; each is specified as behaviour elsewhere, or is
deliberately not committed to. This table exists so that the difference is
visible.

| Characteristic | Where it is specified |
|---|---|
| Functional suitability | The functional sections; the exact rounding of [`scaling-rules`](../80-scaling-rules/00-index.md) is where correctness is measurable rather than asserted, and QUAL-010 is where the product commits to a person rather than to a device. |
| Performance efficiency | QUAL-001 to QUAL-004 for time behaviour; resource use is bounded by CON-002 and watched as a signal (OBS-001, OBS-003). |
| Compatibility | QUAL-005 and QUAL-011; the product interoperates with four services and one observer, and the list is closed (EXT-001). |
| Interaction capability | Section [`accessibility`](../200-accessibility.md): a fixed list of thresholds — contrast, touch targets, text scale, the screen reader on every screen including the editor, no action that needs a drag, and the cooking card's own figures — with no conformance claim made. |
| Reliability | QUAL-006 for availability, QUAL-008 for recoverability. Fault tolerance is not committed to: there is one server and no redundancy, which is what the 95 per cent of QUAL-006 accounts for. |
| Security | QUAL-007 and QUAL-009, the two places a key is presented; the rest is structural rather than measurable — no personal data exists to lose (CON-001), the account key is never uploaded (EXT-008), and a stolen deletion key destroys an account but never takes it over (AUTH-017). |
| Maintainability | Not committed to as a measured attribute. The one maintainability property the product does buy is the replaceability of the storage provider (EXT-003). |
| Flexibility | Not committed to. The product has one client surface (CON-005) and two languages at first release, with a third a product decision of its own (L10N-001, MVP-002). |
| Safety | `n/a`. The product controls no physical process; the one place where harm is possible — a recipe that is dangerous to cook — is handled by moderation and by MOD-008, not by a quality threshold. |
