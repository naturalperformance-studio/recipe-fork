# A recipe reaches the server only when sent, in one request; no outbox holds recipes

**Status:** accepted — supersedes [20260927-server-authoritative-sync-with-outbox.md](20260927-server-authoritative-sync-with-outbox.md), and the words "the synchronisation outbox and media uploads" of [20260927-android-client-stack.md](20260927-android-client-stack.md), which otherwise stands
**Date:** 2026-09-28
**Requirements:** FLOW-002, FLOW-003, FLOW-004, FLOW-005, FLOW-006, FLOW-007, FLOW-008, FLOW-052, FLOW-059, ST-009, ACT-002, DOM-005, MEDIA-010

**Context:** A recipe is written on the device by anyone and reaches the server only when its owner sends it (FLOW-002, FLOW-059). A sending that fails queues nothing, and nothing of the recipe reaches the server — not its text without its media, nor part of its media (FLOW-059 (8)). Each recipe shows one of three marks: never sent, sent with every change, sent with changes not sent (FLOW-005). A recipe's identity is given when it is created on the device and kept when it is sent, so that a fork can name a parent the server does not hold (FLOW-007, the domain model's `recipe_id`). Divergence between devices is found at sending and becomes a fork (FLOW-008). Favourites and blocks keep reaching the server by themselves (FLOW-052).

**Decision:**
- A recipe's identity is a UUID minted on the device when the recipe is created. The server accepts it as the recipe's primary key on the first sending and never mints one.
- Sending is one HTTP request per recipe: a multipart body holding the recipe document and every media item it carries that the server does not hold yet. The server writes the media to object storage under staging keys, then writes the recipe, its revision and its media references in one database transaction; on any failure it deletes what it staged and answers with the failure. The device changes nothing until the answer says the recipe was stored.
- Every sent recipe carries a server revision. The device keeps, per recipe, the revision its copy was made from and a digest of the content it last sent. A sending names that revision; if the server's revision differs, or its state changed alone, the server keeps its version and stores the sent one as a `private` fork owned by the same account, and says so in the answer (FLOW-008).
- The mark of FLOW-005 is computed on the device: no revision means never sent; a revision with a content digest equal to the last sent digest, and no media added since, means sent; otherwise, changes not sent.
- The device holds no queue of recipe operations. WorkManager carries only what FLOW-052 sends by itself — favourites and blocked authors — as a small outbox of those operations.
- Acts that FLOW-003 refuses are never written anywhere to be retried.

**Consequences:**
- No merge logic exists anywhere, as before.
- The largest request the server receives is one recipe with its media, tens of megabytes at most; the server's request size limit is set from the media limits of MEDIA-001 to MEDIA-009.
- Staged objects left by a crash between staging and commit are orphans; the media sweep of MEDIA-012 deletes objects no recipe holds, which covers them.
- A recipe's identity is visible to whoever holds a file of it (FLOW-063) and is not secret; nothing grants access by it, because the server checks ownership and visibility on every request.

**Alternatives considered:**
- *Keep the outbox and drain it when the owner presses send.* Rejected: FLOW-059 (8) requires a failed sending to queue nothing; an outbox is a queue.
- *Send the text first and the media in later requests.* Rejected: a failure between them leaves text without media on the server, which FLOW-059 (8) forbids.
- *Server-minted identities, assigned at the first sending.* Rejected: a fork of an unsent recipe must name its parent before the parent is sent (FLOW-007), and a file must be recognised when imported twice (FLOW-064).
