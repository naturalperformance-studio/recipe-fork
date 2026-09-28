# Serverless mode is a build flag read once, at the composition root

**Status:** accepted
**Date:** 2026-09-28
**Requirements:** FLOW-061, FLOW-062, FLOW-063, FLOW-064, FLOW-065, MVP-001

**Context:** Serverless mode is on only in a release built with it on; nothing in the application or from the network switches it (FLOW-061). With it on, the application makes no request to the product's server, every control that needs the server is absent rather than refused, and export and import of recipe files exist (FLOW-062). The first release is built with the mode off, and the mode is built and tested in it (MVP-001 (6)).

**Decision:**
- A Gradle property `recipefork.serverless` (default `false`) sets `BuildConfig.SERVERLESS`. Nothing else reads the property, and nothing but `MainActivity` reads `BuildConfig.SERVERLESS`.
- `MainActivity` passes a `Mode` (`Standard` or `Serverless`) to the composition root. Every screen and component that FLOW-062 changes takes the `Mode` it is given, never the build constant.
- In `Serverless`, the composition root builds no HTTP client at all; components that would use the network receive an implementation that has none.
- The JVM suite runs the tests of each mode-dependent screen in both modes by passing `Mode` explicitly, so one test command covers both builds.
- The artefact command builds two APKs, `recipe-fork-debug.apk` and `recipe-fork-serverless-debug.apk`.

**Consequences:**
- "No request to the product's server" (FLOW-061 (2)) is checked by a test that the serverless composition root constructs no HTTP client.
- Each task that adds a control FLOW-062 removes carries FLOW-062 (1) for that control, as every screen task carries L10N-001, so the mode is not retrofitted at the end.
- The build commands of `00-index.md` do not change.

**Alternatives considered:**
- *Android product flavors.* Rejected: every test and build task doubles (`testStandardDebugUnitTest`, …), the index's commands change, and the two variants differ in one boolean.
- *A runtime switch or a server signal.* Excluded by FLOW-061.
