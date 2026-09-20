# A fork carries the method, not the expression

**Status:** accepted

**Context:** A fork copies a recipe. Part of a recipe is not copyrightable at
all: under art. 1259 §5 of the Civil Code of the Russian Federation, ideas,
methods, processes and facts are excluded from copyright, which covers a
sequence of cooking operations and a list of ingredients. What is protected is
the form of expression: photographs, video, and the author's own prose.

The question was therefore not whether forking is legitimate — copying the
method is — but what happens to the protected parts when a fork is published,
and whether an author should be able to forbid forks of their recipe outright.

**Decision:** A fork inherits title, tags, servings, ingredients and steps, and
names its parent. It never inherits media or the description, in its draft or
afterwards. There is no per-recipe flag that forbids forking or forbids
publishing forks.

**Consequences:**
- A published fork never redistributes a photograph or a description its author
  did not write, so the protected material is not multiplied by the fork
  mechanic at all.
- To publish a fork, its author must supply their own photograph or publish
  without one (media is not required — DOM-006).
- The author of a fork cannot see the parent's photos inside the fork. They can
  open the parent and see them there (FORK-003).
- Step wording is inherited although it is arguably expression too. Drawing the
  line there would leave a fork without the instructions it exists to modify.
  Step text in this product is short and functional, and this is the accepted
  residual risk.
- The catalogue will contain forks that show no photograph. Search and browsing
  must look correct with a missing image (section `catalogue`).

**Alternatives considered:**
- *A per-recipe licence flag forbidding publication of forks.* Rejected. It
  would grant authors control over the part of a recipe the law does not give
  them — the method and the ingredient list — while adding nothing to the
  protection of what is actually theirs. It is unenforceable in substance,
  because retyping the same ingredients and steps into a new recipe is lawful,
  undetectable and faster than forking, so the flag would deter only the honest
  user who would have left an attribution link, and would push copying towards
  the unattributed path. It would also disable the product's distinguishing
  mechanic whenever authors set it defensively, which is the default behaviour
  of people who do not know what a setting does.
- *The fork inherits everything, with a licence granted in the terms of use.*
  Rejected: it makes every fork a redistribution of someone else's photograph,
  resting entirely on terms that a reporting rights holder has never read.
- *The fork inherits media in the draft only, stripped at submission.* Rejected
  by the product owner in favour of the simpler rule; media that disappears at
  submission has to be explained in the interface and surprises the author at
  the worst moment.
