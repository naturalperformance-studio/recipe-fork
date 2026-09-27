# Test strategy: a JVM suite, and device tests where a device is required

**Status:** accepted
**Date:** 2026-09-27
**Requirements:** QUAL-005, MEDIA-002, MEDIA-003, MEDIA-004, AUTH-001, FLOW-013

**Context:** Every acceptance criterion of every task is checked by an automated test, the full suite runs several times per task (developer, team lead, reviewer, and once per mutated criterion), and there is no CI. A suite that needs an emulator for everything would make each review very slow. A few behaviours genuinely need real hardware: video conversion through platform codecs, and the device verdict.

**Decision:**
- `:core` and `:server`: JUnit 5. Server tests run against a real PostgreSQL started in Docker by Testcontainers; no in-memory substitute.
- `:app`: JUnit 4 with Robolectric and the Compose UI test APIs, running on the JVM.
- The test command runs these three and needs no device.
- Behaviour that needs real codecs or a real device goes in the instrumented source set (`androidTest`) and runs on a headless emulator at API 29 by a separate device-test command. A task whose criteria need it carries that command in its packet; other tasks do not run it.
- The device verdict is behind an interface; tests use a fake. The real service is exercised by hand at batch time.

**Consequences:**
- The ordinary suite stays fast enough to run per mutation.
- Tasks touching video conversion are reviewed more slowly.
- A criterion that depends on the real attestation service is checked by hand, and appears in the batch's hand-check list.

**Alternatives considered:**
- *Emulator for every Android test.* Rejected: the suite runs per mutation, and boot time multiplies.
- *H2 or an in-memory database for server tests.* Rejected: search, locking and SQL dialect differ from PostgreSQL, so tests would pass against a database the product does not run on.
