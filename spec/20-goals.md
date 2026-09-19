---
slug: GOAL
status: closed
updated: 2026-09-20
ids: [GOAL-001, GOAL-002, GOAL-003, NGOAL-001, NGOAL-002, NGOAL-003, NGOAL-004, NGOAL-005, NGOAL-006]
---

# Goals and non-goals

This section states what the product is for and what it deliberately refuses to
be. It contains no `shall` requirements by design: goals are the arbiter that
settles scope disputes in every other section, not behaviour a developer
implements.

## Goals

### GOAL-001 — Cooking from a structured recipe

The product exists so that a person can cook from a recipe that is a
recalculable structured object rather than a block of prose: ingredients with
typed units, scalable quantities, and steps with timers.

**Source:** user decision, 2026-09-20.

### GOAL-002 — Forking as the way a recipe changes

A recipe changes by being forked into an independent recipe that names its
direct parent, never by being edited in place after it has become visible to
anyone other than its owner.

**Source:** draft sections 1, 4; user decision, 2026-09-20.

### GOAL-003 — Tiebreak rule: cooking wins over lineage

When GOAL-001 and GOAL-002 demand different behaviour, or when scope must be
cut, the behaviour that serves cooking is kept and the behaviour that serves
lineage visibility is cut. Lineage in the MVP is limited to naming the direct
parent and navigating to it; grouping, comparing or aggregating a fork tree is
out of scope. See [ADR 20260920 — cooking over lineage](decisions/20260920-cooking-over-lineage.md).

**Source:** user decision, 2026-09-20.

## Non-goals

These are refusals, not unimplemented features. A change to any of them is a
product decision that supersedes this section, recorded as a new ADR.

### NGOAL-001 — No personal data
No name, email address, phone number or avatar is collected at any point.

### NGOAL-002 — No social layer
No comments, no feed, no subscriptions, no messaging, no follower graph.

### NGOAL-003 — No account recovery
An account that loses its key file is unrecoverable by design.

### NGOAL-004 — No editing of published content
Once a recipe is visible to anyone other than its owner it is immutable forever.

### NGOAL-005 — No automated media moderation
Automatic classification of images and video is not purchased or built.

### NGOAL-006 — No monetisation
No advertising, no paid tiers, no revenue of any kind.

**Source for NGOAL-001..006:** draft section 10.
