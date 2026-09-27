# Specification defects

What the specification gets wrong, as found at a gate. Append-only; an entry turns
resolved only on the user's word. Repairs happen in `spec/` through `/req-forge`.

### SD-001 — The words a nickname is drawn from are stated nowhere (2026-09-27)
**Requirement IDs:** AUTH-018, DOM-001
**Class:** omission
**What the gate hit:** T-005 generates the three-word nickname and carries the word
lists it is drawn from (`server/src/main/resources/nickname/**`). AUTH-018, DOM-001,
the glossary and the data model fix that a nickname is three generated words,
unique, permanent and the only public representation of an account; none says which
words. Unstated: the language and script the words are in, when the interface is
Russian and English (L10N-001 governs interface strings, not this stored value); and
which words or combinations are admissible, when a nickname can never be changed
(DOM-001) and is never reissued (RET-001). Whoever writes the lists makes that decision,
and it becomes irreversible with the first account created. The size of the lists
is OQ-009's question and is not restated here.
**Stopped:** T-005, T-009 (both rest on AUTH-018); tasks depending on T-005 wait
through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-002 — The rounding grid does not say which values it holds above 1000 g or above one unit, nor how a value reaches one (2026-09-27)
**Requirement IDs:** SCALE-001, SCALE-010 (the rounding grid of `spec/80-scaling-rules.md` they invoke); read against DOM unit-type table (`spec/50-domain-model/00-index.md`), SCALE-008, ADR 20260920-scaling-extremes
**Class:** ambiguity (items 1 and 2); omission (item 3)
**What the gate hit:** T-001 builds the grid itself (`core/.../recipe/Units.kt`,
`core/.../scaling/**`): SCALE-001 displays every quantity "rounded by the grid
above", and SCALE-010's third criterion requires a quantity over 1000 g to be
"displayed in kilograms by the rounding grid". Three things the grid does not
decide, each changing what a cook reads, and none exercised by any criterion, so
whoever writes the code would decide them and gate 2 could not tell:
1. **The values of the upper rungs of `exact`.** The grid states "whole grams or
   millilitres", then only the unit — "kilograms or litres at 1000 g or 1000 ml
   and above; tonnes or cubic metres at 1000 kg or 1000 l and above" — not the
   values a rung may hold. The DOM table says `exact` scales "linearly, no
   rounding loss", which reads as gram precision carried into kilograms (1500 g →
   1.5 kg, 1234 g → 1.234 kg); "whole grams" read as a pattern for every rung
   reads as whole kilograms (1500 g → 2 kg). The two differ by a third of the
   quantity, and decide whether SCALE-004 marks the value as rounded and in which
   direction.
2. **`household` and `taste_measured` at one unit and above.** "Whole units, and
   the fractions ¼, ⅓, ½, ⅔, ¾" reads either as mixed values (1½ tablespoons) or
   as the five fractions below one and whole numbers above (1.5 tablespoons shown
   as 1 or 2). The grid section says the serving count uses "the same fraction set
   as `household`" and shows fractions below one only, and the scaling-extremes
   ADR rejects "the fraction set at every magnitude, e.g. 2⅓" for the serving
   count as "a display rule distinct from SCALE-008" giving "two grids where one
   suffices" — which implies the second reading; cooking usage and the plural
   "halves" for divisible `piece` suggest the first. The same question applies to
   divisible `piece` above one (2½ pieces or not).
3. **How a computed value is mapped to a grid value.** "Nearest" is stated for the
   serving count (grid section, SCALE-008) and not for quantities; SCALE-004's
   criteria show quantities moving both up and down, which is consistent with
   nearest but does not state it, and no tie rule is stated for a value exactly
   between two grid values (4.5 g; 1.5 tablespoons under the second reading of
   item 2).
