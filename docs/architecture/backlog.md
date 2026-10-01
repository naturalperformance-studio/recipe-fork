# Backlog

The ordered task list of the first release and the status of every task. Statuses use the vocabulary of the team lead's
process: `planned` · `gated` · `in-progress` · `in-review` · `po-review` · `on-develop` · `released-to-main` · `returned` · `blocked`.

Behaviour lives in `spec/` alone. An entry names the requirements it rests on and copies their acceptance criteria
verbatim; where a requirement's criteria are split between tasks, the entry says where the rest are carried.

## How the backlog is sliced

- **Waves.** Tasks are written out and gated one wave at a time. Waves 1 and 1b are below in full: wave 1 is what
  the device does alone, wave 1b the account and sending, re-sliced on 2026-09-28 against spec/ @ 21b80b2 when the
  specification put the device first. Every other requirement of
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
- **Serverless mode is built as it goes.** A task that adds a control serverless mode removes carries that control's
  own serverless criterion where the specification states one (FLOW-059 (11), FLOW-005 (7)); wave 9 carries the mode
  itself, the sweep of FLOW-062 (1) and recipe files. See `decisions/20260928-serverless-mode-is-a-build-flag.md`.
- **Commands.** Build `./gradlew assembleAll`, full suite `./gradlew testAll`, setup `scripts/setup.sh` — see
  `00-index.md`. No task of waves 1 and 1b needs the device-test command.

## Wave 1 — a person writes a recipe on the device and cooks from it

A person with no account and no connection writes a recipe in the recipe book's pool, and cooks from it on the card
with the serving count, a fixed ingredient and the step timers. The server is reached for the dictionaries alone, and
the application ships them for a device that has never reached it. Nothing is sent, submitted, published or shared in
this wave, and no media is attached.

| Task | Title | Depends on | Status |
|---|---|---|---|
| T-001 | Quantities follow the serving count | — | on-develop |
| T-029 | Large and fractional quantities are written on the ladder | T-001 | on-develop |
| T-002 | A fixed ingredient sets the scale | T-029 | on-develop |
| T-030 | Changing the count or releasing the last fixing puts the scale back | T-002 | on-develop |
| T-003 | The store refuses a malformed dictionary entry | — | on-develop |
| T-004 | The device holds both dictionaries and replaces them when they change | T-003 | on-develop |
| T-005 | An account is created with a unique nickname and three keys, and only once it is confirmed | T-004 | on-develop |
| T-006 | Creating an account costs a device slot, and no verdict makes it muted | T-005 | on-develop |
| T-007 | The recipe book's own-recipes view lists every recipe written in it | T-004, T-001 | on-develop |
| T-008 | The editor opens on a new recipe from the own-recipes view | T-007 | on-develop |
| T-044 | Every interactive element on the screens so far is 48 dp as laid out | T-008 | on-develop |
| T-045 | No interface string of the release is fixed in the code in one language only | T-008 | in-progress |
| T-031 | What the ingredient field offers for what the author types | T-044, T-045 | gated |
| T-011 | An ingredient is picked from the dictionary in three fields | T-031 | gated |
| T-012 | An ingredient outside the dictionary is kept as free text with a warning | T-011 | gated |
| T-013 | Steps are written in the editor and survive a restart offline | T-012 | gated |
| T-014 | A step carries one timer, named by its step unless the author names it | T-013 | gated |
| T-015 | A quantity goes into a step as a reference | T-014, T-019 | gated |
| T-016 | Steps and ingredients are inserted anywhere and moved, by dragging or by a discrete action | T-015 | gated |
| T-017 | A part below the minimum or over a limit is marked on leaving it | T-016 | gated |
| T-018 | A saved recipe opens as the card a reader sees, at the serving count chosen | T-008, T-029, T-044, T-045 | gated |
| T-019 | A derived quantity says so, and which way it was rounded | T-018, T-030 | gated |
| T-020 | The reader fixes an ingredient on the card | T-019, T-030, T-015 | gated |
| T-021 | A reference in a step reads as the quantity the list shows | T-020 | gated |
| T-022 | A timer runs on its step | T-021, T-014 | gated |
| T-023 | A timer announces itself when the application is closed | T-022 | gated |
| T-034 | A long step with an unnamed timer is shown what its notification will say | T-017, T-023 | gated |
| T-033 | A timer asks for notifications when first started, and a refusal is explained at once | T-023 | gated |
| T-024 | Several timers run at once, and restarting one asks first | T-033 | gated |
| T-025 | The card keeps the screen awake and is where the reader left it | T-024 | gated |
| T-035 | The quantity field holds only a value on its unit type's grid | T-028, T-029, T-020 | gated |

### T-001 — Quantities follow the serving count

**Requirements:** SCALE-001, SCALE-003, SCALE-005, SCALE-010, SCALE-015, DOM-017
**Status:** on-develop
**Depends on:** —
**Expected files:** `core/src/main/kotlin/app/recipefork/core/recipe/Units.kt` · `core/src/main/kotlin/app/recipefork/core/scaling/**` · `core/src/test/kotlin/app/recipefork/core/scaling/**`
**Binding ADRs:** decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Note:** `Units.kt` holds the twelve units of DOM-016 with the unit type the table in `spec/50-domain-model/20-ingredients-and-limits.md` gives each; T-011 and T-029 read it and do not change it.
**Criteria of these requirements carried elsewhere:** SCALE-001 (2) → T-019 · SCALE-010 (2) → T-002; (3) → T-029 · SCALE-015 (1–2) → T-019; (4–5) → T-002 · DOM-017 (1–5) → T-035; (6–7) → wave 2; (9) → wave 4

**Acceptance criteria** (verbatim)

*SCALE-001 — spec/80-scaling-rules/10-scaling.md*
- Given a recipe authored for 4 servings with 500 g of an `exact` ingredient, when the reader asks for 6, then it shows 750 g.
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

*DOM-017 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given a recipe at its authored serving count, when an ingredient is displayed, then it shows the authored value, which is a grid value and so is the same under SCALE-001 (4) and under the grid; DOM-018 is the one exception.

**Gate 1:** blocked-by-spec (SD-002) — docs/product/verdicts/20260927-T-001-gate1.md; re-sliced against spec/ @ ead1a44 after SD-002 resolved, re-gate due; re-gated: blocked-by-spec (SD-027, SCALE-001 (2)) — docs/product/verdicts/20260927-T-001-gate1-2.md; SCALE-001 (2) corrected in spec/ @ 959130e and moved to T-019, since it now states the rounding direction T-019 renders; re-gated: blocked-by-spec (SD-034, what an author may enter as a quantity and how an off-grid one is shown) — docs/product/verdicts/20260927-T-001-gate1-3.md; SD-034 answered in spec/ @ 1168954 (DOM-017: an authored quantity lies on its grid; DOM-018 the one exception, placed in wave 4 with DOM-017 (8)); DOM-017 (7) added, re-gate due; re-gated: accepted — docs/product/verdicts/20260928-T-001-gate1.md
**Review:** pass — 13 of 13 criteria checked by mutation, worktree as it was
**Gate 2:** accepted — docs/product/verdicts/20260929-T-001-gate2.md
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-029 — Large and fractional quantities are written on the ladder

**Requirements:** SCALE-010, SCALE-013, SCALE-014, L10N-009
**Status:** on-develop
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
**Review:** pass — 13 of 13 criteria checked by mutation, worktree as it was
**Gate 2:** accepted — docs/product/verdicts/20260929-T-029-gate2.md
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-002 — A fixed ingredient sets the scale

**Requirements:** SCALE-002, SCALE-006, SCALE-008, SCALE-010, SCALE-015
**Status:** on-develop
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
- Given a computed count whose nearest value in the set is one serving or more, when it is displayed, then it is displayed as a whole number and not as a fraction.
- Given any state, when the serving count is displayed, then no quantity in the recipe is computed from it; SCALE-003 governs.
- Given a recipe authored for 4 servings with 500 g of chicken, when the reader fixes 115 g of it (a computed 0.92 servings), then the serving count shows an approximate 1 serving and not ¾.
- Given a recipe authored for 4 servings with 320 g of chicken, when the reader fixes 70 g of it (a factor of 0.21875, a computed 0.875 servings, exactly between ¾ and 1), then the serving count shows an approximate 1 serving.
- Given a recipe authored for 4 servings with 500 g of chicken, when the reader fixes 100 g of it (a factor of 0.2, a computed 0.8 servings), then the serving count shows an approximate ¾ serving.

*SCALE-010 — spec/80-scaling-rules/10-scaling.md*
- Given a recipe authored for 4 servings with 5 g of an `exact` ingredient, when the reader fixes 10 kg of that ingredient, then the derived count is displayed by SCALE-002 and is not capped.

*SCALE-015 — spec/80-scaling-rules/20-display.md*
- Given a recipe authored for 4 servings with 400 g of an ingredient, when the reader fixes 250 g of it, then the serving count shows an approximate 3 servings.
- Given a recipe authored for 4 servings with 240 g of an ingredient, when the reader fixes 25 g of it (a computed 5/12 serving), then the serving count shows an approximate ½ serving.

