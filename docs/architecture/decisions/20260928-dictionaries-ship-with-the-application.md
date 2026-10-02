# Both dictionaries ship with the application and are refreshed from the server

**Status:** accepted
**Date:** 2026-09-28
**Requirements:** FLOW-001, FLOW-002, DOM-014, FLOW-061, FLOW-062

**Context:** The device holds both dictionaries and refreshes them whenever a connection is available (FLOW-001); offline, the editor offers exactly what it offers online (FLOW-001 (1), (4)). Since anyone writes on a clean installation with no connection (FLOW-002 (1)), a device that has never reached the server must already hold them. A serverless release makes no request at all (FLOW-061 (2)), so a device installed after the server is gone has no other source. The dictionaries are maintained only by direct operation on the production store (DOM-014); the repository holds no copy of their content.

**Decision:**
- The APK carries `app/src/main/assets/dictionaries.json`: both dictionaries in the form of `DictionariesDto`, with the version the server gave them.
- The snapshot is refreshed by `scripts/dictionary-snapshot.sh`, which fetches `/api/v1/dictionaries` from the production server and writes the file; it is run and the file committed before a release build. Builds between releases use the committed file.
- At start, the device database is seeded from the snapshot when it holds no dictionaries, or when the held copy came from an earlier snapshot and the APK's snapshot has another version. A copy fetched from the server is replaced only by the server (FLOW-001 (6)), except in serverless mode, where the APK's snapshot replaces any held copy whose version differs.
- Seeding follows the same replacement path as a server refresh, so an entry a held recipe names stays resolvable when it leaves the dictionary (FLOW-001 (7), DOM-014 (2)).
- A JVM test reads the committed snapshot and checks it against the store's rules: labels in both languages, at most two levels (DOM-010, L10N-003).

**Consequences:**
- A clean installation offers the dictionaries with no connection, and a serverless release carries the last dictionaries the server had.
- The snapshot is as old as the last release; the first connection brings the current dictionaries, as before.
- Refreshing the snapshot is a release step, not something the build does, so a build never depends on the production server being up.

**Alternatives considered:**
- *Fetch only, as now.* Rejected: a clean installation with no connection would have empty dictionaries, and a serverless release none at all.
- *Ship only, with no refresh.* Rejected: FLOW-001 (6) requires the held copy to follow the product owner's changes.
- *Keep the dictionaries' content in the repository as the source the store is loaded from.* Rejected: DOM-014 places their maintenance in the store.
