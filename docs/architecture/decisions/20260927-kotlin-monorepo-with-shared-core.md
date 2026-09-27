# One Kotlin monorepo with a shared core

**Status:** accepted
**Date:** 2026-09-27
**Requirements:** CON-005, ST-009, FLOW-002, FLOW-035, SCALE (whole section), L10N-007, CAT-001

**Context:** The same rules run in two places. The device scales recipes, edits drafts and searches favourites without a connection (FLOW-002, FLOW-009, FLOW-035); the server orders every state transition and refuses one whose guard stopped holding (ST-009) and answers catalogue search. If the scaling grid, the state machine or the search analysis were written twice, the device and the server would drift apart and the drift would surface as a recipe that scales one way offline and another online, or a favourites search that disagrees with the catalogue.

**Decision:** One repository, one Gradle build (Kotlin DSL, version catalog), three modules, all Kotlin:
- `:core` — pure Kotlin/JVM, no Android and no server dependency: domain model, invariants, state transitions and their guards, scaling and rounding, the text analyser, and the API contract (request/response types shared by client and server).
- `:server` — the product's server, depends on `:core`.
- `:app` — the Android application, depends on `:core`.
JDK 21 toolchain; Android bytecode targets Java 17.

**Consequences:**
- Every rule shared by device and server is implemented and tested once, in `:core`.
- The API contract is compiled into both ends, so a mismatched field is a compile error, not a runtime one. No code generation, no OpenAPI step.
- Building the server needs the Android SDK installed, because it is the same Gradle build. Accepted: there is one developer machine.
- `:core` must stay free of Android and server libraries; a dependency added there lands on both ends.

**Alternatives considered:**
- *Separate repositories for client and server.* Rejected: two copies of the rules, and the drift described above.
- *Kotlin Multiplatform.* Rejected: there is one client platform (CON-005); a plain JVM module is consumable by Android and by the server without KMP's build complexity. If iOS ever arrives, `:core` is the module that would become multiplatform.
- *A server in Go or TypeScript.* Rejected: no shared core is possible, so the rules would be written twice.
- *Java instead of Kotlin.* Rejected: Kotlin is the platform language on Android and removes a second language from the repository.
