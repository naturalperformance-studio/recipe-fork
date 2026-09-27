# The imported ancestor is named, however far up it is
**Status:** accepted, 2026-09-27, narrows GOAL-003 once the import feature is built.

**Context:** GOAL-003 limits lineage to the direct parent: a recipe names one
recipe above it and a reader walks the chain one step at a time. The product
owner decided that every recipe descending from an imported one says so, through
any number of forks (IMPORT-018), because the import's source is the one fact
about origin that lies outside the product.

**Decision:** A recipe descending from an imported recipe names that imported
ancestor in addition to its direct parent, and leads to it inside the
application. It names nothing between the two.

**Consequences:**
- Buys: the creator whose post started a chain is one step away from every recipe
  in it, which is the promotion the product owner wants the feature to give.
- Costs: finding the imported ancestor needs the chain to be walked, or the
  ancestor to be carried by each fork at creation. Which of the two is an
  engineering choice; the second makes the mark immune to anything that later
  happens between the two recipes.
- GOAL-003's rule still holds everywhere else: there is no tree, no list of
  descendants and no count beyond CAT-005.

**Alternatives considered:**
- *Only a direct fork of an import carries the mark.* Rejected by the product
  owner: a second-generation fork would lose all trace of the source.
- *Every descendant leads straight to the external post.* Rejected: the imported
  recipe is the one place attribution lives, with its model version and its
  creator's name, and two ways out would split it.
