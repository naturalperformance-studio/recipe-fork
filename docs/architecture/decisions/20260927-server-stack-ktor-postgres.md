# Server stack: Ktor, PostgreSQL, plain SQL

**Status:** accepted
**Date:** 2026-09-27
**Requirements:** CON-002, ST-009, DOM-001, CAT-001, CAT-005, RATE (whole section), ABUSE-003, MEDIA-010, MEDIA-012, QUAL-008, EXT-001

**Context:** The server orders every state transition under a guard (ST-009), and one transition touches several records at once — the recipe, its submission, counters, readers' favourites. The model is dense with relations: unique nicknames (DOM-001), one rating per account per recipe, a parent that must exist, a count of direct published forks (CAT-005), reports counted only from accounts that have published (ABUSE-003), media deleted when no recipe holds it (MEDIA-012) and an allowance counted over distinct files (MEDIA-010). The catalogue pages through four total orderings without repeating or skipping (CAT-001). All of it runs on one small server under USD 20 a month (CON-002).

**Decision:**
- HTTP: Ktor (Netty engine), JSON via kotlinx.serialization, API under `/api/v1`.
- Store: PostgreSQL 16, on the same machine as the server.
- Schema: Flyway migrations written in SQL.
- Access: JDBC through HikariCP with hand-written SQL; no ORM. Transitions run in one transaction with row locks.
- A recipe's frozen content may be kept as `jsonb` beside relational columns for what is filtered on and related; decided in the first task that stores recipes.

**Consequences:**
- Relational invariants are enforced by the database (foreign keys, unique constraints, transactions), not re-implemented in application code.
- Keyset pagination over composite indexes gives the total orderings of CAT-001 directly.
- Hand-written SQL is more text than an ORM would produce; it is also the text a reviewer can read.
- The database and the server share one machine's memory; the database is tuned small.

**Alternatives considered:**
- *MongoDB.* Considered at the user's question, 2026-09-27. For: a recipe is naturally a nested document. Against: the invariants above are relations, which MongoDB would need separate indexes and multi-document transactions for — and those transactions need a replica set even on one node; the self-hosted server wants a large share of a small machine's memory; the hosted offering (Atlas) is a new external dependency, which EXT-001 makes a product decision. The document shape is available in PostgreSQL as `jsonb`.
- *Spring Boot.* Rejected: memory footprint and start-up time on a machine of a few gigabytes.
- *Exposed or Hibernate.* Rejected: full-text search over prepared tokens, keyset pagination and row locking read more clearly as SQL.
- *Managed PostgreSQL.* Rejected: it alone costs most of the monthly ceiling.
- *SQLite on the server.* Rejected: single-writer concurrency and weaker indexing for search, for no saving beyond a process.
