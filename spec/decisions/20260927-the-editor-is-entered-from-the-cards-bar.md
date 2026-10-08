# The editor is entered from the card's bar

**Status:** accepted, corrects FLOW-029 and FLOW-053; its last sentence on deletion is superseded — since 2026-10-04 the card's menu offers deletion too (FLOW-053, user decision at v0.19)

**Context:** FLOW-029 (2) had the author "choose to edit" from the saved view,
while FLOW-029 (3) and FLOW-053 (7) read as forbidding any edit control on the
card. No requirement named the way into the editor (SD-031).

**Decision:** the card screen of a recipe that is still editable carries an edit
action in its top bar, outside the recipe's content. The recipe itself holds no
edit control and nothing on it becomes editable in place. A recipe that is no
longer editable has no such action. Deletion stays on the entry in the
own-recipes view (FLOW-036, FLOW-053) and never appears on the card screen.

**Consequences:** after saving, the author sees the reader's card (FLOW-029 (1))
and is one action away from correcting it. The card stays a read surface, so the
reader's view and the editor remain two screens. The bar holds one action that a
reader's card of someone else's recipe does not have.

**Alternatives considered:**
- *No way into the editor from the card; edit only from the own-recipes list.*
  Rejected: every typo found right after saving would cost a return to the list
  and a search for the entry.
