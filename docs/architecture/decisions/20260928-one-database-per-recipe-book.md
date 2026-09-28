# Each recipe book on the device is a database file of its own

**Status:** accepted
**Date:** 2026-09-28
**Requirements:** AUTH-011, AUTH-012, AUTH-013, AUTH-026, AUTH-027, FLOW-051, FLOW-038, ACT-002

**Context:** A device holds every account signed in on it and a pool for what was made with none. Each account's recipes, unsent changes, favourites and blocked authors are kept apart and only the active one's are shown (AUTH-011); switching deletes nothing (AUTH-012); removing an account deletes everything it holds on the device (AUTH-013); the pool is moved into an account once, on the person's word (AUTH-026). The interface language, the usual serving count, the recipe that was open and the media setting belong to the device, not to any book (AUTH-012 (3)). The specification leaves the means of separation to the implementation.

**Decision:**
- The device keeps one Room database per recipe book: `book-<account id>.db` for each account on the device and `book-pool.db` for the pool, and one device database holding both dictionaries, the device's settings, and the list of accounts on the device with their keys.
- Each book's media is kept in a directory of its own named like its database.
- The composition root holds the active book. Switching closes one book database and opens another; nothing reads a book that is not active.
- Removing an account from the device deletes its database file, its media directory and its row in the account list, in that order, after the confirmation of AUTH-013.
- Moving the pool into an account copies every row and media file of the pool into the account's book in one transaction of the account's database, then deletes the pool's.
- Books are named by the account's identity, never by a digest of a key: the recovery key replaces all three keys (AUTH-021) and a name derived from one would lose its account.

**Consequences:**
- Isolation between books is structural: a query can only reach the book it was opened on, so no filter can be forgotten.
- Every book repeats the schema and its FTS table; the device holds as many copies as there are books, which the specification accepts (spec ADR `20260928-several-accounts-on-one-device.md`).
- A migration of the book schema runs on each book database when it is opened.
- The pool move copies across files, so it is written and tested once, as its own operation.

**Alternatives considered:**
- *One database with a `book_id` column on every table.* Rejected: one query without the filter shows one person's book in another's, which AUTH-011 (2) forbids, and removal becomes a set of deletes that must not miss a table.
- *One directory per account named by a digest of its key* (the product owner's sketch in the specification). Rejected for the naming: see the last point of the decision.
