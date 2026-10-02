# A typed quantity is read by its value, with either decimal mark

**Status:** accepted — adds DOM-019, completes DOM-017.

**Context:** L10N-009 fixed how a number is displayed in each interface language,
and nothing fixed how a typed one is read. In the English interface `1,500 kg` was
either 1500 kg or 1.5 kg, and `1.5 кг` in the Russian interface was either 1.5 kg
or not a number. DOM-017 could also be read as holding `2,5 шт` of a divisible
ingredient and `0,5 ч. л.` — values its grids display — or as refusing them for
not being written as fractions. Gate 1 of T-035 could not mark a parser right or
wrong (SD-037, 2026-09-28).

**Decision:**
- A comma and a point are both a decimal mark, in either interface language. No
  digit grouping separator is accepted in a typed number (DOM-019).
- A typed value is held when its value lies on its unit type's grid, whichever way
  it is written: `2,5 шт` of a divisible ingredient is 2½, `0,5 ч. л.` is ½,
  `0,25 ч. л.` is ¼. A value with no decimal form on the grid, ⅓ or ⅔, is typed
  as a fraction (DOM-017 (3)).

**Consequences:** a number typed on whatever keyboard the phone offers is read, and
a half is typable without a ½ key. The cost is that an English-speaking author who
types `1,500 kg` meaning a tonne and a half gets 1.5 kg; the field shows the value
it read, so the error is visible, but catching it is the author's. `0,33 ч. л.` is
refused, and the field's error has to name the fractions it accepts.

**Alternatives considered:**
- *Each interface language reads its own form only, with its own grouping.*
  Rejected: a keyboard that offers only the other mark would make a number
  untypable, and the same keystrokes would mean different quantities in the two
  languages.
- *Fraction grids accept only fraction notation.* Rejected: it contradicts
  DOM-017's own statement, which holds any value the grid displays, and leaves a
  numeric keypad with no way to write a half.
