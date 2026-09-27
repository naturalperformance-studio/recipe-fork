# The narrowest supported window is 320 dp, and numbers follow the interface language

**Status:** accepted

**Context:** SCALE-012 was measured on "the narrowest supported screen width", a
figure stated nowhere, and handed digit grouping to section `localization`, which
did not hold it (SD-016).

**Decision:**
- Every requirement holds in a window 320 dp wide and wider, at text scale up to
  200 per cent; below 320 dp, split-screen included, nothing binds (QUAL-011).
- Digit grouping and the decimal mark follow the interface language: `10 000 000`
  and `1,5 кг` in Russian, `10,000,000` and `1.5 kg` in English (L10N-009).

**Consequences:** The width is the product's figure and can be tested on any
device or emulator configured to it. A reader who narrows the application below
320 dp in split-screen gets whatever layout results. Numbers read as each
language writes them, so the same recipe looks different in the two interfaces.

**Alternatives considered:**
- *360 dp.* Rejected: it leaves the narrower phones still sold outside every
  requirement.
- *Support split-screen down to its platform minimum.* Rejected: a layout
  guarantee at roughly two thirds of the chosen width, for a use the product was
  not built for.
- *One grouping for both languages, or no grouping.* Rejected: each reader would
  meet a form their language does not use.