**Gate 1:** blocked-by-spec (SD-003) — docs/product/verdicts/20260927-T-002-gate1.md; re-sliced against spec/ @ ead1a44 after SD-003 resolved, re-gate due; re-gated: blocked-by-spec (SD-027, SCALE-008 vs SCALE-015) — docs/product/verdicts/20260927-T-002-gate1-2.md; re-sliced against spec/ @ 959130e after SD-027 resolved (SCALE-008 (4) replaced, (6–8) added); re-gated: accepted — docs/product/verdicts/20260927-T-002-gate1-3.md; SCALE-008 (7) replaced in spec/ @ 1168954 (SD-033: 109.375 g is off the grid), re-gate due; re-gated: blocked-by-spec (SD-036, SCALE-008 (7) gives 3.5 servings, not 0.875, and (8)'s "same recipe" is ambiguous) — docs/product/verdicts/20260928-T-002-gate1.md; re-sliced against spec/ @ 952398f after SD-036 resolved (SCALE-008 (7–8) replaced); re-gate due; re-gated: accepted — docs/product/verdicts/20260929-T-002-gate1.md
**Review:** pass — 14 of 14 criteria checked by mutation, build and suite run by the reviewer, worktree as it was
**Gate 2:** accepted — docs/product/verdicts/20260929-T-002-gate2.md
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-030 — Changing the count or releasing the last fixing puts the scale back

**Requirements:** SCALE-016, SCALE-017
**Status:** on-develop
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
**Review:** pass — 6 of 6 criteria checked by mutation, build and suite run by the reviewer, worktree as it was
**Gate 2:** accepted — docs/product/verdicts/20260929-T-030-gate2.md
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
**Status:** on-develop
**Depends on:** T-004
**Expected files:** `server/src/main/kotlin/app/recipefork/server/Application.kt` · `server/src/main/kotlin/app/recipefork/server/account/**` · `server/src/main/resources/db/migration/V2__accounts.sql` · `core/src/main/kotlin/app/recipefork/core/api/AccountContract.kt` · `deploy/local/seed/**` · `deploy/local/compose.yml` · `server/src/test/kotlin/app/recipefork/server/account/**`
**Binding ADRs:** decisions/20260927-keys-are-hashed-random-secrets.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-test-strategy.md
**Note:** No new library is expected; the version catalog is outside this task's files.
**Note:** The nickname words are the product owner's two lists (AUTH-023), changed in the store as the dictionaries are. This task holds them in tables of its migration and seeds the local deployment with a placeholder list that says it is one; the real lists are the product owner's to write before the first account (release record AUTH-023 (1)), and their size is OQ-009.
**Criteria of these requirements carried elsewhere:** AUTH-014 (5–6) → wave 7; (7) → T-032 · AUTH-017 (1–4) → T-032; (5, 8–9) → wave 7 · AUTH-020 (1–2, 6–9) → T-010; (3–4) → wave 7 · AUTH-018 (1–2) → T-009; (4) → wave 7 · DOM-001 (2) → wave 7 · LEG-004 (1) → T-010; (5) → T-009; (2) → wave 3; (3) → wave 7 · CON-001 (1) → T-009 · AUTH-022 (2) → T-009 · AUTH-023 (1) → release record; (2) → wave 8

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
**Review:** defects — the confirming call accepts any well-formed keys from the client (keys ADR, AUTH-014 (1, 3), AUTH-017 (4), AUTH-020 (5)); AUTH-023 (4) "no longer drawn" and AUTH-014 (2) strength tests survive their mutations; returned to the developer; re-review: defects — all 17 criteria checked by mutation and the three earlier defects gone; KEY_SET_SECRET missing or blank silently falls back to a per-process secret, so a restart voids issued key sets (AUTH-024 (6)), and a malformed secret fails without naming the variable; no test covers the secret's configuration. Second return: stopped for the user; worktree and branch kept. 2026-09-27: the user authorised a third run beyond the limit for this one defect; `deploy/local/compose.yml` added to the expected files so the local deployment and the artefact carry the secret; third review: pass — 17 of 17 criteria and the six secret points checked by mutation, worktree as it was
**Gate 2:** accepted — docs/product/verdicts/20260927-T-005-gate2.md
**Returns:** 2 of 2, plus a third run the user authorised (gate 1 · reviewer · gate 2 share the count)

### T-006 — Creating an account costs a device slot, and no verdict makes it muted

**Requirements:** AUTH-001, AUTH-002, AUTH-004, AUTH-015
**Status:** on-develop
**Depends on:** T-005
**Expected files:** `server/src/main/kotlin/app/recipefork/server/Application.kt` · `server/src/main/kotlin/app/recipefork/server/account/**` · `server/src/main/kotlin/app/recipefork/server/attestation/**` · `core/src/main/kotlin/app/recipefork/core/api/AccountContract.kt` · `server/src/test/kotlin/app/recipefork/server/account/**` · `server/src/test/kotlin/app/recipefork/server/attestation/**` · `deploy/local/compose.yml`
**Binding ADRs:** decisions/20260927-keys-are-hashed-random-secrets.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-test-strategy.md
**Note:** The attestation service sits behind an interface (test-strategy ADR). Tests and the local deployment use a fake that models the service's three per-device bits and their write months; the Play Integrity adapter is a later task.
**Note:** Left open at merge, for the tasks named: the server returns whether an account is muted but does not store it — the column (a new migration) goes to whichever task first makes the server act on it, T-028's re-slice at the latest; two creations racing on one device can occupy one slot, since the attestation service has no compare-and-set — named for the Play Integrity adapter task.
**Criteria of these requirements carried elsewhere:** AUTH-001 (1) → wave 3; (2, 6–7) → T-026; (4) → wave 7 · AUTH-004 (1) → wave 3; (2) → T-026; (3) → wave 4; (4–5, 7) → wave 6 · AUTH-015 (3) → wave 7; (4) → T-040

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
**Review:** defects — a missing or blank `ATTESTATION` silently started a server that creates only muted accounts (AUTH-001 statement), and a test locked the fallback in; returned to the developer; re-review: pass — 10 of 10 criteria and the missing, blank and unknown `ATTESTATION` cases checked by mutation, worktree as it was
**Gate 2:** accepted — docs/product/verdicts/20260927-T-006-gate2.md
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-007 — The recipe book's own-recipes view lists every recipe written in it

**Requirements:** FLOW-034, FLOW-058, FLOW-036, FLOW-005, FLOW-039, FLOW-041, FLOW-043, FLOW-038, A11Y-008, L10N-001, A11Y-003, A11Y-005
**Status:** on-develop
**Depends on:** T-004, T-001
**Expected files:** `core/src/main/kotlin/app/recipefork/core/recipe/**` · `!core/src/main/kotlin/app/recipefork/core/recipe/Units.kt` · `core/src/test/kotlin/app/recipefork/core/recipe/**` · `app/build.gradle.kts` · `gradle/libs.versions.toml` · `app/src/main/kotlin/app/recipefork/app/MainActivity.kt` · `app/src/main/kotlin/app/recipefork/app/AppContainer.kt` · `app/src/main/kotlin/app/recipefork/app/data/db/**` · `app/src/main/kotlin/app/recipefork/app/recipe/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/main/kotlin/app/recipefork/app/ui/**` · `app/src/test/kotlin/app/recipefork/app/recipe/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/ui/**` · `app/lint.xml` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml` · `app/src/main/res/values/strings_common.xml` · `app/src/main/res/values-ru/strings_common.xml` · `app/src/main/kotlin/app/recipefork/app/data/AppDatabase.kt` · `app/src/main/kotlin/app/recipefork/app/book/**` · `app/src/test/kotlin/app/recipefork/app/book/**`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md, decisions/20260928-one-database-per-recipe-book.md
**Size:** First screen of the application: it carries the recipe model in `:core` (every field of `50-domain-model`), its local tables, the navigation host and the test helpers for the three cross-cutting criteria every later screen repeats. Expected at 600–800 changed lines.
**Note:** This task builds the recipe book's store as the architecture now places it: the device database (the dictionaries T-004 holds, moved out of `AppDatabase` unchanged) and one book database, the pool's (AUTH-026), which is the only book in this wave. Accounts, their books and the switch between them are wave 1b's.
**Criteria of these requirements carried elsewhere:** FLOW-034 (2, 4) → wave 3 · FLOW-058 (5) → wave 3 · FLOW-036 (3, 8) → wave 2; (7) → wave 3 · FLOW-005 (3, 5, 7) → T-028; (4) → wave 5; (6) → wave 2 · FLOW-039 (3–4) → wave 3 · FLOW-041 (1–4, 6) → wave 2 · FLOW-043 (1–4) → wave 2; (6) → wave 3 · FLOW-038 (1–2, 6) → T-018; (3–4) → wave 5; (7) → wave 3 · A11Y-008 (1) → T-019; (2) → wave 6; (4) → wave 8 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-034 — spec/70-flows/50-browsing.md*
- Given an account that has marked recipes and owns recipes, when it opens the section, then both views are reachable from it and the switch between them is visible without scrolling.
- Given the view of the account's own recipes, when it is shown, then it lists every recipe the account owns in every state, including states no other account can see.
- Given a switch between the views, when the reader leaves the section and returns within the same session (FLOW-058), then the view they were in is still in force, as CAT-001 keeps a chosen ordering.
- Given a session (FLOW-058) in which the reader has not chosen a view, when they open the section, then it opens on the view of marked recipes, whatever the account holds.
- Given no account active, when the section is opened, then both views and the switch are there, holding the pool of AUTH-026: what was marked and what was written with no account.
- Given the section in either interface language, when its name is shown, then it is "My recipe book" in English and «Моя книга рецептов» in Russian, and the word "favourites" names no section anywhere in the interface.

*FLOW-058 — spec/70-flows/20-device-storage.md*
- Given a view chosen in the favourites section (FLOW-034), when the reader leaves the application by the home action or the task switcher and returns while its process is alive, then it is the same session and the view is still in force.
- Given the same choice, when the operating system ends the application's process while it is away and the reader returns to it, then it is the same session and the view is still in force, exactly as the recipe being read is restored by FLOW-012.
- Given the same choice, when the reader removes the application from the list of recent applications and opens it again, then a new session has started and the section opens as FLOW-034 (6) states.
- Given the same choice, when the device is restarted and the reader opens the application, then a new session has started.

*FLOW-036 — spec/70-flows/50-browsing.md*
- Given a recipe in `local` or `private`, when the view is shown, then its entry is marked as a draft, and FLOW-005 says whether it was sent.
- Given a recipe in `pending`, when its entry is shown, then it states that the recipe is awaiting review, and the expectation MOD-005 states is not repeated on the entry.
- Given a recipe in `reported_hidden`, when its entry is shown, then it states that the recipe is hidden and gives no further detail (ST-006).
- Given a recipe in `removed`, when its entry is shown, then it stays in the view marked as removed, because its owner may still open it (`entity-states`).
- Given a recipe never sent or holding changes not sent, when its entry is shown, then the mark FLOW-005 requires is present as well and does not replace the state marker.

*FLOW-005 — spec/70-flows/10-offline-and-sync.md*
- Given the own-recipes view, when it is displayed, then each entry carries one of the three marks.
- Given a recipe never sent, when its mark is shown, then it says the recipe is held on this device alone and is lost with it.

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

**Gate 1:** blocked-by-spec (SD-004) — docs/product/verdicts/20260927-T-007-gate1.md; re-sliced against spec/ @ ead1a44 after SD-004 resolved, re-gated: rejected — docs/product/verdicts/20260927-T-007-gate1-2.md (FLOW-034 (6, 7) left to wave 3); FLOW-034 (6, 7) added, re-gated: blocked-by-spec (SD-028, "session" in FLOW-034 (5, 6)) — docs/product/verdicts/20260927-T-007-gate1-3.md; re-sliced against spec/ @ 959130e after SD-028 resolved (FLOW-034 (5, 6) replaced, FLOW-058 (1–4) added; (5) needs the ordering, filter and usual count of waves 2 and 3); re-gated: accepted — docs/product/verdicts/20260927-T-007-gate1-4.md; 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): FLOW-034 (7) and FLOW-036 (1) replaced, FLOW-034 (8), FLOW-036 (6) and FLOW-005 (1–2) added; the pool is the only book; re-gate due; re-gated: accepted — docs/product/verdicts/20260928-T-007-gate1.md
**Review:** defects — build and suite ok, 22 of 24 criteria held under mutation; two tests did not: BookDatabaseTest.theBookOrdersOwnRecipesByTheirLastChangeOfContent leaves the view's ordering of a published recipe untested (FLOW-039 (2)), and the screen helper LocalizationChecks.assertScreenTextFromResources accepts a Russian string that fell back to English (L10N-001 (1); the criterion itself is held at resource level); worktree as it was. Second return, taken to the user; a third run authorised by the user on 2026-09-29, to fix the two tests; after the third run: defects — build and suite ok; the two earlier findings now held; four more tests did not bite: BookDatabaseTest leaves the view's ordering of a never-edited draft untested (FLOW-039 (3)); LibraryScreenTest compares the pending and hidden entries with their own string resources, so an entry repeating MOD-005's expectation or giving detail on hiding passes (FLOW-036 (2), (4)); the offline scrolling test stops at the last entry, so a "connection needed" notice at the end of the list goes unseen (FLOW-041); two screen tests with fixed 5 s waits each failed once in about twenty runs; worktree as it was. Taken to the user; a fourth and last run authorised by the user on 2026-09-29: if its review finds defects again, the branch is abandoned, T-007 is re-sliced into smaller tasks and built again; the fourth run was interrupted when its session ended, leaving uncommitted test changes in the worktree; kept by the user on 2026-09-29 and the run resumed on them; after the fourth run: pass — build and suite ok, 28 of 28 criteria checked by mutation, worktree as it was
**Gate 2:** accepted — docs/product/verdicts/20260929-T-007-gate2.md
**Returns:** 2 of 2, plus a third run the user authorised (gate 1 · reviewer · gate 2 share the count)

### T-008 — The editor opens on a new recipe from the own-recipes view

**Requirements:** FLOW-042, FLOW-024, FLOW-002, L10N-004, DOM-006, DOM-008, FLOW-001, L10N-001, A11Y-005
**Status:** on-develop
**Depends on:** T-007
**Expected files:** `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/main/kotlin/app/recipefork/app/ui/nav/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml` · `app/src/main/kotlin/app/recipefork/app/dictionary/**` · `app/src/test/kotlin/app/recipefork/app/dictionary/**` · `app/src/main/assets/dictionaries.json` · `scripts/dictionary-snapshot.sh` · `app/src/test/kotlin/app/recipefork/app/ui/InterfaceStringsTest.kt` · `app/src/test/kotlin/app/recipefork/app/ui/LocalizationChecks.kt` · `app/src/main/kotlin/app/recipefork/app/ui/**` · `app/src/main/res/values/strings_common.xml` · `app/src/main/res/values-ru/strings_common.xml` (added at the return: the release-wide L10N-001 (2) check is T-007's and missed this task's code)
**Size:** not declared at slicing, which was a slicing miss: the shipped snapshot, its seeding and the editor's screen tests came to about 1,900 changed lines (1,929 at review).
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md, decisions/20260928-dictionaries-ship-with-the-application.md, decisions/20260928-one-database-per-recipe-book.md
**Note:** FLOW-001 (1) holds on a clean installation with no connection only if the device already has the dictionaries, so the shipped snapshot and the seeding of the dictionaries-ship ADR are built here: `scripts/dictionary-snapshot.sh`, the committed `dictionaries.json`, and seeding through T-004's replacement path. A new recipe is created in `local`, in the active book — the pool in this wave.
**Note:** Re-sliced on 2026-09-30 after its third run, as the entry stated: all three reviews that returned it found defects in the cross-cutting checks for L10N-001 (2) and A11Y-003 (1) that T-007 built and every screen reuses — never in this task's own behaviour. Those two criteria move to T-043, which rebuilds both checks over the library and the editor. The branch `task/T-008` (cada29d) is kept and carries on: the last review found no defect in the 11 criteria that remain. The return count does not restart: it stays at 2 of 2 with the user's third run spent, so any further defect goes to the user.
**Criteria of these requirements carried elsewhere:** FLOW-042 (1–3, 6–7) → wave 2; (5) → wave 3 · FLOW-024 (1) → wave 5; (2) → T-013; (3, 5) → wave 4 · FLOW-002 (1) → T-013; (2) → wave 4; (5–6) → T-028 · L10N-004 (2–3, 5) → wave 2; (4) → wave 4 · DOM-006 (1–2) → wave 2 · DOM-008 (2) → wave 2 · FLOW-001 (2, 7) → T-011; (3) → T-012; (4) → T-031; (5) → wave 3; (6) → T-004 · L10N-001 (2) → T-045 (T-043 until its split); (3) → release record · A11Y-003 (1) → T-044 (T-043 until its split); (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-042 — spec/70-flows/50-browsing.md*
- Given an active recipe book in which nothing has been written — an account's, or the pool of AUTH-026 when no account is active — when the own-recipes view is opened, then the view states so and offers to write a recipe, and the offer opens the editor on a new recipe in that recipe book (FLOW-024, FLOW-002).

*FLOW-024 — spec/70-flows/40-editor.md*
- Given a new recipe, when the editor opens, then the declared language is already set to the author's interface language and sits beside the title, where changing it is one action and missing it is not possible (L10N-004).
- Given the author leaves the editor at any point, when they return, then they return to a screen in the same order with everything they had entered.

*FLOW-002 — spec/70-flows/10-offline-and-sync.md*
- Given a recipe in `local`, `private` or `rejected`, when its owner edits it with or without a connection, then the edit is accepted.
- Given a recipe in `pending`, `link_shared`, `published`, `reported_hidden` or `removed`, when its owner opens it, then it is not editable, with or without a connection.

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

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** accepted — docs/product/verdicts/20260927-T-008-gate1.md; 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): FLOW-002 (3–4) replaced; the shipped dictionaries added for FLOW-001 (1) on a clean installation; re-gate due; re-gated: blocked-by-spec (SD-038, FLOW-042 (4) observes the empty own-recipes view of an account only, and wave 1 has the pool alone) — docs/product/verdicts/20260928-T-008-gate1.md; re-sliced against spec/ @ 952398f after SD-038 resolved (FLOW-042 (4) replaced: it names the pool, the only book of this wave); re-gate due; re-gated: accepted — docs/product/verdicts/20260929-T-008-gate1.md; re-sliced on 2026-09-30 (L10N-001 (2), A11Y-003 (1) moved to T-043); re-gate due; re-gated: accepted — docs/product/verdicts/20260930-T-008-gate1.md
**Review:** defects — build and suite ok; `InterfaceStringsTest.noInterfaceTextIsFixedInTheCode` still passed with a literal in place of `stringResource` in EditorScreen.kt, because its check reads one line at a time and misses `Text(` followed by the literal on the next line (L10N-001 (2)); worktree as it was. Returned to the developer; after the fix: defects — build and suite ok; the widened check still misses a literal when the argument breaks across lines after an operator or inside `if`/`else` (e.g. `Text(name +\n "Tags")`), because it cuts an expression at a line break at bracket depth 0 (LocalizationChecks.kt:145); worktree as it was. Second return: blocked, taken to the user; a third run authorised by the user on 2026-09-30: the text scanner is replaced by a strict rule — any string literal holding a letter in the interface packages fails, except a short named allow-list; if its review finds defects again, T-008 is re-sliced; after the third run: defects — build and suite ok; the strict literal rule reads only the interface packages and `src/main/kotlin`, and accepts any literal given an allowed constant name; the A11Y-003 check measures touch bounds, which Compose widens to 48 dp for any clickable, so it cannot fail; worktree as it was. Re-sliced as stated; after the re-slice: pass — build and suite ok, 11 of 11 criteria checked by mutation (FLOW-042 (4) on both branches), worktree as it was; after the fourth run (entry route removed, f7a7d08): pass — build and suite ok, 11 of 11 criteria checked by mutation, the guard against an entry route back into the editor checked by mutation too, worktree as it was
**Gate 2:** rejected — docs/product/verdicts/20260930-T-008-gate2.md (every own-recipes entry opens the editor, a frozen one read-only, which FLOW-029 (1, 4) forbid: a saved recipe opens as the card, T-018's); taken to the user; a fourth run authorised by the user on 2026-10-01: the entry route into the editor is removed, and the tests that reached an existing recipe through it (`EditorScreenTest.openOwn`, `leaveAndReturn`, `offlineTheTagEditorOffersExactlyWhatItOffersOnline`, the last two sections of `EditorLanguageAndAccessibilityTest.everyScreen`) open it directly; if its review or gate 2 finds defects again, the branch is abandoned and T-008 is re-sliced, without asking again; after the fourth run: accepted — docs/product/verdicts/20261001-T-008-gate2.md
**Returns:** 2 of 2, plus a third and a fourth run the user authorised (gate 1 · reviewer · gate 2 share the count)

### T-044 — Every interactive element on the screens so far is 48 dp as laid out

**Requirements:** A11Y-003
**Status:** on-develop
**Depends on:** T-008
**Expected files:** `app/src/test/kotlin/app/recipefork/app/ui/AccessibilityChecks.kt` · `app/src/test/kotlin/app/recipefork/app/ui/AccessibilityChecksTest.kt`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Note:** Split out of T-043 on 2026-10-01 (see the withdrawal note after T-045). A11Y-003 (1) is checked on the size of each interactive node as laid out, not on the touch bounds Compose widens to 48 dp for any clickable, so a node drawn smaller than 48 dp fails however its touch bounds read; spacing between targets is A11Y-003 (3), wave 8's, and is not checked here. The check is the one the library (T-007) and the editor (T-008) tests already call, so it applies to both screens through them; a self-test shows a node Compose widens failing. Both reviews of T-043 found this half sound, and the branch `task/T-043` (4b797c1) holds it and may be read as a reference. Should the check find an element under 48 dp on either screen, the fix is not in this entry's files: it is returned as an open item.
**Note:** Review bar, set by the user on 2026-10-01 after the third run: from the fourth run on, a review defect of this task is a miss — an interactive element laid out under 48 dp in either direction that the check lets pass, on a screen or in a fixture built from controls the release uses. The wording of a failure message, and constructs no screen would contain, are listed under the review line for later screen tasks and do not return the task.
**Criteria of this requirement carried elsewhere:** A11Y-003 (1) → every later screen task, through this check; (2) → T-018; (3–4) → wave 8

**Acceptance criteria** (verbatim)

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

**Gate 1:** accepted — docs/product/verdicts/20261001-T-044-gate1.md
**Review:** defects — build and suite ok; the check reads the merged tree, so an element interactive through a semantics action alone inside a merging parent folds into the parent and is not measured (a 24 dp `semantics { onClick }` inside a 200 dp `clickable` passed; on the unmerged tree it fails and both screens still pass) (AccessibilityChecks.kt:34); noted, not a defect: Material controls such as `Checkbox` (24 dp) and `IconButton` (40 dp) measure under 48 dp as laid out and need an explicit size; after the fifth run (164e4c3, one width-only fixture): pass — build and suite ok, the criterion checked by mutation in both directions in the self-tests and on both screens, worktree as it was. Questions the developer returned unanswered, for the gate: whether a scroll container is an interactive element (excluded today), whether a disabled element is measured (it is), and whether a node whose only action is focus, text selection or copying is interactive (focus excluded, selection and copying included); no current screen has any of them on later screens; worktree as it was. Returned to the developer; after the fix (unmerged tree, 2d5f551): defects — build and suite ok, every size mutation on both screens and in the fixtures caught; a nested element with no label of its own is named in the failure by its semantics id ("node 40"), not by its click label, test tag or labelled ancestor (AccessibilityChecks.kt:45); a node acted on only through custom actions, expand/collapse, dismiss or an IME action is never selected, so one at 24 dp passes (AccessibilityChecks.kt:20); worktree as it was. Second return: blocked, taken to the user; a third run authorised by the user on 2026-10-01: the failure names an element by its click label, test tag or labelled ancestor, every node with any semantics action is selected, and the KDoc states that a tap handled by bare `pointerInput` is invisible to a semantics check; if its review finds defects again, it goes to the user (there is nothing left to split); after the third run (c8d8895): defects — build and suite ok, every size mutation on both screens and every selection and naming weakening caught; a small unlabelled element inside a merging ancestor that has a click label or test tag but no text is named by its semantics id, and the message wrongly says no action label exists (AccessibilityChecks.kt:103-108; on each such screen A11Y-005 fails too); worktree as it was. Blocked, taken to the user; a fourth run authorised by the user on 2026-10-01 for that naming defect, with the review bar below; after the fourth run (8fcfca1): defects — build and suite ok, no miss (18 screen mutations, 900 fixture combinations, every weakening but one caught); removing the width comparison fails no test, since no fixture is short in width alone (AccessibilityChecksTest.kt:81); worktree as it was. The user read that as within the bar on 2026-10-02 and authorised a fifth run to add the fixture; notes for later screen tasks: no self-test for an element whose only action is OnLongClick, SetProgress or Collapse; Material `Checkbox` (24 dp) and `IconButton` (40 dp) need an explicit size
**Gate 2:** accepted — docs/product/verdicts/20261002-T-044-gate2.md
**Returns:** 2 of 2, plus a third, fourth and fifth run the user authorised (gate 1 · reviewer · gate 2 share the count)

### T-045 — No interface string of the release is fixed in the code in one language only

**Requirements:** L10N-001
**Status:** in-progress
**Depends on:** T-008
**Expected files:** `app/src/test/kotlin/app/recipefork/app/ui/LocalizationChecks.kt` · `app/src/test/kotlin/app/recipefork/app/ui/InterfaceStringsTest.kt` · `app/src/test/kotlin/app/recipefork/app/ui/l10n/**` · `app/build.gradle.kts` · `gradle/libs.versions.toml` · `app/src/main/kotlin/app/recipefork/app/**` · `app/src/main/java/**` · `app/src/main/res/values/strings_*.xml` · `app/src/main/res/values-ru/strings_*.xml` · `core/src/main/kotlin/app/recipefork/core/**` · `core/src/test/kotlin/app/recipefork/core/**` · not `app/src/test/kotlin/app/recipefork/app/ui/AccessibilityChecks*.kt` (T-044's)
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Size:** about 500–700 changed lines, over the review ceiling of this backlog. It cannot be split: L10N-001 (2) is one criterion and one check, and it is reviewed as a whole because the holes three reviews found were each in a different part of it (the reading of the sources, the allow-list, the holders). The constant moves into holders are mechanical and make up roughly a third.
**Note:** Split out of T-043 on 2026-10-01 (see the withdrawal note below). L10N-001 (2) is checked over every Kotlin and Java main source of the release's code, `:app` and `:core` alike, not a chosen set of packages. A string literal holding a letter fails unless either its exact value is on a short allow-list of text that is never shown to a person (test tags, log tags, SQL, route and key names, file and module names, refusal codes on the wire, and the messages of programming-error checks such as `require` and `check`), kept in the test by value and by the one declared holder of its kind, so that the value passes only where that holder declares it — the same word anywhere else fails, and no name lets interface text through; or it sits in a two-language table the test names (today `UnitLabels` in `:core`, the twelve DOM-016 units), and the check then proves every entry of that table holds a Russian and an English value. Text in resources is enumerated by key: every key of `values/` exists in `values-ru/` and the reverse. Applied to the library (T-007) and the editor (T-008); whatever it finds is fixed there, with each moved string given its `values-ru` twin.
**Note:** How the check reads the code, decided after T-043's third review found a hand-written lexer reading Java by Kotlin's rules and holders matched by simple name: sources are read by real front ends — Kotlin through the Kotlin compiler's PSI, Java through JavaParser, each with its own comment and literal rules (Java comments do not nest; `$` in a Java literal is a letter like any other). A holder is matched by what it resolves to, not by its simple name: an object holder by its fully qualified name; a Room annotation only when it is `androidx.room`'s, by import or by full name; a programming-error call only when it is Kotlin's own `require`, `check`, `error`, `requireNotNull` or `checkNotNull` with no declaration of that name in the file or its package, or the argument of an `IllegalArgumentException` or `IllegalStateException` that is thrown where it is built. A literal of no kind is rewritten so that it is none, and the rewrite changes no invariant: a type whose constructor guards a value does not become a data class, whose `copy()` would bypass the guard. A kind may have one holder per module or per screen; each allowed value has exactly one. The branch `task/T-043` (a2fab41) holds the holder moves and may be read as a reference; what lands here is written and tested for this entry alone.
**Note:** Review bar, set by the user on 2026-10-01 after the second return: from the third run on, a review defect of this task is a letter-bearing literal that reaches the code check and passes it unflagged, or a condition of the check that no test fails without. Accepted limit, not a defect: a property named `kotlin` or `java` inherited from a library supertype outside the release can hide the package of a fully qualified call. Anything else is listed under the review line for later tasks.
**Criteria of this requirement carried elsewhere:** L10N-001 (1) → every screen task; (3) → release record

**Acceptance criteria** (verbatim)

*L10N-001 — spec/160-localization.md*
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

**Gate 1:** accepted — docs/product/verdicts/20261001-T-045-gate1.md
**Review:** defects — build and suite ok; every T-043 hole and the new probes caught (aliases, raw strings, `String.format`, Java text blocks, a same-named holder elsewhere); a programming-error call is still owned by name: a `error`/`require` inherited from a class in another file, or a member of an implicit receiver (`with(Say) { error(…) }`), passes a listed message as plain text (CodeLiterals.kt:159, 210); no test fails when Java files stop being listed (CodeLiterals.kt:67); noted: no test keeps `ScaleFactor` from becoming a data class; worktree as it was. Returned to the developer; after the fix (7cc07d5): defects — build and suite ok, every earlier hole and new probes caught; an unqualified `error` in a class whose companion object extends a class of another file, or in a class nested in such a class, still passes a listed message (CodeLiterals.kt:305-325); a supertype written `Any` is taken for `kotlin.Any` though an import alias may name another class `Any` (CodeLiterals.kt:315); no test fails when the Java throw condition is removed (CodeLiterals.kt:413) or when Kotlin's same-file declaration clause is removed (CodeLiterals.kt:253); worktree as it was. Second return: blocked, taken to the user; a third run authorised by the user on 2026-10-01: a programming-error message counts only in a fully qualified call (`kotlin.require`, `kotlin.check`, `kotlin.error`, `kotlin.requireNotNull`, `kotlin.checkNotNull`, and `java.lang.IllegalArgumentException`/`IllegalStateException` built in a `throw`), with the review bar below; the third run's review did not happen: the reviewer wrote a probe into the main tree's `LibraryScreen.kt` while running its tests in the worktree, was stopped on 2026-10-02 and left a mutation in `CodeLiterals.kt:243`; both restored on the user's word; not a return, the review is run again
**Gate 2:** —
**Returns:** 2 of 2, plus a third run the user authorised (gate 1 · reviewer · gate 2 share the count)

**T-043 withdrawn (2026-10-01).** T-043 — the release-wide checks of L10N-001 (2) and A11Y-003 (1), cut out of T-008 on 2026-09-30 — was rejected at gate 1 once (`:core` left out), and its first review returned it for the allow-list (a group outside the Note's kinds, and words let through by value): the second return. The user authorised a third run on 2026-10-01 with the fallback stated: if review or a gate found defects again, the branch would be abandoned and the task split into A11Y-003 (1) and L10N-001 (2) without asking again. Its third review found the A11Y-003 (1) check and the resource-key check sound, and four defects in the code check: Java comments read with Kotlin's nesting rule, so `/* /* */` hid the rest of a Java file; Java literals read with Kotlin's `$` template rule; the SQL and programming-error holders matched by simple name, so a local `Query` annotation or a local `error` function faked them; and `ScaleFactor` made a data class, whose `copy()` bypasses the positive-factor guard. So T-043 is split into T-044 and T-045. Its verdicts stay where they are: docs/product/verdicts/20260930-T-043-gate1.md, 20260930-T-043-gate1-2.md, 20261001-T-043-gate1.md. The branch `task/T-043` (a2fab41) and its worktree are kept as a reference; nothing of it is merged. The ID T-043 is not reused.

### T-031 — What the ingredient field offers for what the author types

**Requirements:** FLOW-057, L10N-001, A11Y-003, A11Y-005, FLOW-023, FLOW-001
**Status:** gated
**Depends on:** T-044, T-045 (T-043 until its split on 2026-10-01)
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
**Criteria of these requirements carried elsewhere:** FLOW-024 (1) → wave 5; (3, 5) → wave 4; (4, 6) → T-008 · FLOW-002 (2) → wave 4; (3–4) → T-008; (5–6) → T-028 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8 · DOM-015 (3) → wave 2; (4) → T-018

**Acceptance criteria** (verbatim)

*FLOW-024 — spec/70-flows/40-editor.md*
- Given a half-filled recipe, when the author scrolls to the steps without having entered a serving count, then the steps accept input.

*FLOW-002 — spec/70-flows/10-offline-and-sync.md*
- Given a clean installation with no account and no network connection, when the person creates a recipe and fills its title, servings, ingredients and steps, then every change is kept and is present after the application is closed and reopened, and no account was created or asked for.

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

**Gate 1:** rejected — docs/product/verdicts/20260927-T-013-gate1.md; re-sliced; blocked-by-spec (SD-013) — docs/product/verdicts/20260927-T-013-gate1-2.md; re-sliced against spec/ @ ead1a44 after SD-013 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-013-gate1-3.md; 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): FLOW-002 (1) replaced: a clean installation with no account; re-gate due; re-gated: accepted — docs/product/verdicts/20260928-T-013-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-014 — A step carries one timer, named by its step unless the author names it

**Requirements:** FLOW-027, DOM-013, GUIDE-007, L10N-001, A11Y-003, A11Y-005
**Status:** gated
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
- Given a timer being attached, when its duration is entered, then it is entered as hours and whole minutes, from 0 to 99 hours and from 0 to 59 minutes, and no seconds are offered.
- Given a duration of 0 hours and 0 minutes, when the recipe is saved, then it is a duration the author has not entered, and criterion 11 governs: the timer is not stored and nothing is refused.
- Given the duration field, when the author tries to enter more than 99 hours and 59 minutes, then the field cannot hold it, and the longest timer a recipe can carry is 99 hours 59 minutes.

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
- Given the statement, when the author touches any field or control of the editor or types a character, then that is their next action and the statement is gone.
- Given the statement, when the author scrolls the editor, or moves the screen reader's focus (A11Y-005) — including onto the statement itself — then that is not an action in the sense of criterion 4, and the statement stays, so a person using the screen reader can reach it and hear it.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-005) — docs/product/verdicts/20260927-T-014-gate1.md; re-sliced against spec/ @ ead1a44 after SD-005 resolved; re-gated: blocked-by-spec (SD-030, the timer's duration field and GUIDE-007's next action) — docs/product/verdicts/20260927-T-014-gate1-2.md; re-sliced against spec/ @ 959130e after SD-030 resolved (FLOW-027 (12–14) and GUIDE-007 (5–6) added); re-gated: accepted — docs/product/verdicts/20260927-T-014-gate1-3.md
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
**Criteria of these requirements carried elsewhere:** FLOW-027 (1–7, 9, 11–14) → T-014; (8) → T-017 · A11Y-006 (2) → wave 8; (3) → wave 2 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

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
**Criteria of these requirements carried elsewhere:** FLOW-028 (4, 6) → wave 2 · FLOW-027 (1–7, 9, 11–14) → T-014; (10) → T-016 · DOM-012 (1–7) → wave 2 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

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

**Requirements:** FLOW-029, FLOW-053, FLOW-019, FLOW-038, SCALE-011, SCALE-012, DOM-003, DOM-011, L10N-003, A11Y-007, A11Y-003, L10N-001, A11Y-005, L10N-009, DOM-015, FLOW-017
**Status:** gated
**Depends on:** T-008, T-029, T-044, T-045 (added 2026-10-01 as T-043, whose expected files covered all of `:app`'s main code; T-043 was split the same day)
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/main/kotlin/app/recipefork/app/ui/nav/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Size:** The card is the screen the product exists for and its first version carries the layout every later card task builds on. Expected at 450–550 changed lines.
**Note:** A11Y-003 (2) and A11Y-007 (2) hold for every control on the card, so each card task that adds one — this one (the serving control), T-022, T-033 and T-024 (the timer controls) — carries them against the controls it adds.
**Criteria of these requirements carried elsewhere:** FLOW-053 (1–6, 8) → wave 2 · FLOW-019 (4) → wave 3 · FLOW-038 (3–4) → wave 5; (5) → T-007; (7) → wave 3 · SCALE-011 (4) → T-020 · DOM-011 (2–3) → wave 2; (4) → wave 8 · L10N-003 (1) → T-003; (2) → T-011 · A11Y-007 (3) → T-033; (4) → T-025; (5) → wave 8 · A11Y-003 (3–4) → wave 8 · L10N-001 (3) → release record · A11Y-005 (2–6) → wave 8 · L10N-009 (1–4, 6) → T-029 · DOM-015 (1–2) → T-013; (3) → wave 2 · FLOW-017 (1, 3–7) → wave 3

**Acceptance criteria** (verbatim)

*FLOW-029 — spec/70-flows/40-editor.md*
- Given a recipe the author saves, when it is opened afterwards, then it is the same card a reader sees (FLOW-019, FLOW-020), not an editor with reader styling.
- Given that view of a recipe that is still editable, when the author chooses the edit action in the card screen's top bar, then the editor opens on the same recipe.
- Given that view, when the author looks for an edit control inside the recipe's content — its title, ingredients, steps, description or media — then there is none and no part of it becomes editable in place: the card is read, the editor is written, and they are two screens; the edit action sits in the screen's bar, outside the recipe.
- Given a recipe that is no longer editable by `entity-states`, when the author opens it, then the card screen's bar carries no edit action and no way into the editor is offered at all.

*FLOW-053 — spec/70-flows/55-own-recipe-and-author.md*
- Given the recipe card screen of an own recipe, when it is looked at, then it carries no deletion control, neither in the recipe nor in the screen's bar, because a recipe's state is managed on its entry in the list (FLOW-036); the edit action in the bar (FLOW-029) opens the editor and deletes nothing.

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

**Gate 1:** blocked-by-spec (SD-016, SD-002, SD-013) — docs/product/verdicts/20260927-T-018-gate1.md; A11Y-003 (2) and A11Y-007 (2) added after this verdict; re-sliced against spec/ @ ead1a44 after SD-016, SD-002, SD-013 resolved; re-gated: blocked-by-spec (SD-031, where a saved recipe's edit is chosen) — docs/product/verdicts/20260927-T-018-gate1-2.md; re-sliced against spec/ @ 959130e after SD-031 resolved (FLOW-029 (2–4) replaced; FLOW-053 (7) added, since this task builds the bar it constrains); re-gated: accepted — docs/product/verdicts/20260927-T-018-gate1-3.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-019 — A derived quantity says so, and which way it was rounded

**Requirements:** CON-004, SCALE-004, A11Y-008, L10N-001, A11Y-003, A11Y-005, SCALE-013, SCALE-014, SCALE-015, SCALE-001
**Status:** gated
**Depends on:** T-018, T-030
**Expected files:** `core/src/main/kotlin/app/recipefork/core/scaling/**` · `core/src/test/kotlin/app/recipefork/core/scaling/**` · `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Criteria of these requirements carried elsewhere:** A11Y-008 (2) → wave 6; (3) → T-007; (4) → wave 8 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8 · SCALE-013 (1–2, 4) → T-029 · SCALE-014 (1–3, 5) → T-029 · SCALE-015 (3, 6) → T-001; (4–5) → T-002 · SCALE-001 (1, 3–4) → T-001

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

*SCALE-001 — spec/80-scaling-rules/10-scaling.md*
- Given the same recipe with 3 indivisible pieces, when the reader asks for 6 (an exact 4.5 pieces), then it shows 5 pieces and states that the exact value was lower (SCALE-015, SCALE-004).

**Gate 1:** blocked-by-spec (SD-012) — docs/product/verdicts/20260927-T-019-gate1.md; re-sliced against spec/ @ ead1a44 after SD-012 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-019-gate1-2.md; SCALE-001 (2) moved here from T-001 after spec/ @ 959130e, re-gated: accepted — docs/product/verdicts/20260927-T-019-gate1-3.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-020 — The reader fixes an ingredient on the card

**Requirements:** SCALE-002, SCALE-006, SCALE-008, SCALE-009, SCALE-011, A11Y-003, A11Y-007, L10N-001, A11Y-005, SCALE-018, DOM-019
**Status:** gated
**Depends on:** T-019, T-030, T-015
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml` · `core/src/main/kotlin/app/recipefork/core/recipe/TypedNumber.kt` · `core/src/test/kotlin/app/recipefork/core/recipe/TypedNumberTest.kt`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-test-strategy.md
**Note:** The rules for a fixed line and for releasing a fixing (SCALE-016, SCALE-017) are T-030's, checked in `:core` where they are computed; this card renders the state T-030's model gives it and computes none of it. A11Y-003 (2) is not carried here: the controls it names are the serving control (T-018) and the timer controls (T-022 onwards); the fixing control this task adds is held to 56 dp by A11Y-007 (2).
**Note:** 20260929 re-slice against spec/ @ 952398f (SD-037 answered by DOM-019): a typed number is read by one reader in `:core`, `recipe/TypedNumber.kt`, built here for the fixing control and used again by T-035 for the quantity field. DOM-019 (1–3, 5) speak of any typed number: they are observed here in the fixing control and again in T-035 in the quantity field; DOM-019 (4) is the quantity field's grid (DOM-017) and is T-035's.
**Criteria of these requirements carried elsewhere:** SCALE-002 (1, 3) → T-002 · SCALE-006 (1, 3) → T-002 · SCALE-008 (1–2, 4–8) → T-002 · SCALE-011 (1–3) → T-018 · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-007 (1) → T-018; (3) → T-033; (4) → T-025; (5) → wave 8 · L10N-001 (3) → release record · A11Y-005 (2–6) → wave 8 · DOM-019 (1–3, 5) → T-035 as well, in the quantity field; (4) → T-035

**Acceptance criteria** (verbatim)

*SCALE-002 — spec/80-scaling-rules/10-scaling.md*
- Given that state, when the serving count is displayed, then it is shown as an approximate 2 servings and is marked as approximate.

*SCALE-006 — spec/80-scaling-rules/10-scaling.md*
- Given that state, when the shortfall is displayed, then it names both the required and the available amount.

*SCALE-008 — spec/80-scaling-rules/10-scaling.md*
- Given a computed serving count displayed as a fraction, when it is displayed, then it carries the same approximate marking that SCALE-002 requires; a count the reader requested is never marked as approximate, in any form (SCALE-011).

*SCALE-009 — spec/80-scaling-rules/20-display.md*
- Given a recipe scaled so that exactly one ingredient is held at its floor by SCALE-005, when the recipe is displayed, then no warning is shown.
- Given a recipe scaled so that two ingredients are held at their floors, when the recipe is displayed, then the warning is shown.
- Given a recipe scaled so that two ingredients are held at their floors, when the reader returns the recipe to its authored scale, then the warning disappears.
- Given the warning is shown, when the reader reads the ingredient list, then every affected line still carries its own rounded-up marking from SCALE-005.

*SCALE-011 — spec/80-scaling-rules/10-scaling.md*
- Given a count requested as a fraction, when the recipe is displayed, then the count is displayed in the same form it was requested and is not marked as approximate, because it is the count the reader asked for (SCALE-008).

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-007 — spec/200-accessibility.md*
- Given the cooking card, when its controls are measured, then A11Y-003's larger figure holds.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

*SCALE-018 — spec/80-scaling-rules/10-scaling.md*
- Given 500 g of chicken, when the reader fixes `300 г` or `0,3 кг`, then either is accepted and the chicken line shows 300 g (SCALE-002).
- Given 5 g of an ingredient, when the reader fixes `10 кг`, then it is accepted (SCALE-010).
- Given the fixing control, when the reader enters 0, a negative number or `109,375 г`, then the control does not hold it and the scale in force does not change.
- Given an indivisible piece ingredient, when the reader enters `2,5 шт` or `½ шт`, then the control does not hold it; given a divisible one, `2½ шт` is held.
- Given an ingredient authored in grams, when the reader looks for a unit to fix it in, then ml, l and m³ are not offered: nothing converts a mass to a volume.
- Given an ingredient authored in `ст. л.`, when the reader fixes it, then the control accepts tablespoons only, on the `household` grid, and offers neither `ч. л.` nor `стакан` (DOM-003).
- Given a `taste_unmeasured` ingredient, when the ingredient list is displayed, then it offers no fixing, because it has no authored amount to divide by (DOM-002, SCALE-002).
- Given the fixing control holding an amount, when the reader clears it, then the scale in force does not change until the reader enters an accepted amount or releases the fixing (SCALE-017); clearing the control is not a release.

*DOM-019 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given the interface in Russian, when `1,5 кг` or `1.5 кг` is typed, then either is read as 1.5 kg and displayed as `1,5 кг` (L10N-009).
- Given the interface in English, when `1.5 kg` or `1,5 kg` is typed, then either is read as 1.5 kg and displayed as `1.5 kg`.
- Given the interface in English, when `1,500 kg` is typed, then it is read as 1.5 kg, not 1500 kg, and the field displays `1.5 kg`.
- Given either interface language, when a number carrying a space, two decimal marks, or both a comma and a point is typed — `1 500 г`, `1.500,5 г`, `1,5,0 кг` — then the field does not hold it and states that a number is written without separators.
- Given the fixing control, when `0,3 кг` or `0.3 кг` is typed for an ingredient authored in grams, then either is accepted as 300 g (SCALE-018 (1)).

**Gate 1:** blocked-by-spec (SD-003, SD-011, SD-002) — docs/product/verdicts/20260927-T-020-gate1.md; A11Y-003 (2) and A11Y-007 (2) added after this verdict; re-sliced against spec/ @ ead1a44 after SD-003, SD-011, SD-002 resolved; re-gated: blocked-by-spec (SD-032, whether a requested fractional count is marked approximate) — docs/product/verdicts/20260927-T-020-gate1-2.md; re-sliced against spec/ @ 959130e after SD-032 resolved (SCALE-008 (3), SCALE-011 (4) replaced; A11Y-003 (2) left to the tasks that build the controls it names); re-gated: blocked-by-spec (SD-033, the amounts a reader may fix) — docs/product/verdicts/20260927-T-020-gate1-3.md; 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): SD-033 answered in spec/ @ 1168954; SCALE-018 added; re-gate due; re-gated: blocked-by-spec (SD-037, reached through SCALE-018) — docs/product/verdicts/20260928-T-020-gate1.md; SCALE-018 moved to T-039 at the end of the card chain, so that SD-037 holds up only the control's input rule; re-gate due; re-gated: blocked-by-spec (SD-037: reading a typed amount belongs to the control this task builds) — docs/product/verdicts/20260928-T-020-gate1-2.md; SCALE-018 returned to this task and T-039 withdrawn, since the split moved nothing SD-037 stops; re-sliced against spec/ @ 952398f after SD-037 resolved (DOM-019 (1–3, 5–6) added, observed in the fixing control); re-gate due; re-gated: accepted — docs/product/verdicts/20260929-T-020-gate1.md
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
**Depends on:** T-021, T-014
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/kotlin/app/recipefork/app/timer/**` · `app/src/test/kotlin/app/recipefork/app/timer/**` · `app/src/main/kotlin/app/recipefork/app/AppContainer.kt` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml` · `app/src/main/res/values/strings_timer.xml` · `app/src/main/res/values-ru/strings_timer.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Note:** 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): the account tasks this depended on for shared files moved to wave 1b; criteria and expected files unchanged, so the gate-1 verdict stands.
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
**Depends on:** T-022
**Expected files:** `app/src/main/kotlin/app/recipefork/app/card/**` · `app/src/test/kotlin/app/recipefork/app/card/**` · `app/src/main/res/values/strings_card.xml` · `app/src/main/res/values-ru/strings_card.xml` · `core/src/main/kotlin/app/recipefork/core/timer/**` · `core/src/test/kotlin/app/recipefork/core/timer/**` · `app/src/main/kotlin/app/recipefork/app/timer/**` · `app/src/test/kotlin/app/recipefork/app/timer/**` · `app/src/main/AndroidManifest.xml` · `app/src/main/kotlin/app/recipefork/app/MainActivity.kt` · `app/src/main/kotlin/app/recipefork/app/ui/nav/**` · `app/src/main/res/values/strings_timer.xml` · `app/src/main/res/values-ru/strings_timer.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Note:** The cut of FLOW-016 (3–8) is built here as a function in `:core` `timer`, so that T-034's editor display shows exactly the text this notification carries.
**Note:** 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): the account tasks this depended on for shared files moved to wave 1b; criteria and expected files unchanged, so the gate-1 verdict stands.
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

### T-035 — The quantity field holds only a value on its unit type's grid

**Requirements:** DOM-017, DOM-019, L10N-001
**Status:** gated
**Depends on:** T-028, T-029, T-020
**Expected files:** `core/src/main/kotlin/app/recipefork/core/recipe/**` · `!core/src/main/kotlin/app/recipefork/core/recipe/Units.kt` · `core/src/test/kotlin/app/recipefork/core/recipe/**` · `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260927-kotlin-monorepo-with-shared-core.md, decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Note:** The grid values are those T-001 and T-029 compute in `:core` `scaling`; this task reads them and does not change `scaling`. The quantity field itself is T-011's and the free-text divisibility T-012's; this task constrains what that field holds.
**Note:** 20260929 re-slice against spec/ @ 952398f (SD-037 answered): a typed number is read by T-020's `recipe/TypedNumber.kt`, which this task uses in the quantity field; DOM-019 (1–3, 5) are observed here in that field as T-020 observes them in the fixing control. A decimal entry of a fraction-grid value is held and displayed as the grid writes it, and the field's error names the fractions it accepts (DOM-017 (3)).
**Note:** Ordered after T-028, the last task on the editor's files, so that SD-037 holds up no other task.
**Criteria of these requirements carried elsewhere:** DOM-017 (6–7) → wave 2, where submission exists; (8) → T-001; (9) → wave 4 · DOM-019 (6) → T-020 · L10N-001 (3) → release record

**Acceptance criteria** (verbatim)

*DOM-017 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given the quantity field, when the author enters `500 г`, `1,5 кг`, `⅓ ч. л.`, `1½ шт` of a divisible ingredient or `3 шт` of an indivisible one, then the field holds that value.
- Given the quantity field, when the author enters 0, a negative number, `7,5 г`, `1,2345 кг`, `0,3 ч. л.`, or, of an indivisible ingredient, `2,5 шт` or `½ шт`, then the field does not hold it.
- Given the quantity field, when the author enters a value of a fraction grid with a decimal mark — `2,5 шт` of a divisible ingredient, `0,5 ч. л.`, `0,25 ч. л.` — then the field holds it and it is displayed as the grid writes it: 2½, ½, ¼. `0,33 ч. л.` and `0,66 ч. л.` are not held, because ⅓ and ⅔ have no decimal form on the grid; the field's error names the fractions it accepts.
- Given the quantity field, when the author enters `1 г`, `¼ ч. л.`, `½ шт` of a divisible ingredient or `1 шт` of an indivisible one, then the field holds it: each is the floor of its grid.
- Given the quantity field, when the author enters a number however large that lies on the grid, then the field holds it.

*DOM-019 — spec/50-domain-model/20-ingredients-and-limits.md*
- Given the interface in Russian, when `1,5 кг` or `1.5 кг` is typed, then either is read as 1.5 kg and displayed as `1,5 кг` (L10N-009).
- Given the interface in English, when `1.5 kg` or `1,5 kg` is typed, then either is read as 1.5 kg and displayed as `1.5 kg`.
- Given the interface in English, when `1,500 kg` is typed, then it is read as 1.5 kg, not 1500 kg, and the field displays `1.5 kg`.
- Given either interface language, when `1,2345 кг` or `1.2345 kg` is typed, then it is read as 1.2345 kg and not held, because it is not a whole number of grams (DOM-017).
- Given either interface language, when a number carrying a space, two decimal marks, or both a comma and a point is typed — `1 500 г`, `1.500,5 г`, `1,5,0 кг` — then the field does not hold it and states that a number is written without separators.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

**Gate 1:** blocked-by-spec (SD-037, how a typed number is read in each language, and whether a decimal entry of a fraction value is held) — docs/product/verdicts/20260928-T-035-gate1.md; re-sliced against spec/ @ 952398f after SD-037 resolved (DOM-017 (2) replaced, (3) added; DOM-019 (1–5) added); re-gate due; re-gated: accepted — docs/product/verdicts/20260929-T-035-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

## Wave 1b — an account, the recipe books on a device, and sending

A person creates an account from the recipe book, gets a book of their own and is offered the pool; a second account
on the same device is switched to from a list; the owner sends a recipe, sees whether each recipe was sent, and is told
when a divergent sending became a fork. Sending with no account offers creation.

| Task | Title | Depends on | Status |
|---|---|---|---|
| T-009 | The first step of creation shows the nickname and the terms | T-006, T-022 | gated |
| T-032 | The second step of creation issues the account key and the deletion key | T-009 | gated |
| T-010 | The recovery key is issued on the third step, and leaving creation leaves nothing | T-032 | gated |
| T-036 | A new account gets a recipe book of its own, and the pool is offered to it once | T-010 | gated |
| T-040 | The server keeps a sent recipe under its owner, reached with a sign-in secret | T-006, T-045 | gated |
| T-041 | A divergent sending becomes a fork, and a frozen recipe is never written | T-040 | gated |
| T-042 | A sending the server already holds changes nothing, and a repeated divergent sending makes one fork | T-041 | gated |
| T-046 | A recipe the server lost is created again, and a sending with no revision is taken as made from the first | T-042 | gated |
| T-028 | The owner sends a recipe, and every recipe says whether it was sent | T-042, T-046, T-036, T-025, T-034 | gated |
| T-038 | A divergent sending tells the owner a fork was made, and a changed server copy never overwrites the device's | T-028 | gated |
| T-026 | Sending with no account offers to create one | T-038 | gated |
| T-047 | A pool recipe whose account was created muted, or whose sending was refused, waits in that account's book | T-026 | gated |

### T-009 — The first step of creation shows the nickname and the terms

**Requirements:** AUTH-024, AUTH-018, AUTH-022, LEG-004, CON-001, L10N-001, A11Y-003, A11Y-005, FLOW-051
**Status:** gated
**Depends on:** T-006, T-022
**Expected files:** `app/src/main/kotlin/app/recipefork/app/account/**` · `app/src/test/kotlin/app/recipefork/app/account/**` · `app/src/main/kotlin/app/recipefork/app/AppContainer.kt` · `app/src/main/assets/terms/**` · `app/src/main/res/values/strings_account.xml` · `app/src/main/res/values-ru/strings_account.xml` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260928-sign-in-secrets-lapse-and-retired-keys-remove-the-account.md, decisions/20260927-test-strategy.md
**Note:** Open, raised to the user: the text of the terms of use does not exist yet (MVP-003, OQ-029). The task presents the versioned document from `app/src/main/assets/terms/`; until the text is supplied that document is a placeholder that says so in both languages.
**Note:** Creation is reached from the recipe book's offer of FLOW-051 (7), which this task builds, and later from the send control (T-026). It depends on T-022 for `AppContainer.kt`; the recipe book's files were last touched by T-018, which T-022 follows.
**Criteria of these requirements carried elsewhere:** AUTH-024 (3, 5–6) → T-032; (4, 7–9) → T-010 · AUTH-018 (3, 5) → T-005; (4) → wave 7 · AUTH-022 (1, 3) → T-005 · LEG-004 (1) → T-010; (2) → wave 3; (3) → wave 7; (4) → T-005 · CON-001 (2) → T-005 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8 · FLOW-051 (1–5) → wave 3; (6) → T-036

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

*FLOW-051 — spec/70-flows/20-device-storage.md*
- Given no account, when the recipe book is opened, then it states that what it holds lives on this device and offers to create an account to carry it, without requiring it.

**Gate 1:** blocked-by-spec (SD-001, SD-009, SD-010) — docs/product/verdicts/20260927-T-009-gate1.md; split into T-009 and T-032 by the three steps of AUTH-024; re-sliced against spec/ @ ead1a44 after SD-001, SD-009, SD-010 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-009-gate1-2.md; 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): FLOW-051 (7) added as the trigger of creation; moved to wave 1b; re-gate due; re-gated: rejected — docs/product/verdicts/20260928-T-009-gate1.md (LEG-004 (1) needs an account created, which completes only on T-010's third step); LEG-004 (1) moved to T-010; re-gate due; re-gated: accepted — docs/product/verdicts/20260928-T-009-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-032 — The second step of creation issues the account key and the deletion key

**Requirements:** AUTH-024, AUTH-007, AUTH-017, AUTH-014, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-009
**Expected files:** `app/src/main/kotlin/app/recipefork/app/account/**` · `app/src/test/kotlin/app/recipefork/app/account/**` · `app/src/main/res/values/strings_account.xml` · `app/src/main/res/values-ru/strings_account.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260928-sign-in-secrets-lapse-and-retired-keys-remove-the-account.md, decisions/20260927-test-strategy.md
**Note:** 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): moved to wave 1b and the binding keys ADR replaced by its successor; criteria and expected files unchanged, so the gate-1 verdict stands.
**Criteria of these requirements carried elsewhere:** AUTH-024 (1–2) → T-009; (4, 7–9) → T-010 · AUTH-007 (7, 9–10) → T-010; (8) → wave 7 · AUTH-017 (5, 8–9) → wave 7; (6–7) → T-005 · AUTH-014 (1–4) → T-005; (5–6) → wave 7 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*AUTH-024 — spec/45-auth/20-account-key.md*
- Given the first step completed, when the second step is shown, then it holds the account key and the deletion key, each displayed in full with its own copy control, the file of AUTH-007 (2) carrying both, and the one confirmation of AUTH-007 and AUTH-017, and neither the nickname control nor the terms.
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
- Given account creation, when the key is issued, then the deletion key is issued on the same step (AUTH-024) and is carried by the file that AUTH-007 offers.
- Given that step, when it is shown, then the deletion key is displayed in full beside the account key and carries a copy control of its own, which copies the deletion key alone in one action.
- Given the account key's copy control on that step (AUTH-007 (1)), when it is used, then what reaches the clipboard is the account key alone and not the deletion key.
- Given that step, when its confirmation is shown, then it is one confirmation whose text names both keys, and AUTH-007 (4) governs it; the deletion key adds no second confirmation.

*AUTH-014 — spec/45-auth/30-account-life.md*
- Given the account key, when it is issued, then it is text the holder can copy and keep anywhere (AUTH-008, EXT-008), and the deletion key is issued alongside it as separate text with its own copy control (AUTH-017); the account key's text does not contain the deletion key.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-029, the deletion key on the second step) — docs/product/verdicts/20260927-T-032-gate1.md; re-sliced against spec/ @ 959130e after SD-029 resolved (AUTH-024 (3), AUTH-017 (1) replaced; AUTH-017 (2–4) and AUTH-014 (7) added, since this step issues both texts); re-gated: accepted — docs/product/verdicts/20260927-T-032-gate1-2.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-010 — The recovery key is issued on the third step, and leaving creation leaves nothing

**Requirements:** AUTH-020, AUTH-007, AUTH-024, L10N-001, A11Y-003, A11Y-005, LEG-004
**Status:** gated
**Depends on:** T-032
**Expected files:** `app/src/main/kotlin/app/recipefork/app/account/**` · `app/src/test/kotlin/app/recipefork/app/account/**` · `app/src/main/res/values/strings_account.xml` · `app/src/main/res/values-ru/strings_account.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260928-sign-in-secrets-lapse-and-retired-keys-remove-the-account.md, decisions/20260927-test-strategy.md
**Note:** 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): moved to wave 1b and the binding keys ADR replaced by its successor.
**Criteria of these requirements carried elsewhere:** AUTH-020 (3–4) → wave 7; (5) → T-005 · AUTH-007 (1–6) → T-032; (8) → wave 7 · AUTH-024 (1–2) → T-009; (3, 5–6) → T-032 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8 · LEG-004 (2) → wave 3; (3) → wave 7; (4) → T-005; (5) → T-009

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

*LEG-004 — spec/190-legal.md*
- Given account creation (AUTH-007), when the account is created, then the terms are presented and accepted there — on its first step, before any key is shown (AUTH-024) — and the accepted version is recorded with the account.

**Gate 1:** blocked-by-spec (SD-007) — docs/product/verdicts/20260927-T-010-gate1.md; re-sliced against spec/ @ ead1a44 after SD-007 resolved; re-gated: accepted — docs/product/verdicts/20260927-T-010-gate1-2.md; LEG-004 (1) added from T-009 after its gate 1 of 2026-09-28 (the account exists once this step confirms it); re-gate due; re-gated: accepted — docs/product/verdicts/20260928-T-010-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-036 — A new account gets a recipe book of its own, and the pool is offered to it once

**Requirements:** AUTH-026, ACT-002, FLOW-051, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-010
**Expected files:** `app/src/main/kotlin/app/recipefork/app/book/**` · `app/src/test/kotlin/app/recipefork/app/book/**` · `app/src/main/kotlin/app/recipefork/app/account/**` · `app/src/test/kotlin/app/recipefork/app/account/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/main/kotlin/app/recipefork/app/AppContainer.kt` · `app/src/main/res/values/strings_account.xml` · `app/src/main/res/values-ru/strings_account.xml` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml`
**Binding ADRs:** decisions/20260928-one-database-per-recipe-book.md, decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Note:** The account created by T-009–T-010 gets its book database here and becomes the active book; the pool's move into it is one transaction of the account's database, as the one-database-per-book ADR states. In this wave the pool holds recipes only: favourites arrive in wave 3.
**Note:** 20260929: AUTH-026 (3) now keeps in the account the pool recipe whose sending created it (SD-040). That recipe first exists with T-026, which carries (3) again for it; here a declined offer leaves nothing from the pool in the account's book.
**Criteria of these requirements carried elsewhere:** AUTH-026 (3) → T-026 as well, for the recipe whose sending created the account; (4) → T-026; (5–6) → wave 7; (8) → T-028 · ACT-002 (2) → T-028; (3) → T-026; (4) → T-040; (5) → wave 8 · FLOW-051 (1–5) → wave 3; (7) → T-009 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*AUTH-026 — spec/45-auth/25-accounts-on-a-device.md*
- Given a pool holding recipes and favourites, when an account is created or signs in on the device, then the offer is shown before the account's recipe book is.
- Given the offer accepted, when it completes, then everything in the pool is that account's, the pool is empty, and the account's recipe book shows it.
- Given the offer declined, when the account's recipe book is shown, then nothing from the pool is in it but a recipe whose sending created the account (FLOW-059 (1)), and the pool is shown again whenever no account is active.
- Given a pool that is empty, when any account becomes active, then no offer is shown and the menu action is not offered.

*ACT-002 — spec/40-actors.md*
- Given a recipe created while an account was active, when its owner is resolved at any point in its lifetime, then it is that account.

*FLOW-051 — spec/70-flows/20-device-storage.md*
- Given the pool, when an account becomes active on the device, then AUTH-026 offers to move the pool into it; declined, the pool stays on the device and is shown again whenever no account is active.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** accepted — docs/product/verdicts/20260928-T-036-gate1.md; AUTH-026 (3) replaced in spec/ @ 952398f (SD-040: the pool recipe whose sending created the account is that account's); re-gate due; re-gated: accepted — docs/product/verdicts/20260929-T-036-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-040 — The server keeps a sent recipe under its owner, reached with a sign-in secret

**Requirements:** ACT-002, AUTH-015, AUTH-025, FLOW-059, FLOW-008
**Status:** gated
**Depends on:** T-006, T-045 (added 2026-10-01: T-045's expected files cover all of `:core`'s main code, which holds `api/RecipeContract.kt`)
**Expected files:** `server/src/main/kotlin/app/recipefork/server/Application.kt` · `server/src/main/kotlin/app/recipefork/server/recipe/**` · `server/src/main/kotlin/app/recipefork/server/auth/**` · `server/src/main/resources/db/migration/V3__recipes.sql` · `core/src/main/kotlin/app/recipefork/core/api/RecipeContract.kt` · `server/src/test/kotlin/app/recipefork/server/recipe/**` · `server/src/test/kotlin/app/recipefork/server/auth/**` · `server/src/test/kotlin/app/recipefork/server/account/VerdictNotKeptTest.kt`
**Binding ADRs:** decisions/20260928-manual-sending-replaces-the-outbox.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260928-sign-in-secrets-lapse-and-retired-keys-remove-the-account.md, decisions/20260927-test-strategy.md
**Size:** The first recipe on the server and the first request signed with a sign-in secret: the recipes table and its guards, the multipart sending of the sending ADR, the sign-in record and its secret. Expected at 500–700 changed lines.
**Note:** Re-sliced out of T-027 on 2026-09-30 (see the withdrawal note after T-042). The branch `task/T-027` (727bd60) holds a working implementation of all three tasks and may be read as a reference; what lands here is written and tested for this entry's criteria alone, and a test of sameness or divergence varies every field of the content, not the title only.
**Note:** A sending is one multipart request whose one part, until wave 5, is the recipe document; it names the revision the device's copy was made from, or none on a first sending. A first sending creates the recipe in `private` under the sending account; a later one whose revision is the server's current one replaces it. A recipe of another account is answered as one that does not exist. Until the tasks after this one land, three kinds of sending are refused here and store nothing, and no user can reach any of them before T-028, which depends on all of those tasks: a revision other than the server's current one, which is divergence and becomes T-041's (this includes a revision above the server's current one, which a restore under QUAL-008 can leave on a device, as the gate-1 verdict of 2026-09-30 found); a recipe the server does not hold, sent naming a revision, which is FLOW-059 (13) and T-046's; and a recipe the server holds, sent naming no revision, which is FLOW-059 (14) and T-046's. The server half of FLOW-059 (4) is carried here and observed at the service; T-028 carries the same criterion on the device. AUTH-025 (1)'s "does not count toward QUAL-009" is observed as the sign-in secret, not the key, being presented; the attempt counter itself is wave 7's.
**Criteria of these requirements carried elsewhere:** ACT-002 (1) → T-036; (2) → T-028; (3) → T-026 and T-047; (5) → wave 8 · AUTH-015 (1–2) → T-006; (3) → wave 7 · AUTH-025 (2, 4–9) → wave 7 · FLOW-059 (1) → T-026; (2) → T-028; (3) → wave 7; (4) → T-028 as well, on the device; (5–6, 11) → T-028; (7–8) → wave 5; (9) → T-038; (10) → wave 4; (12) → T-047; (13–14) → T-046 at the server, T-038 on the device · FLOW-008 (1, 3, 6) → T-038; (2, 4) → T-041; (7) → T-042

**Acceptance criteria** (verbatim)

*FLOW-008 — spec/70-flows/15-divergence.md*
- Given no divergence — device B's version was made from the one the server holds — when device B sends, then the server's copy is replaced and no fork is created.

*ACT-002 — spec/40-actors.md*
- Given a recipe the server holds, when its owner is resolved, then it is the account that sent it, which is always the account it belonged to on the device.

*AUTH-015 — spec/45-auth/30-account-life.md*
- Given a sign-in record (AUTH-025), when the stored data is enumerated, then it is not a device identifier under this requirement: it is a random secret the server minted, derived from nothing the device is, runs or announces.

*AUTH-025 — spec/45-auth/30-account-life.md*
- Given a device that has signed in, when it makes any later request for the account, then it presents the sign-in secret and not the account key, and the request does not count toward the ten of QUAL-009.
- Given a sign-in record, when it is examined, then it holds the account it belongs to and nothing from which the device could be identified (AUTH-015).

*FLOW-059 — spec/70-flows/10-offline-and-sync.md*
- Given a sent recipe edited on the device, when it is sent again, then the server's copy is replaced by the device's version, unless FLOW-008 finds the two diverged.

**Gate 1:** blocked-by-spec (SD-045, what the server does with a sending that names a revision of a recipe it no longer holds — after a restore that lost it, or a deletion on another device) — docs/product/verdicts/20260930-T-040-gate1.md; the verdict also names, for the re-slice, a device revision ahead of the server's after a restore, which FLOW-008 makes a divergence and this entry refused; re-sliced against spec/ @ e70b62c after SD-042–SD-045 resolved (FLOW-059 (13) and (14) placed in T-046 and refused here only until it lands; a revision above the server's current one is divergence and goes to T-041; FLOW-008 (5) recopied from 15-divergence.md); re-gate due; re-gated: accepted — docs/product/verdicts/20261001-T-040-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-041 — A divergent sending becomes a fork, and a frozen recipe is never written

**Requirements:** FLOW-008, CON-003, DOM-004
**Status:** gated
**Depends on:** T-040
**Expected files:** `server/src/main/kotlin/app/recipefork/server/recipe/**` · `server/src/main/resources/db/migration/V4__forks.sql` · `core/src/main/kotlin/app/recipefork/core/api/RecipeContract.kt` · `server/src/test/kotlin/app/recipefork/server/recipe/**`
**Binding ADRs:** decisions/20260928-manual-sending-replaces-the-outbox.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-test-strategy.md
**Note:** Re-sliced out of T-027 on 2026-09-30 (see the withdrawal note after T-042). The branch `task/T-027` (727bd60) holds a working implementation of all three tasks and may be read as a reference; what lands here is written and tested for this entry's criteria alone, and a test of sameness or divergence varies every field of the content, not the title only.
**Note:** The following sendings are kept apart from the server's version as a `private` fork owned by the same account, naming the server's version as parent, and the answer says a fork was made and why:
- a sending made from a revision other than the server's current one, whether older than it or, after a restore under QUAL-008, above it (the T-040 gate-1 verdict of 2026-09-30, unknown (3), and FLOW-008 (5): such a version was not "made from the one the server holds");
- a sending against a server copy whose state alone changed.

The store holds the parent and the frozen content fixed. The notice on the device is T-038's. FLOW-008 (4) is tested two ways: with an edit, and with a change of state alone to `link_shared` or `published` where device B's version carries an edit. A version the same as the server's is T-042's case, not a fork. States other than `private` cannot be reached in wave 1b, so tests seed them.
**Criteria of these requirements carried elsewhere:** FLOW-008 (1, 3, 6) → T-038; (5) → T-040; (7) → T-042 · CON-003 (2) → wave 4; (3) → wave 9

**Acceptance criteria** (verbatim)

*FLOW-008 — spec/70-flows/15-divergence.md*
- Given a recipe edited on device B while device A's edit was sent to the server, when device B sends it, then the server's version keeps the recipe's identity and device B's version becomes a `private` fork naming it as parent.
- Given the server's version has since been published or otherwise frozen, when device B sends a divergent version, then the same rule applies and nothing is written to the frozen recipe.

*CON-003 — spec/30-constraints.md*
- Given a recipe that has become accessible to anyone but its owner, when any edit is attempted through any interface, then the edit is rejected and the stored content is unchanged.

*DOM-004 — spec/50-domain-model/10-invariants.md*
- Given a forked recipe, when its parent is resolved, then it is the recipe it was forked from, at every point in its lifetime.
- Given any recipe, when its stored fields are enumerated, then none of them lists descendants or names a chain root.

**Gate 1:** accepted — docs/product/verdicts/20260930-T-041-gate1.md; waits on T-040. For the packet: until T-042 is on `develop`, a same-content sending from an older revision is refused and stores nothing, never forked; `pending` is tested as frozen under FLOW-008 (4); CON-003 (1) holds against a non-divergent sending to a frozen recipe; re-sliced against spec/ @ e70b62c after SD-042–SD-045 resolved (FLOW-008 (2, 4) recopied from 15-divergence.md, same words; divergence widened to a revision above the server's current one, as the T-040 gate-1 verdict found); re-gate due; re-gated: accepted — docs/product/verdicts/20261001-T-041-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-042 — A sending the server already holds changes nothing, and a repeated divergent sending makes one fork

**Requirements:** FLOW-008, FLOW-066
**Status:** gated
**Depends on:** T-041
**Expected files:** `server/src/main/kotlin/app/recipefork/server/recipe/**` · `server/src/main/resources/db/migration/V5__sending_digest.sql` · `core/src/main/kotlin/app/recipefork/core/api/RecipeContract.kt` · `server/src/test/kotlin/app/recipefork/server/recipe/**`
**Binding ADRs:** decisions/20260928-manual-sending-replaces-the-outbox.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-test-strategy.md
**Note:** Re-sliced out of T-027 on 2026-09-30 (see the withdrawal note after T-042). The branch `task/T-027` (727bd60) holds a working implementation of all three tasks and may be read as a reference; what lands here is written and tested for this entry's criteria alone, and a test of sameness or divergence varies every field of the content, not the title only.
**Note:** Sameness is decided over the whole content on a canonical form (the amended sending ADR). Recipes carry no media before wave 5, so here sameness is over the content document; wave 5 extends it to media. The server checks in a fixed order, as `spec/decisions/20261001-a-fork-already-made-is-answered-first.md` decides:
1. Whether the same sending (the same content, made from the same revision) already made a fork. If it did, the answer is that fork, whatever the server's content has become since.
2. Only otherwise, whether the content is the same as the server's.

FLOW-066 (4) is therefore tested with the server's content changed, after the fork, to the same content as the repeated sending. A test that leaves the server's content untouched cannot tell the two orders apart.

The server half of FLOW-066 (1–5) is carried here and observed at the service: what is written, what forks exist, and what the answer carries, which is the state the server holds and, for (4), the fork already made. T-038 carries FLOW-066 (1–4) on the device. A test of FLOW-066 (5) differs from the server's content by one character in each kind of field in turn (the title, the description, an ingredient and a step), not in the title alone.
**Criteria of these requirements carried elsewhere:** FLOW-008 (1, 3, 6) → T-038; (2, 4) → T-041; (5) → T-040 · FLOW-066 (1–4) → T-038 as well, on the device

**Acceptance criteria** (verbatim)

*FLOW-008 — spec/70-flows/15-divergence.md*
- Given a sending whose content is the same as the content the server holds, when it arrives, then it is not a divergence whatever version it was made from, and FLOW-066 governs it — including a repeat of a sending that already made a fork, which FLOW-066 (4) answers with that fork.

*FLOW-066 — spec/70-flows/15-divergence.md*
- Given a sending the server stored whose answer never reached the device, when the owner sends the recipe again, then no fork is created, no notice of FLOW-008 (3) is shown, and the recipe is marked as sent.
- Given device A's edit sent to the server, when device B sends an edit that leaves the recipe exactly as device A left it and that has not already made a fork, then the server's copy is unchanged, no fork is created and device B marks the recipe as sent.
- Given a recipe that, on the server, was submitted and became `pending` since the device's copy was made, when the device sends content the same as the server's, then no fork is created and the device shows the recipe in `pending`.
- Given a divergent sending that made a fork (FLOW-008) whose answer never reached the device, when the same sending — the same content, made from the same revision — arrives again, then, whatever the server's content has become since, the server answers with the fork it already created and creates no second one, and the owner is told of that fork once, as FLOW-008 (3) states.
- Given a sending whose content differs from the server's in one character or one media item, when it arrives, then it is not governed here: FLOW-059 (4) or FLOW-008 applies.

**Gate 1:** blocked-by-spec (SD-044, whether a repeated divergent sending whose content the server now holds is answered with its fork, FLOW-066 (4), or as sent, FLOW-066 (2)) — docs/product/verdicts/20260930-T-042-gate1.md; re-sliced against spec/ @ e70b62c after SD-042–SD-045 resolved (FLOW-008 (7), FLOW-066 (2) and (4) recopied as changed, from 15-divergence.md; the fork already made is checked first, and tested against a server content that became the same since); re-gate due; re-gated: accepted — docs/product/verdicts/20261001-T-042-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-046 — A recipe the server lost is created again, and a sending with no revision is taken as made from the first

**Requirements:** FLOW-059
**Status:** gated
**Depends on:** T-042
**Expected files:** `server/src/main/kotlin/app/recipefork/server/recipe/**` · `server/src/test/kotlin/app/recipefork/server/recipe/**` · `core/src/main/kotlin/app/recipefork/core/api/RecipeContract.kt`
**Binding ADRs:** decisions/20260928-manual-sending-replaces-the-outbox.md, decisions/20260927-server-stack-ktor-postgres.md, decisions/20260927-test-strategy.md
**Note:** Cut on 2026-10-01 from the re-slice against spec/ @ e70b62c, which answered SD-045 and added FLOW-059 (13) and (14). Both are the server's answer to a sending that neither creates a recipe on its first sending nor replaces a copy made from the server's current revision, and T-040 refuses both until this task lands. (14)'s three outcomes need T-040's replacement, T-041's fork and T-042's sameness, which is why this task follows T-042. The server half of both criteria is carried here and observed at the service: whether the recipe exists, under which account and in which state, what forks exist, and what the answer carries. The mark of FLOW-005 that each criterion names is T-038's, on the device. For (13), both routes are seeded as the server's store sees them: the recipe is absent and the device names a revision. The server cannot tell a restore from a deletion, and the test shows the same answer for both. A recipe whose identity another account holds is still answered as one that does not exist (T-040), not created again. For (14), each of the three outcomes is tested, and the test that the server's copy "has not changed since" varies every field of the content, not the title only.
**Criteria of this requirement carried elsewhere:** FLOW-059 (1) → T-026; (2) → T-028; (3) → wave 7; (4) → T-040 at the server, T-028 on the device; (5–6, 11) → T-028; (7–8) → wave 5; (9) → T-038; (10) → wave 4; (12) → T-047; (13–14) → T-038 as well, on the device

**Acceptance criteria** (verbatim)

*FLOW-059 — spec/70-flows/10-offline-and-sync.md*
- Given a recipe the device holds as sent, when the server no longer holds it — its store restored from a copy older than the recipe's sending (QUAL-008), or the recipe deleted on another device of the account before this device learned of it (ST-010 (2)) — and the owner sends it, then the server creates it again in `private` with the same identity, owned by the sending account, as on a first sending, and FLOW-005 marks it as sent.
- Given a recipe whose first sending the server stored and whose answer did not reach the device, so that the device holds no revision for it, when the owner sends it again, edited or not, then the server treats the sending as made from the recipe's first revision: it replaces the server's copy if that copy has not changed since (criterion 4), FLOW-008 governs if another device changed it, and FLOW-066 governs if the content is the same.

**Gate 1:** accepted — docs/product/verdicts/20261001-T-046-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

**T-027 withdrawn (2026-09-30).** T-027 — the server's whole half of sending — was accepted at gate 1 four times as the specification moved and passed review twice, and after the re-slice of 2026-09-29 its reviews found a different defect at each run: the wire shape against the sending ADR and a disclosed `forkId` (second return), a revision the server never issued forked instead of refused (third run), and a FLOW-066 (5) test that could not tell sameness over the whole content from sameness over the title (fourth run). The fourth run was authorised by the user on 2026-09-30 as the last, with the branch abandoned and the task re-sliced if its review found defects again. It did, so its 17 criteria are carried by T-040, T-041 and T-042, which are small enough for a review to cover. Its verdicts stay where they are: docs/product/verdicts/20260927-T-027-gate1.md, -gate1-2.md, -gate1-3.md, 20260928-T-027-gate1.md, -gate1-2.md, 20260929-T-027-gate1.md, 20260927-T-027-gate2.md, 20260929-T-027-gate2.md. The branch `task/T-027` (727bd60) and its worktree are kept as a reference; nothing of it is merged. The ID T-027 is not reused.

### T-028 — The owner sends a recipe, and every recipe says whether it was sent

**Requirements:** FLOW-059, FLOW-005, FLOW-002, ACT-002, AUTH-026, AUTH-006, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-042, T-046, T-036, T-025, T-034 (T-046 added 2026-10-01: until it lands, a sending with no revision for a recipe the server holds is refused, and no user may reach that before it)
**Expected files:** `server/src/main/kotlin/app/recipefork/server/recipe/**` · `server/src/test/kotlin/app/recipefork/server/recipe/**` · `core/src/main/kotlin/app/recipefork/core/api/RecipeContract.kt` · `app/build.gradle.kts` · `scripts/artefact.sh` · `app/src/main/kotlin/app/recipefork/app/MainActivity.kt` · `app/src/main/kotlin/app/recipefork/app/AppContainer.kt` · `app/src/main/kotlin/app/recipefork/app/data/**` · `app/src/main/kotlin/app/recipefork/app/send/**` · `app/src/test/kotlin/app/recipefork/app/send/**` · `app/src/main/kotlin/app/recipefork/app/book/**` · `app/src/test/kotlin/app/recipefork/app/book/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/main/kotlin/app/recipefork/app/editor/**` · `app/src/test/kotlin/app/recipefork/app/editor/**` · `app/src/main/res/values/strings_send.xml` · `app/src/main/res/values-ru/strings_send.xml` · `app/src/main/res/values/strings_library.xml` · `app/src/main/res/values-ru/strings_library.xml` · `app/src/main/res/values/strings_editor.xml` · `app/src/main/res/values-ru/strings_editor.xml`
**Binding ADRs:** decisions/20260928-manual-sending-replaces-the-outbox.md, decisions/20260928-serverless-mode-is-a-build-flag.md, decisions/20260928-one-database-per-recipe-book.md, decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Size:** The first path from the editor to the server's store and back to the mark: the send request, the per-recipe revision and digest on the device, the three marks, and the first `Mode` passed from `MainActivity` (FLOW-059 (11), FLOW-005 (7)). Indivisible because the mark is only observable against a sending that happened. Expected at 600–800 changed lines.
**Note:** 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): the automatic sending this task was cut around no longer exists. The earlier gate-1 rejection (a muted account's recipes left through the outbox) has no object now; a muted account's send control is replaced by AUTH-006's statement (see the note of 2026-09-29). This task is the first to read the serverless `Mode`: `MainActivity` passes it, and `scripts/artefact.sh` gains the serverless APK.
**Note:** 2026-09-29, third gate 1 authorised by the user after the second return: a muted account can be created in wave 1b (T-006 creates one when no verdict is obtained), so the send control's replacement for it is carried here — FLOW-059 (2) and the statement of AUTH-006 it names, its contents (4, 5, 7) and its absence for a full account (6). The statement's retry control (AUTH-006 (2–3, 8)) stays in wave 7 with AUTH-005; AUTH-004 (4), which also names media, stays in wave 6.
**Note:** 2026-09-29: spec/ @ 952398f inserted FLOW-059 (9), ACT-002 (3) and AUTH-026 (4), so the criterion numbers in this entry's map moved; no criterion of this task changed in wording.
**Criteria of these requirements carried elsewhere:** FLOW-059 (1) → T-026; (3) → wave 7; (7–8) → wave 5; (9) → T-038; (10) → wave 4; (12) → T-047; (13–14) → T-046 at the server, T-038 on the device · FLOW-005 (1–2) → T-007; (4) → wave 5; (6) → wave 2; (8) → T-038 · FLOW-002 (1) → T-013; (2) → wave 4; (3–4) → T-008 · ACT-002 (1) → T-036; (3) → T-026 and T-047; (4) → T-040; (5) → wave 8 · AUTH-026 (1–3, 7) → T-036; (3–4) → T-026; (4) → T-047 as well, for a recipe not sent; (5–6) → wave 7 · AUTH-006 (2–3, 8) → wave 7 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-059 — spec/70-flows/10-offline-and-sync.md*
- Given a muted account (AUTH-004), when a recipe is open in the editor, then the send control is replaced by the statement of AUTH-006.
- Given a sent recipe edited on the device, when it is sent again, then the server's copy is replaced by the device's version, unless FLOW-008 finds the two diverged.
- Given a recipe that holds no change the server does not hold, when its entry or its editor is shown, then no send control is offered.
- Given a recipe in `pending`, `link_shared`, `published`, `reported_hidden` or `removed`, when it is shown, then no send control is offered, because the server already holds it and it cannot change.
- Given serverless mode (FLOW-061), when any screen is shown, then no send control is present (FLOW-062).

*FLOW-005 — spec/70-flows/10-offline-and-sync.md*
- Given a recipe open in the editor, when it holds changes not sent, then the editor states so and offers the send control of FLOW-059.
- Given a recipe whose every change is on the server, when its entry or the editor is displayed, then it is marked as sent and not merely left unmarked.
- Given serverless mode (FLOW-061), when the own-recipes view or the editor is shown, then none of the three marks is shown, because nothing can be sent.

*FLOW-002 — spec/70-flows/10-offline-and-sync.md*
- Given changes made on the device, when a connection returns, then nothing is sent to the server without the owner sending the recipe.
- Given a recipe in `private` edited on the device, when the server's copy is inspected, then it is unchanged until the owner sends the recipe, and FLOW-005 marks the recipe as holding changes not sent.

*ACT-002 — spec/40-actors.md*
- Given a recipe created in the pool, when the pool is moved into account A, then A is its owner from then on, and it can be sent only by A.

*AUTH-026 — spec/45-auth/25-accounts-on-a-device.md*
- Given recipes in the pool that were moved into an account, when they are sent (FLOW-059), then that account is their owner (ACT-002).

*AUTH-006 — spec/45-auth/10-creating-an-account.md*
- Given a muted account, when a withheld control would have been shown, then a statement takes its place rather than the control failing silently.
- Given that statement, when it is read, then it says the device could not be checked and that a connection and an installation from the store lift the limit.
- Given a muted account on a device with no free slot, when the statement is shown, then it names the month a slot frees instead (AUTH-003).
- Given a full account, when any screen is shown, then no such statement appears anywhere.
- Given a muted account, when the statement is shown in the editor or in the own-recipes view, then it also says that its recipes cannot be sent while it is muted, and are held on this device only and lost with it (FLOW-005).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-017) — docs/product/verdicts/20260927-T-028-gate1.md; re-sliced against spec/ @ ead1a44 after SD-017 resolved; re-gated: rejected — docs/product/verdicts/20260927-T-028-gate1-2.md (the outbox would send a muted account's recipes; AUTH-004 carried by no wave-1 task); 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): rewritten for manual sending; re-gate due; re-gated: rejected — docs/product/verdicts/20260928-T-028-gate1.md (a muted account can exist in wave 1b, and the entry's send control has no rule for it: FLOW-059 (2), AUTH-004 (4) and AUTH-006 left in wave 6); second return, taken to the user; FLOW-059 (2) and AUTH-006 (1, 4–7) added; third gate 1 authorised by the user on 2026-09-29; re-gated: accepted — docs/product/verdicts/20260929-T-028-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 2 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-038 — A divergent sending tells the owner a fork was made, and a changed server copy never overwrites the device's

**Requirements:** FLOW-008, FLOW-059, FLOW-066, FLOW-005, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-028
**Expected files:** `app/src/main/kotlin/app/recipefork/app/send/**` · `app/src/test/kotlin/app/recipefork/app/send/**` · `app/src/main/kotlin/app/recipefork/app/book/**` · `app/src/test/kotlin/app/recipefork/app/book/**` · `app/src/main/kotlin/app/recipefork/app/library/**` · `app/src/test/kotlin/app/recipefork/app/library/**` · `app/src/main/res/values/strings_send.xml` · `app/src/main/res/values-ru/strings_send.xml`
**Binding ADRs:** decisions/20260928-manual-sending-replaces-the-outbox.md, decisions/20260927-android-client-stack.md, decisions/20260927-test-strategy.md
**Size:** about 400–550 changed lines. The criteria added on 2026-10-01 are further ways of taking the server's answer to a sending, and they reuse the marks of T-028 and this entry's handling of answers. They add no screen.
**Note:** The server's half of FLOW-008 is T-041's and T-042's: which version keeps the identity, and the answer that says a fork was made, a change of state alone included. This task is what the device does with that answer and with a server copy that changed underneath it.
**Note:** 20260929 re-slice against spec/ @ 952398f (SD-039, SD-041 answered): FLOW-008 (6) now tells the owner as criterion 3 states. If a sending's answer is lost and the recipe is sent again, the server's answer that it already holds that content is taken as sent, with the state the server holds and no notice (FLOW-066 (1–3)). An answer naming a fork already made tells the owner of it once (FLOW-066 (4)). The server half of FLOW-066 is T-042's.
**Note:** 20261001 re-slice against spec/ @ e70b62c (SD-043 answered):
- **Lost or refused answer.** A sending that was refused, or whose answer was lost, leaves the recipe with the mark it carried before that sending (FLOW-059 (9), FLOW-005 (8)). After a first sending that is "never sent". After a later one it is "holding changes not sent". Both are tested, each with the server having stored the sending and with it not having stored it.
- **No revision held.** After a lost first answer the device holds no revision for the recipe, and its next sending names none. The server's answer to that sending is T-046's at the server. Here the device sends it and takes each of the three answers: sent, a fork, or already held (FLOW-059 (14)).
- **Recipe the server no longer holds.** The server creates it again (T-046), and the device marks it as sent (FLOW-059 (13)).
- **Statement wording.** FLOW-066 (4) changed in wording only. The device half is unchanged: an answer naming a fork already made tells the owner once.
**Criteria of these requirements carried elsewhere:** FLOW-008 (2, 4) → T-041; (5) → T-040; (7) → T-042 · FLOW-059 (1) → T-026; (2) → T-028; (3) → wave 7; (4) → T-040 at the server, T-028 on the device; (5–6, 11) → T-028; (7–8) → wave 5; (10) → wave 4; (12) → T-047; (13–14) → T-046 as well, at the server · FLOW-066 (1–4) → T-042 as well, at the server; (5) → T-042 · FLOW-005 (1–2) → T-007; (3, 5, 7) → T-028; (4) → wave 5; (6) → wave 2 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-008 — spec/70-flows/15-divergence.md*
- Given a device holding changes not sent to a recipe, when the server's copy of that recipe changes, then the device keeps its own version and does not take the server's over it; the difference is settled when the device sends.
- Given that outcome, when the server's answer reaches device B, then the owner is told a fork was created and why at that moment if the application is open, or when they next open it if it is not; the notice stays until they acknowledge it, is not shown again afterwards, and both recipes are reachable.
- Given a recipe edited on device B while, on the server, nobody changed its content but it was submitted and became `pending`, `link_shared` or `published`, when device B sends it, then device B's version becomes a `private` fork naming it as parent, and the owner is told as criterion 3 states.

*FLOW-059 — spec/70-flows/10-offline-and-sync.md*
- Given a sending the server stored whose answer did not reach the device, when the recipe is shown, then FLOW-005 marks it exactly as it marked it before that sending — never sent after a first sending, holding changes not sent after a later one — and sending it again is answered by FLOW-066.
- Given a recipe the device holds as sent, when the server no longer holds it — its store restored from a copy older than the recipe's sending (QUAL-008), or the recipe deleted on another device of the account before this device learned of it (ST-010 (2)) — and the owner sends it, then the server creates it again in `private` with the same identity, owned by the sending account, as on a first sending, and FLOW-005 marks it as sent.
- Given a recipe whose first sending the server stored and whose answer did not reach the device, so that the device holds no revision for it, when the owner sends it again, edited or not, then the server treats the sending as made from the recipe's first revision: it replaces the server's copy if that copy has not changed since (criterion 4), FLOW-008 governs if another device changed it, and FLOW-066 governs if the content is the same.

*FLOW-005 — spec/70-flows/10-offline-and-sync.md*
- Given a sending that was refused or whose answer did not reach the device, when the recipe is shown afterwards, then it carries the mark it carried before that sending, whether or not the server stored it, because the device cannot tell the two apart (FLOW-059 (9)).

*FLOW-066 — spec/70-flows/15-divergence.md*
- Given a sending the server stored whose answer never reached the device, when the owner sends the recipe again, then no fork is created, no notice of FLOW-008 (3) is shown, and the recipe is marked as sent.
- Given device A's edit sent to the server, when device B sends an edit that leaves the recipe exactly as device A left it and that has not already made a fork, then the server's copy is unchanged, no fork is created and device B marks the recipe as sent.
- Given a recipe that, on the server, was submitted and became `pending` since the device's copy was made, when the device sends content the same as the server's, then no fork is created and the device shows the recipe in `pending`.
- Given a divergent sending that made a fork (FLOW-008) whose answer never reached the device, when the same sending — the same content, made from the same revision — arrives again, then, whatever the server's content has become since, the server answers with the fork it already created and creates no second one, and the owner is told of that fork once, as FLOW-008 (3) states.

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** blocked-by-spec (SD-039, FLOW-008 (6) points to "criterion 2", which since 0745e70 states no notice) — docs/product/verdicts/20260928-T-038-gate1.md; re-sliced against spec/ @ 952398f after SD-039 and SD-041 resolved (FLOW-008 (6) replaced; FLOW-059 (9) and FLOW-066 (1–4) added as the device half); re-gate due; re-gated: blocked-by-spec (SD-043, which mark a recipe shows when its first sending was stored and the answer lost, FLOW-059 (9)) — docs/product/verdicts/20260929-T-038-gate1.md; re-sliced against spec/ @ e70b62c after SD-042–SD-045 resolved (FLOW-059 (9), FLOW-066 (2, 4) recopied as changed; FLOW-005 (8) and the device halves of FLOW-059 (13–14) added; FLOW-008 and FLOW-066 recopied from 15-divergence.md); re-gate due; re-gated: accepted — docs/product/verdicts/20261001-T-038-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-026 — Sending with no account offers to create one

**Requirements:** FLOW-059, ACT-001, ACT-002, AUTH-001, AUTH-026, FLOW-003, AUTH-004, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-038
**Expected files:** `app/src/main/kotlin/app/recipefork/app/account/**` · `app/src/test/kotlin/app/recipefork/app/account/**` · `app/src/main/kotlin/app/recipefork/app/send/**` · `app/src/test/kotlin/app/recipefork/app/send/**` · `app/src/main/kotlin/app/recipefork/app/book/**` · `app/src/test/kotlin/app/recipefork/app/book/**` · `app/src/main/kotlin/app/recipefork/app/ui/nav/**` · `app/src/main/res/values/strings_account.xml` · `app/src/main/res/values-ru/strings_account.xml` · `app/src/main/res/values/strings_send.xml` · `app/src/main/res/values-ru/strings_send.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260928-sign-in-secrets-lapse-and-retired-keys-remove-the-account.md, decisions/20260927-test-strategy.md
**Note:** 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): writing needs no account any more (FLOW-002); the control that needs one is the send control of T-028, which is this task's trigger. The earlier gate-1 rejection — no control in wave 1 fired the offer — is answered by it.
**Note:** 20260929 re-slice against spec/ @ 952398f (SD-040 answered): the pool recipe whose sending created the account is that account's and is sent whatever the answer to the pool offer T-036 builds; the offer then says so and covers the rest of the pool (ACT-002 (3), AUTH-026 (3–4)). T-036 carries AUTH-026 (3) for the rest of the pool.
**Note:** 20261001 re-slice against spec/ @ e70b62c (SD-042 answered):
- **What decides ownership.** The account's creation makes the pool recipe the account's, not the sending (FLOW-059 (1), ACT-002 (3), AUTH-026 (4)). This task carries the path where the account is created full and the sending that follows succeeds.
- **What moved to T-047.** The two paths where nothing is sent move to T-047, which depends on this task because their files overlap: an account created muted, and a sending refused after creation (FLOW-059 (12)). With them go AUTH-004 (5), which the 2026-09-29 re-slice had placed here, and the not-sent clauses of ACT-002 (3) and AUTH-026 (4). Those two criteria are carried by both tasks, divided by path.
- **No connection.** Creation offered with no connection creates nothing (AUTH-001 (2), AUTH-004 (2), FLOW-003 (6)). That stays here, because no account exists to receive the recipe.
**Criteria of these requirements carried elsewhere:** FLOW-059 (2) → T-028; (3) → wave 7; (4) → T-040 at the server, T-028 on the device; (5–6, 11) → T-028; (7–8) → wave 5; (9) → T-038; (10) → wave 4; (12) → T-047; (13–14) → T-046 at the server, T-038 on the device · ACT-002 (1) → T-036; (2) → T-028; (3) → T-047 as well, for a recipe not sent; (4) → T-040; (5) → wave 8 · AUTH-026 (1–3, 7) → T-036; (4) → T-047 as well, for a recipe not sent; (5–6) → wave 7; (8) → T-028 · ACT-001 (2) → wave 3; (4) → wave 2; (5) → wave 8 · AUTH-001 (1) → wave 3; (3, 5) → T-006; (4) → wave 7 · FLOW-003 (1–4, 7–11) → wave 2; (5) → wave 7; (12) → wave 9 · AUTH-004 (1) → wave 3; (3) → wave 4; (4, 7) → wave 6; (5) → T-047, and wave 6 for rating; (6) → T-006 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-059 — spec/70-flows/10-offline-and-sync.md*
- Given a recipe written with no account active, when the person chooses to send it, then account creation is offered (AUTH-001), the recipe becomes that account's the moment the account exists, whether the offer of AUTH-026 that follows is accepted or declined (ACT-002), and it is then sent if the account can send; criterion 12 states what happens when it cannot.

*ACT-002 — spec/40-actors.md*
- Given a recipe in the pool whose sending, with no account active, creates an account, when that account exists, then that account is the recipe's owner whether the offer of AUTH-026 is accepted or declined and whether or not the recipe was then sent — the account muted (AUTH-004) or the sending refused (FLOW-003) — and the rest of the pool stays without an owner if the offer is declined.

*AUTH-026 — spec/45-auth/25-accounts-on-a-device.md*
- Given the offer declined, when the account's recipe book is shown, then nothing from the pool is in it but a recipe whose sending created the account (FLOW-059 (1)), and the pool is shown again whenever no account is active.
- Given an account created by sending a recipe from the pool, when the offer is shown, then it states that this recipe is already in the account whatever the answer, and the offer covers the rest of the pool; this holds whether or not the recipe was sent (FLOW-059 (12)).

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

**Gate 1:** blocked-by-spec (SD-019) — docs/product/verdicts/20260927-T-026-gate1.md; re-sliced against spec/ @ ead1a44 after SD-019 resolved; re-gated: rejected — docs/product/verdicts/20260927-T-026-gate1-2.md (no control in wave 1 fires the offer for a person with no account); 20260928 re-slice against spec/ @ 21b80b2 (the device first, 0745e70): rewritten: the send control is the trigger; re-gate due; re-gated: blocked-by-spec (SD-040, a pool recipe sent with no account against a declined pool offer) — docs/product/verdicts/20260928-T-026-gate1.md; noted for the re-slice once SD-040 is answered: a muted account can be created from the send control, so AUTH-004 (5) and the statement of AUTH-006 it names belong here, not in wave 6; re-sliced against spec/ @ 952398f after SD-040 resolved (FLOW-059 (1) replaced; ACT-002 (3), AUTH-026 (3–4) and AUTH-004 (5) added); re-gate due; re-gated: blocked-by-spec (SD-042, whether the pool recipe whose sending caused the account becomes its when the account is created muted or the sending is refused) — docs/product/verdicts/20260929-T-026-gate1.md; re-sliced against spec/ @ e70b62c after SD-042–SD-045 resolved (FLOW-059 (1), ACT-002 (3), AUTH-026 (4) recopied as changed; the muted and refused paths, FLOW-059 (12) and AUTH-004 (5), moved to T-047); re-gate due; re-gated: accepted — docs/product/verdicts/20261001-T-026-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 1 of 2 (gate 1 · reviewer · gate 2 share the count)

### T-047 — A pool recipe whose account was created muted, or whose sending was refused, waits in that account's book

**Requirements:** FLOW-059, ACT-002, AUTH-026, AUTH-004, L10N-001, A11Y-003, A11Y-005
**Status:** gated
**Depends on:** T-026
**Expected files:** `app/src/main/kotlin/app/recipefork/app/account/**` · `app/src/test/kotlin/app/recipefork/app/account/**` · `app/src/main/kotlin/app/recipefork/app/send/**` · `app/src/test/kotlin/app/recipefork/app/send/**` · `app/src/main/kotlin/app/recipefork/app/book/**` · `app/src/test/kotlin/app/recipefork/app/book/**` · `app/src/main/res/values/strings_account.xml` · `app/src/main/res/values-ru/strings_account.xml` · `app/src/main/res/values/strings_send.xml` · `app/src/main/res/values-ru/strings_send.xml`
**Binding ADRs:** decisions/20260927-android-client-stack.md, decisions/20260928-one-database-per-recipe-book.md, decisions/20260927-test-strategy.md
**Note:** Cut on 2026-10-01 out of T-026 in the re-slice against spec/ @ e70b62c, which answered SD-042.
- **What this task carries.** The two paths from the send control where the account is created and nothing is sent: the account is created muted (T-006 creates one when no verdict is obtained), or the server refuses the sending that follows creation. On both, the recipe is the account's and sits in its recipe book, marked never sent (T-028's mark), and nothing of it is on the server.
- **Refused path.** The send control is offered on the recipe again at once.
- **Muted path.** The statement of AUTH-006 that T-028 builds takes the control's place. AUTH-004 (5) is observed for sending only; its half for rating stays in wave 6.
- **Muted account that becomes full.** FLOW-059 (12)'s clause "once the account is full when it is muted" needs AUTH-005 (5), which is wave 7's, so it is carried there. Here the test observes that a muted account is not offered the control.
- **Shared criteria.** ACT-002 (3) and AUTH-026 (4) are carried by T-026 for the path that sends, and here for the two that do not.
**Criteria of these requirements carried elsewhere:** FLOW-059 (1) → T-026; (2) → T-028; (3) → wave 7; (4) → T-040 at the server, T-028 on the device; (5–6, 11) → T-028; (7–8) → wave 5; (9) → T-038; (10) → wave 4; (12) → wave 7 as well, once a muted account becomes full (AUTH-005 (5)); (13–14) → T-046 at the server, T-038 on the device · ACT-002 (1) → T-036; (2) → T-028; (3) → T-026 as well, for a recipe sent; (4) → T-040; (5) → wave 8 · AUTH-026 (1–3, 7) → T-036; (3) → T-026; (4) → T-026 as well, for a recipe sent; (5–6) → wave 7; (8) → T-028 · AUTH-004 (1) → wave 3; (2) → T-026; (3) → wave 4; (4, 7) → wave 6; (5) → wave 6 as well, for rating; (6) → T-006 · L10N-001 (3) → release record · A11Y-003 (2) → T-018; (3–4) → wave 8 · A11Y-005 (2–6) → wave 8

**Acceptance criteria** (verbatim)

*FLOW-059 — spec/70-flows/10-offline-and-sync.md*
- Given a recipe from the pool whose sending created an account, when that account is muted (AUTH-004) or the sending that follows creation is refused (FLOW-003), then the recipe is in that account's recipe book, marked never sent, nothing of it reached the server, and the send control is offered on it again once the account can send — at once after a refusal, once the account is full when it is muted (AUTH-005 (5)).

*ACT-002 — spec/40-actors.md*
- Given a recipe in the pool whose sending, with no account active, creates an account, when that account exists, then that account is the recipe's owner whether the offer of AUTH-026 is accepted or declined and whether or not the recipe was then sent — the account muted (AUTH-004) or the sending refused (FLOW-003) — and the rest of the pool stays without an owner if the offer is declined.

*AUTH-026 — spec/45-auth/25-accounts-on-a-device.md*
- Given an account created by sending a recipe from the pool, when the offer is shown, then it states that this recipe is already in the account whatever the answer, and the offer covers the rest of the pool; this holds whether or not the recipe was sent (FLOW-059 (12)).

*AUTH-004 — spec/45-auth/10-creating-an-account.md*
- Given a muted account, when it was created because the person asked to send a recipe or to rate, then AUTH-006 states at that moment that the action cannot proceed and why; a recipe from the pool whose sending created it is in its recipe book, marked never sent (FLOW-059 (12), ACT-002).

*L10N-001 — spec/160-localization.md*
- Given any screen, statement, warning or error in this specification, when it is shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed in the code in one language only.

*A11Y-003 — spec/200-accessibility.md*
- Given any screen, when each interactive element is measured, then its touch target is at least 48 by 48 dp, whatever the element looks like.

*A11Y-005 — spec/200-accessibility.md*
- Given any interactive element, when the screen reader reaches it, then it announces what the element does, not what it looks like, and an element with no visible label carries one anyway.

**Gate 1:** accepted — docs/product/verdicts/20261001-T-047-gate1.md
**Review:** —
**Gate 2:** —
**Returns:** 0 of 2 (gate 1 · reviewer · gate 2 share the count)

## Coverage beyond waves 1 and 1b

Every criterion of every first-release requirement that no task of waves 1 and 1b carries, placed in the wave that will carry
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
| DOM-017 — An authored quantity is a value of its unit type's grid | 6–7 |
| DOM-018 — A quantity published before a divisibility correction stays as written | 3 |
| EXT-009 — The product sends nothing to a device | 3 |
| FLOW-003 — What is refused while the server has not answered | 1–4, 7–11 |
| FLOW-005 — Every recipe shows whether it was sent and what it holds unsent | 6 |
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
| FLOW-053 — Deleting a recipe is offered in one place and confirmed against what it costs | 1–6, 8 |
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
| ST-010 — An owner deletes a recipe that was never externally visible | all (the server's re-creation of a recipe deleted on another device, FLOW-059 (13), first carried by T-046) |

### Wave 3 — Reading other people's recipes: the catalogue card, favourites, settings and ratings

| Requirement | Criteria |
|---|---|
| AUTH-026 — What is made with no account is a pool, offered once to each account | 2 (again, with favourites in the pool; first carried by T-036) |
| ACT-001 — Most of the product needs no account | 2 |
| ANL-005 — A published recipe counts its opens and its favourites | all |
| ANL-006 — Those two numbers belong to the recipe's owner alone | 1, 3–4 |
| ANL-007 — Every number says what it counts | 1–3, 5 |
| AUTH-001 — An account appears when an action needs one | 1 |
| AUTH-004 — Without a verdict the account is created muted | 1 |
| DOM-018 — A quantity published before a divisibility correction stays as written | 1–2 |
| FLOW-001 — Both dictionaries are held on the device | 5 |
| FLOW-009 — Every favourite is readable without a network connection | all |
| FLOW-010 — A favourite that becomes unavailable stays in the list | all |
| FLOW-011 — One setting governs whether media is held | 1–5 |
| FLOW-012 — Leaving the application does not lose the recipe being read | 4 |
| FLOW-017 — The reader's usual serving count opens every recipe | 1, 3–7 |
| FLOW-019 — What the card shows when parts of a recipe are absent | 4 |
| FLOW-020 — The card while it loads and when it cannot be loaded | all |
| FLOW-034 — The recipe book holds two views | 2, 4 |
| FLOW-035 — Both views are searched, filtered and ordered exactly as the catalogue is | all |
| FLOW-036 — An own recipe carries its state on its entry, not in a screen of its own | 7 |
| FLOW-037 — The own-recipes view narrows to a state | all |
| FLOW-038 — Every recipe the account owns is readable without a connection | 7 |
| FLOW-039 — What `newest` means for a recipe that was never published | 3–4 |
| FLOW-040 — An own recipe is not marked as a favourite | all |
| FLOW-042 — An empty listing names what is empty and offers the way out of it | 5 |
| FLOW-043 — A listing loading for the first time, and a listing that failed to load | 6 |
| FLOW-051 — What is kept with no account lives on the device and nowhere else | 1–5 |
| FLOW-052 — An account carries favourites beyond the device | all |
| FLOW-058 — A session ends only when the reader closes the application | 5 |
| L10N-002 — The interface language starts from the device and the reader may change it | all |
| LEG-004 — The terms are accepted before anything leaves the device, and they carry a version | 2 |
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
| ANL-006 — Those two numbers belong to the recipe's owner alone | 2 |
| ANL-007 — Every number says what it counts | 4 |
| ANL-008 — A published recipe counts the forks others make of it | 1–9 |
| AUTH-004 — Without a verdict the account is created muted | 3 |
| CAT-004 — A fork is visible as a fork before it is opened | all |
| CAT-005 — The number of direct forks is stated | all |
| CAT-008 — The fork number opens the recipes forked from it | all |
| CON-003 — Immutability after first external visibility | 2 |
| DOM-017 — An authored quantity is a value of its unit type's grid | 9 |
| FLOW-002 — A recipe is written on the device, with no account and no connection | 2 |
| FLOW-007 — A fork may be sent before its parent, and names it once the parent is published | all |
| FLOW-015 — Forking twice is offered as the fork already held | all |
| FLOW-024 — The editor is one screen in a fixed order | 3, 5 |
| FLOW-059 — A recipe reaches the server only when its owner sends it | 10 |
| FORK-001 — What a fork inherits | all |
| FORK-002 — A fork by another account carries no media and no description | all |
| FORK-003 — The parent's media stays visible in the parent | 1–2 |
| FORK-004 — Only the direct parent is shown | all |
| FORK-005 — A parent that was never published is not mentioned at all | 1–5, 7 |
| FORK-006 — No fork carries what readers gave the parent | all |
| FORK-007 — A fork of one's own recipe carries everything else | all |
| FORK-008 — An owner may mark a fork of their own recipe as replacing it | all |
| FORK-009 — A replaced recipe says so and leads to the replacement | all |
| L10N-004 — A recipe declares the language it is written in | 4 |
| ST-004 — Forking is allowed from published recipes and from one's own | all |

### Wave 5 — Media

| Requirement | Criteria |
|---|---|
| DOM-005 — Media is owned by an account from upload | 3 |
| EXT-003 — Object storage is used as a plain store | all |
| EXT-004 — Storage that cannot be reached costs media, not recipes | all |
| FLOW-004 — Media stays on the device until its recipe is sent | all |
| FLOW-005 — Every recipe shows whether it was sent and what it holds unsent | 4 |
| FLOW-011 — One setting governs whether media is held | 6 |
| FLOW-024 — The editor is one screen in a fixed order | 1 |
| FLOW-026 — The cover is attached with the title, step media with the step | all |
| FLOW-038 — Every recipe the account owns is readable without a connection | 3–4 |
| FLOW-059 — A recipe reaches the server only when its owner sends it | 7–8 |
| FLOW-008 — Two devices that diverge produce a fork, never an overwrite | 2, 7 (again, with a sending that differs in one media item; first carried by T-041 and T-042 over the content document) |
| FLOW-066 — A sending the server already holds changes nothing | 1–5 (again, with sameness over every media item; first carried by T-042 at the server and T-038 on the device) |
| FLOW-060 — Media the device can fetch again is deleted on request | 1–3 |
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
| AUTH-004 — Without a verdict the account is created muted | 4, 5 (for rating; its half for sending is T-047's), 7 |
| AUTH-006 — A muted account is told what it is and what lifts it | 1, 4, 6–7 (again, for the controls of this wave; first carried by T-028 for the send control) |
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

The list of accounts on a device and the switch between them (AUTH-011 (1–5), AUTH-012 (3), AUTH-026 (5–7) as spec/ @ 952398f numbers them) were sliced into T-037 for wave 1b and withdrawn after its gate-1 rejection of 2026-09-28 (docs/product/verdicts/20260928-T-037-gate1.md): a second account and a return to the pool are reached only through signing in and AUTH-011 (5), both in this wave. The ID T-037 is kept for the task this wave cuts, with its return count of 1 of 2.

| Requirement | Criteria |
|---|---|
| AUTH-026 — What is made with no account is a pool, offered once to each account | 1 (again, for an account that signs in; first carried by T-036), 5–6, 7 (again, for the menu action; first carried by T-036) |
| AUTH-001 — An account appears when an action needs one | 4 |
| AUTH-003 — A device with no free slot says when it will have one | all |
| AUTH-005 — A muted account becomes full when a verdict and a slot allow it | all |
| AUTH-006 — A muted account is told what it is and what lifts it | 2–3, 8 |
| AUTH-007 — The key is issued once, in full, and creation waits for an acknowledgement | 8 |
| AUTH-008 — The product neither stores the key nor recovers an account | all |
| AUTH-009 — Signing in with a key costs no slot | all |
| AUTH-010 — Signing in with a new key requires a connection | all |
| AUTH-011 — A device holds every account signed in on it, each apart | 1–5 |
| AUTH-012 — Switching the active account deletes nothing | all |
| AUTH-013 — An account is removed from the device only on request, after being told what is lost | 1–6 |
| AUTH-014 — What the server accepts as proof of ownership | 5–6 |
| AUTH-015 — Nothing from the device verdict is kept | 3 |
| AUTH-016 — Losing the key keeps the published work in the world | all |
| AUTH-017 — Every account carries a deletion key | 5, 8–9 |
| AUTH-018 — The nickname is generated and may be rerolled any number of times before the account exists | 4 |
| AUTH-019 — Afterwards the key is shown masked and copied whole | all |
| AUTH-020 — The recovery key is issued apart from the account key and shown once | 3–4 |
| AUTH-021 — Using the recovery key retires every key the account had | all |
| AUTH-025 — A sign-in leaves a record that lapses after six months unused | 2, 4–9 |
| AUTH-027 — A key already used on this device opens its account without the server | 1–2, 4 |
| DOM-001 — A nickname is unique and permanent | 2 |
| DOM-005 — Media is owned by an account from upload | 1–2 |
| EXT-002 — The attestation service may be absent | all |
| EXT-006 — The account deletion page takes a deletion key, never the account key | all |
| EXT-008 — The account key is handed over and never managed | all |
| FLOW-003 — What is refused while the server has not answered | 5 |
| FLOW-059 — A recipe reaches the server only when its owner sends it | 3, 12 (again, for a muted account that becomes full, AUTH-005 (5); first carried by T-047) |
| LEG-004 — The terms are accepted before anything leaves the device, and they carry a version | 3 |
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
| ACT-002 — Ownership is fixed at creation, or when a pool recipe first gets an account | 5 |
| ANL-001 — Nothing is measured about a person | all |
| ANL-002 — The product can be described from what it already holds | all |
| ANL-003 — Reach is taken from the distribution channel, not measured here | all |
| ANL-004 — Application starts are counted, and nothing else about them | all |
| ANL-008 — A published recipe counts the forks others make of it | 10 |
| AUTH-023 — The nickname words come from lists the product owner keeps by hand | 2 |
| CON-005 — Native Android is the only client surface in the MVP | 1 |
| DOM-011 — A comparison factor orders and nothing else | 4 |
| DOM-014 — Both dictionaries are maintained outside the application | 1, 4–5 |
| DOM-016 — The units are a closed list of twelve | 4 |
| EXT-001 — The list of external dependencies is closed | all |
| EXT-009 — The product sends nothing to a device | 2 |
| EXT-010 — Nothing receives content or identifiers | 1–5 |
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

### Wave 9 — Serverless mode and recipe files

The mode is built with the first release and switched on by a later one (MVP-001 (6)). Each earlier task that adds a control
serverless mode removes carries that control's own serverless criterion where the specification states one (FLOW-059 (11),
FLOW-005 (7)); this wave carries the mode itself, the sweep of FLOW-062 (1), and recipe files.

| Requirement | Criteria |
|---|---|
| ANL-008 — A published recipe counts the forks others make of it | 11 |
| AUTH-011 — A device holds every account signed in on it, each apart | 6 |
| AUTH-013 — An account is removed from the device only on request, after being told what is lost | 7 |
| AUTH-027 — A key already used on this device opens its account without the server | 3 |
| CON-003 — Immutability after first external visibility | 3 |
| EXT-010 — Nothing receives content or identifiers | 6 |
| FLOW-003 — What is refused while the server has not answered | 12 |
| FLOW-060 — Media the device can fetch again is deleted on request | 4 |
| FLOW-061 — Serverless mode is switched on by a release, never by the application | all |
| FLOW-062 — What serverless mode takes away and what it keeps | all |
| FLOW-063 — A recipe is exported to a file | all |
| FLOW-064 — A file is imported as a found recipe | all |
| FLOW-065 — A fork of an imported recipe names it as its parent | all |
| FORK-003 — The parent's media stays visible in the parent | 3 |
| FORK-005 — A parent that was never published is not mentioned at all | 6 |

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
