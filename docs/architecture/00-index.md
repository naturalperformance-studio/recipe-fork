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
| Device tests | `scripts/device-test.sh` | Instrumented tests (`app/src/androidTest`) on a headless API 29 emulator. Only for tasks whose criteria need real codecs or a real device; named in that task's packet. |
| Hand-test artefact | `scripts/artefact.sh` | Lands at `build/artefact/`: `recipe-fork-debug.apk` and `server/` (run `docker compose up --build` inside it). `SOURCE_COMMIT` names the commit it was built from. |

## Test frameworks

- `:core`, `:server`: JUnit 5 with `kotlin.test`; server tests use a real PostgreSQL via Testcontainers (`postgres:16-alpine`).
- `:app`: JUnit 4, Robolectric (SDK pinned in `app/src/test/resources/robolectric.properties`), Compose UI test.
- `:app` device tests: AndroidX Test runner on the emulator.

## Module map

| Module | Holds | Depends on |
|---|---|---|
| `:core` | Domain model and invariants, state transitions and guards, scaling and rounding, the text analyser, the API contract types. Pure Kotlin/JVM: no Android, no server library. | kotlinx.serialization |
| `:server` | HTTP API, web surfaces (deletion page, terms, privacy, private-link landing, health), persistence, media proxy, signals and alerting, backups. | `:core` |
| `:app` | The Android application: screens, local store, outbox and sync, media conversion, timers, device verdict. | `:core` |
| `deploy/` | Compose definitions: `deploy/local/` for development and the artefact. | — |
| `scripts/` | Setup, artefact and device-test commands. | — |

Packages: `app.recipefork.core`, `app.recipefork.server`, `app.recipefork.app`.

## Decisions

| ADR | Decides |
|---|---|
| [decisions/20260927-kotlin-monorepo-with-shared-core.md](decisions/20260927-kotlin-monorepo-with-shared-core.md) | One Kotlin monorepo; rules shared by device and server live once, in `:core`. |
| [decisions/20260927-android-client-stack.md](decisions/20260927-android-client-stack.md) | Compose, Room, WorkManager, Media3, Play Integrity, manual DI, `minSdk` 29. |
| [decisions/20260927-server-stack-ktor-postgres.md](decisions/20260927-server-stack-ktor-postgres.md) | Ktor, PostgreSQL, Flyway, plain SQL; MongoDB and ORMs rejected. |
| [decisions/20260927-one-text-analyser.md](decisions/20260927-one-text-analyser.md) | One Snowball-based analyser in `:core`; both ends index its tokens. |
| [decisions/20260927-media-served-through-the-server.md](decisions/20260927-media-served-through-the-server.md) | Media in R2, stored and served only through the server, visibility checked per request. |
| [decisions/20260927-keys-are-hashed-random-secrets.md](decisions/20260927-keys-are-hashed-random-secrets.md) | 256-bit keys stored as SHA-256; sign-in yields a device session; per-source attempt counters. |
| [decisions/20260927-server-authoritative-sync-with-outbox.md](decisions/20260927-server-authoritative-sync-with-outbox.md) | Server is authoritative; device outbox; revision mismatch becomes a fork. |
| [decisions/20260927-web-surfaces-from-the-server.md](decisions/20260927-web-surfaces-from-the-server.md) | Pages outside the app are plain HTML from the same server; private links are App Links. |
| [decisions/20260927-single-vm-deployment-and-hourly-dump.md](decisions/20260927-single-vm-deployment-and-hourly-dump.md) | One VM with Compose; hourly `pg_dump` to R2, 48 hourly + 30 daily. |
| [decisions/20260927-telegram-alerting.md](decisions/20260927-telegram-alerting.md) | Alerts via Telegram `sendMessage`, never reading from the bot. |
| [decisions/20260927-test-strategy.md](decisions/20260927-test-strategy.md) | JVM suite for every task; emulator tests only where a device is required. |