**Stopped:** T-001 (rests on SCALE-001 and SCALE-010). Also resting on the grid's
content: T-002, T-018, T-019, T-020, T-021; every task downstream of T-001 waits
through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-003 — The two entry points of scaling are not said to combine: a count changed while an ingredient is fixed, the scale left when the last fixing is released, and a serving count exactly between two values (2026-09-27)
**Requirement IDs:** SCALE-001, SCALE-002, SCALE-006, SCALE-008; read against SCALE-003, the glossary's "Fixed ingredient" (`spec/10-glossary.md`), FLOW-012 (1) and FLOW-017
**Class:** inconsistency (item 1); omission (item 2); ambiguity (item 3)
**What the gate hit:** T-002 builds the fixed-ingredient scale in the core
(`core/.../scaling/**`) on top of T-001's serving-count scale, and SCALE-006's third
criterion obliges it to model releasing a fixing. The section states "two entry
points ... both are required", and FLOW-012 (1) holds "a changed serving count with a
fixed ingredient set" as one state; no requirement says how the two combine once
both have been used:
1. **The count changed while an ingredient is fixed.** SCALE-001 is unconditional:
   "When the reader changes the serving count of a recipe, the system shall display
   every ingredient quantity multiplied by the ratio of the requested count to the
   authored count." SCALE-002 applies the fixed ingredient's factor "to the whole
   recipe" and its first criterion has the fixed line show "exactly" the fixed
   amount; SCALE-006 and the glossary make "the most recently fixed" ingredient the
   one that "sets the scale". Fix 300 g of chicken against an authored 500 g
   (factor 0.6), then ask for 6 servings of an authored 4: SCALE-001 displays the
   chicken at 750 g, SCALE-002 at 300 g. Nothing states that changing the count
   releases the fixing, that the count cannot be changed while an ingredient is
   fixed, or that the fixed ingredient then shows a shortfall as SCALE-006 does for
   a second fixing; each is a different card.
2. **The scale once the last fixing is released.** SCALE-006 (3) says what releasing
   the more recent of two fixings does — "the factor returns to the one implied by
   the chicken" — and nothing says what releasing the only remaining one does:
   candidates the text leaves open include the authored count, the count requested
   before the first fixing, the reader's usual count (FLOW-017), and the approximate
   count on display at the moment of release. SCALE-003 and SCALE-008 (5) bear on
   the last of these; which applies is not stated.
3. **A computed serving count exactly between two displayable values.** The grid
   section rounds the serving count "to the nearest value the set can express" and
   SCALE-008 takes "the nearest of the fractions"; no tie rule is stated. Reachable
   through T-002: 250 g fixed against an authored 400 g in a recipe for 4 computes
   2.5 servings (≈2 or ≈3); 25 g against 240 g computes 5/12, exactly between ⅓ and
   ½. SD-002 item 3 records the same gap for quantities only.
None of the three is exercised by a criterion, so whoever writes the core would
decide them and gate 2 could not tell (TEAM-008).
**Stopped:** T-002 (SCALE-002, SCALE-006, SCALE-008); T-020 (carries the card half
of the same three requirements and depends on T-002). T-021–T-025 and T-028 wait
behind T-020 through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-004 — Opening the favourites section is not said to show either view: which one is in force before the reader has chosen, and whether a person with no account is offered the own-recipes view at all (2026-09-27)
**Requirement IDs:** FLOW-034; read against FLOW-042 (4–5), FLOW-051 (6), ACT-001 (1, 5) and the permissions table of `spec/40-actors.md`
**Class:** omission (items 1 and 2)
**What the gate hit:** T-007 builds the favourites section, its two views and the
switch between them, and carries FLOW-034 (1) — "when it opens the section, then
both views are reachable from it and the switch between them is visible without
scrolling" — and FLOW-034 (5), which keeps "the view they were in" in force when the
reader returns within the same session. Opening the section has to show something,
and two things it shows are decided nowhere:
1. **The view in force when the section is opened and no view has been chosen.**
   FLOW-034 (1) makes both views reachable and (5) keeps a chosen one; neither, nor
   any other requirement, says which view the section opens on before the reader
   has chosen — the marked recipes or the own recipes — nor whether that first view
   is fixed or depends on what the account holds. The two differ on the first thing
   the reader sees: FLOW-042 gives the empty marked view an offer to open the
   catalogue (5) and the empty own-recipes view an offer to write a recipe (4). The
   specification treats a default of this kind as a requirement elsewhere: CAT-001's
   note of 2026-09-21 records that "the default ordering of the catalogue was left
   to whoever implemented it" as a gap and closes it.
