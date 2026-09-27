# Backlog

The ordered task list of the first release and the status of every task. Statuses use the vocabulary of the team lead's
process: `planned` · `gated` · `in-progress` · `in-review` · `po-review` · `on-develop` · `released-to-main` · `returned` · `blocked`.

Behaviour lives in `spec/` alone. An entry names the requirements it rests on and copies their acceptance criteria
verbatim; where a requirement's criteria are split between tasks, the entry says where the rest are carried.

## How the backlog is sliced

- **Waves.** Tasks are written out and gated one wave at a time. Wave 1 is below in full. Every other requirement of
  the first release is placed in a later wave in the coverage map at the end, criterion by criterion, and is sliced
  into tasks when the wave before it is on `develop`: expected files for work that far ahead would be guesses, and
  the rule that concurrent tasks touch disjoint files is only as good as those lists.
- **Expected files are a contract.** A task touches only what its entry lists (`**` is any depth; a `!` entry is
  excluded). Two tasks whose lists overlap are never in progress together: one depends on the other.
- **Shared files are few, and serialised.** Server routes (`Application.kt`), migrations (`V<n>__*.sql`, numbered
  in dependency order), the application container, the navigation graph, the manifest and the version catalog are
  each touched by a chain of tasks that depend on one another. Interface strings live in one resource file per
  feature (`values/strings_<feature>.xml` and `values-ru/strings_<feature>.xml`) so that screens do not collide there.
- **Three criteria recur on every screen.** L10N-001 (both languages, nothing hard-coded), A11Y-003 (48 dp targets)
  and A11Y-005 (every control announces what it does) are copied into every task that adds a screen; T-007 builds the
  test helpers they are checked with.
- **Commands.** Build `./gradlew assembleAll`, full suite `./gradlew testAll`, setup `scripts/setup.sh` — see
  `00-index.md`. No wave-1 task needs the device-test command.

## Wave 1 — an author writes a recipe and cooks from it

A person creates an account, writes a recipe — offline as well — cooks from it on the card with the serving count,
a fixed ingredient and the step timers, and the draft reaches the server. Nothing is submitted, published or shared
in this wave, and no media is attached.

| Task | Title | Depends on | Status |
|---|---|---|---|
| T-001 | Quantities follow the serving count | — | blocked |
| T-029 | Large and fractional quantities are written on the ladder | T-001 | gated |
| T-002 | A fixed ingredient sets the scale | T-029 | blocked |
| T-030 | Changing the count or releasing the last fixing puts the scale back | T-002 | gated |
| T-003 | The store refuses a malformed dictionary entry | — | on-develop |
| T-004 | The device holds both dictionaries and replaces them when they change | T-003 | on-develop |
| T-005 | An account is created with a unique nickname and three keys, and only once it is confirmed | T-004 | in-progress |
| T-006 | Creating an account costs a device slot, and no verdict makes it muted | T-005 | gated |
| T-007 | The own-recipes view lists every recipe the account owns | T-004, T-001 | blocked |
| T-008 | The editor opens on a new recipe from the own-recipes view | T-007 | gated |
| T-009 | The first step of creation shows the nickname and the terms | T-006, T-008 | gated |
| T-032 | The second step of creation issues the account key and the deletion key | T-009 | blocked |
| T-010 | The recovery key is issued on the third step, and leaving creation leaves nothing | T-032 | gated |
| T-031 | What the ingredient field offers for what the author types | T-008 | gated |
| T-011 | An ingredient is picked from the dictionary in three fields | T-031 | gated |
| T-012 | An ingredient outside the dictionary is kept as free text with a warning | T-011 | gated |
| T-013 | Steps are written in the editor and survive a restart offline | T-012 | gated |
| T-014 | A step carries one timer, named by its step unless the author names it | T-013 | blocked |
| T-015 | A quantity goes into a step as a reference | T-014, T-019 | gated |
| T-016 | Steps and ingredients are inserted anywhere and moved, by dragging or by a discrete action | T-015 | gated |
| T-017 | A part below the minimum or over a limit is marked on leaving it | T-016 | gated |
| T-018 | A saved recipe opens as the card a reader sees, at the serving count chosen | T-008, T-029 | blocked |
| T-019 | A derived quantity says so, and which way it was rounded | T-018, T-030 | gated |
| T-020 | The reader fixes an ingredient on the card | T-019, T-030, T-015 | blocked |
| T-021 | A reference in a step reads as the quantity the list shows | T-020 | gated |
| T-022 | A timer runs on its step | T-021, T-014, T-010 | gated |
| T-023 | A timer announces itself when the application is closed | T-022, T-026 | gated |
| T-034 | A long step with an unnamed timer is shown what its notification will say | T-017, T-023 | gated |
| T-033 | A timer asks for notifications when first started, and a refusal is explained at once | T-023 | gated |
| T-024 | Several timers run at once, and restarting one asks first | T-033 | gated |
| T-025 | The card keeps the screen awake and is where the reader left it | T-024 | gated |
| T-026 | Writing a recipe asks for an account first | T-010, T-018 | planned |
| T-027 | The server keeps an account's drafts and turns a stale edit into a fork | T-006 | gated |
| T-028 | Drafts written offline reach the server by themselves, and each says whether they have | T-027, T-034, T-026, T-025 | planned |

### T-001 — Quantities follow the serving count

**Requirements:** SCALE-001, SCALE-003, SCALE-005, SCALE-010, SCALE-015
**Status:** blocked
**Depends on:** —
**Expected files:** `core/src/main/kotlin/app/recipefork/core/recipe/Units.kt` · `core/src/main/kotlin/app/recipefork/core/scaling/**` · `core/src/test/kotlin/app/recipefork/core/scaling/**`
**Binding ADRs:** decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Note:** `Units.kt` holds the twelve units of DOM-016 with the unit type the table in `spec/50-domain-model/20-ingredients-and-limits.md` gives each; T-011 and T-029 read it and do not change it.
**Criteria of these requirements carried elsewhere:** SCALE-010 (2) → T-002; (3) → T-029 · SCALE-015 (1–2) → T-019; (4–5) → T-002

**Acceptance criteria** (verbatim)

*SCALE-001 — spec/80-scaling-rules/10-scaling.md*
- Given a recipe authored for 4 servings with 500 g of an `exact` ingredient, when the reader asks for 6, then it shows 750 g.
- Given the same recipe with 3 indivisible pieces, when the reader asks for 6, then it shows 6 pieces.
- Given the same recipe with an ingredient of type `taste_unmeasured`, when the reader asks for any count, then its line is unchanged.
- Given any scaled view, when the reader returns the count to the authored value, then every quantity equals the authored quantity and nothing is marked as derived.

*SCALE-003 — spec/80-scaling-rules/10-scaling.md*
- Given a factor of 0.6 and an authored 7 g, when the line is displayed, then it shows 4 g and the stored value remains 7 g.
- Given that same view, when the reader changes the factor to 1.0, then the line shows 7 g again, not a value derived from 4 g.

*SCALE-005 — spec/80-scaling-rules/20-display.md*
- Given 3 indivisible pieces at a factor of 0.1, when the line is displayed, then it shows 1 piece and not 0.
- Given 1 teaspoon at a factor of 0.05, when the line is displayed, then it shows ¼ teaspoon and not 0.
- Given any factor above zero, when the list is displayed, then no line shows a zero quantity.

*SCALE-010 — spec/80-scaling-rules/10-scaling.md*
- Given any recipe, when the reader requests 1000 servings, then the recipe is displayed at that count and nothing is refused.
- Given any scaled view, when a quantity is displayed, then it was computed from the exact factor by SCALE-003, whatever the size of the factor.

*SCALE-015 — spec/80-scaling-rules/20-display.md*
- Given a tablespoon quantity computed as 1 5⁄12, exactly between 1⅓ and 1½, when it is displayed, then it shows 1½ tablespoons.
- Given a value whose nearest grid value is below the floor of its unit type, when it is displayed, then SCALE-005 governs and the floor is shown.

**Gate 1:** blocked-by-spec (SD-002) — docs/product/verdicts/20260927-T-001-gate1.md; re-sliced against spec/ @ ead1a44 after SD-002 resolved, re-gate due; re-gated: blocked-by-spec (SD-027, SCALE-001 (2)) — docs/product/verdicts/20260927-T-001-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-029 — Large and fractional quantities are written on the ladder

**Requirements:** SCALE-010, SCALE-013, SCALE-014, L10N-009
**Status:** gated
**Depends on:** T-001
**Expected files:** `core/src/main/kotlin/app/recipefork/core/scaling/**` · `core/src/test/kotlin/app/recipefork/core/scaling/**`
**Binding ADRs:** decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** SCALE-010 (1, 4) → T-001; (2) → T-002 · SCALE-013 (3) → T-019 · SCALE-014 (4) → T-019 · L10N-009 (5) → T-018

**Acceptance criteria** (verbatim)

*SCALE-010 — spec/80-scaling-rules/10-scaling.md*
- Given a recipe scaled to a very large count, when an `exact` ingredient exceeds 1000 g, then it is displayed in kilograms by the rounding grid.

*SCALE-013 — spec/80-scaling-rules/20-display.md*
- Given an `exact` quantity computed as 1234 g, when it is displayed, then it shows 1.234 kg.
- Given an `exact` quantity computed as 1500 g, when it is displayed, then it shows 1.5 kg and not 1.500 kg or 2 kg.
- Given an `exact` quantity computed as 999.6 g, when it is displayed, then it shows 1 kg, because the nearest whole gram is 1000 g.

*SCALE-014 — spec/80-scaling-rules/20-display.md*
- Given 1 tablespoon at a factor of 1.5, when the line is displayed, then it shows 1½ tablespoons.
- Given 1 teaspoon at a factor of 2.3, when the line is displayed, then it shows 2⅓ teaspoons.
- Given 1 divisible onion at a factor of 2.5, when the line is displayed, then it shows 2½ onions.
- Given any quantity of these unit types, when it is displayed, then it never carries a decimal mark.

*L10N-009 — spec/160-localization.md*
- Given the interface in Russian, when 10 000 000 pieces are displayed, then the number reads `10 000 000` with a space that never breaks the number across two lines.
- Given the interface in English, when the same quantity is displayed, then it reads `10,000,000`.
- Given an `exact` quantity of 1500 g, when it is displayed, then it reads `1,5 кг` in Russian and `1.5 kg` in English (SCALE-013).
- Given the interface in Russian, when a four-digit whole number such as 1234 servings is displayed, then it reads `1 234`; in English it reads `1,234`.
- Given a vulgar fraction of SCALE-014, when it is displayed, then it carries no decimal mark in either language.

**Gate 1:** accepted — docs/product/verdicts/20260927-T-029-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-002 — A fixed ingredient sets the scale

**Requirements:** SCALE-002, SCALE-006, SCALE-008, SCALE-010, SCALE-015
**Status:** blocked
**Depends on:** T-029
**Expected files:** `core/src/main/kotlin/app/recipefork/core/scaling/**` · `core/src/test/kotlin/app/recipefork/core/scaling/**`
**Binding ADRs:** decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** SCALE-002 (2) → T-020 · SCALE-006 (2) → T-020 · SCALE-008 (3) → T-020 · SCALE-010 (1, 4) → T-001; (3) → T-029 · SCALE-015 (1–2) → T-019; (3, 6) → T-001

**Acceptance criteria** (verbatim)

*SCALE-002 — spec/80-scaling-rules/10-scaling.md*
- Given a recipe authored for 4 servings with 500 g of chicken, when the reader fixes 300 g of chicken, then the chicken line shows exactly 300 g.
- Given that state, when another `exact` ingredient of 350 g is displayed, then it shows 210 g, computed from 0.6 and not from a rounded serving count.

*SCALE-006 — spec/80-scaling-rules/10-scaling.md*
- Given 300 g of chicken fixed against an authored 500 g, when the reader then fixes 3 eggs against an authored 3, then the factor in force is 1.0 and the chicken line shows a requirement of 500 g against 300 g available.
- Given that state, when the reader releases the egg fixing, then the factor returns to the one implied by the chicken and the shortfall marking disappears.

*SCALE-008 — spec/80-scaling-rules/10-scaling.md*
- Given a recipe authored for 4 servings, when the reader fixes 50 g of an ingredient authored at 500 g, then the serving count shows an approximate ⅓ serving.
- Given the same recipe, when the reader fixes 25 g of that ingredient (a computed 0.2 servings), then the serving count shows an approximate ¼ serving.
- Given a computed count of exactly one serving or more, when it is displayed, then it is displayed as a whole number and not as a fraction.
- Given any state, when the serving count is displayed, then no quantity in the recipe is computed from it; SCALE-003 governs.

*SCALE-010 — spec/80-scaling-rules/10-scaling.md*
- Given a recipe authored for 4 servings with 5 g of an `exact` ingredient, when the reader fixes 10 kg of that ingredient, then the derived count is displayed by SCALE-002 and is not capped.

*SCALE-015 — spec/80-scaling-rules/20-display.md*
- Given a recipe authored for 4 servings with 400 g of an ingredient, when the reader fixes 250 g of it, then the serving count shows an approximate 3 servings.
- Given a recipe authored for 4 servings with 240 g of an ingredient, when the reader fixes 25 g of it (a computed 5/12 serving), then the serving count shows an approximate ½ serving.

**Gate 1:** blocked-by-spec (SD-003) — docs/product/verdicts/20260927-T-002-gate1.md; re-sliced against spec/ @ ead1a44 after SD-003 resolved, re-gate due; re-gated: blocked-by-spec (SD-027, SCALE-008 vs SCALE-015) — docs/product/verdicts/20260927-T-002-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-030 — Changing the count or releasing the last fixing puts the scale back

**Requirements:** SCALE-016, SCALE-017
**Status:** gated
**Depends on:** T-002
**Expected files:** `core/src/main/kotlin/app/recipefork/core/scaling/**` · `core/src/test/kotlin/app/recipefork/core/scaling/**`
**Binding ADRs:** decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** SCALE-017 (3) → wave 3

**Acceptance criteria** (verbatim)

*SCALE-016 — spec/80-scaling-rules/10-scaling.md*
- Given a recipe authored for 4 servings with 500 g of chicken fixed at 300 g, when the reader asks for 6 servings, then the chicken line shows 750 g and is no longer marked as fixed.
- Given two fixed ingredients, one showing a shortfall (SCALE-006), when the reader changes the serving count, then neither is marked as fixed and no shortfall is shown.
- Given that the fixings were released by a change of count, when the reader looks at the serving count, then it is the count they asked for and is not marked as approximate.

*SCALE-017 — spec/80-scaling-rules/10-scaling.md*
- Given a recipe opened at its authored 4 servings, when the reader fixes an ingredient and then releases it, then the recipe is displayed at 4 servings and nothing is marked as derived.
- Given a recipe the reader had set to 6 servings, when they fix an ingredient, then fix a second, then release the second and then the first, then the recipe is displayed at 6 servings.
- Given the fixing is released, when the serving count is displayed, then it is not marked as approximate.

**Gate 1:** accepted — docs/product/verdicts/20260927-T-030-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-003 — The store refuses a malformed dictionary entry

