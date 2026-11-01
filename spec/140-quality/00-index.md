---
slug: QUAL
status: closed
updated: 2026-09-27
ids: []
---

# Quality attributes

Every requirement here carries a metric, a method of measuring it and a
threshold. Nothing is measured about a person (RET-010), so every threshold is
verified on a bench, against the reference configurations of QUAL-001, before a
release. Availability, recoverability of a store and key-trying rates belonged to
a server and are later (`../35-server-later.md`).

## Parts

| Part | What is in it | Requirements |
|---|---|---|
| [10-time-behaviour.md](10-time-behaviour.md) | The two reference configurations and the response times on each | QUAL-001, QUAL-002, QUAL-003, QUAL-004 |
| [20-compatibility.md](20-compatibility.md) | The lowest Android version and the narrowest supported window | QUAL-005, QUAL-011 |

Other quality characteristics are specified as behaviour where they arise —
correctness of rounding in `scaling-rules`, interaction in `accessibility`, no
personal data to lose (CON-001) — or are not committed to.
