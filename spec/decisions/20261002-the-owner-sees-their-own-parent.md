# The owner sees the parent of their own fork

**Status:** accepted, narrows [ADR — provenance follows publication](20260920-provenance-follows-publication.md); changes FORK-005 (5)

**Context:** FORK-005 hid a never-published parent from everyone, the owner
included, "so that every viewer sees the same card". The v0.1 build has no server:
a fork made on the device names its parent by title and opens it, and the user
accepted this in the demo scenario. Under FORK-005 as written, the owner would
stop seeing that link as soon as the server arrives.

**Decision:**
- In the owner's own recipe book, the fork of their own recipe names its parent
  to them by title, opens it while the book holds it, and names it as
  unavailable when the book does not.
- Everyone else sees no parent until it has been published, as before.

**Consequences:** the card of one recipe differs between its owner and everyone
else. The reasons for hiding the parent — it points at a recipe no reader can
open, and it discloses that the author holds an unpublished one — do not apply to
the person who holds that recipe. The owner sees what they will need when
deciding whether to publish the fork or the parent.

**Alternatives considered:**
- *Keep FORK-005 as written; the v0.1 behaviour is temporary.* Rejected: the
  owner would lose a relationship they made and can open, for the sake of a card
  that looks the same to everyone.
