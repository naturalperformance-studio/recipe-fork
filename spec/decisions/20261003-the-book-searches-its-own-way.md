# The book searches its own way

**Status:** accepted, narrows L10N-006, L10N-007 and FLOW-035 to the server catalogue

**Context:** L10N-006 matches a text query against a recipe's title and description
only, and L10N-007 matches a query word by its normalised form under the recipe's
language. FLOW-035 had the device's own book searched "exactly as the catalogue
is". Both rules were set on 2026-09-21 for the server catalogue: ingredients were
left out of the text query because indexing them costs the server (CON-002) and
the ingredient filter already reaches them (CAT-006). The v0.8 build searched the
book on the device by title and every ingredient's name, matching the beginning of
a word, and v0.11 added the description. At the v0.8 demo the user approved the
scenario "`кур` finds `Курица с рисом` and a recipe with chicken only among its
ingredients".

**Decision:**
- The device's own book (the recipe book and favourites) searches the title, the
  description and every ingredient's name — both labels of a dictionary entry, its
  qualifier, free text — and matches a query word to the beginning of a word,
  ignoring letter case.
- The server catalogue keeps L10N-006 and L10N-007 as written.
- Filters stay one mechanism in every place (FLOW-035); only the text query differs.

**Consequences:** a person searching their own book finds a recipe by what is in
it, which is what a book of one's own recipes is searched for. A query in an
inflected form (`блинов`) finds less in the book than in the catalogue; a query
by a word's beginning (`кур`) finds more. The two places no longer behave
identically, so a reader moving from the book to the catalogue can be surprised;
the catalogue does not exist until there is a server, and the question is
revisited when it is built.

**Alternatives considered:**
- *One rule everywhere, the build changed to L10N-006/007.* Rejected: the reason
  for leaving ingredients out — the cost of a server index — does not apply on the
  device, and the user approved the ingredient match in use.
- *The catalogue changed to the book's rule.* Not taken now: it reopens CON-002
  for a surface that is not built.
