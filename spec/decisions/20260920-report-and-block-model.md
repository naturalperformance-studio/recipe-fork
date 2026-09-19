# Reporting hides at three reports; blocking hides only from catalogue and search

**Status:** accepted

**Context:** Distribution through Google Play requires in-app reporting of
content and authors and in-app blocking of authors (CON-006). The product has a
single moderator and tells users that review may take up to a week, so any
reported item has a long window before a human sees it. Accounts are free,
anonymous and instant to create, which makes coordinated reporting cheap. The
product has no messaging or comments, so blocking can only act on visibility of
content.

**Decision:**
- A published recipe is hidden from everyone except its owner once three
  distinct accounts have reported it, and stays hidden until the moderator
  decides. One account counts once per recipe.
- Blocking an author removes that author's recipes from the blocker's catalogue
  browsing and search results only. The recipes remain reachable for the blocker
  by direct link and as the named parent of a fork.

**Consequences:**
- Three colluding accounts can hide any recipe for up to a week. Accepted: the
  product owner judged the alternative — objectionable content visible for a
  week — the worse outcome under a store policy that demands timely action.
- Blocking never breaks a provenance chain and never empties a favourites entry,
  because it does not remove access, only discovery.
- Blocking therefore does not protect a blocker from seeing the blocked author's
  content when they navigate to it deliberately. This is a known and accepted
  limit of the chosen semantics.
- The moderator queue gains a second input besides first publication, so its
  load is no longer bounded by the rate of new recipes.

**Alternatives considered:**
- *Hide on the first report.* Rejected: a single hostile account could hide any
  recipe for a week, with no recourse for the author.
- *Hide at ten reports.* Rejected: unreachable at the expected audience size,
  which would make auto-hiding a dead letter and leave every report waiting for
  the single moderator.
- *No auto-hiding at all.* Rejected: it leaves reported content visible for up to
  a week, against the policy expectation of timely action.
- *Blocking hides the author's recipes everywhere, including by direct link and
  as a fork parent.* Rejected: it breaks provenance chains and favourites for
  reasons invisible to the user.
