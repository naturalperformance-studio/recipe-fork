# One text analyser for the device and the server

**Status:** accepted
**Date:** 2026-09-27
**Requirements:** L10N-006, L10N-007, L10N-005, FLOW-035, CAT-001, QUAL-002, QUAL-003

**Context:** A query matches on the normalised word in the recipe's declared language, ignoring case and treating `ё` and `е` as one letter (L10N-007), over title and description only (L10N-006). The same search runs in the catalogue on the server and in favourites and own recipes on the device, offline (FLOW-035). Two analysers would give two different answers to the same query.

**Decision:** The analyser lives in `:core`: lowercasing, `ё`→`е`, tokenisation, and the Snowball stemmers for Russian and English (the BSD-licensed Java port, vendored or depended upon). Both ends store the analysed tokens, not the raw text, for matching:
- server: a `tsvector` built with the `simple` configuration from the analysed tokens, with a GIN index;
- device: a Room FTS table over the analysed tokens.
Queries go through the same analyser before matching. With no language filter, a query is analysed in each language and matched against recipes declared in that language (L10N-007).

**Consequences:**
- The catalogue and favourites answer the same query the same way.
- A change to the analyser changes stored tokens: it requires re-indexing on both ends, done by a migration on the server and a rebuild of the FTS table on the device.

**Alternatives considered:**
- *PostgreSQL's `russian` and `english` configurations.* Rejected: they do not fold `ё`, and the device would need a different analyser anyway.
- *Prefix matching.* Rejected by the specification itself (L10N-007).
- *A search engine (Meilisearch, Elasticsearch).* Rejected: memory and money under CON-002, and another component to back up.