2. **Whether a person with no account is offered the own-recipes view and the
   switch.** FLOW-034 speaks of "the recipes the account has marked, and every
   recipe the account owns". A person with no account has the favourites screen
   (ACT-001, FLOW-051 (6)) and owns nothing; FLOW-042 (4) gives the empty
   own-recipes view to "an account that has written nothing". Nothing says whether
   a device holding no account shows the own-recipes view (empty, with its offer to
   write a recipe) and the switch, or the marked view alone; the permissions table,
   which ACT-001 (5) makes the full enumeration of what such a person reaches, has
   no row for either. This reaches T-007 at once: the client holds no account until
   T-009 and T-010, which come after it, so the section T-007 builds is shown on
   devices without an account from the day it lands.
Whoever writes the section would decide both, and gate 2 could not tell (TEAM-008).
**Stopped:** T-007 (FLOW-034). T-008–T-026 and T-028 wait behind T-007 through the
backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-005 — A second timer on a step is not offered, yet the author is to be told why when they attempt one: no path to the attempt exists, and the statement is to be closed where a hint has nothing to dismiss (2026-09-27)
**Requirement IDs:** FLOW-027, GUIDE-007, DOM-013; read against GUIDE-005 and the ADR `spec/decisions/20260920-timer-named-by-its-step.md`
**Class:** inconsistency (item 1); ambiguity (item 2)
**What the gate hit:** T-014 builds attaching a timer inside a step in the editor
and carries FLOW-027 (1–7, 9, 11), DOM-013 (1–4) and all four criteria of GUIDE-007.
1. **The trigger GUIDE-007 fires on is one FLOW-027 removes.** FLOW-027's statement
   "shall not offer a second timer on a step that already carries one", and its
   second criterion: "Given a step that already carries a timer, when the author
   looks at it, then attaching another is not offered at all (DOM-013)." GUIDE-007's
   statement: "If the author attaches a timer to a step that already carries one,
   then the system shall state …", and its first criterion: "when the author
   attempts to attach another, then the statement is shown and no second timer is
   created". GUIDE-007's verification attempts a second timer "through every path
   FLOW-027 leaves open"; FLOW-027 and DOM-013 verify through "every path the
   editor offers". No requirement, and not the ADR, names any path by which an
   author can attempt to attach a second timer once the control is not offered —
   timers are attached inside a step (FLOW-027 (1)), move only with their step
   (FLOW-027 (10)), and steps are never joined (DOM-013 (3), GUIDE-004 item 2). Read
   one way, the editor has no such path, and GUIDE-007 (1), (2) and (4) describe a
   statement nobody can reach and gate 2 cannot observe. Read the other way, the
   editor must present something the author can act on for a second timer — a
   control shown and answered by the statement — which FLOW-027 (2) says is "not
   offered at all". Whether a visible control that only explains the refusal counts
   as "offered" is exactly what separates the two, and whoever writes the editor
   would decide it.
2. **Whether GUIDE-007's statement is closed.** GUIDE-007 (4): "Given the statement,
   when the author closes it, then the step is unchanged …". GUIDE-007 is a hint
   (the section places GUIDE-005–GUIDE-008 under hints), and GUIDE-005 (3), which
   governs every hint, says whether a hint is shown "is recomputed from the recipe
   and the author's action every time, and there is nothing to dismiss". A statement
   with a close action reads as something dismissed; read narrowly, "nothing to
   dismiss" means only that no dismissal is stored. The two readings produce
   different screens (a closable statement, or one that is present only while its
   trigger holds), and nothing states which.
