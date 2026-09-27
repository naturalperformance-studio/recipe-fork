# A change of state alone is divergence, the fork notice comes when the answer does, and acceptance is marked where the owner manages recipes

**Status:** accepted, corrects FLOW-005 and FLOW-008

**Context:** FLOW-008 fired on an edit made on each side, while its fourth
criterion treated any difference as divergence; an offline edit to a recipe that
had meanwhile only been submitted or published fell between them, and nothing said
what became of it (SD-018). The fork notice was tied to opening the application,
which automatic sync does not wait for, and nothing bounded it; the accepted mark
of FLOW-005 named no surface (SD-017).

**Decision:**
- An edit sent against a server version that changed since — in content or in
  state alone — is divergent: the server's version keeps the recipe, the edit
  becomes a `private` fork (FLOW-008).
- The owner is told when the server's answer arrives, at once if the application
  is open or at the next opening if not, once, until acknowledged.
- The accepted mark is on the own-recipes entry and in the editor, and nowhere
  else (FLOW-005).

**Consequences:** No edit is ever discarded, which FLOW-008's source already
promised. An author who submitted on one phone and kept editing on another gets a
private fork they may not have wanted, and is told why. The card and the catalogue
stay free of sync marks.

**Alternatives considered:**
- *Refuse the edit and discard it, telling the owner what was lost.* Rejected:
  it breaks "no edit is discarded".
- *Tell the owner only at the next launch.* Rejected: the fork would exist
  unannounced for a whole session.
- *The mark also on the card, or everywhere an own recipe appears.* Rejected: a
  sync state is the owner's business, not the reader's view.
- *No positive mark, only unaccepted changes marked.* Rejected: absence is not
  distinguishable from a mark that failed to render.
