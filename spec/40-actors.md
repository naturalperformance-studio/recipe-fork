---
slug: ACT
status: closed
updated: 2026-11-01
ids: [ACT-001]
---

# Actors

One actor: the person holding the device. While the product is serverless there is
no account, no sign-in and no role; owners on a server, moderators and publishing
bans are later (`35-server-later.md`).

### ACT-001 — Nothing needs an account

The system shall let a person use everything the product offers — writing,
editing, forking and deleting recipes, the recipe book and its collections,
scaling, cooking and recipe files — without an account and without being asked
for anything.

**Acceptance criteria**
- Given a clean installation, when the application is opened, then it is usable
  at once and nothing is created, asked for or generated.

**Source:** user decision, 2026-09-20 (an account only when an action needs one);
2026-09-28 ([ADR — the device first](decisions/20260928-the-device-first.md));
2026-11-01, a server is later — so no action needs one.
