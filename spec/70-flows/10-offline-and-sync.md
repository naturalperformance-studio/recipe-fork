---
slug: FLOW
status: closed
updated: 2026-11-01
ids: [FLOW-001, FLOW-002]
---

# The device holds everything

The product exists so that a person can cook from the recipe (GOAL-001), in a
kitchen, where the network is worst and the hands are busy. Everything is on the
device: the recipe book, the dictionaries, every recipe the person wrote or found.
Nothing is sent anywhere (EXT-010); a recipe leaves the device only as a file the
person hands on (FLOW-063).

### FLOW-001 — The dictionaries are held on the device

The system shall hold the tag dictionary (DOM-008) and the ingredient dictionary
(DOM-009) on the device, as shipped with the application, and shall get newer
copies only with an application update. A recipe that uses an entry later removed
keeps it; the editor stops offering it for new use.

**Source:** user decision, 2026-09-20: naming an ingredient means searching the
dictionary, so the editor is usable offline only if it is held. Shipped with the
application: [ADR — the serverless dictionary ships with the application](../decisions/20261003-the-serverless-dictionary-ships-with-the-app.md).

### FLOW-002 — A recipe is written on the device, with no account and no connection

The system shall let a person create a recipe, and edit, fork and delete any
recipe of theirs that `entity-states` makes editable, on the device with no
account and no connection, and shall keep every change on the device.

**Acceptance criteria**
- Given a clean installation in aeroplane mode, when the person writes and forks
  recipes and restarts the application, then every change is there and nothing
  was asked for.

**Source:** user decision, 2026-09-20; 2026-09-28
([ADR — the device first](../decisions/20260928-the-device-first.md)).
