# Deletion from outside the application uses its own key

**Status:** accepted

**Context:** the distribution channel obliges an application that creates
accounts to offer deletion both inside the application and through a web
resource reachable by someone who has uninstalled it
([Google Play — app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en),
checked 2026-09-20). The product has no identity to authenticate with: no email
address, no password, no identity provider. The only thing a user holds is the
account key, and whoever holds that holds the account outright.

**Decision:** every account carries a deletion key — unique, unguessable, placed
beside the account key and shown inside the application (AUTH-017). The web page accepts
that key as text and nothing else, deletes immediately on a match, and limits the
rate at which keys may be tried (EXT-006).

**Consequences:**
- The rule "your account key is never uploaded anywhere" has no exception, including
  the product's own page. A rule with an exception is what a counterfeit page
  lives on.
- The worst a counterfeit page can achieve drops a class: a stolen deletion key
  destroys an account, it does not take it over. With published recipes surviving
  deletion, what such a theft costs is the victim's drafts and unpublished media.
- The product's server holds a verifier for each account. That is a per-account
  secret the product would otherwise not have; it is small, and it can do nothing
  but destroy.
- The key's strength, not its uniqueness, is what protects the page: a short
  unique key would make the deletion page a machine for erasing random accounts,
  which is why AUTH-017 requires unguessability and EXT-006 requires a rate
  limit.
- OQ-002 is constrained rather than blocked: whatever the account key turns out to
  be, it carries this value as copyable text.

**Alternatives considered:**
- *Upload the account key to the page.* Rejected: it would not stop a counterfeit
  page from harvesting keys, and it would both cost the exceptionless rule and
  create a store of other people's whole accounts — the one thing the product
  otherwise has nowhere.
- *Select the account key as a file on the page, sign a challenge in the browser, send only the
  signature.* Rejected although it keeps the file on the device: it depends on
  OQ-002 producing a signing key, it teaches the same gesture — choose your key
  file on a web page — and a counterfeit page imitating that gesture takes the
  whole account rather than only the power to delete it.
- *Identify the account by its nickname.* Rejected outright: a nickname is public,
  so the page would delete other people's accounts on request.
