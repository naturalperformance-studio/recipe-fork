# Equipment is bound to its dictionary

**Status:** accepted

**Context:** v0.27 gave a recipe a plain list of equipment lines. Planning v0.28 on
2026-10-10, the product owner wanted a reader to tick once what their kitchen
holds and see at once what a recipe lacks, and to hide recipes they cannot cook.
That needs a recipe's equipment and a kitchen's to be compared.

**Decision:** a third dictionary, of equipment, kept like the ingredients' (DOM-026).
An equipment item binds to an entry with an optional qualifier, or stays free text
(DOM-025). My kitchen is a set of ticked entries on the device (FLOW-071); only
bound items are ever marked missing or hide a recipe (FLOW-072). Old lines whose
whole text is an entry's name become that entry; the rest stay free text.

**Consequences:**
- The recipe file and the device's book changed format; older files still import.
- Free-text equipment is never checked against the kitchen — the editor says so
  when an author leaves a line unbound.
- Consumables are in the dictionary for recipes to name, but never count as
  missing: an unticked roll of foil should not hide a dish.

**Alternatives considered:**
- *Match a recipe's equipment lines to the dictionary by their text alone, keeping
  them free text.* Rejected by the owner: matching by text misses every line
  written with a size or an adjective, and silently changes what counts when a
  synonym is added.
