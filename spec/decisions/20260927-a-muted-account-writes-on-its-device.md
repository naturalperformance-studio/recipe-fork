# A muted account writes on its own device, and nothing of it reaches the server until it is full

**Status:** accepted, supersedes the muted-account permissions of
[20260920-account-creation-gated-by-the-device.md](20260920-account-creation-gated-by-the-device.md);
corrects AUTH-001 (2) and AUTH-004

**Context:** A person who asked to write a recipe and got a muted account was told
by AUTH-001 (2) that writing proceeds and by AUTH-004 that it could not, and no
path led a muted account to AUTH-005's retry (SD-019). The gate exists to protect
the moderator's queue, the ratings and the storage budget, and a draft that never
leaves the device touches none of them.

**Decision:**
- A muted account writes, edits, forks and deletes its own recipes on its device.
  None of them is sent to the server while the account is muted.
- It attaches no media, does not submit, request a private link or rate. Each of
  those controls is replaced by AUTH-006's statement, which carries a retry
  control (AUTH-005); the account's own screen carries the same statement.
- The statement says the recipes are held on this device only and are lost with
  it or when another key signs in.
- When the account becomes full, every recipe on the device is sent to the server
  without asking.

**Consequences:** A person on a device that cannot be attested still gets the
product's central act — writing and forking — at no cost to anyone else. A sideloaded
build gets it too, forever, but nothing of its work reaches another person. Local
recipes are exposed to the loss of the device, which the statement says. A fork a
muted account makes is not counted on its parent until it reaches the server.

**Alternatives considered:**
- *A muted account writes nothing (the earlier rule).* Superseded by the user.
- *Local media as well.* Rejected by the user: on becoming full every recipe moves
  to the server, and media gathered locally could exceed the 150 MB an account may
  hold unpublished.
- *Ask before uploading on becoming full.* Rejected: the recipes are the
  account's, and asking only adds a way to lose them.
