# A timer is named by its step, and a step carries one timer

**Status:** accepted

**Context:** FLOW-016 required a timer to be identified by what it is for, and the
domain model gave `Timer` an optional `label` for that, falling back to the
step's position when the author left it empty. That fallback was the hole: two
unlabelled timers in one recipe announce themselves as "step 3" and "step 7",
which tells a cook with dirty hands nothing about which pan. Closing it needed a
name that is always present and that the author does not have to type twice.

**Decision:** a timer's name is derived from the text of the step it belongs to
and is never asked for. A step carries at most one timer (DOM-013), so the
derived name is unambiguous. An announcement carries the recipe's title and the
first 60 characters of the step's text, cut at a word boundary (FLOW-016). A step
has no title.

**Amended the same day, 2026-09-20:** the derived name is a default, not a fixed
rule. `Timer.label` stays in the domain model as an override the author may set
and clear, bounded by the same 60 characters (DOM-012). It is absent unless the
author set one, and while it is absent the name follows the step's text. The
product never asks for it: the editor shows the derived name and offers to
correct it (FLOW-027), and GUIDE-006 offers the same correction at the moment it
matters. The amendment was made because the opening of a step is not always the
point of its wait, and the author who can see that is the one who should be able
to fix it. Nothing else in the decision changed: no fallback to a step's
position, no step title, one timer per step.

**Consequences:**
- The author types the name once, as the step they were writing anyway: `курица в
  духовку на 30 минут` is both the instruction and the announcement.
- Nothing can be left unnamed, so the step-position fallback disappears entirely.
- Which 60 characters the author writes first now matters, and the author is
  taught that rather than left to discover it — see `author-guidance`.
- A step needing two waits in sequence becomes two steps. The product does not
  join them back together and offers no way to express both in one step.
- Two steps of one recipe whose first 60 characters agree produce identical
  announcements. The product does not disambiguate them.
- A step longer than 60 characters whose decisive words are at the end announces
  its opening instead, unless the author names the timer. Nothing refuses such a
  step, because a step is text for a reader first and a notification second;
  GUIDE-006 shows the author the result and offers the correction.
- A name the author set stops following the step. Editing the step afterwards
  leaves the name as it was, and the product does not warn about the divergence —
  that is what overriding means, and detecting a stale name would mean judging
  whether a name still describes a step.
- An author who sets a name is typing a fact the step already carries, so the
  override is worth offering but not worth prompting for: it is shown as a
  correction to something already filled in, never as an empty field.

**Alternatives considered:**
- *Keep `Timer.label`, optional, with the step's position as fallback.* Rejected:
  the fallback is exactly the case where the cook has to guess. The amendment
  keeps the field but not the fallback — an unset name derives from the step, and
  a position is never announced.
- *Copy the step's first 60 characters into `label` when the timer is attached.*
  Rejected: the copy goes stale the moment the step is edited, silently, and the
  author would have two places holding one fact with no sign of which is true.
  Deriving the name until it is overridden keeps one source until the author
  deliberately makes a second.
- *Keep `Timer.label` and require it once a recipe holds two or more timers.*
  Rejected after being provisionally accepted on 2026-09-20: the label an author
  would type is the sentence they had already written in the step, so the field
  buys nothing but a second place to keep it true.
- *Give every step a mandatory title and let the timer take it.* Rejected: it
  taxes every recipe, including those with no timer at all — up to 50 titles
  under DOM-012 — and most titles would be a restatement of the step's own first
  words.
- *Give every step an optional title, with the step's position as fallback.*
  Rejected for the same reason as the optional label: an optional name is absent
  exactly when the recipe is careless, which is when the cook needs it.
- *Several timers per step, each with its own label.* Rejected: it re-introduces
  the label field for a case the author can already express as two steps, and two
  waits in sequence are two steps in the kitchen.
- *Send the whole step text and let Android truncate it.* Rejected: what the cook
  sees would then depend on the firmware and on whether the shade is expanded,
  and the requirement would have nothing to verify.
