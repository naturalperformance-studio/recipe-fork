---
slug: RET
status: closed
updated: 2026-11-01
ids: [RET-006, RET-010]
---

# What is kept and what is deleted

Everything lives on the device, and the person deletes by their own act: a recipe
(ST-010), media the device holds (FLOW-060). Deleting an account, server logs and a
removed recipe kept for its author belong to a server and are later (`35-server-later.md`).

### RET-006 — Time deletes nothing

The system shall delete no recipe and no media because time has passed.

**Source:** user decision, 2026-09-21: there is no channel to warn anyone
(CON-001), so deletion by age would always arrive unannounced. See
[ADR — nothing is deleted for being old](decisions/20260921-nothing-expires.md).

### RET-010 — Nothing is kept that was never needed

The system shall hold no record of what a person read, searched for or cooked.
What the device holds serves the reader — their recipes, the recipe last open with
its scale and place (FLOW-012), done marks (FLOW-068), the usual serving count,
My kitchen (FLOW-071), a shopping list (FLOW-074) — and is sent nowhere.

**Source:** CON-001 and NGOAL-002: a reading history is the one thing in a recipe
product that would say something real about the person.
