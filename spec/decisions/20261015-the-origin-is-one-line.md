# The Origin is one line; the parent's author is on the parent's card

**Status:** accepted, 2026-10-15. Adds FORK-010; changes FLOW-065; refines the
display part of [serverless mode and recipe files](20260928-serverless-mode-and-recipe-files.md).

## Context

Since 2026-10-03 a fork of a recipe imported from a file named its parent on its
card by title and by the nickname the file states, marked as stated in the file and
not verified (FLOW-065). In the redesign of 2026-10-15 the owner looked at the
version's card as a whole: the element that leads to the parent — the Origin,
«Происхождение» — stands above the title, and a second line naming somebody else's
unverified nickname on the person's own version is noise there. The nickname
already appears, with its caveat, on the imported recipe's own card (FLOW-064), one
tap away.

## Decision

On the card of a fork the Origin is one line naming the parent by its title —
"after the recipe «…»", «по рецепту «…»» — that leads to the parent. The book
entry of a fork names its parent in the same words. The parent's author is shown on
the parent's own card. One exception: in serverless mode, when the parent is not in
the recipe book, the fork's card keeps the stated author beside the title, with the
caveat, because there is nowhere else to see it.

## Consequences

- A fork's card never shows an unverified nickname while the recipe it belongs to
  can be opened; the caveat travels with the nickname wherever it is shown.
- The rule is the same for every parent a card shows (FORK-004, FORK-005's owner
  case, FLOW-065); only serverless mode has a parent that can be absent from the
  book with nothing else naming its author.
- How the line looks is the design's (`docs/brand/`), not the vision's.

## Alternatives considered

- **Keep the stated author on the fork's card always** (the rule since
  2026-10-03): rejected — two lines of provenance on a card whose subject is the
  person's own version, one of them a claim nobody checked.
- **Drop the stated author from the fork's card always**: rejected — when the
  parent is gone from the book the author would vanish from the device entirely.

**Source:** user decision, 2026-10-15 (`docs/brand/identity/decisions.md`, fixes to
the redesign, and interface recommendations 2 and 3).
