# Architecture index

Decisions the team lead owns: how the product in `spec/` is built. What it does lives in `spec/` alone.

## Stack and runtime

| Part | Choice |
|---|---|
| Language | Kotlin 2.4 on JDK 21 (toolchain); Android bytecode targets Java 17 |
| Build | Gradle 9.8 (wrapper), Kotlin DSL, version catalog `gradle/libs.versions.toml` |
| Client | Android, Jetpack Compose + Material 3, AGP 9.4; `minSdk` 29, `compileSdk` 37.2, `targetSdk` 37 |
| Client storage and work | Room, WorkManager, Media3 Transformer, Play Integrity (added by the tasks that need them) |
| Server | Ktor 3.6 (Netty), kotlinx.serialization, JSON under `/api/v1` |
| Store | PostgreSQL 16, Flyway SQL migrations, JDBC via HikariCP, no ORM |
| Media | Cloudflare R2 over the S3 API (put, get, delete), reached only by the server |
| Deployment | One Hetzner Cloud VM, Docker Compose: server, PostgreSQL, Caddy; hourly `pg_dump` to R2 |
| Alerting | Telegram Bot API, `sendMessage` only |

## Commands

All commands run from the repository root.

| Purpose | Command | Notes |
|---|---|---|
| Setup (fresh checkout or worktree) | `scripts/setup.sh` | Writes `local.properties` with the Android SDK path. Gradle dependencies live in `~/.gradle`, shared by all worktrees. |
| Build | `./gradlew assembleAll` | `:core` jar, `:server:installDist`, `:app:assembleDebug`. |
| Test (full suite) | `./gradlew testAll` | `:core:test`, `:server:test`, `:app:testDebugUnitTest`. Needs Docker (Testcontainers); needs no device. |
| Dictionary snapshot | `scripts/dictionary-snapshot.sh` | Release step: writes `app/src/main/assets/dictionaries.json` from the production server; the file is committed. Added by the first task that seeds from it. |
| Device tests | `scripts/device-test.sh` | Instrumented tests (`app/src/androidTest`) on a headless API 29 emulator. Only for tasks whose criteria need real codecs or a real device; named in that task's packet. |
| Hand-test artefact | `scripts/artefact.sh` | Lands at `build/artefact/`: `recipe-fork-debug.apk`, `recipe-fork-serverless-debug.apk` (built with `-Precipefork.serverless=true`; the script gains the second build with the first task that reads the mode) and `server/` (run `docker compose up --build` inside it). `SOURCE_COMMIT` names the commit it was built from. |

## Test frameworks

- `:core`, `:server`: JUnit 5 with `kotlin.test`; server tests use a real PostgreSQL via Testcontainers (`postgres:16-alpine`).
- `:app`: JUnit 4, Robolectric (SDK pinned in `app/src/test/resources/robolectric.properties`), Compose UI test.
- `:app` device tests: AndroidX Test runner on the emulator.

## Module map

| Module | Holds | Depends on |
|---|---|---|
| `:core` | Domain model and invariants, state transitions and guards, scaling and rounding, the text analyser, the API contract types. Pure Kotlin/JVM: no Android, no server library. | kotlinx.serialization |
| `:server` | HTTP API, web surfaces (deletion page, terms, privacy, private-link landing, health), persistence, media proxy, signals and alerting, backups. | `:core` |
| `:app` | The Android application: screens, the device database and one database per recipe book, sending, the favourites outbox, media conversion, timers, device verdict, recipe files. | `:core` |
| `deploy/` | Compose definitions: `deploy/local/` for development and the artefact. | — |
| `scripts/` | Setup, artefact and device-test commands. | — |

Packages: `app.recipefork.core`, `app.recipefork.server`, `app.recipefork.app`. Inside each, one package per feature, so that tasks running at the same time touch disjoint files (see the increment files):

| Module | Feature packages |
|---|---|
| `:core` | `recipe` (the model of `spec/50-domain-model`, units, step text, limits), `scaling`, `timer`, `text`, `api` (one contract file per feature), `file` (the recipe file document) |
| `:server` | `db`, `dictionary`, `account`, `attestation`, `auth`, `recipe`, and one per later feature; `Application.kt` installs each feature's routes |
| `:app` | `data` (database, network), `recipe`, `dictionary`, `account`, `library` (the recipe book section), `book` (the book databases, switching, the pool move), `editor`, `card`, `timer`, `send` (sending a recipe), `sync` (the favourites outbox), `file` (recipe files), `ui` (theme, navigation); `AppContainer.kt` is the composition root |

