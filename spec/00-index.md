# Recipe Fork — specification index

> **NOT READY — 3 questions must be answered before MVP.**
> Sections: 5 closed · 0 blocked · 17 open · 1 n/a.
> Blocking: OQ-002, OQ-003, OQ-004. Last updated 2026-09-20.

The product is a recipe platform built on a fork model: any accessible recipe
can be forked into an independent recipe that names its direct parent. A recipe
is a structured, recalculable object, not prose. Accounts carry no personal
data and are held by a key file the user stores themselves.

Source draft: `/home/personal/Projects/drafts/recipe-app-requirements.md`
(read 2026-09-20; the draft is input, not a spec, and is not maintained here).

## Sections

| File | Slug | Status | What it is for |
|---|---|---|---|
| [10-glossary.md](10-glossary.md) | GLOSS | open | Terms that mean something specific here: fork, parent, publication, private link, unit type. |
| [20-goals.md](20-goals.md) | GOAL | closed | What the product is for, what it refuses to be, and the rule that settles scope disputes. |
| [30-constraints.md](30-constraints.md) | CON | closed | Hard constraints the product owner accepts as non-negotiable: no personal data, minimal infrastructure cost, immutability, visible automation. |
| [40-actors.md](40-actors.md) | ACT | closed | Who acts on the system and what each one may do, including whoever is not signed in. |
| [50-domain-model.md](50-domain-model.md) | DOM | closed | Entities, their fields and invariants: recipe, ingredient, unit type, step, media, account, moderation record. |
| [60-entity-states.md](60-entity-states.md) | ST | closed | State machines with transition tables and, for each state, the transitions that must not happen. |
| 70-flows.md | FLOW | open | User flows and screens, each with its empty, loading, error and offline behaviour. |
| 80-scaling-rules.md | SCALE | open | Recalculation and rounding: by servings, from a fixed ingredient, conflicting constraints, exact rounding rules per unit type. |
| 90-media.md | MEDIA | open | Media capture, client-side conversion, upload before publication, privacy of unpublished media. |
| 100-moderation.md | MOD | open | What triggers moderation, what is checked automatically and what by a human, and what a rejection produces. |
| 110-catalogue.md | CAT | open | Public catalogue, search, filters, favourites and the search inside favourites. |
| 120-ratings.md | RATE | open | Binary like and dislike, and favourites as a separate action. |
| 130-external-interfaces.md | EXT | open | Interfaces crossing the product boundary: the key file as a portable artefact, object storage, anything the client depends on. |
| 140-quality.md | QUAL | open | Quality attributes with a metric, a measurement method and a threshold each. |
| 150-retention.md | RET | open | What is kept, for how long, what deletion means, and what account deletion does to published content. |
| 160-localization.md | L10N | open | String externalisation, Russian-only first release, language-dependent search and filtering. |
| 170-abuse.md | ABUSE | open | Flooding, mass publication, and what a single moderator can absorb. |
| 180-analytics.md | ANL | open | What the product owner wants to learn about usage, and what may be measured without personal data. |
| 190-legal.md | LEG | open | Licence of recipe content, attribution on fork, terms of use, responsibility for user-generated content. |
| 200-accessibility.md | A11Y | open | Accessibility commitments and their verification. |
| 210-observability.md | OBS | open | Operational signals: moderation queue, upload failures, recalculation errors. |
| 220-mvp-boundary.md | MVP | open | What is in the first release and what explicitly waits. |
| — | MIG | n/a | Migration: the product launches from scratch — no existing data, users or predecessor system to migrate from. Moving a user between devices by key file is an authentication requirement, not a migration. |

[Open questions ledger](open-questions.md) · [Decisions](decisions/)

## Decisions

| ADR | Status | Subject |
|---|---|---|
| [20260920-cooking-over-lineage.md](decisions/20260920-cooking-over-lineage.md) | accepted | Cooking outranks lineage when the two conflict. |
| [20260920-native-android-mvp.md](decisions/20260920-native-android-mvp.md) | accepted | Native Android is the only client of the MVP; private links are Android-only. |
| [20260920-report-and-block-model.md](decisions/20260920-report-and-block-model.md) | accepted | Three reports hide a recipe; blocking hides an author from catalogue and search only. |
| [20260920-publication-is-permanent.md](decisions/20260920-publication-is-permanent.md) | accepted | Once published, a recipe can never be withdrawn by its owner. |
| [20260920-immutability-trigger.md](decisions/20260920-immutability-trigger.md) | accepted | A recipe freezes at external visibility; withdrawn and rejected recipes stay editable. |
| [20260920-forking-published-only.md](decisions/20260920-forking-published-only.md) | accepted | Only published recipes are forkable by others; owners may fork their own in any state. |

## C4 context

Written once actors and external systems are settled (sections ACT and EXT).

## Retired IDs

No requirement has been retired yet. When one is deleted its ID is recorded
here with the date and reason, and is never reused.

| ID | Retired | Reason |
|---|---|---|