Both reach the first screen T-014 adds to the step; neither is exercised so that
gate 2 could tell one build from the other (TEAM-008).
**Stopped:** T-014 (FLOW-027, GUIDE-007). T-015–T-017, T-021–T-025 and every task
behind them wait through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-006 — When the ingredient field "matches nothing" is not stated: what the dictionary search counts as a match, and whether free text is offered while entries are offered that are not the author's ingredient (2026-09-27)
**Requirement IDs:** FLOW-022, FLOW-023 (6), DOM-009 (2); read against FLOW-023 (2–4), the glossary's "Free text" and "Qualifier" (`spec/10-glossary.md`), OQ-019, OQ-020, L10N-007 and the ADR `spec/decisions/20260920-ingredient-dictionary.md`
**Class:** omission (item 1); ambiguity (item 2)
**What the gate hit:** T-012 builds the free-text path of the ingredient field, and
the trigger of its two central criteria is "the dictionary offers nothing that
matches" (FLOW-022 (1)) and "nothing in the dictionary matches" (FLOW-023 (6));
DOM-009 (2) and the glossary store free text for an ingredient named "because no
entry matched". Two things that trigger rests on are decided nowhere:
1. **What counts as a match in the ingredient field.** No requirement states how
   typed text is compared with entry labels — whole label or beginning of a word,
   one word of several or all of them, letter case and `ё`/`е`, word forms, and
   the labels of one interface language or of both (L10N-003 gives every entry a
   label in each). L10N-007 decides these for a reader's text query over recipes
   (L10N-006) and not for the dictionary. The rule fixes when the free-text offer
   appears: for `кокосовое молоко` against a dictionary holding `молоко` and no
   coconut milk, a rule matching any one word offers `молоко`, and a rule matching
   the whole label offers nothing.
2. **Whether free text is reachable while entries are offered.** "Nothing matches"
   reads either as (a) the search returns no entry — the offer appears only on an
   empty list — or (b) no entry is the ingredient the author means — the offer
   stands beside the entries and the author judges. Under (a) and any rule looser
   than exact equality, the author of `кокосовое молоко` is offered `молоко` and
   no free text, so the remaining route is `молоко` with the qualifier
   `кокосовое`, which a filter on `молоко` then matches (CAT-006), permanently once
   published (CON-003); FLOW-023 (3) and OQ-020 call the qualifier "a last resort
   and not a substitute for picking the right entry", and FLOW-022's source and
   the ADR say a missing entry never blocks authoring. Under (b) free text is
   available when an entry does match, which the conditional wording of FLOW-022,
   FLOW-023 (6), DOM-009 (2) and the glossary does not describe, and which moves
   the fraction of free-text ingredients OQ-019 names as the operational signal.
   The two are different screens.
Whoever writes the field would decide both, and gate 2 could not tell a right
reading from a wrong one (TEAM-008). Item 1 also underlies FLOW-023 (2) and (3),
carried by T-011; that task's own gate decides whether it stops there.
**Stopped:** T-012 (FLOW-022, FLOW-023 (6), DOM-009 (2)). T-013–T-017, T-021–T-025 and
T-028 wait behind T-012 through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-007 — The recovery key is presumed to be offered for saving, but no requirement says whether or how, nor on which screen it is shown (2026-09-27)
**Requirement IDs:** AUTH-020 (1, 2); read against AUTH-007 (statement, 1, 2, 4, 6, 7), AUTH-008 (1), EXT-008, AUTH-021, the glossary's "Recovery key" (`spec/10-glossary.md`) and ADR 20260921-the-recovery-key
**Class:** ambiguity (items 1 and 2)
**What the gate hit:** T-010 builds the part of account creation that issues the
recovery key and carries AUTH-020 (1) and (2). Two things the person sees are
decided nowhere, and whoever writes the screen would decide them:
1. **Whether, and how, the recovery key can be saved.** AUTH-020 (1) requires that
   the recovery key "is not offered to be saved in the same action as the account
   key", which presumes some offer to save it; AUTH-020 (2) has the person confirm
   "they have saved it". Nothing states what that offer is. For the account key
   the choice was made explicitly — AUTH-007 offers "both to copy it and to save it
   as a file", with the reason that "the two ways people keep a secret are a
   password manager and a file". For the recovery key the statement, the glossary
   and the ADR say only that it is shown once in full, and the ADR's "a key that
   can never be seen again is indeed lost with the note it was written on" reads
   as writing it down. Three readings fit the text: display only (the key — 256
   bits in base32 under the binding architecture ADR, some fifty characters — is
   transcribed by hand, and AUTH-020 (1) is met vacuously); a copy control; a file
   of its own. They differ in what the person can do on the screen and in how
   easily the key lands beside the account key, which is the one outcome the ADR
   says the scheme cannot survive and the product "can say so but cannot
   enforce". AUTH-008 (1) — no screen "offers to keep, back up or send the key
   anywhere" — is squared with AUTH-007's copy and file only because AUTH-007
   states them; no requirement states them for this key. The answer also reaches
   wave 7: EXT-008 accepts the account key from any file the device can read and
   says nothing of the recovery key, so whether the screen of AUTH-021 accepts a
   file follows from this choice. At gate 2 a copy control or a file offer would be
   behaviour no requirement states, while its absence leaves AUTH-020 (1) with
   nothing to test; either way the gate could not tell a right screen from a wrong
   one (TEAM-008).