**Requirements:** DOM-010, L10N-003, DOM-014
**Status:** on-develop
**Depends on:** —
**Expected files:** `server/src/main/kotlin/app/recipefork/server/db/**` · `server/src/main/kotlin/app/recipefork/server/dictionary/**` · `server/src/main/resources/db/migration/V1__dictionaries.sql` · `server/src/test/kotlin/app/recipefork/server/db/**` · `server/src/test/kotlin/app/recipefork/server/dictionary/**` · `deploy/local/compose.yml` · `deploy/local/seed/**`
**Binding ADRs:** decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-test-strategy.md
**Size:** First task to hold data on the server: it carries the connection pool, the migration runner and the PostgreSQL test fixture every later server task uses. Expected at 300–400 changed lines.
**Note:** spec paths updated to spec/ @ ead1a44; criteria unchanged word for word, so the gate-1 verdict stands.
**Criteria of these requirements carried elsewhere:** DOM-010 (3–4) → T-011; (5) → wave 2 · L10N-003 (2) → T-011; (3–5) → T-018 · DOM-014 (1, 4–5) → wave 8; (2) → T-011

**Acceptance criteria** (verbatim)

*DOM-010 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given the entry `крылышки` whose parent is `курица`, when `курица` is read, then it has no parent of its own.
- Given an attempt to give a parent to an entry that already has children, when it is validated, then it is rejected.

*L10N-003 — spec/160-localization.md*
- Given a new dictionary entry, when it is added with a label in one language only, then it is rejected and does not become available to any author.

*DOM-014 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given the rule that an entry carries a label in both languages (L10N-003), when a half-translated entry is written, then what refuses it is the store's own constraint, because there is no screen at which the refusal could be shown.

**Gate 1:** accepted — docs/product/verdicts/20260927-T-003-gate1.md
**Review:** pass — 4 of 4 criteria checked by mutation, worktree as it was
**Gate 2:** accepted — docs/product/verdicts/20260927-T-003-gate2.md
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-004 — The device holds both dictionaries and replaces them when they change

**Requirements:** FLOW-001
**Status:** on-develop
**Depends on:** T-003
**Expected files:** `server/src/main/kotlin/app/recipefork/server/Application.kt` · `server/src/main/kotlin/app/recipefork/server/dictionary/**` · `server/src/test/kotlin/app/recipefork/server/dictionary/**` · `core/src/main/kotlin/app/recipefork/core/api/DictionaryContract.kt` · `app/build.gradle.kts` · `build.gradle.kts` · `gradle/libs.versions.toml` · `app/src/main/AndroidManifest.xml` · `app/src/main/kotlin/app/recipefork/app/RecipeForkApplication.kt` · `app/src/main/kotlin/app/recipefork/app/AppContainer.kt` · `app/src/main/kotlin/app/recipefork/app/data/**` · `app/src/main/kotlin/app/recipefork/app/dictionary/**` · `app/src/test/kotlin/app/recipefork/app/data/**` · `app/src/test/kotlin/app/recipefork/app/dictionary/**`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Size:** One criterion, but it is the first path from the server to the device: it carries the application container, the local database, the network client and connectivity observation. Indivisible because the criterion needs both ends. Expected at 500–700 changed lines.
**Note:** checked against spec/ @ ead1a44; criteria and their spec paths unchanged word for word, so the gate-1 verdict stands.
**Criteria of these requirements carried elsewhere:** FLOW-001 (1) → T-008; (2, 7) → T-011; (3) → T-012; (4) → T-031; (5) → wave 3

**Acceptance criteria** (verbatim)

*FLOW-001 — spec/70-flows/10-offline-and-sync.md*
- Given a connection returns after the product owner has changed a dictionary, when the application next reaches the server, then the held copy is replaced.

**Gate 1:** accepted — docs/product/verdicts/20260927-T-004-gate1.md
**Review:** defects — a 200 answer whose body is not the dictionaries escapes refresh() and ends the sync (DictionaryRepository.kt:36); returned to the developer; re-review: pass — FLOW-001 (6) checked by five mutations, worktree as it was
**Gate 2:** accepted — docs/product/verdicts/20260927-T-004-gate2.md
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-005 — An account is created with a unique nickname and three keys, and only once it is confirmed

**Requirements:** AUTH-014, AUTH-017, AUTH-020, AUTH-018, DOM-001, LEG-004, CON-001, AUTH-022, AUTH-023
**Status:** in-progress
**Depends on:** T-004
**Expected files:** `server/src/main/kotlin/app/recipefork/server/Application.kt` · `server/src/main/kotlin/app/recipefork/server/account/**` · `server/src/main/resources/db/migration/V2__accounts.sql` · `core/src/main/kotlin/app/recipefork/core/api/AccountContract.kt` · `deploy/local/seed/**` · `server/src/test/kotlin/app/recipefork/server/account/**`
**Binding ADRs:** decisions/20260927-keys-are-hashed-random-secrets.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-test-strategy.md
**Note:** No new library is expected; the version catalog is outside this task's files.
**Note:** The nickname words are the product owner's two lists (AUTH-023), changed in the store as the dictionaries are. This task holds them in tables of its migration and seeds the local deployment with a placeholder list that says it is one; the real lists are the product owner's to write before the first account (release record AUTH-023 (1)), and their size is OQ-009.
**Criteria of these requirements carried elsewhere:** AUTH-014 (5–7) → wave 7 · AUTH-017 (1) → T-032; (2, 5–6) → wave 7 · AUTH-020 (1–2, 6–9) → T-010; (3–4) → wave 7 · AUTH-018 (1–2) → T-009; (4) → wave 7 · DOM-001 (2) → wave 7 · LEG-004 (1, 5) → T-009; (2) → wave 3; (3) → wave 7 · CON-001 (1) → T-009 · AUTH-022 (2) → T-009 · AUTH-023 (1) → release record; (2) → wave 8

**Acceptance criteria** (verbatim)

*AUTH-014 — spec/45-auth/30-account-life.md*
- Given two accounts, when their keys are compared, then they differ, and no key of any of the three kinds is ever issued twice — including keys issued again under AUTH-021.
- Given the account key, the deletion key and the recovery key, when each is examined, then the properties in this requirement govern all three alike; the three differ in what they do and in where they are kept, never in how strong they are.
- Given an account key, when its length and randomness are examined, then trying keys at the rate QUAL-009 permits cannot find a live one in any practical time.
- Given everything the server stores, when it is examined by someone holding all of it, then nothing in it allows them to reconstruct any of an account's keys, to act as that account, or to retire its account key.

*AUTH-017 — spec/45-auth/20-account-key.md*
- Given two accounts, when their deletion keys are compared, then they differ, and no key is ever issued twice.
- Given a deletion key, when its length and randomness are examined, then trying keys at the page's permitted rate (QUAL-007) cannot find a live one in any practical time.

*AUTH-020 — spec/45-auth/30-account-life.md*
- Given the recovery key, when its uniqueness and its resistance to guessing are examined, then AUTH-014 governs it exactly as it governs the account key, at the rate QUAL-009 permits.

*AUTH-018 — spec/45-auth/10-creating-an-account.md*
- Given a nickname the person passed over, when the account is created, then only the accepted nickname is consumed and the others stay available to others.
- Given two accounts created at the same moment, when their nicknames are compared, then they differ.

*DOM-001 — spec/50-domain-model/10-invariants.md*
- Given any two accounts, when their nicknames are compared, then they differ.
- Given the account creation screen, when a nickname is rerolled before the account is created, then no nickname is assigned yet and none is consumed (AUTH-018).

*LEG-004 — spec/190-legal.md*
- Given the recorded acceptance, when it is inspected, then it is a version identifier and a date and nothing about a person (RET-010).

*CON-001 — spec/30-constraints.md*
- Given the stored data of any account, when its fields are enumerated, then none of them holds a value of those four kinds.

*AUTH-022 — spec/45-auth/10-creating-an-account.md*
- Given the creation screen, when a nickname is generated, then it has the form `Brave Copper Otter`: two adjectives, then a noun, each capitalised, separated by one space.
- Given any two nicknames, when they are compared for uniqueness (DOM-001), then they are compared as the three words, so two nicknames never differ only in letter case or spacing.

*AUTH-023 — spec/45-auth/10-creating-an-account.md*
- Given a combination of three admitted words, when it is generated, then it is not checked against anything further: no list of forbidden combinations exists.
- Given a word removed from a list, when an account already holds a nickname containing it, then that nickname is unchanged (DOM-001) and the word is no longer drawn for new ones.

**Gate 1:** blocked-by-spec (SD-001) — docs/product/verdicts/20260927-T-005-gate1.md; re-sliced against spec/ @ ead1a44 after SD-001 resolved, re-gate due; re-gated: accepted — docs/product/verdicts/20260927-T-005-gate1-2.md
**Review:** defects — the confirming call accepts any well-formed keys from the client (keys ADR, AUTH-014 (1, 3), AUTH-017 (4), AUTH-020 (5)); AUTH-023 (4) "no longer drawn" and AUTH-014 (2) strength tests survive their mutations; returned to the developer
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-006 — Creating an account costs a device slot, and no verdict makes it muted

**Requirements:** AUTH-001, AUTH-002, AUTH-004, AUTH-015
**Status:** gated
**Depends on:** T-005
**Expected files:** `server/src/main/kotlin/app/recipefork/server/Application.kt` · `server/src/main/kotlin/app/recipefork/server/account/**` · `server/src/main/kotlin/app/recipefork/server/attestation/**` · `core/src/main/kotlin/app/recipefork/core/api/AccountContract.kt` · `server/src/test/kotlin/app/recipefork/server/account/**` · `server/src/test/kotlin/app/recipefork/server/attestation/**` · `deploy/local/compose.yml`
**Binding ADRs:** decisions/20260927-keys-are-hashed-random-secrets.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-test-strategy.md
**Note:** The attestation service sits behind an interface (test-strategy ADR). Tests and the local deployment use a fake that models the service's three per-device bits and their write months; the Play Integrity adapter is a later task.
**Criteria of these requirements carried elsewhere:** AUTH-001 (1) → wave 3; (2, 6–7) → T-026; (4) → wave 7 · AUTH-004 (1) → wave 3; (2) → T-026; (3, 5) → wave 4; (4, 7) → wave 6 · AUTH-015 (3) → wave 7

**Acceptance criteria** (verbatim)

*AUTH-001 — spec/45-auth/10-creating-an-account.md*
- Given account creation, when a full account is created, then one slot on the device is occupied with the month it was occupied in; a muted account occupies none (AUTH-004).
- Given a verdict that cannot be obtained while the server can be reached, when creation is attempted, then AUTH-004 governs what is created.

*AUTH-002 — spec/45-auth/10-creating-an-account.md*
- Given a device on which no account has ever been created, when its slots are read, then three are free.
- Given a slot occupied in March 2026, when the month is September 2026 or later, then that slot is free.
- Given a slot occupied in March 2026, when the month is August 2026, then that slot is not free.
- Given three slots occupied in the same month, when six months have passed, then all three are free at once.
- Given a slot that frees, when an account is created into it, then the account that occupied it before is neither deleted nor affected in any way.

*AUTH-004 — spec/45-auth/10-creating-an-account.md*
- Given a muted account, when it is created, then no slot on the device is occupied.

*AUTH-015 — spec/45-auth/30-account-life.md*
- Given a verdict received and checked, when the stored record of the account is enumerated, then it holds no field from that verdict.
- Given the stored data of any account, when it is enumerated, then it holds nothing that identifies a device (CON-001).

**Gate 1:** accepted — docs/product/verdicts/20260927-T-006-gate1.md; AUTH-001 (3) rewritten in spec/ @ ead1a44 and replaced here, re-gate due; re-gated: accepted — docs/product/verdicts/20260927-T-006-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-007 — The own-recipes view lists every recipe the account owns

**Requirements:** FLOW-034, FLOW-036, FLOW-039, FLOW-041, FLOW-043, FLOW-038, A11Y-008, L10N-001, A11Y-003, A11Y-005
**Status:** blocked
**Depends on:** T-004, T-001
**Expected files:** `core/src/main/kotlin/app/recipefork/core/recipe/**` · `!core/src/main/kotlin/app/recipefork/core/recipe/Units.kt` · `core/src/test/kotlin/app/recipefork/core/recipe/**` · `app/build.gradle.kts` · `gradle/libs.versions.toml` · `app/src/main/kotlin/app/recipefork/app/MainActivity.kt` · `app/src/main/kotlin/app/recipefork/app/AppContainer.kt` · `app/src/main/kotlin/app/recipefork/app/data/db/**` · `app/src/main/kotlin/app/recipefork/app/recipe/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/main/kotlin/app/recipefork/app/ui/**` · `app/src/test/kotlin/app/recipefork/app/recipe/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/ui/**` · `app/lint.xml` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml` · `app/src/main/res/values/strings_common.xml` · `app/src/main/res/values-ru/strings_common.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Size:** First screen of the application: it carries the recipe model in `:core` (every field of `50-domain-model`), its local tables, the navigation host and the test helpers for the three cross-cutting criteria every later screen repeats. Expected at 600–800 changed lines.
**Criteria of these requirements carried elsewhere:** FLOW-034 (2, 4) → wave 3 · FLOW-036 (3, 8) → wave 2; (6) → T-028; (7) → wave 3 · FLOW-039 (3–4) → wave 3 · FLOW-041 (1–4, 6) → wave 2 · FLOW-043 (1–4) → wave 2; (6) → wave 3 · FLOW-038 (1–2, 6) → T-018; (3–4) → wave 5; (7) → wave 3 · A11Y-008 (1) → T-019; (2) → wave 6; (4) → wave 8 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-034 — spec/70-flows/50-browsing.md*
- Given an account that has marked recipes and owns recipes, when it opens the section, then both views are reachable from it and the switch between them is visible without scrolling.
- Given the view of the account's own recipes, when it is shown, then it lists every recipe the account owns in every state, including states no other account can see.
- Given a switch between the views, when the reader leaves the section and returns within the same session, then the view they were in is still in force, as CAT-001 keeps a chosen ordering.
- Given a session in which the reader has not chosen a view, when they open the section, then it opens on the view of marked recipes, whatever the account holds.
- Given a device holding no account, when the section is opened, then it shows the view of marked recipes alone and no switch; the own-recipes view and the switch appear once the device holds an account.

*FLOW-036 — spec/70-flows/50-browsing.md*
- Given a recipe in `private`, when the view is shown, then its entry is marked as a draft.
- Given a recipe in `pending`, when its entry is shown, then it states that the recipe is awaiting review, and the expectation MOD-005 states is not repeated on the entry.
- Given a recipe in `reported_hidden`, when its entry is shown, then it states that the recipe is hidden and gives no further detail (ST-006).
- Given a recipe in `removed`, when its entry is shown, then it stays in the view marked as removed, because its owner may still open it (`entity-states`).

*FLOW-039 — spec/70-flows/50-browsing.md*
- Given a draft created a month ago and edited today and a recipe published yesterday, when the own-recipes view is ordered by `newest`, then the draft is first.
- Given a published recipe, when it is ordered in that view, then the time used is the last time its content changed, which for a frozen recipe is the last edit before it became externally visible (ST-003).
- Given a draft that has never been edited since it was created, when it is ordered, then its creation is the change the ordering uses.

*FLOW-041 — spec/70-flows/50-browsing.md*
- Given the own-recipes view with no network connection, when the reader scrolls to the end, then the following recipes are loaded from the device (FLOW-038) and nothing states that a connection is needed.

*FLOW-043 — spec/70-flows/50-browsing.md*
- Given the own-recipes view, when it is opened, then it is built from what the device holds (FLOW-038), so it never shows a load failure and never waits on the network.

*FLOW-038 — spec/70-flows/50-browsing.md*
- Given an account that owns hundreds of recipes, when they are held, then no own recipe's text is evicted to make room for another.