Interface strings: one resource file per feature, `res/values/strings_<feature>.xml` beside `res/values-ru/strings_<feature>.xml`. Server migrations: `server/src/main/resources/db/migration/V<n>__<feature>.sql`, numbered in the order tasks merge.

Increments, their tasks and decision logs: [`docs/increments/`](../increments/). The earlier backlog is archived in [`docs/archive/process-v1/`](../archive/process-v1/).

## Decisions

| ADR | Decides |
|---|---|
| [decisions/20260927-kotlin-monorepo-with-shared-core.md](decisions/20260927-kotlin-monorepo-with-shared-core.md) | One Kotlin monorepo; rules shared by device and server live once, in `:core`. |
| [decisions/20260927-android-client-stack.md](decisions/20260927-android-client-stack.md) | Compose, Room, WorkManager, Media3, Play Integrity, manual DI, `minSdk` 29. |
| [decisions/20260927-server-stack-ktor-postgres.md](decisions/20260927-server-stack-ktor-postgres.md) | Ktor, PostgreSQL, Flyway, plain SQL; MongoDB and ORMs rejected. |
| [decisions/20260927-one-text-analyser.md](decisions/20260927-one-text-analyser.md) | One Snowball-based analyser in `:core`; both ends index its tokens. |
| [decisions/20260927-media-served-through-the-server.md](decisions/20260927-media-served-through-the-server.md) | Media in R2, stored and served only through the server, visibility checked per request. |
| [decisions/20260927-keys-are-hashed-random-secrets.md](decisions/20260927-keys-are-hashed-random-secrets.md) | Superseded by the sign-in secrets ADR of 2026-09-28. |
| [decisions/20260927-server-authoritative-sync-with-outbox.md](decisions/20260927-server-authoritative-sync-with-outbox.md) | Superseded by the manual-sending ADR of 2026-09-28. |
| [decisions/20260927-web-surfaces-from-the-server.md](decisions/20260927-web-surfaces-from-the-server.md) | Pages outside the app are plain HTML from the same server; private links are App Links. |
| [decisions/20260927-single-vm-deployment-and-hourly-dump.md](decisions/20260927-single-vm-deployment-and-hourly-dump.md) | One VM with Compose; hourly `pg_dump` to R2, 48 hourly + 30 daily. |
| [decisions/20260927-telegram-alerting.md](decisions/20260927-telegram-alerting.md) | Alerts via Telegram `sendMessage`, never reading from the bot. |
| [decisions/20260927-test-strategy.md](decisions/20260927-test-strategy.md) | JVM suite for every task; emulator tests only where a device is required. |
| [decisions/20260928-manual-sending-replaces-the-outbox.md](decisions/20260928-manual-sending-replaces-the-outbox.md) | Device-minted UUIDs; one atomic request per sending; revision mismatch becomes a fork; an outbox for favourites and blocks only. |
| [decisions/20260928-one-database-per-recipe-book.md](decisions/20260928-one-database-per-recipe-book.md) | One Room file per recipe book (each account, the pool) plus a device database; switching opens another file. |
| [decisions/20260928-serverless-mode-is-a-build-flag.md](decisions/20260928-serverless-mode-is-a-build-flag.md) | `recipefork.serverless` → `BuildConfig` → a `Mode` passed from `MainActivity`; no HTTP client in serverless. |
| [decisions/20260928-sign-in-secrets-lapse-and-retired-keys-remove-the-account.md](decisions/20260928-sign-in-secrets-lapse-and-retired-keys-remove-the-account.md) | Hashed 256-bit keys; a sign-in secret lapses after six months and is renewed silently; retired keys remove the account from other devices. |
| [decisions/20260928-dictionaries-ship-with-the-application.md](decisions/20260928-dictionaries-ship-with-the-application.md) | A snapshot of both dictionaries in the APK seeds a clean install; the server refreshes it. |
| [decisions/20260928-recipe-file-is-a-zip.md](decisions/20260928-recipe-file-is-a-zip.md) | A recipe file is a ZIP of a versioned `recipe.json` and its media, streamed at import. |
