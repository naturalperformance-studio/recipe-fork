# The deletion key is handed over beside the account key

**Status:** accepted, corrects AUTH-014, AUTH-017 and AUTH-024; applies
[20260920-deletion-key.md](20260920-deletion-key.md)

**Context:** the second step of creation holds both keys "with the controls of
AUTH-007 and AUTH-017", and AUTH-017 named no control at creation. Three passages
read three ways: the deletion key travels only in the file; it has its own copy
control; the account key's one copy carries it (SD-029).

**Decision:** on the second step the deletion key is displayed in full beside the
account key, with a copy control of its own. Each copy control puts one key on
the clipboard. The key file carries both. The step has one confirmation, and its
text names both keys.

**Consequences:** a person who keeps secrets in a password manager leaves creation
holding the deletion key as text, the only form the deletion page accepts
(EXT-006). The account key's text never contains the deletion key, so the rule
that the account key goes nowhere, including the product's own page, has no
exception. The step shows one more control.

**Alternatives considered:**
- *One copy action that carries both keys.* Rejected: pasting into the sign-in
  field or into the deletion page would hand over both, which puts the account key
  on the one page built to never receive it.
- *The deletion key only in the file at creation.* Rejected: a person who copies
  instead of saving leaves without it, and can reach it only while an account is
  signed in (AUTH-019).