*A11Y-008 — spec/200-accessibility.md*
- Given an own recipe's state on its entry (FLOW-036), when it is shown, then the state is named and not only coloured.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-004) — docs/product/verdicts/20260927-T-007-gate1.md; re-sliced against spec/ @ ead1a44 after SD-004 resolved, re-gated: rejected — docs/product/verdicts/20260927-T-007-gate1-2.md (FLOW-034 (6, 7) left to wave 3); FLOW-034 (6, 7) added, re-gated: blocked-by-spec (SD-028, "session" in FLOW-034 (5, 6)) — docs/product/verdicts/20260927-T-007-gate1-3.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-008 — The editor opens on a new recipe from the own-recipes view

**Requirements:** FLOW-042, FLOW-024, FLOW-002, L10N-004, DOM-006, DOM-008, FLOW-001, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-007
**Expected files:** `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/main/kotlin/app/recipefork/app/ui/nav/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Note:** checked against spec/ @ ead1a44; criteria and their spec paths unchanged word for word, so the gate-1 verdict stands.
**Criteria of these requirements carried elsewhere:** FLOW-042 (1–3, 6–7) → wave 2; (5) → wave 3 · FLOW-024 (1) → wave 5; (2) → T-013; (3, 5) → wave 4 · FLOW-002 (1) → T-013; (4) → T-028 · L10N-004 (2–3, 5) → wave 2; (4) → wave 4 · DOM-006 (1–2) → wave 2 · DOM-008 (2) → wave 2 · FLOW-001 (2, 7) → T-011; (3) → T-012; (4) → T-031; (5) → wave 3; (6) → T-004 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-042 — spec/70-flows/50-browsing.md*
- Given an account that has written nothing, when it opens the own-recipes view, then the view states so and offers to write a recipe, and the offer opens the editor (FLOW-024).

*FLOW-024 — spec/70-flows/40-editor.md*
- Given a new recipe, when the editor opens, then the declared language is already set to the author's interface language and sits beside the title, where changing it is one action and missing it is not possible (L10N-004).
- Given the author leaves the editor at any point, when they return, then they return to a screen in the same order with everything they had entered.

*FLOW-002 — spec/70-flows/10-offline-and-sync.md*
- Given no network connection, when the owner edits a recipe in state `private` or `rejected`, then the edit is accepted.
- Given no network connection, when the owner opens a recipe in state `pending`, `link_shared`, `published`, `reported_hidden` or `removed`, then it is not editable, exactly as it is with a connection.

*L10N-004 — spec/160-localization.md*
- Given a new recipe, when the editor opens it, then the declared language is set to the author's current interface language and the author may change it.

*DOM-006 — spec/50-domain-model/10-invariants.md*
- Given the declared language, when a new recipe is opened in the editor, then it is already set from the author's interface language (L10N-004, FLOW-024), so this part of the minimum is the one an author cannot reach by omission and can only reach by clearing it.

*DOM-008 — spec/50-domain-model/10-invariants.md*
- Given the tag editor, when the author looks for a tag, then only dictionary entries are offered and free text cannot be committed.

*FLOW-001 — spec/70-flows/10-offline-and-sync.md*
- Given no network connection, when the author opens the tag editor, then the dictionary entries are offered exactly as they are with a connection.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** accepted — docs/product/verdicts/20260927-T-008-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-009 — The first step of creation shows the nickname and the terms

**Requirements:** AUTH-024, AUTH-018, AUTH-022, LEG-004, CON-001, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-006, T-008
**Expected files:** `app/src/main/kotlin/app/recipefork/app/account/**` · `app/src/test/kotlin/app/recipefork/app/account/**` · `app/src/main/kotlin/app/recipefork/app/AppContainer.kt` · `app/src/main/assets/terms/**` · `app/src/main/res/values/strings_account.xml` · `app/src/main/res/values-ru/strings_account.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-keys-are-hashed-random-secrets.md, decisions/20260927-test-strategy.md
**Note:** Open, raised to the user: the text of the terms of use does not exist yet (MVP-003, OQ-029). The task presents the versioned document from `app/src/main/assets/terms/`; until the text is supplied that document is a placeholder that says so in both languages.
**Criteria of these requirements carried elsewhere:** AUTH-024 (3, 5–6) → T-032; (4, 7–9) → T-010 · AUTH-018 (3, 5) → T-005; (4) → wave 7 · AUTH-022 (1, 3) → T-005 · LEG-004 (2) → wave 3; (3) → wave 7; (4) → T-005 · CON-001 (2) → T-005 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*AUTH-024 — spec/45-auth/20-account-key.md*
- Given creation is offered, when it opens, then the first step shows the generated nickname with the control for another (AUTH-018) and the terms of use with their acceptance (LEG-004), and no key is shown on it.
- Given the first step, when the terms are not accepted, then the step cannot be left forward.

*AUTH-018 — spec/45-auth/10-creating-an-account.md*
- Given the first step of creation (AUTH-024), when it opens, then a generated nickname is already shown and nothing is typed by the person (CON-001).
- Given a shown nickname, when the person asks for another, then a different generated nickname replaces it.

*AUTH-022 — spec/45-auth/10-creating-an-account.md*
- Given an interface in Russian, when a nickname is displayed anywhere, then it is displayed in Latin letters exactly as generated and is neither translated nor transliterated.

*LEG-004 — spec/190-legal.md*
- Given account creation (AUTH-007), when the account is created, then the terms are presented and accepted there — on its first step, before any key is shown (AUTH-024) — and the accepted version is recorded with the account.
- Given the terms, when they are opened from inside the application, then they are readable in full without leaving it, in the interface language (L10N-002).

*CON-001 — spec/30-constraints.md*
- Given any screen in the application, when a user creates an account or edits it, then no input accepts a name, email address, telephone number or image representing the user.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-001, SD-009, SD-010) — docs/product/verdicts/20260927-T-009-gate1.md; split into T-009 and T-032 by the three steps of AUTH-024; re-sliced against spec/ @ ead1a44 after SD-001, SD-009, SD-010 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-009-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-032 — The second step of creation issues the account key and the deletion key

**Requirements:** AUTH-024, AUTH-007, AUTH-017, L10N-001, A11Y-003, A11Y-005
**Status:** blocked
**Depends on:** T-009
**Expected files:** `app/src/main/kotlin/app/recipefork/app/account/**` · `app/src/test/kotlin/app/recipefork/app/account/**` · `app/src/main/res/values/strings_account.xml` · `app/src/main/res/values-ru/strings_account.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-keys-are-hashed-random-secrets.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** AUTH-024 (1–2) → T-009; (4, 7–9) → T-010 · AUTH-007 (7, 9–10) → T-010; (8) → wave 7 · AUTH-017 (2, 5–6) → wave 7; (3–4) → T-005 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*AUTH-024 — spec/45-auth/20-account-key.md*
- Given the first step completed, when the second step is shown, then it holds the account key and the deletion key with the controls and the confirmation of AUTH-007 and AUTH-017, and neither the nickname control nor the terms.
- Given the second step, when the person makes the system back action, then the first step is shown with the nickname and the acceptance as they were left, and nothing is asked.
- Given the first step reached by going back, when the person asks for another nickname and goes forward again, then the account key and the deletion key shown are the ones shown before: nothing exists yet, so nothing is issued anew.

*AUTH-007 — spec/45-auth/20-account-key.md*
- Given the account key step of creation (AUTH-024), when it is shown, then the key is displayed in full and can be copied in one action.
- Given the same screen, when the person prefers a file, then the key can be saved as one, and the product does not choose where.
- Given the warning, when it is read, then it states that losing the account key loses the account unless the recovery key is held (AUTH-021), and that nobody at all can restore an account whose keys are both gone.
- Given the confirmation control, when it is not chosen, then creation does not complete, and a back action does not leave creation without first asking the person to confirm leaving (criterion 10); on this step it returns to the first step of creation (AUTH-024).
- Given the warning, when any setting is searched for, then none switches it off (GUIDE-001 governs the same pattern for submission).
- Given a completed creation, when the screen is left, then the key is never shown in full again and AUTH-019 governs how it is reached.

*AUTH-017 — spec/45-auth/20-account-key.md*
- Given account creation, when the key is issued, then the deletion key is issued on the same screen and is carried by the file that AUTH-007 offers.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-029, the deletion key on the second step) — docs/product/verdicts/20260927-T-032-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-010 — The recovery key is issued on the third step, and leaving creation leaves nothing

**Requirements:** AUTH-020, AUTH-007, AUTH-024, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-032
**Expected files:** `app/src/main/kotlin/app/recipefork/app/account/**` · `app/src/test/kotlin/app/recipefork/app/account/**` · `app/src/main/res/values/strings_account.xml` · `app/src/main/res/values-ru/strings_account.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-keys-are-hashed-random-secrets.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** AUTH-020 (3–4) → wave 7; (5) → T-005 · AUTH-007 (1–6) → T-032; (8) → wave 7 · AUTH-024 (1–2) → T-009; (3, 5–6) → T-032 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*AUTH-020 — spec/45-auth/30-account-life.md*
- Given account creation, when the keys are issued, then the recovery key is presented on its own, is not contained in the file AUTH-007 offers, and is not offered to be saved in the same action as the account key.
- Given that screen, when it is shown, then it states that the recovery key is the only way to retire a leaked or stolen account key, that it must be kept somewhere other than the account key, and that losing it loses that ability for good; and creation does not complete until the person confirms they have saved it.
- Given the recovery key displayed, when the person wants to keep it, then it can be copied in one action, and no file of it and no other way of saving it is offered.
- Given account creation, when the person confirms the account key (AUTH-007), then the recovery key is shown on a step of its own that follows, and neither the account key nor its copy and file controls are on that step.
- Given the recovery key step, when the person makes the system back action, then they return to the account key step with the account key displayed in full and its confirmation still given; creation has not completed, so nothing is shown that could be used.
- Given the recovery key step, when the person chooses the cancel control of AUTH-007 (10) and confirms, then nothing of the attempt exists, exactly as from the account key step.

*AUTH-007 — spec/45-auth/20-account-key.md*
- Given creation, when the person leaves it without giving every confirmation it requires — this key, the recovery key (AUTH-020) and the terms of use (LEG-004) — then no account exists at all: the nickname is not consumed (AUTH-018, DOM-001), no slot on the device is occupied (AUTH-001, AUTH-002), and nothing of the attempt is kept anywhere.
- Given the same creation, when it completes, then the recovery key has been issued as its own artefact with its own confirmation (AUTH-020), and the warning above no longer says there is no recovery at all: it says what NGOAL-003 now says.
- Given any step of creation before it completes, when the person chooses its cancel control, or makes the system back action on the first step (AUTH-024), then they are asked to confirm leaving without an account and told that any key already shown or copied will match nothing; when they confirm, they are returned to where they were before the offer of AUTH-001, and nothing of the attempt exists (criterion 7); when they do not, the step stays as it was.

*AUTH-024 — spec/45-auth/20-account-key.md*
- Given the second step confirmed, when the third step is shown, then AUTH-020 governs it, and confirming it completes creation.
- Given the first step, when the person makes the system back action, then AUTH-007 (10) governs it; every step carries the cancel control of AUTH-007 (10).
- Given any step, when the person leaves the application by the home action or the task switcher and returns while its process is still alive, then the same step is shown with the same nickname, keys and confirmations, and creation has not been left in the sense of AUTH-007 (7).
- Given any step, when the operating system ends the application's process before creation completes, then on the next opening creation is not resumed, nothing of the attempt exists (AUTH-007 (7)), and the application opens as it would for a person who declined the offer (AUTH-001 (7)).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-007) — docs/product/verdicts/20260927-T-010-gate1.md; re-sliced against spec/ @ ead1a44 after SD-007 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-010-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-031 — What the ingredient field offers for what the author types

**Requirements:** FLOW-057, L10N-001, A11Y-003, A11Y-005, FLOW-023, FLOW-001
**Status:** gated
**Depends on:** T-008
**Expected files:** `core/src/main/kotlin/app/recipefork/core/text/**` · `core/src/test/kotlin/app/recipefork/core/text/**` · `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/kotlin/app/recipefork/app/dictionary/**` · `app/src/test/kotlin/app/recipefork/app/dictionary/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-one-text-analyser.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Note:** The folding FLOW-057 names — letter case ignored, `ё` and `е` one letter — is the first stage of the analyser the one-text-analyser ADR places in `:core` `text`; this task builds that stage. The offer matches the beginnings of words, not the stemmed tokens of L10N-007 (spec ADR `20260927-the-ingredient-field.md`), so no stemmer is added here.
**Note:** This task builds the ingredient field of the editor — the first of a row's three fields — and the offer it shows as the author types. The quantity and unit fields and the stored row are T-011's; adding free text is T-012's. Every criterion here is observed on the field and its offer alone.
**Criteria of these requirements carried elsewhere:** FLOW-057 (2, 6–8) → T-012 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8 · FLOW-023 (1–2, 4, 7–11) → T-011; (5) → wave 2; (6) → T-012 · FLOW-001 (1) → T-008; (2, 7) → T-011; (3) → T-012; (5) → wave 3; (6) → T-004

**Acceptance criteria** (verbatim)

*FLOW-057 — spec/70-flows/40-editor.md*
- Given a dictionary holding `молоко`, when the author types `мол`, then `молоко` is offered.
- Given the author types `МОЛ` or `Мол`, when entries are offered, then they are those offered for `мол`; and `еж` offers what `ёж` offers.
- Given a parent `курица` with leaves `крылышки` and `филе`, when the author types `кур`, then `крылышки` and `филе` are offered before `курица`.
- Given the interface in Russian and an entry whose English label is `chicken`, when the author types `chick`, then the entry is offered, shown by its Russian label (L10N-003).
- Given a dictionary holding `молоко` and `кокосовое молоко`, when the author types `мол`, then `молоко` is offered before `кокосовое молоко`, because its label begins with the typed text and the other's does not.
- Given a parent `курица` with leaves `филе` and `крылышки`, and an entry `куркума`, when the author types `кур`, then the offer reads `крылышки`, `филе`, `курица`, `куркума`, and no entry appears twice.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

*FLOW-023 — spec/70-flows/40-editor.md*
- Given the ingredient field, when what the author typed matches a parent, then its leaves are offered before it (FLOW-057), so that a qualifier is a last resort and not a substitute for picking the right entry.

*FLOW-001 — spec/70-flows/10-offline-and-sync.md*
- Given no network connection, when the author looks for an ingredient, then the dictionary is searched on the device and offers the same entries it offers with a connection.

**Gate 1:** rejected — docs/product/verdicts/20260927-T-031-gate1.md (FLOW-057 (8) and FLOW-023 (2) not observable here); moved to T-012 and T-011; re-gated: accepted — docs/product/verdicts/20260927-T-031-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-011 — An ingredient is picked from the dictionary in three fields

**Requirements:** FLOW-023, DOM-002, DOM-009, DOM-010, FLOW-001, L10N-003, DOM-014, L10N-001, A11Y-003, A11Y-005, DOM-016
**Status:** gated
**Depends on:** T-031
**Expected files:** `server/src/main/resources/db/migration/V1_1__dictionary_divisibility.sql` · `server/src/main/kotlin/app/recipefork/server/dictionary/**` · `server/src/test/kotlin/app/recipefork/server/dictionary/**` · `core/src/main/kotlin/app/recipefork/core/api/DictionaryContract.kt` · `core/src/main/kotlin/app/recipefork/core/recipe/**` · `!core/src/main/kotlin/app/recipefork/core/recipe/Units.kt` · `core/src/test/kotlin/app/recipefork/core/recipe/**` · `app/src/main/kotlin/app/recipefork/app/data/db/**` · `app/src/main/kotlin/app/recipefork/app/dictionary/**` · `app/src/test/kotlin/app/recipefork/app/dictionary/**` · `app/src/main/kotlin/app/recipefork/app/recipe/**` · `app/src/test/kotlin/app/recipefork/app/recipe/**` · `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Size:** The first task to store an ingredient row, and the one that gives dictionary entries their divisibility from the store to the device; the row's fields, their storage and the flag are one path the criteria observe together, so they are not split. Expected at 500–700 changed lines.
**Note:** The dictionary T-003 and T-004 built holds labels and parents only. An entry's divisibility flag (spec/50-domain-model/20-ingredients-and-limits.md) is added here, end to end: a migration numbered `V1_1` so that it sorts after the dictionaries whatever order the account and recipe migrations land in, the served contract and the device's held copy. The ingredient row's stored shape — entry, qualifier, quantity, unit — is added to the recipe model and the device store here too.
**Criteria of these requirements carried elsewhere:** FLOW-023 (3) → T-031; (5) → wave 2; (6) → T-012 · DOM-002 (2) → wave 2 · DOM-009 (2) → T-012; (3–4) → wave 2 · DOM-010 (1–2) → T-003; (5) → wave 2 · FLOW-001 (1) → T-008; (3) → T-012; (4) → T-031; (5) → wave 3; (6) → T-004 · L10N-003 (1) → T-003; (3–5) → T-018 · DOM-014 (1, 4–5) → wave 8; (3) → T-003 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8 · DOM-016 (3) → wave 2; (4) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-023 — spec/70-flows/40-editor.md*
- Given the editor, when the author adds an ingredient, then exactly three fields are offered: `крылышки` · `500` · `г`, or `тушка` · `2` · `шт`.
- Given the ingredient field, when the author types, then dictionary entries are offered whether they are leaves or parents (DOM-010), so `курица · 500 · г` is an ordinary row.
- Given the author picks the entry `филе` and keeps typing `без кожи`, when the row is read, then it reads `филе без кожи`, the ingredient holds that entry and that qualifier, and no fourth field has appeared.
- Given the unit field, when the author picks `шт`, then the ingredient's unit type is `piece` and the author is never shown a unit type at all.
- Given the unit field, when the author picks the unit that means "to taste", then the quantity field is withdrawn, the unit field stays and shows "to taste", and the ingredient is stored with no quantity (DOM-002).
- Given an ingredient set to "to taste", when the author picks another unit in its unit field, then the quantity field returns, empty.
- Given the unit field, when it is opened, then it offers exactly the units of DOM-016.
- Given a `piece` ingredient whose dictionary entry supplies its divisibility, when the row is shown, then divisibility is not one of the three fields (FLOW-001).

*DOM-002 — spec/50-domain-model/10-invariants.md*
- Given an ingredient of type `taste_unmeasured`, when it is stored, then it has no quantity and no unit, the editor offers no quantity field for it, and its unit field shows "to taste" (FLOW-023).

*DOM-009 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given an ingredient the author picked from the dictionary, when it is stored, then it holds that entry, holds no `free_text_name`, and holds a `qualifier` only if the author wrote one.
- Given a dictionary entry that the product owner removes upstream, when a recipe already refers to it, then the recipe keeps the reference, exactly as FLOW-001 requires of a removed tag.

*DOM-010 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given a recipe naming `крылышки`, when it is stored, then it holds that entry and nothing about `курица` is copied into the recipe.
- Given a recipe whose author had only generic chicken to state, when they pick `курица` itself, then it is accepted as the ingredient's entry.

*FLOW-001 — spec/70-flows/10-offline-and-sync.md*
- Given no network connection, when the author adds a `piece` ingredient whose dictionary entry is marked indivisible, then its divisibility is set from the entry and the author is not asked to set it by hand.
- Given a dictionary entry was removed upstream, when a held recipe already uses it, then the recipe keeps it and the editor stops offering it for new use.

*L10N-003 — spec/160-localization.md*
- Given any entry the author can pick, when the interface language changes, then its label changes with it and the ingredient the author chose does not.

*DOM-014 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given a dictionary changed in the store, when a device next reaches the server, then its held copy is replaced as FLOW-001 already requires, and an entry removed upstream stays resolvable in the recipes that already name it (DOM-009, DOM-010).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

*DOM-016 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given the unit field of the editor, when it is opened, then it offers these twelve units, named in the interface language, and no other.
- Given the author picks `стакан`, when the ingredient is stored, then its unit type is `household`; given `щепотка`, `taste_measured`; given `шт`, `piece`.

**Gate 1:** blocked-by-spec (SD-008, SD-006) — docs/product/verdicts/20260927-T-011-gate1.md; re-sliced against spec/ @ ead1a44 after SD-008, SD-006 resolved; re-gated: rejected — docs/product/verdicts/20260927-T-011-gate1-2.md (the offer rule of FLOW-057 came after the task that offers); FLOW-023 (2, 3) and FLOW-001 (4) moved to T-031, which now precedes this task; re-gated: accepted — docs/product/verdicts/20260927-T-011-gate1-3.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-012 — An ingredient outside the dictionary is kept as free text with a warning

**Requirements:** FLOW-022, FLOW-023, DOM-009, FLOW-001, L10N-001, A11Y-003, A11Y-005, FLOW-057
**Status:** gated
**Depends on:** T-011
**Expected files:** `core/src/main/kotlin/app/recipefork/core/recipe/**` · `!core/src/main/kotlin/app/recipefork/core/recipe/Units.kt` · `core/src/test/kotlin/app/recipefork/core/recipe/**` · `app/src/main/kotlin/app/recipefork/app/data/db/**` · `app/src/main/kotlin/app/recipefork/app/recipe/**` · `app/src/test/kotlin/app/recipefork/app/recipe/**` · `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** FLOW-022 (4) → wave 2 · FLOW-023 (1–2, 4, 7–11) → T-011; (3) → T-031; (5) → wave 2 · DOM-009 (1, 5) → T-011; (3–4) → wave 2 · FLOW-001 (1) → T-008; (2, 7) → T-011; (4) → T-031; (5) → wave 3; (6) → T-004 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8 · FLOW-057 (1, 3–5, 9–10) → T-031

**Acceptance criteria** (verbatim)

*FLOW-022 — spec/70-flows/40-editor.md*
- Given the ingredient editor, when the author types a name that is not exactly the label of an offered entry, as FLOW-057 compares them, then adding it as free text is offered — beside any entries offered, or alone where none is (FLOW-057) — and is not refused.
- Given the author adds it, when the warning is shown, then it states both that filters will not find the recipe by this ingredient and that publication makes it permanent (CON-003).
- Given a recipe that holds a free-text ingredient, when the author later finds a matching entry while the recipe is still editable, then the ingredient can be replaced by the entry and the warning is gone.
- Given a `piece` ingredient added as free text, when it is added, then the author is asked for its divisibility, because no entry supplies it (FLOW-001).

*FLOW-023 — spec/70-flows/40-editor.md*
- Given the ingredient field, when what the author typed is not exactly the label of an offered entry, then free text is offered with the warning FLOW-022 requires (FLOW-057).

*DOM-009 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given an ingredient the author added in their own words (FLOW-057), when it is stored, then it holds a `free_text_name`, no entry and no `qualifier`.

*FLOW-001 — spec/70-flows/10-offline-and-sync.md*
- Given no network connection, when the author adds a `piece` ingredient as free text, then the author sets its divisibility, because there is no entry to take it from.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

*FLOW-057 — spec/70-flows/40-editor.md*
- Given the same dictionary and no entry for coconut milk, when the author types `кокосовое молоко`, then `молоко` is not offered, because `кокосовое` begins no word of its label, and adding `кокосовое молоко` as free text is offered.
- Given entries are offered, when the typed text is not exactly the label of one of them, then adding it as free text is offered beside them, with the warning of FLOW-022, and the author chooses.
- Given the author has typed exactly the label of an offered entry, when the offer is shown, then free text is not offered, because the entry is that ingredient.
- Given the interface in Russian and an entry `молоко` whose English label is `milk`, when the author types `Молоко`, ` молоко ` or `milk`, then the entry is offered and free text is not.

**Gate 1:** blocked-by-spec (SD-006) — docs/product/verdicts/20260927-T-012-gate1.md; re-sliced against spec/ @ ead1a44 after SD-006 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-012-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-013 — Steps are written in the editor and survive a restart offline

**Requirements:** FLOW-024, FLOW-002, L10N-001, A11Y-003, A11Y-005, DOM-015
**Status:** gated
**Depends on:** T-012
**Expected files:** `core/src/main/kotlin/app/recipefork/core/recipe/**` · `!core/src/main/kotlin/app/recipefork/core/recipe/Units.kt` · `core/src/test/kotlin/app/recipefork/core/recipe/**` · `app/src/main/kotlin/app/recipefork/app/data/db/**` · `app/src/main/kotlin/app/recipefork/app/recipe/**` · `app/src/test/kotlin/app/recipefork/app/recipe/**` · `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** FLOW-024 (1) → wave 5; (3, 5) → wave 4; (4, 6) → T-008 · FLOW-002 (2–3) → T-008; (4) → T-028 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8 · DOM-015 (3) → wave 2; (4) → T-018

**Acceptance criteria** (verbatim)

*FLOW-024 — spec/70-flows/40-editor.md*
- Given a half-filled recipe, when the author scrolls to the steps without having entered a serving count, then the steps accept input.

*FLOW-002 — spec/70-flows/10-offline-and-sync.md*
- Given no network connection, when the owner creates a recipe and fills its title, servings, ingredients and steps, then every change is kept and is present after the application is closed and reopened.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

*DOM-015 — spec/50-domain-model/10-invariants.md*
- Given the serving count field of the editor, when the author tries to enter 0, a negative number, or a number with a fractional part such as 2.5 or ½, then the field does not hold it.
- Given that field, when the author enters 1, 4 or 1000, then it holds that value.

**Gate 1:** rejected — docs/product/verdicts/20260927-T-013-gate1.md; re-sliced; blocked-by-spec (SD-013) — docs/product/verdicts/20260927-T-013-gate1-2.md; re-sliced against spec/ @ ead1a44 after SD-013 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-013-gate1-3.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-014 — A step carries one timer, named by its step unless the author names it

**Requirements:** FLOW-027, DOM-013, GUIDE-007, L10N-001, A11Y-003, A11Y-005
**Status:** blocked
**Depends on:** T-013
**Expected files:** `core/src/main/kotlin/app/recipefork/core/recipe/**` · `!core/src/main/kotlin/app/recipefork/core/recipe/Units.kt` · `core/src/test/kotlin/app/recipefork/core/recipe/**` · `app/src/main/kotlin/app/recipefork/app/data/db/**` · `app/src/main/kotlin/app/recipefork/app/recipe/**` · `app/src/test/kotlin/app/recipefork/app/recipe/**` · `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `core/src/main/kotlin/app/recipefork/core/timer/**` · `core/src/test/kotlin/app/recipefork/core/timer/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** FLOW-027 (8) → T-017; (10) → T-016 · DOM-013 (5) → T-024 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-027 — spec/70-flows/45-steps-timers-references.md*
- Given a step reading `в духовку на 190 °C на 30 минут`, when the author attaches a timer and enters 30 minutes, then the timer belongs to that step.
- Given a step that already carries a timer, when the author looks at it, then the control for attaching a timer is still shown, and choosing it creates no second timer and shows the statement of GUIDE-007 (DOM-013).
- Given two waits in sequence, when the author writes them, then they are two steps with one timer each.
- Given a timer being attached, when the author is asked for anything, then a duration is the only thing required of them.
- Given a timer with no name of its own, when the author looks at it, then the name it will announce itself by is shown, derived from the step (FLOW-016), and correcting it by hand is offered there.
- Given the author corrects that name, when they leave it, then the timer holds what they wrote and stops following the step's text.
- Given a name the author corrected, when they clear it, then the timer's name follows the step's text again and nothing of what they had typed is kept.
- Given a step with a timer, when the step is deleted, then its timer is deleted with it.
- Given a duration the author has not entered, when the recipe is saved, then the timer is not stored, because a timer without a duration is not a timer.

*DOM-013 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given a step with no timer, when a timer is attached to it, then it is accepted.
- Given a step that already carries a timer, when another is attached, then the attachment is refused and the step still carries exactly one.
- Given two waits in sequence — thirty minutes covered, then fifteen uncovered — when the author writes them, then they are two steps with one timer each, and nothing in the product joins them back together.
- Given a step whose timer the author deleted, when a new timer is attached, then it is accepted.

*GUIDE-007 — spec/75-author-guidance.md*
- Given a step that already carries a timer, when the author chooses the control for attaching a timer on it (FLOW-027), then the statement is shown and no second timer is created (DOM-013).
- Given that statement, when it is shown, then it names splitting the step as what to do instead, so the author is not left with a refusal and no way forward.
- Given a step with no timer, when a timer is attached, then nothing is shown.
- Given the statement, when the author takes their next action in the editor, then the statement is gone, the step is unchanged, and the timer it already had still holds its duration; the statement has no control that closes it (GUIDE-005).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-005) — docs/product/verdicts/20260927-T-014-gate1.md; re-sliced against spec/ @ ead1a44 after SD-005 resolved; re-gated: blocked-by-spec (SD-030, the timer's duration field and GUIDE-007's next action) — docs/product/verdicts/20260927-T-014-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-015 — A quantity goes into a step as a reference

**Requirements:** FLOW-031, FLOW-032, DOM-007, GUIDE-008, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-014, T-019
**Expected files:** `core/src/main/kotlin/app/recipefork/core/recipe/**` · `!core/src/main/kotlin/app/recipefork/core/recipe/Units.kt` · `core/src/test/kotlin/app/recipefork/core/recipe/**` · `app/src/main/kotlin/app/recipefork/app/data/db/**` · `app/src/main/kotlin/app/recipefork/app/recipe/**` · `app/src/test/kotlin/app/recipefork/app/recipe/**` · `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml` · `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Note:** A reference to an ingredient that holds no quantity — none entered yet, or `taste_unmeasured` — is offered, inserted, kept and rendered here, in the editor and in the saved view of FLOW-029, so this task follows T-019, where the card and its derived marker exist.
**Criteria of these requirements carried elsewhere:** FLOW-032 (1–4) → T-021 · DOM-007 (1) → T-021 · GUIDE-008 (5) → wave 2 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-031 — spec/70-flows/45-steps-timers-references.md*
- Given a step being written, when the author types the trigger character, then the recipe's ingredients are offered and choosing one inserts a reference at that position.
- Given the same step, when the author uses the control on the step instead, then the same list is offered and the result is the same reference, so an author who never discovers the trigger character loses nothing.
- Given the list of ingredients offered, when it is shown, then it holds only ingredients of this recipe (DOM-007), including one whose quantity is not yet entered and one whose unit type is `taste_unmeasured`.
- Given an inserted reference, when the author deletes the character range it occupies, then the reference is gone and the step's text is left as the author typed it.
- Given a reference to an ingredient, when that ingredient is deleted from the recipe, then DOM-007 governs and the reference is dropped.
- Given a referenced ingredient, when the author changes its unit to "to taste" or from it to a measured unit, then the change is allowed and every reference to it stays and renders by FLOW-032.

*FLOW-032 — spec/70-flows/45-steps-timers-references.md*
- Given the editor, when the author inserts a reference, then what they see in place of it is the authored quantity, because the authored scale is the recipe's own.
- Given a reference to a `taste_unmeasured` ingredient, when the step is read at any scale, then the reference reads "to taste" in the interface language (`по вкусу` in Russian), and nothing in it is marked as derived.
- Given a reference to an ingredient whose quantity is not yet entered, when the step is shown in the editor or in the saved view of FLOW-029, then the reference shows a visibly empty placeholder, followed by the unit where one is chosen; the placeholder occupies a character range and is removed as FLOW-031 (4) removes any reference.

*DOM-007 — spec/50-domain-model/10-invariants.md*
- Given an ingredient that a step references, when the author deletes that ingredient before the recipe becomes externally visible, then the reference is removed from the step text in the same action.

*GUIDE-008 — spec/75-author-guidance.md*
- Given a recipe holding at least one ingredient, when the author begins its first step, then the statement is shown.
- Given the statement, when it is shown, then it names both ways of inserting a reference that FLOW-031 offers — the trigger character and the control on the step.
- Given a recipe that holds no ingredient yet, when the author begins a step, then nothing is shown, because there is nothing to reference.
- Given the statement, when the decision to show it is taken, then no step's text was read to take it: the trigger is that the first step is being written and that the recipe has ingredients, never what the author has typed (NGOAL-007, GUIDE-005).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-014) — docs/product/verdicts/20260927-T-015-gate1.md; GUIDE-008 (5) moved to wave 2, where submission exists; re-sliced against spec/ @ ead1a44 after SD-014 resolved; re-gated: rejected — docs/product/verdicts/20260927-T-015-gate1-2.md (FLOW-032 (6, 7) came after the references they render); moved here from T-021; re-gated: accepted — docs/product/verdicts/20260927-T-015-gate1-3.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-016 — Steps and ingredients are inserted anywhere and moved, by dragging or by a discrete action

**Requirements:** FLOW-025, FLOW-027, A11Y-006, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-015
**Expected files:** `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Note:** spec paths updated to spec/ @ ead1a44; criteria unchanged word for word, so the gate-1 verdict stands.
**Criteria of these requirements carried elsewhere:** FLOW-027 (1–7, 9, 11) → T-014; (8) → T-017 · A11Y-006 (2) → wave 8; (3) → wave 2 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-025 — spec/70-flows/40-editor.md*
- Given a recipe with five steps, when the author inserts a step between the second and the third, then it takes that position and the ones after it move down.
- Given a recipe with five steps, when the author drags the fourth above the second, then the order is as dragged and no step's content changed.
- Given a step that references an ingredient, when either is reordered, then the reference still resolves to the same ingredient (DOM-007).
- Given a timer inside a step, when the step is moved, then the timer moves with it (FLOW-027).
- Given an ingredient that a step references, when the author deletes that ingredient, then the reference is dropped as DOM-007 already requires.
- Given a user who cannot drag, when they reorder a step or an ingredient, then a discrete action does the same thing (A11Y-006).

*FLOW-027 — spec/70-flows/45-steps-timers-references.md*
- Given a step with a timer, when the step is moved, then the timer moves with it (FLOW-025).

*A11Y-006 — spec/200-accessibility.md*
- Given the editor's reordering of steps and ingredients (FLOW-025), when a screen reader or a user who cannot drag operates it, then each item can be moved up and down by a discrete action.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** accepted — docs/product/verdicts/20260927-T-016-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-017 — A part below the minimum or over a limit is marked on leaving it

**Requirements:** FLOW-028, FLOW-027, DOM-012, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-016
**Expected files:** `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `core/src/main/kotlin/app/recipefork/core/recipe/Limits.kt` · `core/src/test/kotlin/app/recipefork/core/recipe/LimitsTest.kt` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** FLOW-028 (4, 6) → wave 2 · FLOW-027 (1–7, 9, 11) → T-014; (10) → T-016 · DOM-012 (1–7) → wave 2 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-028 — spec/70-flows/40-editor.md*
- Given an empty ingredient list, when the author moves from it to the steps, then the ingredient list is marked as still needing at least one ingredient.
- Given a warning of that kind, when the author keeps typing anywhere in the recipe, then nothing is blocked and no input is refused.
- Given a part that was marked, when the author completes it, then the mark is gone without the author asking for it.
- Given a step that passes 2 000 characters, when the author leaves it, then it is marked as over its limit and the text they typed is kept in full, not truncated.
- Given a field the author has not yet visited, when the editor opens, then it is not marked, because nothing has been left.

*FLOW-027 — spec/70-flows/45-steps-timers-references.md*
- Given a name the author is typing, when it passes 60 characters, then FLOW-028 governs — it is marked on leaving the field and refused at submission (DOM-012), never refused while typing.

*DOM-012 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given a recipe over a limit, when the author keeps editing it, then editing is not blocked and the recipe stays in its state (FLOW-028).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-015) — docs/product/verdicts/20260927-T-017-gate1.md; GUIDE-006 and GUIDE-005 moved to T-034; re-sliced against spec/ @ ead1a44 after SD-015 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-017-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-018 — A saved recipe opens as the card a reader sees, at the serving count chosen

**Requirements:** FLOW-029, FLOW-019, FLOW-038, SCALE-011, SCALE-012, DOM-003, DOM-011, L10N-003, A11Y-007, A11Y-003, L10N-001, A11Y-005, L10N-009, DOM-015, FLOW-017
**Status:** blocked
**Depends on:** T-008, T-029
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/main/kotlin/app/recipefork/app/ui/nav/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Size:** The card is the screen the product exists for and its first version carries the layout every later card task builds on. Expected at 450–550 changed lines.
**Criteria of these requirements carried elsewhere:** FLOW-019 (4) → wave 3 · FLOW-038 (3–4) → wave 5; (5) → T-007; (7) → wave 3 · SCALE-011 (4) → T-020 · DOM-011 (2–3) → wave 2; (4) → wave 8 · L10N-003 (1) → T-003; (2) → T-011 · A11Y-007 (3) → T-033; (4) → T-025; (5) → wave 8 · A11Y-003 (3–4) → wave 8 · L10N-001 (3) → release record · A11Y-005 (2–6) → wave 8 · L10N-009 (1–4, 6) → T-029 · DOM-015 (1–2) → T-013; (3) → wave 2 · FLOW-017 (1, 3–7) → wave 3

**Acceptance criteria** (verbatim)

*FLOW-029 — spec/70-flows/40-editor.md*
- Given a recipe the author saves, when it is opened afterwards, then it is the same card a reader sees (FLOW-019, FLOW-020), not an editor with reader styling.
- Given that view of a recipe that is still editable, when the author chooses to edit, then the editor opens on the same recipe.
- Given that view, when the author looks for an edit control on the card itself, then there is none: the card is read, the editor is written, and they are two screens.
- Given a recipe that is no longer editable by `entity-states`, when the author opens it, then no way into the editor is offered at all.

*FLOW-019 — spec/70-flows/30-cooking.md*
- Given a recipe with no media at all, when its card is opened, then no image frame, no placeholder image and no empty media area is shown.
- Given a recipe with an empty description, when its card is opened, then no description heading and no empty block is shown.
- Given a recipe whose parent is not shown by FORK-005, when its card is opened, then no provenance area is shown at all.
- Given a recipe reduced to the minimum DOM-006 accepts, when its card is opened, then it reads as a complete recipe and not as a broken one.

*FLOW-038 — spec/70-flows/50-browsing.md*
- Given no network connection, when the owner opens the own-recipes view, then every recipe they own is listed, carries its state marker, and opens in full with its steps, ingredients and timers.
- Given no network connection, when the owner scales an own published recipe, then `scaling-rules` applies to it exactly as to a favourite.
- Given a recipe a moderator removed, when the owner opens the view offline, then it is still listed and still opens, because its owner may read it.

*SCALE-011 — spec/80-scaling-rules/10-scaling.md*
- Given the serving count control, when the reader takes the count below one serving, then the values it can hold are ¼, ⅓, ½, ⅔ and ¾ and no others.
- Given the serving count control, when the reader takes the count to one serving or above, then it can hold whole numbers only.
- Given the serving count control at ¼ serving, when the reader tries to go lower, then the count stays at ¼.

*SCALE-012 — spec/80-scaling-rules/20-display.md*
- Given an ingredient displayed as 10 000 000 pieces, when its line is shown in a window 320 dp wide (QUAL-011) with the device text scale at 200 per cent (A11Y-004), then every digit is visible and no ellipsis replaces part of the number.
- Given that same line, when the ingredient name does not fit beside the quantity, then the name wraps to another line and remains fully readable.
- Given an `exact` ingredient above 1000 kg, when its line is shown, then it is displayed in tonnes by the rounding grid, which is a change of unit and not an abbreviation of the number.
- Given a `household` ingredient of 2 000 000 tablespoons, when its line is shown, then it is displayed in tablespoons in full and is not converted to any other unit.

*DOM-003 — spec/50-domain-model/10-invariants.md*
- Given an ingredient measured in tablespoons, when the recipe is displayed at any scale, then its quantity is shown in tablespoons only.

*DOM-011 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given an ingredient of 3 tablespoons, when the recipe is displayed at any scale, then it is displayed in tablespoons and no mass appears anywhere (DOM-003).

*L10N-003 — spec/160-localization.md*
- Given a recipe written in one language, when it is read in the other, then its ingredient names and its tags are shown in the reading language, because both are dictionary references and not the author's text (DOM-009, CAT-006).
- Given a free-text ingredient (DOM-009), when it is read in either language, then it appears exactly as the author typed it and is translated by nothing.
- Given a qualifier (FLOW-023), when it is read, then the same applies: it is the author's text.

*A11Y-007 — spec/200-accessibility.md*
- Given the cooking card, when it is opened with the device at its default font scale, then its step text is larger than body text elsewhere in the product.
- Given the cooking card, when its controls are measured, then A11Y-003's larger figure holds.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.
- Given the cooking card, when its controls are measured — the timer controls and the serving control — then each is at least 56 by 56 dp. There is no step advance among them: the card is one scrolling screen (`flows`, 30-cooking).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

*L10N-009 — spec/160-localization.md*
- Given a recipe declared in one language and the interface in the other, when a quantity is displayed, then it follows the interface language, not the recipe's.

*DOM-015 — spec/50-domain-model/10-invariants.md*
- Given any authored serving count, when a reader later returns the recipe to it (SCALE-001, FLOW-017), then it is a count the serving count control can hold (SCALE-011), because every whole number of one or more is one.

*FLOW-017 — spec/70-flows/30-cooking.md*
- Given the setting has never been set, when the reader opens a recipe, then it opens at the count its author wrote and nothing is marked as derived.

**Gate 1:** blocked-by-spec (SD-016, SD-002, SD-013) — docs/product/verdicts/20260927-T-018-gate1.md; A11Y-003 (2) and A11Y-007 (2) added after this verdict; re-sliced against spec/ @ ead1a44 after SD-016, SD-002, SD-013 resolved; re-gated: blocked-by-spec (SD-031, where a saved recipe's edit is chosen) — docs/product/verdicts/20260927-T-018-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-019 — A derived quantity says so, and which way it was rounded

**Requirements:** CON-004, SCALE-004, A11Y-008, L10N-001, A11Y-003, A11Y-005, SCALE-013, SCALE-014, SCALE-015
**Status:** gated
**Depends on:** T-018, T-030
**Expected files:** `core/src/main/kotlin/app/recipefork/core/scaling/**` · `core/src/test/kotlin/app/recipefork/core/scaling/**` · `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** A11Y-008 (2) → wave 6; (3) → T-007; (4) → wave 8 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8 · SCALE-013 (1–2, 4) → T-029 · SCALE-014 (1–3, 5) → T-029 · SCALE-015 (3, 6) → T-001; (4–5) → T-002

**Acceptance criteria** (verbatim)

*CON-004 — spec/30-constraints.md*
- Given a recipe scaled to a serving count other than the author's, when its ingredient list is displayed, then every quantity is visibly marked as derived, including one whose displayed value equals the authored quantity.
- Given a derived quantity that was rounded, when it is displayed, then the interface states whether the displayed value is above or below the exact computed value.
- Given a recipe displayed at its authored serving count, when its ingredient list is displayed, then no quantity is marked as derived.

*SCALE-004 — spec/80-scaling-rules/20-display.md*
- Given a scaled recipe, when its list is displayed, then every quantity carries a derived marker, including one whose displayed value equals its authored value.
- Given 3 indivisible pieces at a factor of 0.6, when the line is displayed, then it shows 2 pieces and states that the exact value was lower.
- Given a recipe authored for 4 servings with 1 indivisible egg, when the reader asks for 2 servings, then the line shows 1 egg, is marked as derived, and states that the exact value was lower.
- Given 1 teaspoon at a factor of 0.2, when the line is displayed, then it shows ¼ teaspoon and states that the exact value was lower.
- Given a quantity that the grid represented exactly, when it is displayed, then it is marked as derived but carries no direction.

*A11Y-008 — spec/200-accessibility.md*
- Given a derived quantity (CON-004), when it is displayed, then what marks it as derived is not colour alone.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

*SCALE-013 — spec/80-scaling-rules/20-display.md*
- Given an `exact` quantity computed as 1 234 567 g, when it is displayed, then it shows 1.235 t and states that the exact value was lower (SCALE-004).

*SCALE-014 — spec/80-scaling-rules/20-display.md*
- Given 1 divisible onion at a factor of 2.3, when the line is displayed, then it shows 2½ onions and states that the exact value was lower.

*SCALE-015 — spec/80-scaling-rules/20-display.md*
- Given an `exact` quantity computed as 4.5 g, when it is displayed, then it shows 5 g and states that the exact value was lower.
- Given an `exact` quantity computed as 4.4 g, when it is displayed, then it shows 4 g and states that the exact value was higher.

**Gate 1:** blocked-by-spec (SD-012) — docs/product/verdicts/20260927-T-019-gate1.md; re-sliced against spec/ @ ead1a44 after SD-012 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-019-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-020 — The reader fixes an ingredient on the card

**Requirements:** SCALE-002, SCALE-006, SCALE-008, SCALE-009, SCALE-011, A11Y-003, A11Y-007, L10N-001, A11Y-005
**Status:** blocked
**Depends on:** T-019, T-030, T-015
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Note:** Open for the team lead at the re-slice after SD-032: no criterion here observes on the card the "fixed" marking of a line or the release of a fixing, which SCALE-016 and SCALE-017 assume and T-030 carries in `:core` only (gate-1 verdict -2, unknown 3).
**Criteria of these requirements carried elsewhere:** SCALE-002 (1, 3) → T-002 · SCALE-006 (1, 3) → T-002 · SCALE-008 (1–2, 4–5) → T-002 · SCALE-011 (1–3) → T-018 · A11Y-003 (3–4) → wave 8 · A11Y-007 (1) → T-018; (3) → T-033; (4) → T-025; (5) → wave 8 · L10N-001 (3) → release record · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*SCALE-002 — spec/80-scaling-rules/10-scaling.md*
- Given that state, when the serving count is displayed, then it is shown as an approximate 2 servings and is marked as approximate.

*SCALE-006 — spec/80-scaling-rules/10-scaling.md*
- Given that state, when the shortfall is displayed, then it names both the required and the available amount.

*SCALE-008 — spec/80-scaling-rules/10-scaling.md*
- Given any serving count displayed as a fraction, when it is displayed, then it carries the same approximate marking that SCALE-002 requires.

*SCALE-009 — spec/80-scaling-rules/20-display.md*
- Given a recipe scaled so that exactly one ingredient is held at its floor by SCALE-005, when the recipe is displayed, then no warning is shown.
- Given a recipe scaled so that two ingredients are held at their floors, when the recipe is displayed, then the warning is shown.
- Given a recipe scaled so that two ingredients are held at their floors, when the reader returns the recipe to its authored scale, then the warning disappears.
- Given the warning is shown, when the reader reads the ingredient list, then every affected line still carries its own rounded-up marking from SCALE-005.

*SCALE-011 — spec/80-scaling-rules/10-scaling.md*
- Given a count requested as a fraction, when the recipe is displayed, then the count is displayed by SCALE-008 in the same form it was requested.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.
- Given the cooking card, when its controls are measured — the timer controls and the serving control — then each is at least 56 by 56 dp. There is no step advance among them: the card is one scrolling screen (`flows`, 30-cooking).

*A11Y-007 — spec/200-accessibility.md*
- Given the cooking card, when its controls are measured, then A11Y-003's larger figure holds.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-003, SD-011, SD-002) — docs/product/verdicts/20260927-T-020-gate1.md; A11Y-003 (2) and A11Y-007 (2) added after this verdict; re-sliced against spec/ @ ead1a44 after SD-003, SD-011, SD-002 resolved; re-gated: blocked-by-spec (SD-032, whether a requested fractional count is marked approximate) — docs/product/verdicts/20260927-T-020-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-021 — A reference in a step reads as the quantity the list shows

**Requirements:** SCALE-007, FLOW-032, DOM-007, CON-004, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-020
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** FLOW-032 (5–7) → T-015 · DOM-007 (2) → T-015 · CON-004 (1, 3) → T-019 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*SCALE-007 — spec/80-scaling-rules/20-display.md*
- Given a scaled recipe, when a step referencing an ingredient is read, then the quantity in the step equals the quantity in the list, including its rounding and its derived marker.
- Given a recipe at its authored scale, when a step is read, then the referenced quantity equals the authored one.

*FLOW-032 — spec/70-flows/45-steps-timers-references.md*
- Given a recipe authored for 4 servings with 200 g of flour and a step reading `всыпьте [200 г] муки`, when the reader asks for 8 servings, then the step reads `всыпьте 400 г муки` and the word `муки` is the author's own text, untouched.
- Given the same step at any scale, when it is read, then the value in the reference equals the value the ingredient list shows for that ingredient (DOM-007).
- Given a scaled value that the rounding grid changed, when the step is read, then the reference carries the same derived marker the list carries (SCALE-004), so the step never states a rounded number as though it were exact.
- Given a value held at the floor of its unit type, when the step is read, then it shows the floor exactly as the list does (SCALE-005).

*DOM-007 — spec/50-domain-model/10-invariants.md*
- Given a step containing a reference, when the step is displayed, then the referenced quantity equals the quantity shown for that ingredient in the ingredient list at the current scale.

*CON-004 — spec/30-constraints.md*
- Given a derived quantity that was rounded, when it is displayed, then the interface states whether the displayed value is above or below the exact computed value.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** rejected — docs/product/verdicts/20260927-T-021-gate1.md; CON-004 (2) added; blocked-by-spec (SD-012, SD-014) — docs/product/verdicts/20260927-T-021-gate1-2.md; re-sliced against spec/ @ ead1a44 after SD-012, SD-014 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-021-gate1-3.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-022 — A timer runs on its step

**Requirements:** FLOW-013, A11Y-003, A11Y-007, L10N-001, A11Y-005
**Status:** gated
**Depends on:** T-021, T-014, T-010
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/kotlin/app/recipefork/app/timer/**` · `app/src/test/kotlin/app/recipefork/app/timer/**` · `app/src/main/kotlin/app/recipefork/app/AppContainer.kt` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml` · `app/src/main/res/values/strings_timer.xml` · `app/src/main/res/values-ru/strings_timer.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** FLOW-013 (1–4, 7) → T-023; (5, 8–13) → T-033 · A11Y-003 (3–4) → wave 8 · A11Y-007 (1) → T-018; (3) → T-033; (4) → T-025; (5) → wave 8 · L10N-001 (3) → release record · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-013 — spec/70-flows/30-cooking.md*
- Given a running timer, when the reader opens the recipe, then the time remaining is shown on its step.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.
- Given the cooking card, when its controls are measured — the timer controls and the serving control — then each is at least 56 by 56 dp. There is no step advance among them: the card is one scrolling screen (`flows`, 30-cooking).

*A11Y-007 — spec/200-accessibility.md*
- Given the cooking card, when its controls are measured, then A11Y-003's larger figure holds.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** rejected — docs/product/verdicts/20260927-T-022-gate1.md; FLOW-013 (7) moved to T-023, re-gate due; checked against spec/ @ ead1a44; re-gated: accepted — docs/product/verdicts/20260927-T-022-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-023 — A timer announces itself when the application is closed

**Requirements:** FLOW-013, FLOW-016, EXT-009, L10N-001, A11Y-003, A11Y-005, A11Y-007
**Status:** gated
**Depends on:** T-022, T-026
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml` · `core/src/main/kotlin/app/recipefork/core/timer/**` · `core/src/test/kotlin/app/recipefork/core/timer/**` · `app/src/main/kotlin/app/recipefork/app/timer/**` · `app/src/test/kotlin/app/recipefork/app/timer/**` · `app/src/main/AndroidManifest.xml` · `app/src/main/kotlin/app/recipefork/app/MainActivity.kt` · `app/src/main/kotlin/app/recipefork/app/ui/nav/**` · `app/src/main/res/values/strings_timer.xml` · `app/src/main/res/values-ru/strings_timer.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Note:** The cut of FLOW-016 (3–8) is built here as a function in `:core` `timer`, so that T-034's editor display shows exactly the text this notification carries.
**Criteria of these requirements carried elsewhere:** FLOW-013 (5, 8–13) → T-033; (6) → T-022 · FLOW-016 (1–2, 9–11) → T-024 · EXT-009 (2) → wave 8; (3) → wave 2 · L10N-001 (3) → release record · A11Y-003 (3–4) → wave 8 · A11Y-005 (2–6) → wave 8 · A11Y-007 (1) → T-018; (3) → T-033; (4) → T-025; (5) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-013 — spec/70-flows/30-cooking.md*
- Given a timer started on a step, when the reader closes the application and the timer ends, then it announces itself.
- Given the screen is locked when the timer ends, then it announces itself.
- Given the announcement, when it is displayed, then it names the recipe and the step, so that timers from two recipes are told apart.
- Given the announcement, when the reader acts on it, then the application opens at the step the timer belongs to.
- Given a running timer, when the reader cancels it, then nothing announces itself afterwards.

*FLOW-016 — spec/70-flows/30-cooking.md*
- Given a timer whose author gave it no name, when it ends, then the announcement carries the recipe's title and the first 60 characters of its step's text, cut at a word boundary, with an ellipsis where the text was longer.
- Given a timer on a step of 60 characters or fewer and with no name of its own, when it ends, then the step's whole text is in the announcement and nothing marks it as cut.
- Given a timer whose author named it, when it ends, then the announcement carries the recipe's title and that name, and no part of the step's text.
- Given a timer whose author named it, when its step's text is later changed, then the name is unchanged, because it was overridden and no longer follows the step.
- Given a timer whose author gave it no name, when its step's text is changed, then what the announcement will say changes with it, because the name is derived and not stored.
- Given a step whose first 60 characters hold an ingredient reference, when a derived name is built from it, then the reference reads there exactly as it reads on the card, at the scale the reader is using (FLOW-032).

*EXT-009 — spec/130-external-interfaces.md*
- Given a timer, when it ends while the application is closed, then the announcement comes from the device itself (FLOW-013).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.
- Given the cooking card, when its controls are measured — the timer controls and the serving control — then each is at least 56 by 56 dp. There is no step advance among them: the card is one scrolling screen (`flows`, 30-cooking).

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

*A11Y-007 — spec/200-accessibility.md*
- Given the cooking card, when its controls are measured, then A11Y-003's larger figure holds.

**Gate 1:** blocked-by-spec (SD-021) — docs/product/verdicts/20260927-T-023-gate1.md; FLOW-013 (7) added from T-022 after this verdict, re-gate due with the spec fix; FLOW-013 (5) and the permission criteria moved to T-033; re-sliced against spec/ @ ead1a44 after SD-021 resolved, re-gate due; re-gated: rejected — docs/product/verdicts/20260927-T-023-gate1-2.md (the card's cancel control not held to 56 dp); A11Y-003 (2) and A11Y-007 (2) added; re-gated: accepted — docs/product/verdicts/20260927-T-023-gate1-3.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-034 — A long step with an unnamed timer is shown what its notification will say

**Requirements:** GUIDE-006, GUIDE-005, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-017, T-023
**Expected files:** `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Note:** GUIDE-006 (1) shows the text "cut exactly as FLOW-016 cuts it". That cut — FLOW-016 (3–8), including a step of 60 characters or fewer announced whole (4) and a reference rendered as the card reads it before the cut (8), which rests on FLOW-032 (1–4) in T-021 — is built and tested in T-023, in `:core` `timer`. This task follows T-023 and uses that function unchanged; it builds no cut of its own.
**Criteria of these requirements carried elsewhere:** GUIDE-005 (1, 6) → wave 2; (4) → wave 8 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*GUIDE-006 — spec/75-author-guidance.md*
- Given such a step, when the author leaves it, then what is shown is the text the notification will carry at the authored serving count, cut exactly as FLOW-016 cuts it, with the ellipsis.
- Given the step holds a reference within the text shown, when the display is read, then it states that the quantities in it follow the reader's serving count, so the reader's notification may read and be cut differently.
- Given that display, when the author reads it, then it states that this is what the notification will say and offers to name the timer differently, and it does not tell the author to rewrite the step.
- Given that display, when the author names the timer from it, then the timer holds that name and the display does not appear for this step again while the name is set (FLOW-016).
- Given a step of 60 characters or fewer at the authored serving count, holding no reference, carrying a timer, when the author leaves it, then nothing is shown, because nothing will be cut.
- Given a step of 55 characters at the authored serving count whose opening holds a reference, carrying an unnamed timer, when the author leaves it, then the display is shown, because a reader's larger count can lengthen it past the cut.
- Given a step of more than 60 characters carrying no timer, when the author leaves it, then nothing is shown, because no notification will be built from it.
- Given a timer the author already named, when they leave its step however long, then nothing is shown, because the step's length no longer decides anything.
- Given an unnamed timer whose step the author rewrites so the decisive words come first, when they leave it again, then the new opening is shown, because this reports a fact and not an error that was corrected.
- Given the display, when it is shown, then nothing is refused and the step is stored exactly as typed: submission does not depend on it (FLOW-027, DOM-012).

*GUIDE-005 — spec/75-author-guidance.md*
- Given a hint, when it is shown, then it blocks nothing, refuses nothing and delays nothing the author was doing.
- Given a hint, when it is shown, then no record is kept of its having been seen: whether it is shown is recomputed from the recipe and the author's action every time, and there is nothing to dismiss.
- Given a hint and a warning of FLOW-028 that apply at the same moment, when both apply, then both are shown and neither replaces the other.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** rejected — docs/product/verdicts/20260927-T-034-gate1.md (FLOW-016's cut not named and not yet built); now follows T-023, which builds it; re-gated: accepted — docs/product/verdicts/20260927-T-034-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-033 — A timer asks for notifications when first started, and a refusal is explained at once

**Requirements:** FLOW-013, A11Y-007, A11Y-003, L10N-001, A11Y-005
**Status:** gated
**Depends on:** T-023
**Expected files:** `app/src/main/kotlin/app/recipefork/app/timer/**` · `app/src/test/kotlin/app/recipefork/app/timer/**` · `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/AndroidManifest.xml` · `app/src/main/kotlin/app/recipefork/app/MainActivity.kt` · `app/src/main/res/values/strings_timer.xml` · `app/src/main/res/values-ru/strings_timer.xml` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** FLOW-013 (1–4, 7) → T-023; (6) → T-022 · A11Y-007 (1) → T-018; (4) → T-025; (5) → wave 8 · A11Y-003 (3–4) → wave 8 · L10N-001 (3) → release record · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-013 — spec/70-flows/30-cooking.md*
- Given the reader refused permission to show notifications, when a timer ends, then it is still finished and is shown as finished inside the application, and nothing announces it while the application is closed or the screen is locked; what the reader is shown about the permission is stated by criteria 8 to 10 and 13.
- Given a platform that has a request for the permission to show notifications (Android 13 and later) and a device on which it has never been asked for, when the reader starts their first timer, then the platform's request is made at that moment, and at no earlier moment such as the first launch; on a platform that has no such request (Android 10 to 12) nothing is requested, and criteria 10 and 11 govern.
- Given notifications are not permitted, when the reader starts a timer and the platform will still show its own request, then that request is made; and when the reader refuses the request made here or by criterion 8, then the explanation of criterion 10 is shown at once, before the reader returns to the recipe.
- Given notifications are not permitted, when the reader starts a timer and the platform will not show a request — because it has none to show or because the reader has refused it as often as it allows — then the application shows its own explanation that the timer will not be heard while the application is closed or the screen is locked, with a control that opens the system's notification settings for the application; the timer starts either way.
- Given notifications are permitted, when the reader starts a timer, then nothing about the permission is shown.
- Given any request or explanation of criteria 8 to 10, when it is shown, then the timer has already started at the moment the reader started it and runs whatever the reader answers.
- Given the application's notifications are permitted and those of its timers alone are turned off in the system's settings, when the reader starts a timer, then notifications count as not permitted: the explanation of criterion 10 is shown, stating that timer notifications are turned off and the timer will not be heard, and its control opens those settings.

*A11Y-007 — spec/200-accessibility.md*
- Given the cooking card, when its controls are measured, then A11Y-003's larger figure holds.
- Given notifications are permitted, when a timer ends while the screen is off or the application is closed, then it announces itself audibly from the device's own facility (FLOW-013, EXT-009); with notifications refused FLOW-013 (5) governs and the reader has been told so when starting it (FLOW-013 (9), (10)).

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.
- Given the cooking card, when its controls are measured — the timer controls and the serving control — then each is at least 56 by 56 dp. There is no step advance among them: the card is one scrolling screen (`flows`, 30-cooking).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** accepted — docs/product/verdicts/20260927-T-033-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-024 — Several timers run at once, and restarting one asks first

**Requirements:** FLOW-016, FLOW-021, DOM-013, A11Y-003, A11Y-007, L10N-001, A11Y-005
**Status:** gated
**Depends on:** T-033
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/kotlin/app/recipefork/app/timer/**` · `app/src/test/kotlin/app/recipefork/app/timer/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml` · `app/src/main/res/values/strings_timer.xml` · `app/src/main/res/values-ru/strings_timer.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** FLOW-016 (3–8) → T-023 · DOM-013 (1–4) → T-014 · A11Y-003 (3–4) → wave 8 · A11Y-007 (1) → T-018; (3) → T-033; (4) → T-025; (5) → wave 8 · L10N-001 (3) → release record · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-016 — spec/70-flows/30-cooking.md*
- Given two steps that carry timers, when the reader starts both, then both run and each is shown running on its own step.
- Given two running timers, when one ends, then it announces itself and the other keeps running.
- Given several running timers, when the reader opens the recipe, then each shows its own remaining time on its own step, under the same name the announcement would use.
- Given several running timers, when the reader leaves the application and returns, then all of them still run by the clock (FLOW-012).
- Given two timers of one recipe that end up with the same name, whether derived or given, when both end, then the two announcements read alike and the product does nothing to tell them apart.

*FLOW-021 — spec/70-flows/30-cooking.md*
- Given a running timer, when the reader starts it again, then they are told it is running and are asked whether to restart it.
- Given that question, when the reader confirms, then the timer restarts from its full duration and the time it had already counted is gone.
- Given that question, when the reader declines, then the timer keeps running with the time it had.
- Given two steps with running timers, when one is restarted, then the other is untouched (FLOW-016).
- Given a timer that has already ended, when the reader starts it again, then it starts with no question.

*DOM-013 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given a recipe whose steps each carry a timer, when the reader starts several of them, then all of them run at once (FLOW-016).

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.
- Given the cooking card, when its controls are measured — the timer controls and the serving control — then each is at least 56 by 56 dp. There is no step advance among them: the card is one scrolling screen (`flows`, 30-cooking).

*A11Y-007 — spec/200-accessibility.md*
- Given the cooking card, when its controls are measured, then A11Y-003's larger figure holds.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** rejected — docs/product/verdicts/20260927-T-024-gate1.md; A11Y-003 (2) and A11Y-007 (2) added, re-gate due; checked against spec/ @ ead1a44; re-gated: accepted — docs/product/verdicts/20260927-T-024-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-025 — The card keeps the screen awake and is where the reader left it

**Requirements:** FLOW-014, FLOW-012, A11Y-007, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-024
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/kotlin/app/recipefork/app/MainActivity.kt` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** FLOW-012 (4) → wave 3 · A11Y-007 (1–2) → T-018; (3) → T-033; (5) → wave 8 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-014 — spec/70-flows/30-cooking.md*
- Given a recipe card open and untouched for a minute, when the device's own screen timeout is shorter than that, then the screen is still on.
- Given a recipe card open and untouched for 15 minutes, when that time passes, then the screen is left to the device's own setting.
- Given the screen was left to sleep that way, when the reader touches the card, then it is kept awake again for another 15 minutes without a touch.
- Given a card that is keeping the screen awake, when the reader leaves the card, then the screen is left to the device's own setting at once.
- Given a running timer and no touch for 15 minutes, when the screen sleeps, then the timer still announces itself (FLOW-013).

*FLOW-012 — spec/70-flows/20-device-storage.md*
- Given a recipe open at a changed serving count with a fixed ingredient set, when the reader leaves the application and returns to it, then the same recipe is shown at the same scale with the same fixed ingredient, whether or not it is in favourites and whether or not there is a connection.
- Given a running timer, when the reader leaves the application and returns, then the timer shows the time remaining by the clock, not the time it held when the application was left.
- Given the operating system ended the application while it was away, when the reader opens it again, then the same recipe is shown if its content is available — held on the device, or fetchable over the network — at the same serving count, with the same fixed ingredients and at the same place.
- Given the reader had scrolled so that a given ingredient line or step was the first one at the top of the screen, when they return, then that line or step is again the first at the top, even if the text scale (A11Y-004), the orientation of the screen or a timer that ended while they were away changed the layout.

*A11Y-007 — spec/200-accessibility.md*
- Given the card, when it is left untouched, then the screen is kept awake as FLOW-014 requires and this requirement adds no second rule about it.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-026) — docs/product/verdicts/20260927-T-025-gate1.md; re-sliced against spec/ @ ead1a44 after SD-026 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-025-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-026 — Writing a recipe asks for an account first

**Requirements:** ACT-001, AUTH-001, FLOW-003, AUTH-004, L10N-001, A11Y-003, A11Y-005
**Status:** planned
**Depends on:** T-010, T-018
**Expected files:** `app/src/main/kotlin/app/recipefork/app/account/**` · `app/src/test/kotlin/app/recipefork/app/account/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/main/kotlin/app/recipefork/app/ui/nav/**` · `app/src/main/res/values/strings_account.xml` · `app/src/main/res/values-ru/strings_account.xml` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-keys-are-hashed-random-secrets.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** ACT-001 (2) → wave 3; (4) → wave 2; (5) → wave 8 · AUTH-001 (1) → wave 3; (3, 5) → T-006; (4) → wave 7 · FLOW-003 (1–4, 7–11) → wave 2; (5) → wave 7 · AUTH-004 (1) → wave 3; (3, 5) → wave 4; (4, 7) → wave 6; (6) → T-006 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*ACT-001 — spec/40-actors.md*
- Given a clean installation, when the application is opened, then it is usable at once and nothing is created, asked for or generated.
- Given a person with no account, when they use a control that needs one, then account creation is offered and the action proceeds once the account exists.

*AUTH-001 — spec/45-auth/10-creating-an-account.md*
- Given a person with no account, when they use a control that needs one, then account creation is offered, and the action they asked for proceeds once the account exists — for a muted account, as AUTH-004 (5) states.
- Given no network connection at all, when creation is attempted, then nothing is created and FLOW-003 states that a connection is needed.
- Given a person who declines the offer, when they return to what they were doing, then everything that works without an account still works.

*FLOW-003 — spec/70-flows/10-offline-and-sync.md*
- Given no network connection, when a person uses a control that needs an account and has none, then account creation is offered and states that a connection is needed, and nothing is created — neither a full account nor a muted one (AUTH-001, AUTH-004).

*AUTH-004 — spec/45-auth/10-creating-an-account.md*
- Given no network connection at all, when an account is asked for, then nothing is created, not even a muted account, and FLOW-003 states that a connection is needed: a muted account is still an account, with a unique nickname (DOM-001) and three keys the server issues (AUTH-014).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-019) — docs/product/verdicts/20260927-T-026-gate1.md; re-sliced against spec/ @ ead1a44 after SD-019 resolved, re-gate due
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-027 — The server keeps an account's drafts and turns a stale edit into a fork

**Requirements:** FLOW-008, CON-003, ACT-002, DOM-004
**Status:** gated
**Depends on:** T-006
**Expected files:** `server/src/main/kotlin/app/recipefork/server/Application.kt` · `server/src/main/kotlin/app/recipefork/server/recipe/**` · `server/src/main/kotlin/app/recipefork/server/auth/**` · `server/src/main/resources/db/migration/V3__recipes.sql` · `core/src/main/kotlin/app/recipefork/core/api/RecipeContract.kt` · `server/src/test/kotlin/app/recipefork/server/recipe/**` · `server/src/test/kotlin/app/recipefork/server/auth/**`
**Binding ADRs:** decisions/20260927-server-authoritative-sync-with-outbox.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-keys-are-hashed-random-secrets.md, decisions/20260927-test-strategy.md
**Note:** The server-stack ADR leaves one choice to this task: whether a recipe's content is kept as `jsonb` beside relational columns. The task makes the choice and states it in its report; the team lead records it as an ADR when the task merges.
**Criteria of these requirements carried elsewhere:** FLOW-008 (2, 5) → T-028 · CON-003 (2) → wave 4 · ACT-002 (2) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-008 — spec/70-flows/10-offline-and-sync.md*
- Given a recipe edited offline on device B while device A's edit reached the server, when device B reconnects, then the server's version keeps the recipe's identity and device B's version becomes a `private` fork naming it as parent.
- Given the server's version has since been published or otherwise frozen, when device B reconnects with a divergent version, then the same rule applies and nothing is written to the frozen recipe.
- Given no divergence — device B's version is the one the server already holds — when device B reconnects, then no fork is created.

*CON-003 — spec/30-constraints.md*
- Given a recipe that has become accessible to anyone but its owner, when any edit is attempted through any interface, then the edit is rejected and the stored content is unchanged.

*ACT-002 — spec/40-actors.md*
- Given a recipe, when its owner is resolved at any point in its lifetime, then it is the account that created it.

*DOM-004 — spec/50-domain-model/10-invariants.md*
- Given a forked recipe, when its parent is resolved, then it is the recipe it was forked from, at every point in its lifetime.
- Given any recipe, when its stored fields are enumerated, then none of them lists descendants or names a chain root.

**Gate 1:** blocked-by-spec (SD-018) — docs/product/verdicts/20260927-T-027-gate1.md; re-sliced against spec/ @ ead1a44 after SD-018 resolved, re-gate due; re-gated: accepted — docs/product/verdicts/20260927-T-027-gate1-2.md; FLOW-008 (5) then moved to T-028 (its notice is on the device), re-gated: accepted — docs/product/verdicts/20260927-T-027-gate1-3.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-028 — Drafts written offline reach the server by themselves, and each says whether they have

**Requirements:** FLOW-002, FLOW-005, FLOW-008, FLOW-036, L10N-001, A11Y-003, A11Y-005
**Status:** planned
**Depends on:** T-027, T-034, T-026, T-025
**Expected files:** `server/src/main/kotlin/app/recipefork/server/recipe/**` · `server/src/test/kotlin/app/recipefork/server/recipe/**` · `core/src/main/kotlin/app/recipefork/core/api/RecipeContract.kt` · `app/build.gradle.kts` · `gradle/libs.versions.toml` · `app/src/main/kotlin/app/recipefork/app/AppContainer.kt` · `app/src/main/kotlin/app/recipefork/app/sync/**` · `app/src/test/kotlin/app/recipefork/app/sync/**` · `app/src/main/kotlin/app/recipefork/app/recipe/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/AndroidManifest.xml` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Note:** The server half of FLOW-008 (5) — a change of state to `pending` alone making the device's version a fork — and the answer that carries the notice of FLOW-008 (2) to device B live in T-027's server files, which this task may extend; T-027 is on `develop` before this task starts.
**Binding ADRs:** decisions/20260927-server-authoritative-sync-with-outbox.md, decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Note:** AUTH-004 (4) — nothing of a muted account's recipes reaches the server — is placed in wave 6, where the last of the controls it names exists. Until then no wave-1 criterion checks that this task's outbox holds a muted account's recipes back.
**Criteria of these requirements carried elsewhere:** FLOW-002 (1) → T-013; (2–3) → T-008 · FLOW-005 (3) → wave 5; (5) → wave 2 · FLOW-008 (1, 3–4) → T-027 · FLOW-036 (1–2, 4–5) → T-007; (3, 8) → wave 2; (7) → wave 3 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-002 — spec/70-flows/10-offline-and-sync.md*
- Given changes made offline, when a connection returns, then they are sent to the server without the owner asking for it.

*FLOW-005 — spec/70-flows/10-offline-and-sync.md*
- Given the list of the account's own recipes, when it is displayed, then each entry states whether it is fully accepted by the server or holds changes that are not.
- Given a recipe open in the editor, when it holds changes the server has not accepted, then the editor states so.
- Given a recipe fully accepted by the server, when its entry in the own-recipes view or the recipe in the editor is displayed, then it is marked as accepted and not merely left unmarked.

*FLOW-008 — spec/70-flows/10-offline-and-sync.md*
- Given that outcome, when the server's answer reaches device B, then the owner is told a fork was created and why at that moment if the application is open, or when they next open it if it is not; the notice stays until they acknowledge it, is not shown again afterwards, and both recipes are reachable.
- Given a recipe edited offline on device B while, on the server, nobody changed its content but it was submitted and became `pending`, `link_shared` or `published`, when device B reconnects, then device B's version becomes a `private` fork naming it as parent, and the owner is told as criterion 2 states.

*FLOW-036 — spec/70-flows/50-browsing.md*
- Given a recipe the server has not yet accepted, when its entry is shown, then the indication FLOW-005 requires is present as well and does not replace the state marker.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-017) — docs/product/verdicts/20260927-T-028-gate1.md; re-sliced against spec/ @ ead1a44 after SD-017 resolved, re-gate due
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

## Coverage beyond wave 1

Every criterion of every first-release requirement that no wave-1 task carries, placed in the wave that will carry
it. Numbers are the criterion's position in the requirement's **Acceptance criteria** list. A wave is sliced into
tasks, with its hand checks named, when the wave before it is on `develop`.

### Wave 2 — Submission, moderation and the catalogue

| Requirement | Criteria |
|---|---|
| A11Y-006 — Nothing is done only by dragging | 3 |
| ABUSE-001 — At most three submissions per account per day | all |
| ACT-001 — Most of the product needs no account | 4 |
| ACT-003 — Moderator authority is an assignable role | all |
| ACT-004 — A moderator cannot alter content | all |
| ACT-008 — The moderator role is granted outside the application | all |
| CAT-001 — The reader chooses how results are ordered | all |
| CAT-002 — Filtering by an ingredient orders by how much of it there is | all |
| CAT-003 — What a catalogue entry shows | all |
| CAT-006 — A filter on an ingredient matches the dictionary entry, not the words | all |
| CAT-007 — Several lines of one ingredient are summed | all |
| CAT-009 — The comparable quantity of an ingredient line | all |
| DOM-002 — A quantity exists exactly when the unit type carries one | 2 |
| DOM-006 — Submission minimum | 1–2 |
| DOM-008 — Tags come from the dictionary only | 2 |
| DOM-009 — An ingredient is a dictionary entry or the author's own words, never both | 3–4 |
| DOM-010 — The ingredient dictionary is two levels deep at most | 5 |
| DOM-011 — A comparison factor orders and nothing else | 2–3 |
| DOM-012 — Submission maximum | 1–7 |
| DOM-015 — The authored serving count is a whole number of one or more | 3 |
| DOM-016 — The units are a closed list of twelve | 3 |
| EXT-009 — The product sends nothing to a device | 3 |
| FLOW-003 — What is refused while the server has not answered | 1–4, 7–11 |
| FLOW-005 — Every recipe shows whether the server has accepted it | 5 |
| FLOW-006 — Nothing is submitted for moderation before the server holds it | all |
| FLOW-022 — An ingredient outside the dictionary is accepted with a warning | 4 |
| FLOW-023 — An ingredient is three fields and nothing more | 5 |
| FLOW-028 — The minimum and the maximum are shown on leaving a field, as a warning | 4, 6 |
| FLOW-030 — The editor says which parts a fork will carry away | all |
| FLOW-033 — The reader's own recipes are a filter of the catalogue, off by default | all |
| FLOW-036 — An own recipe carries its state on its entry, not in a screen of its own | 3, 8 |
| FLOW-041 — A long listing continues as the reader scrolls | 1–4, 6 |
| FLOW-042 — An empty listing names what is empty and offers the way out of it | 1–3, 6–7 |
| FLOW-043 — A listing loading for the first time, and a listing that failed to load | 1–4 |
| FLOW-044 — The moderation section belongs to the role, and holds two lists | all |
| FLOW-045 — What a queue entry says before it is opened | all |
| FLOW-046 — Deciding an entry, and the reason a rejection carries | all |
| FLOW-047 — A moderator acts on a recipe from its card | all |
| FLOW-048 — The moderation section when there is nothing in it, and when it cannot be loaded | all |
| FLOW-049 — A decision can be taken from the list, and every such decision is confirmed | all |
| FLOW-050 — Working a list through, one entry after another | all |
| FLOW-053 — Deleting a recipe is offered in one place and confirmed against what it costs | all |
| GUIDE-001 — Submission is confirmed against what it will cost | all |
| GUIDE-002 — The statement is shown every time | all |
| GUIDE-003 — The checklist is a control in the editor, open to the author at any time | all |
| GUIDE-004 — What the checklist says | all |
| GUIDE-005 — A hint appears only where the author's own action gives it a reason | 1, 6 |
| GUIDE-008 — References are offered where the first step is written | 5 |
| L10N-004 — A recipe declares the language it is written in | 2–3, 5 |
| L10N-005 — Every language is listed until the reader narrows it | all |
| L10N-006 — A text query looks at the title and the description | all |
| L10N-007 — A match is on the word, not on the letters | all |
| MOD-001 — Moderation is triggered by first external visibility | all |
| MOD-002 — The text filter routes, it never rejects | all |
| MOD-003 — Media is reviewed by a human only | all |
| MOD-004 — A decision is approve or reject, and a rejection carries a reason | all |
| MOD-005 — The expected wait is stated before submission | all |
| MOD-006 — Queue entries are ordered by submission time | all |
| MOD-007 — Approval is final for that content | all |
| MOD-011 — A decision on a submission the owner withdrew is refused and says so | all |
| MOD-012 — A submission whose owner deleted their account leaves the queue | all |
| MOD-013 — A moderator may decide a submission they made themselves | all |
| MOD-014 — A moderator's own content is moderated like anyone else's | all |
| MOD-015 — A decision reaches its owner inside the application | all |
| RET-009 — A removed recipe is kept for its owner | all |
| ST-001 — Publication is permanent | all |
| ST-002 — Only a moderator removes published content | all |
| ST-003 — Freezing happens at external visibility, not at submission | all |
| ST-008 — A withdrawal and a decision are settled by whichever reached the server first | all |
| ST-009 — Every transition is ordered by the server | all |
| ST-010 — An owner deletes a recipe that was never externally visible | all |

### Wave 3 — Reading other people's recipes: the catalogue card, favourites, settings and ratings

| Requirement | Criteria |
|---|---|
| ACT-001 — Most of the product needs no account | 2 |
| ANL-005 — A published recipe counts its opens and its favourites | all |
| ANL-006 — Those two numbers belong to the recipe's owner alone | all |
| ANL-007 — Every number says what it counts | all |
| AUTH-001 — An account appears when an action needs one | 1 |
| AUTH-004 — Without a verdict the account is created muted | 1 |
| FLOW-001 — Both dictionaries are held on the device | 5 |
| FLOW-009 — Every favourite is readable without a network connection | all |
| FLOW-010 — A favourite that becomes unavailable stays in the list | all |
| FLOW-011 — One setting governs whether media is held | all |
| FLOW-012 — Leaving the application does not lose the recipe being read | 4 |
| FLOW-017 — The reader's usual serving count opens every recipe | 1, 3–7 |
| FLOW-019 — What the card shows when parts of a recipe are absent | 4 |
| FLOW-020 — The card while it loads and when it cannot be loaded | all |
| FLOW-034 — The favourites section holds two views | 2, 4 |
| FLOW-035 — Both views are searched, filtered and ordered exactly as the catalogue is | all |
| FLOW-036 — An own recipe carries its state on its entry, not in a screen of its own | 7 |
| FLOW-037 — The own-recipes view narrows to a state | all |
| FLOW-038 — Every recipe the account owns is readable without a connection | 7 |
| FLOW-039 — What `newest` means for a recipe that was never published | 3–4 |
| FLOW-040 — An own recipe is not marked as a favourite | all |
| FLOW-042 — An empty listing names what is empty and offers the way out of it | 5 |
| FLOW-043 — A listing loading for the first time, and a listing that failed to load | 6 |
| FLOW-051 — Favourites without an account live on the device and nowhere else | all |
| FLOW-052 — An account carries favourites beyond the device | all |
| L10N-002 — The interface language starts from the device and the reader may change it | all |
| LEG-004 — The terms are accepted before anything is created, and they carry a version | 2 |
| RATE-001 — One rating per account per recipe | all |
| RATE-002 — A rating is withdrawn by repeating it | all |
| RATE-003 — Only a published recipe can be rated | all |
| RATE-004 — An author never rates their own recipe | all |
| RATE-005 — Every account's rating counts | all |
| RATE-006 — Blocking an author does not affect rating | all |
| RATE-007 — A rating is two counts | all |
| RATE-008 — A recipe nobody has rated shows no counts | all |
| RATE-009 — A rating is given on the card | all |
| RATE-010 — `highest rated` orders by likes minus dislikes | all |
| RATE-011 — `most rated` orders by how many ratings there are | all |
| RATE-012 — Rating needs a connection and is not queued | all |
| RATE-013 — Counts without a connection are the last ones synchronised | all |
| RATE-014 — A rating that cannot be recorded says so | all |
| RATE-015 — Hiding a recipe keeps its ratings | all |
| RATE-016 — Favouriting is not rating | all |
| SCALE-017 — Releasing the last fixed ingredient restores the count in force before the first | 3 |

### Wave 4 — Forking

| Requirement | Criteria |
|---|---|
| AUTH-004 — Without a verdict the account is created muted | 3, 5 |
| CAT-004 — A fork is visible as a fork before it is opened | all |
| CAT-005 — The number of direct forks is stated | all |
| CAT-008 — The fork number opens the recipes forked from it | all |
| CON-003 — Immutability after first external visibility | 2 |
| FLOW-007 — A fork is never sent before its parent | all |
| FLOW-015 — Forking twice is offered as the fork already held | all |
| FLOW-024 — The editor is one screen in a fixed order | 3, 5 |
| FORK-001 — What a fork inherits | all |
| FORK-002 — A fork by another account carries no media and no description | all |
| FORK-003 — The parent's media stays visible in the parent | all |
| FORK-004 — Only the direct parent is shown | all |
| FORK-005 — A parent that was never published is not mentioned at all | all |
| FORK-006 — No fork carries what readers gave the parent | all |
| FORK-007 — A fork of one's own recipe carries everything else | all |
| FORK-008 — An owner may mark a fork of their own recipe as replacing it | all |
| FORK-009 — A replaced recipe says so and leads to the replacement | all |
| L10N-004 — A recipe declares the language it is written in | 4 |
| ST-004 — Forking is allowed from published recipes and from one's own | all |

### Wave 5 — Media

| Requirement | Criteria |
|---|---|
| DOM-005 — Media is owned by an account from upload | all |
| EXT-003 — Object storage is used as a plain store | all |
| EXT-004 — Storage that cannot be reached costs media, not recipes | all |
| FLOW-004 — Media added offline is queued, not lost | all |
| FLOW-005 — Every recipe shows whether the server has accepted it | 3 |
| FLOW-024 — The editor is one screen in a fixed order | 1 |
| FLOW-026 — The cover is attached with the title, step media with the step | all |
| FLOW-038 — Every recipe the account owns is readable without a connection | 3–4 |
| MEDIA-001 — One cover photograph and one item per step | all |
| MEDIA-002 — Every photograph is re-encoded on the device | all |
| MEDIA-003 — Nothing but the picture is uploaded | all |
| MEDIA-004 — A video is short, small and silent | all |
| MEDIA-005 — The product says what it did to the file | all |
| MEDIA-006 — A file that does not fit after conversion is refused | all |
| MEDIA-007 — Duration is checked before anything else | all |
| MEDIA-008 — A conversion that fails attaches nothing | all |
| MEDIA-009 — A second item on the same place replaces the first | all |
| MEDIA-010 — An account's unpublished media is capped at 150 MB | all |
| MEDIA-011 — What the account is told at the cap | all |
| MEDIA-012 — A file is deleted when no surviving recipe holds it | all |
| MEDIA-013 — A recipe a moderator removed keeps only what something else holds | all |

### Wave 6 — Reports, blocks, the safety flag, the author's screen and private links

| Requirement | Criteria |
|---|---|
| A11Y-008 — Colour is never the only carrier | 2 |
| ABUSE-002 — Anyone may report, in one of two categories | all |
| ABUSE-003 — Only established accounts count toward automatic hiding | all |
| ABUSE-004 — A report that does not count is still delivered | all |
| ABUSE-005 — Blocking hides an author from catalogue and search | all |
| ABUSE-006 — An author can be reported, and that never acts automatically | all |
| ACT-005 — A publishing ban stops what would make a recipe public, not the account | all |
| ACT-006 — A publishing ban leaves published content in place | all |
| ACT-007 — A banned account is told it is banned | all |
| AUTH-004 — Without a verdict the account is created muted | 4, 7 |
| AUTH-006 — A muted account is told what it is and what lifts it | 1, 4, 6–7 |
| CON-005 — Native Android is the only client surface in the MVP | 2 |
| EXT-007 — A private link outside the application reveals nothing | all |
| FLOW-018 — A flagged recipe says what it descends from | all |
| FLOW-054 — The private link is shown, copied, revoked and reissued in one place | all |
| FLOW-055 — An author's screen is reached from their nickname | all |
| FLOW-056 — A ban is applied and lifted on the author's screen | all |
| MOD-008 — Removing a recipe as dangerous flags every descendant | all |
| MOD-009 — Flagged recipes are reviewed from the youngest upwards | all |
| MOD-010 — Clearing a flag clears one recipe and no other | all |
| ST-005 — A revoked link stops opening the recipe | all |
| ST-006 — Three counted reports hide a recipe | all |
| ST-007 — An unavailable parent is shown without a reason | all |

### Wave 7 — The account's life: signing in, replacing, recovering and deleting it

| Requirement | Criteria |
|---|---|
| AUTH-001 — An account appears when an action needs one | 4 |
| AUTH-003 — A device with no free slot says when it will have one | all |
| AUTH-005 — A muted account becomes full when a verdict and a slot allow it | all |
| AUTH-006 — A muted account is told what it is and what lifts it | 2–3, 5, 8 |
| AUTH-007 — The key is issued once, in full, and creation waits for an acknowledgement | 8 |
| AUTH-008 — The product neither stores the key nor recovers an account | all |
| AUTH-009 — Signing in with a key costs no slot | all |
| AUTH-010 — Signing in requires a connection | all |
| AUTH-011 — One account is active on a device | all |
| AUTH-012 — Replacing the account wipes what the device held | all |
| AUTH-013 — The warning before replacing names what will be lost | all |
| AUTH-014 — What the server accepts as proof of ownership | 5–7 |
| AUTH-015 — Nothing from the device verdict is kept | 3 |
| AUTH-016 — Losing the key keeps the published work in the world | all |
| AUTH-017 — Every account carries a deletion key | 2, 5–6 |
| AUTH-018 — The nickname is generated and may be rerolled any number of times before the account exists | 4 |
| AUTH-019 — Afterwards the key is shown masked and copied whole | all |
| AUTH-020 — The recovery key is issued apart from the account key and shown once | 3–4 |
| AUTH-021 — Using the recovery key retires every key the account had | all |
| DOM-001 — A nickname is unique and permanent | 2 |
| EXT-002 — The attestation service may be absent | all |
| EXT-006 — The account deletion page takes a deletion key, never the account key | all |
| EXT-008 — The account key is handed over and never managed | all |
| FLOW-003 — What is refused while the server has not answered | 5 |
| LEG-004 — The terms are accepted before anything is created, and they carry a version | 3 |
| LEG-005 — Changed terms are accepted again, and refusing costs only authorship | all |
| QUAL-007 — The rate at which deletion keys may be tried | all |
| QUAL-009 — The rate at which account keys and recovery keys may be tried | all |
| RET-001 — What deleting an account destroys | all |
| RET-002 — What survives deletion of the account that made it | all |
| RET-003 — A deleted account's recipes say the author is gone | all |
| RET-004 — Deletion is immediate and cannot be undone | all |
| RET-005 — What the account is told before it is deleted | all |

### Wave 8 — Operations, the web surfaces and the whole-product sweeps

| Requirement | Criteria |
|---|---|
| A11Y-002 — Contrast | all |
| A11Y-003 — Touch targets | 3–4 |
| A11Y-004 — Text scale | all |
| A11Y-005 — The screen reader works everywhere, including the editor | 2–6 |
| A11Y-006 — Nothing is done only by dragging | 2 |
| A11Y-007 — The cooking card is built for hands that are busy | 5 |
| A11Y-008 — Colour is never the only carrier | 4 |
| ABUSE-007 — A rights holder outside the product reaches it by a published address | all |
| ACT-001 — Most of the product needs no account | 5 |
| ACT-002 — Ownership is fixed at creation | 2 |
| ANL-001 — Nothing is measured about a person | all |
| ANL-002 — The product can be described from what it already holds | all |
| ANL-003 — Reach is taken from the distribution channel, not measured here | all |
| ANL-004 — Application starts are counted, and nothing else about them | all |
| AUTH-023 — The nickname words come from lists the product owner keeps by hand | 2 |
| CON-005 — Native Android is the only client surface in the MVP | 1 |
| DOM-011 — A comparison factor orders and nothing else | 4 |
| DOM-014 — Both dictionaries are maintained outside the application | 1, 4–5 |
| DOM-016 — The units are a closed list of twelve | 4 |
| EXT-001 — The list of external dependencies is closed | all |
| EXT-009 — The product sends nothing to a device | 2 |
| EXT-010 — Nothing receives content or identifiers | all |
| EXT-011 — The health endpoint answers liveness and nothing else | all |
| GUIDE-005 — A hint appears only where the author's own action gives it a reason | 4 |
| L10N-008 — The product is metric and no language changes that | all |
| LEG-006 — The terms declare an age and the product verifies nothing | all |
| LEG-008 — The terms and the privacy statement are reachable from outside the application | all |
| LEG-010 — Distribution is limited; access is not | all |
| OBS-001 — What the product measures | all |
| OBS-002 — The signals are shown to the moderator inside the application | all |
| OBS-003 — Thresholds say when a signal has become a decision | all |
| OBS-004 — Alerts leave by one outbound channel and carry only numbers | all |
| OBS-005 — The alerting channel is one-way | all |
| OBS-006 — An unavailable channel costs alerts, nothing else | all |
| OBS-007 — Nothing is measured about a person | all |
| QUAL-005 — The lowest supported Android version | all |
| QUAL-008 — The store is copied every hour and the copy is known to work | all |
| QUAL-010 — The time a moderation decision takes | all |
| QUAL-011 — The narrowest supported window is 320 dp wide | all |
| RET-006 — Inactivity destroys nothing | all |
| RET-007 — Abandoned storage is watched rather than swept | all |
| RET-008 — Server logs are kept for fourteen days | all |
| RET-010 — Nothing is kept that was never needed | all |

### Release records — verified by a dated record before release, not by a task (MVP-003)

| Requirement | Criteria |
|---|---|
| A11Y-001 — The commitment is this list, and conformance is not claimed | all |
| AUTH-023 — The nickname words come from lists the product owner keeps by hand | 1 |
| CON-002 — Recurring infrastructure cost ceiling | all |
| CON-006 — Google Play policy is binding | all |
| EXT-005 — The distribution channel's obligations are traceable | all |
| EXT-012 — Availability is observed from outside the product | all |
| L10N-001 — Russian and English from the first release | 3 |
| LEG-001 — Publication grants the product a perpetual licence it cannot lose | all |
| LEG-002 — The author licenses the steps to everyone, so that forking rests on a grant | all |
| LEG-003 — Media and description stay the author's | all |
| LEG-007 — The product declares itself not directed to children | all |
| LEG-009 — The published name is cleared before the listing is created | all |
| MVP-001 — The first release is this specification, less what the ledger defers | all |
| MVP-002 — What the first release deliberately does not contain | all |
| MVP-003 — What must be true before the release, beyond the requirements | all |
| QUAL-001 — Two reference configurations | all |
| QUAL-002 — Response times on `typical` | all |
| QUAL-003 — Response times on `modest` | all |
| QUAL-004 — The aimed-at figures are measured on `modest` too, and recorded | all |
| QUAL-006 — Availability of what needs the server | all |
