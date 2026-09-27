# The ingredient field: word-beginning match in both languages, free text always beside, twelve units, "to taste" kept in the unit field

**Status:** accepted, writes FLOW-057 and DOM-016, corrects FLOW-022, FLOW-023 and DOM-002

**Context:** The ingredient row could not be built as written (SD-006, SD-008): no
rule said what the field counts as a match, whether free text stands beside
offered entries, whether a leaf is offered through its parent, which units exist,
and whether the unit field survives a "to taste" choice.

**Decision:**
- An entry is offered when every typed word begins some word of its label, in
  either language, ignoring case and treating `ё` and `е` as one; leaves of an
  offered parent come before it (FLOW-057).
- Free text is offered whenever the typed text is not exactly the label of an
  offered entry, beside the entries; the author chooses (FLOW-057, FLOW-022).
- The units are twelve: g, kg, t, ml, l, m³; pc; tsp, tbsp, cup; pinch; to taste
  (DOM-016). More counting units are OQ-037.
- On "to taste" the quantity field goes and the unit field stays; another unit
  brings the quantity back (FLOW-023, DOM-002).

**Consequences:** The offer works while the author is still typing, and
`кокосовое молоко` is no longer forced onto `молоко` with a qualifier. Free text
becomes one choice away even beside a fitting entry, so more ingredients will be
unfilterable, and the free-text share watched by OBS-001 counts authors' choices
as well as the dictionary's gaps. A bunch of dill is written as one piece.

**Alternatives considered:**
- *Labels of the interface language only.* Rejected: searching both costs nothing.
- *Whole normalised words as in L10N-007.* Rejected: nothing is offered until a
  word is complete.
- *Free text only when nothing is offered.* Rejected: an author of coconut milk
  would be pushed onto milk with a qualifier, permanently once published.
- *Each entry matched on its own label only.* Rejected: FLOW-023's leaves-first
  rule would almost never fire.
- *Bunches, cloves, cans, slices and packs as units now.* Deferred to OQ-037:
  divisibility belongs to the entry, and a can has no size.
- *Withdraw both fields on "to taste".* Rejected: the row would lose the place
  where the choice is undone.
