# The exact label and the order of the offer

**Status:** accepted, completes FLOW-057

**Context:** FLOW-057 withholds free text when the typed text is "exactly the
label" of an offered entry, and did not say how that is compared: whether `Молоко`
or ` молоко ` is the label `молоко`, and whether `milk` is, under a Russian
interface, when the entry's English label is `milk`. It also ordered the offer
only in one respect — leaves before their parent. The re-check of SD-006 found
both still open: two builds of the field would differ in when free text appears
and in which entry comes first.

**Decision:**
- "Exactly the label" folds as the offer does — letter case ignored, `ё` and `е`
  one letter — ignores spaces before and after the text, and takes the label in
  either interface language.
- The offer is two groups: entries whose label, in either language and after
  folding, begins with the typed text; then every other entry offered.
  Alphabetical by the label shown within each group. A parent's leaves stand
  immediately before it, alphabetically; an entry is offered once.

**Consequences:** One comparison governs the whole field, so an author who typed
the entry's name in the other language or with a capital letter is not invited to
turn it into free text by accident. On a short input the entry the author is most
likely spelling comes first. Alphabetical order says nothing about which entry
authors pick most often; the product keeps no such count.

**Alternatives considered:**
- *A literal, character-for-character match with the label in the interface
  language.* Rejected: `Молоко` and `milk` would offer free text beside the entry
  they name, and ingredients would be lost to filters for a capital letter.
- *Plain alphabetical order.* Rejected: on `мол` the entry `молоко` could sit
  below every entry that merely contains a word beginning with `мол`.