2. **Where the recovery key is shown.** AUTH-020 (1) presents it "on its own" and
   (2) speaks of "that screen"; AUTH-007 (7) lists the recovery key's confirmation
   among those "the creation screen" requires, beside the account key's and the
   terms'. Whether the recovery key and its confirmation sit on the creation
   screen, set apart from the account key, or on a screen of their own — and, if
   on their own, whether before or after the account key is confirmed, and whether
   an ordinary back action from it returns to the account key shown in full
   (AUTH-007 (4), (6)) — is not stated. The backlog entry's title answers it ("on
   its own step"); that is the task deciding, not the specification.
**Stopped:** T-010 (AUTH-020). T-022, T-026 and the tasks behind them (T-023–T-025,
T-028) wait through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-008 — The ingredient row's unit field is not specified: which units it offers, whether it stays once "to taste" is picked, and whether a leaf is offered because its parent matched (2026-09-27)
**Requirement IDs:** FLOW-023 (1, 3, 7, 8), DOM-002 (1); read against L10N-008, the unit-type table of `spec/50-domain-model/00-index.md`, the glossary's "Unit type" (`spec/10-glossary.md`) and OQ-020. SD-006 item 1 (what the ingredient field counts as a match) also reaches FLOW-023 (2), (3) and FLOW-001 (4) here and is cited, not restated.
**Class:** ambiguity (item 1); omission (item 2); inconsistency (item 3)
**What the gate hit:** T-011 builds the three fields of an ingredient row and carries
FLOW-023 (1–4, 7–9) and DOM-002 (1). Three things the row cannot be built without
are decided nowhere:
1. **Whether a leaf "could match" through its parent.** FLOW-023 (3): "when both a
   leaf and its parent could match what the author typed, then the leaves are
   offered before the parent, so that a qualifier is a last resort and not a
   substitute for picking the right entry". Read as each entry matched on its own
   label, a leaf and its parent match together only where the typed text is in
   both labels — `крылышки` and `курица` share none — and the criterion rarely
   fires. Read as matching through the family, typing `курица` offers `крылышки`,
   `филе` and the other leaves first, which is what the criterion's purpose and
   OQ-020 ("the editor offers leaves before parents") describe. The two readings
   give different lists on the same keystrokes, and no text-comparison rule of the
   kind SD-006 item 1 asks for settles it.
2. **Which units the unit field offers, and in which unit type each sits.** The
   domain model's unit-type table heads its units "Examples"; the glossary lists
   `household` (teaspoon, tablespoon, cup) and `taste_measured` (a pinch) in
   parentheses; L10N-008 (1) requires a unit to be "one of those DOM-002 defines",
   and its source says DOM-002 admits the `exact` units "alongside pieces and
   household measures and nothing else", while DOM-002 itself names no unit. The
   only `piece` unit named anywhere is `шт` (FLOW-023 (1), (7)), although the same
   table gives "garlic clove" as a `piece` example. Whoever writes the field decides
   whether a cup, a glass, a bunch, a clove or a can can be written at all, and to
   which unit type each belongs — which is what FLOW-023 (7) makes the author never
   see and scaling then applies.
