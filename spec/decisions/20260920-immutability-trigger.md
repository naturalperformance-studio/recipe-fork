# Immutability starts at external visibility, not at submission

**Status:** accepted — refines CON-003

**Context:** The draft (section 4.2) freezes a recipe as soon as it becomes
"accessible to anyone other than its owner". Taken literally, a moderator
opening a submission satisfies that, which would freeze a recipe the moment it
entered the queue and keep it frozen after a rejection — the draft's section 6.4
says exactly that: a rejected recipe can only be forked.

That reading costs the author a duplicate in the catalogue for every typo a
moderator catches, and it makes an accidental submission unrecoverable for up to
a week.

**Decision:** A moderator acting in role is not "someone other than the owner"
for the purposes of the immutability rule. A recipe freezes permanently when it
becomes visible to an ordinary user — when a private link is issued or when it
is published. While it is `pending` it is frozen so that the moderator reviews a
stable text, and it becomes editable again if the submission is withdrawn or
rejected.

**Consequences:**
- An author fixes what a moderator objected to in place and resubmits. No
  duplicate enters the catalogue because of a rejection.
- An accidental submission is recoverable: withdraw, edit, resubmit.
- A rejection is recorded against the submission, not against the recipe, so the
  recorded reason may refer to text that has since changed. The moderation
  history therefore shows decisions attached to submissions, with their dates.
- CON-003 is unchanged in substance and is read through this refinement: the
  principals whose access freezes a recipe are ordinary users, not moderators.

**Alternatives considered:**
- *Literal reading: frozen from submission onwards, rejected recipes forkable
  only.* Rejected by the product owner. It keeps every moderator decision tied
  to an immutable text, at the price of duplicates and an unrecoverable
  misclick.
- *Withdrawal allowed but the recipe stays frozen.* Rejected: it removes the
  recipe from the queue without giving the author any way to act on it.
