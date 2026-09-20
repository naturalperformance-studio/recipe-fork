# Provenance is shown only for a parent that has been published

**Status:** accepted

**Context:** FORK-004 named a recipe's direct parent unconditionally, and ST-007
covered only a parent that had been removed. Neither considered the ordinary
case: an author forks their own draft, publishes the fork, and the parent has
never been visible to anyone but them. Naming such a parent shows a reader a
recipe they can never open and discloses that the author holds an unpublished
one. Two axes were being confused in the discussion and are worth separating
here: whether a recipe is *held by the server*, which is synchronisation
(FLOW-005..007), and whether it is *externally visible*, which is publication
or a private link. This decision hangs on the second axis only.

**Decision:**
- A recipe's parent is named on its card if and only if that parent is, or has
  been, `published` (FORK-004, FORK-005).
- A parent that has never been published is not mentioned at all: no name, no
  placeholder, no hint that a parent exists — and not to the owner either, so
  that every viewer sees the same card. A recipe whose parent is only
  `private`, `pending`, `rejected` or `link_shared` reads as an original.
- A parent that has been published and is now `removed` or `reported_hidden` is
  named as unavailable with no reason given (ST-007, extended from `removed`
  alone).
- The recipe gains a `was_published` fact, set once and never cleared, because
  the rule needs to distinguish a parent that was published and withdrawn from
  view from one that never was.

**Consequences:** The card of an immutable recipe can change over time: a fork
published before its parent reads as an original, and starts naming its parent
the day the author publishes it. Immutability is not broken — the stored parent
never changes, only its display — and the spec already worked this way in the
other direction, since a removed parent turns an ordinary card into one with an
unavailable parent. A reader who favourited a recipe as an original may
therefore one day see a parent on it. A private link alone never produces
provenance, so an author can share a draft by link without that draft ever
appearing in anyone else's lineage.

**Alternatives considered:**
- *Name the parent unconditionally, as FORK-004 originally read.* Rejected: it
  points at something no reader can open and leaks the existence of the
  author's unpublished recipes.
- *Name a never-published parent as unavailable, the way ST-007 treats a removed
  one.* Rejected for the same leak: "based on a recipe you cannot see" is the
  disclosure, whatever word is used for it.
- *Freeze the provenance at the moment of publication, so a fork published
  before its parent stays an original forever.* Rejected: it keeps the card
  stable at the price of permanently denying a true relationship, and the
  product's whole subject is that relationship.
- *Treat `link_shared` as externally visible for this purpose.* Rejected by the
  product owner: a link is shared with chosen people, and the catalogue is not
  the place where its existence becomes public.