3. **Whether the unit field stays once "to taste" is picked in it.** FLOW-023 (8):
   "when the author picks the unit that means "to taste", then the quantity field
   is withdrawn" — the choice is made in the unit field and only the quantity field
   is withdrawn. DOM-002 (1), for the same `taste_unmeasured` ingredient: "the
   editor offers no field for either" quantity or unit. A row that keeps its unit
   field showing "to taste" fails DOM-002 (1) as written; a row that drops it has
   no stated way back to a measured unit and no longer holds the three fields of
   FLOW-023 (1). Each requirement is consistent on its own; they meet on this row.
Whoever writes the row would decide all three, and gate 2 could not tell a right
build from a wrong one (TEAM-008).
**Stopped:** T-011 (FLOW-023, DOM-002). T-012 is also stopped by SD-006; T-013–T-017,
T-021–T-025 and T-028 wait behind T-011 through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-009 — The creation screen "cannot be left by an ordinary back action", and nothing says how a person who has not confirmed leaves it (2026-09-27)
**Requirement IDs:** AUTH-007 (4, 7); read against AUTH-007 (8), AUTH-001 (2, 7) and ADR 20260921-nothing-exists-until-it-is-confirmed
**Class:** ambiguity (item 1); omission (item 2)
**What the gate hit:** T-009 builds the creation screen and carries AUTH-007 (4):
"Given the confirmation control, when it is not chosen, then creation does not
complete and the screen cannot be left by an ordinary back action." AUTH-007 (7)
(carried by T-010) and the ADR are written for a person who "leaves it without
giving every confirmation it requires", and the ADR's reason for creating nothing is
that such a person — "a person who tapped twice and changed their mind" — must be
able to walk away at no cost. Leaving before confirming is therefore meant to be
possible; by what means is not stated, and the screen T-009 builds depends on it:
1. **What an "ordinary back action" is.** Whether it covers the system back gesture
   and button only, or the screen's own up/close control too; and whether a back
   action that first asks the person to confirm leaving without an account, and
   leaves on that confirmation, is still "ordinary" (and so forbidden) or is the
   intended way out.
2. **Whether the screen offers a way out of its own, and where it leads.** No
   requirement states a control that abandons creation, what it tells the person
   (AUTH-007 (8): a key copied before leaving then matches nothing), or where the
   person lands — back at what they were doing, as AUTH-001 (7) says of a person
   who "declines the offer", whether or not the creation screen is that offer.
   Without such a control the only exits are the system's home and task switcher,
   and nothing says whether the creation screen is still there when the person
   returns.
The readings give different screens — back swallowed with no exit but leaving the
application; an explicit abandon control; back guarded by a confirmation. At gate 2
an abandon control would be behaviour no requirement states, and its absence could
trap a person who opened creation by mistake; either way the gate could not tell a
right screen from a wrong one (TEAM-008). SD-007 item 2 asks a neighbouring question
— whether a back action from the recovery key returns to the account key — and does
not cover this one.
**Stopped:** T-009 (AUTH-007 (4)). T-010 carries AUTH-007 (7), whose trigger is this
leaving, and waits behind T-009 through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-010 — AUTH-018's heading reads as one reroll; its statement, criterion and verification say any number (2026-09-27)
**Requirement IDs:** AUTH-018 (heading, statement, criterion 2, verification); read against the glossary's "Nickname" (`spec/10-glossary.md`), DOM-001 (2) and ADR 20260920-account-on-demand
**Class:** ambiguity
**What the gate hit:** T-009 builds the control that replaces the shown nickname
and carries AUTH-018 (2). The requirement's heading is "The nickname is generated
and may be rerolled once, before the account exists"; its statement says the person
may replace it "any number of times before the account is created", criterion 2 has
no bound, the verification says "reroll several times", and the glossary repeats
"any number of times". The heading reads two ways: "once" as a count — a single
reroll, after which the control is gone — which contradicts the statement; or "once"
as the single moment at which rerolling is possible, which agrees with it. Whether
the control survives its first use is visible on the screen, and a developer agent
handed AUTH-018 receives the heading with the rest. The gate does not choose between
the readings.
**Stopped:** T-009 (AUTH-018 (2)).
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
