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
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

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
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

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
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

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
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

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
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

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
**Resolved:** 2026-09-27 — «И помечай эти пункты как закрытые» (re-checked against spec/ @ 4cc2c18)

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
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

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
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

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
**Resolved:** 2026-09-27 — «И помечай эти пункты как закрытые» (re-checked against spec/ @ 4cc2c18)

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
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

### SD-011 — SCALE-006 calls the scale factor "displayed", and no requirement says the reader is shown one (2026-09-27)
**Requirement IDs:** SCALE-006 (1); read against SCALE-002 (statement), SCALE-006 (statement, 3), SCALE-008 (5) and the glossary's "Fixed ingredient" (`spec/10-glossary.md`)
**Class:** ambiguity
**What the gate hit:** T-020 builds what the card shows while an ingredient is fixed
(the approximate serving count, the shortfall, the floor warning). SCALE-006 (1):
"… then the displayed factor is 1.0 and the chicken line shows a requirement of 500 g
against 300 g available." It is the only sentence in `spec/` that calls the scale
factor displayed. SCALE-002's statement displays "the resulting serving count as an
approximation while keeping the exact factor for computation"; SCALE-006's statement
"adopt[s] the factor"; SCALE-006 (3) has the factor "return"; none shows a factor to
the reader. Read one way, "the displayed factor" is the factor the view is displayed
at — an internal value, observable only through the quantities — and the card shows
no factor. Read the other way, the card shows the reader the factor in force (1.0,
0.6), which no statement, no other criterion and no task carries. The two are
different cards: a factor readout on the card is behaviour gate 2 must either
require or reject as an addition, and the text does not say which (TEAM-008). Noted
first at T-002's gate (docs/product/verdicts/20260927-T-002-gate1.md) and left for the
card's gate; T-002 carries the criterion, T-020 the card it would appear on.
**Stopped:** T-020 (also stopped by SD-003). T-002 carries SCALE-006 (1) and is already
stopped by SD-003.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

### SD-012 — SCALE-004's second criterion states the wrong direction, and a scaled quantity displayed at its authored value is not said to be derived or not (2026-09-27)
**Requirement IDs:** SCALE-004 (statement, 1, 2), CON-004 (statement, 1, 2); read against SCALE-004 (3), SCALE-005 (statement, 1), SCALE-001 (4), FLOW-017 (1) and the glossary's "Derived" (`spec/10-glossary.md`)
**Class:** incorrect fact (item 1); inconsistency (item 2)
**What the gate hit:** T-019 builds the derived marker and the rounding direction on
the card and copies these criteria verbatim (C5), so it cannot be corrected at the task.
1. **SCALE-004 (2) names the opposite direction.** "Given 3 indivisible pieces at a
   factor of 0.6, when the line is displayed, then it shows 2 pieces and states that
   the exact value was higher." The exact computed value is 3 × 0.6 = 1.8, which is
   lower than the 2 shown. CON-004 (2) requires the interface to state "whether the
   displayed value is above or below the exact computed value", and SCALE-004 (3)
   (1 teaspoon × 0.2 = 0.2, ¼ shown, "the exact value was lower") uses the same
   convention correctly. An implementation that states the true direction fails
   SCALE-004 (2) as written; one that meets SCALE-004 (2) tells the cook the opposite
   of what CON-004 (2) requires. Gate 2 can mark this criterion met under neither.
   Noted first at T-001's gate (docs/product/verdicts/20260927-T-001-gate1.md) and
   left for this one.
2. **A quantity computed at another scale that lands on its authored value.** Two
   families of wording decide marking differently. By provenance: CON-004's
   statement marks "a quantity it derived rather than the quantity its author
   entered", and CON-004 (2) states the direction of "a derived quantity that was
   rounded". By value: SCALE-004's statement marks "where a displayed quantity differs
   from the authored quantity", SCALE-004 (1) marks "every changed quantity", CON-004 (1)
   marks "every quantity that differs from the authored quantity", and the glossary
   defines derived as "any quantity a reader sees that is not the authored one". They
   part where scaling and rounding return the authored number. Reachable in T-019 with
   no grid question involved: a recipe authored for 4 with 1 indivisible egg, read at
   2 servings (by the control, or on opening under FLOW-017 (1)) — the computed 0.5 is
   held at the floor, and SCALE-005 fixes the line at 1 egg and marks it "rounded up".
   By value the line is unchanged and carries no derived marker (and it is not said
   whether SCALE-005's rounded-up marking then appears without one); by provenance it
   is derived and states that the exact value was lower. The same split applies to
   any value the grid rounds back onto its authored number. SCALE-001 (4) and
   CON-004 (3) settle only the authored count itself, where both readings agree.
   Neither case is exercised by a criterion, so whoever writes the card would decide
   it and gate 2 could not tell (TEAM-008).
**Stopped:** T-019 (CON-004, SCALE-004). T-020, T-021 (whose reference carries "the
same derived marker the list carries"), T-022–T-025 and T-028 wait behind T-019
through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

### SD-013 — The authored serving count's admissible values are stated nowhere: whole or fractional, its floor, zero, and its ceiling (2026-09-27)
**Requirement IDs:** FLOW-002 (1), DOM-006 (statement); read against the `servings` field of the domain model (`spec/50-domain-model/00-index.md`: "number"), the glossary's "Authored serving count" and "Requested serving count" (`spec/10-glossary.md`), SCALE-001 (statement), SCALE-011 (statement, 1, 2), the serving-count paragraph of the rounding grid (`spec/80-scaling-rules.md`), FLOW-017 ("when the reader returns it to the authored count"), CON-004 (3) and DOM-012
**Class:** omission
**What the gate hit:** T-013 is the one wave-1 task that obliges the editor to take
an authored serving count: FLOW-002 (1) — "when the owner creates a recipe and fills
its title, servings, ingredients and steps, then every change is kept" — and no other
wave-1 criterion names the field (FLOW-024 (1), which lists the serving-count part,
is now in wave 5). What that field accepts is decided nowhere. The domain model types
`servings` as "number"; the glossary defines it only as "the number of servings the
author wrote the quantities for"; DOM-006 requires one to be present at submission
and DOM-012 sets no limit on it. For the *requested* count the product owner decided
the form explicitly — SCALE-010 and SCALE-011: a floor of ¼, below one only ¼, ⅓, ½,
⅔, ¾, at one and above whole numbers only (OQ-010, OQ-011). No such decision exists
for the authored count, and four things about it are open:
1. whether it may be fractional (1½, 2.5) or only whole;
2. whether it may be below one, and if so in which forms;
3. whether zero is accepted — SCALE-001 divides by it ("the ratio of the requested
   count to the authored count"), so an authored 0 makes every scaled quantity
   undefined;
4. whether it has a ceiling (SCALE-010 gives the requested count none).
The choice is visible on the editor and on the card. An authored count outside the
set SCALE-011 lets a reader request (2.5, 0.3) cannot be requested back: the
serving-count paragraph displays the count "at or above one serving … as a whole
number", and the control of SCALE-011 (2) "can hold whole numbers only", while
FLOW-017 has the reader "return it to the authored count" and CON-004 (3) and
SCALE-001 speak of the recipe displayed at its authored count. Read as "any number",
the field admits 0 and values the card cannot show as themselves; read as the
requested count's set, the text nowhere says so. Whoever writes the field would
decide it, and gate 2 could not tell a right field from a wrong one (TEAM-008).
**Stopped:** T-013 (FLOW-002 (1)). T-014–T-017, T-021–T-025 and T-028 wait behind T-013 through the
backlog's dependencies. The authored count is also the divisor of T-001 (SCALE-001,
already stopped by SD-002) and the count T-018's card opens at; their own gates decide
whether they stop here.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

### SD-014 — A reference may point at an ingredient that holds no quantity, and nothing says what it shows or whether it survives (2026-09-27)
**Requirement IDs:** FLOW-031 (1, 3, 4), FLOW-032 (statement, 5); read against DOM-002 (1, 2), DOM-007 (statement), FLOW-023 (8) and the glossary's "Ingredient reference" (`spec/10-glossary.md`)
**Class:** omission
**What the gate hit:** T-015 builds inserting a reference into a step and what the
editor shows in its place. FLOW-031 (3) keeps out of the offered list only an
ingredient "whose unit type is `taste_unmeasured`, because such an ingredient carries
no quantity to show (DOM-002)". Two other ingredients carry no quantity to show, and
no requirement says what happens with either:
1. **An ingredient of a measured type whose quantity is not entered yet.** A draft
   may hold one — DOM-002 (2) refuses it only when the recipe is submitted — and
   FLOW-031 (1), (3) offer it. FLOW-032 (5) has the editor show "the authored
   quantity" in place of the reference, and FLOW-032's statement renders a reference
   as "quantity and unit"; there is neither.
2. **An ingredient already referenced whose unit the author changes to "to taste"**
   (FLOW-023 (8)). It then holds "no quantity and no unit" (DOM-002 (1)) — the very
   ingredient FLOW-031 (3) would never have offered — while the reference to it still
   resolves within the recipe (DOM-007). Only deletion of the ingredient is said to
   drop a reference (FLOW-031 (5), DOM-007 (2)).
In both cases a reference that shows nothing occupies no character range, so FLOW-031
(4)'s way of removing it — deleting "the character range it occupies" — has nothing
to act on. Whether such an ingredient is offered, whether the reference is kept,
dropped or shown some other way, and whether the unit change is allowed while a
reference exists, are observable choices; each reading of the requirements is
consistent on its own, and whoever writes the editor would pick one where gate 2
could not tell a right build from a wrong one (TEAM-008). The same reference is what
the card renders at the reader's scale (FLOW-032 (1–4), DOM-007 (1)), so a draft
opened as the card meets the same gap.
**Stopped:** T-015 (FLOW-031, FLOW-032 (5)). T-021 carries FLOW-032 (1–4) for the same
reference and waits behind T-015 through the backlog's dependencies; its own gate decides
whether it stops here. T-015 is also behind T-014 (SD-005) and T-011–T-013 (SD-006,
SD-008).
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

### SD-015 — What a long step's notification "will say" is not one text when its opening holds a reference, and GUIDE-006 does not say at which serving count it is shown or counted (2026-09-27)
**Requirement IDs:** GUIDE-006 (statement, 1, 2, 4); read against FLOW-016 (3, 4, 8), FLOW-032 (1, 3, 5), FLOW-031 (1), GUIDE-004 (item 4) and GUIDE-008
**Class:** ambiguity (item 1); inconsistency (item 2)
**What the gate hit:** T-017 builds GUIDE-006's display in the editor, after T-015 has
put references into steps (FLOW-031, FLOW-032 (5)), and copies GUIDE-006's criteria
verbatim (C5), so it cannot be corrected at the task. GUIDE-004 (item 4) and GUIDE-008
direct the author to write a quantity in a step as a reference, so an unnamed timer on
a step that opens with one — `всыпьте [50 г] сахара …` — is the case the product
itself encourages, not an edge.
1. **Which text is shown.** FLOW-016 (8) renders a reference inside a derived name
   "exactly as it reads on the card, at the scale the reader is using"; FLOW-032 (1)
   makes that differ from the authored quantity whenever the reader changes the
   serving count, and FLOW-032 (3) adds the derived marker where the grid rounded it.
   FLOW-032 (5) has the editor show the authored quantity. For such a step the
   notification's text is therefore one text per serving count, not one text.
   GUIDE-006 (1) shows "the text the notification will carry" and GUIDE-006 (2)
   "states that this is what the notification will say"; neither says at which
   serving count the reference inside the display is rendered, nor whether the
   display says that the text changes with the reader's serving count. Rendering at
   the authored count shows the author a text that a reader at another count does not
   get while the display states that it is what the notification will say; showing
   anything else is a display no criterion describes.
2. **Whether anything is shown.** GUIDE-006's statement fires on a step "whose text
   holds more than 60 characters", and GUIDE-006 (4) shows nothing for a step of 60 or
   fewer "because nothing will be cut". A reference's rendered length changes with the
   serving count (`50 г` authored for 4 reads `100 г` at 8, and carries the derived
   marker where rounded), and FLOW-016 (3)'s word-boundary cut moves with it. A step
   of 59 characters at the authored count is cut in the notification of a reader at
   8 servings (FLOW-016 (3), (8)), so GUIDE-006 (4)'s reason is false for it, and the
   step gets no display although its announcement will be cut. Nothing says on which
   text — rendered at which serving count — the 60 characters of GUIDE-006's trigger
   and of criterion (4) are measured.
Whoever writes the display would choose both, and gate 2 could not tell a right choice
from a wrong one (TEAM-008). FLOW-016 itself is not questioned here: at a reader's
given serving count it reads determinately (render the reference, then cut); the gap is
only in what the editor shows the author, before any reader has chosen a count.
**Stopped:** T-017 (GUIDE-006). T-017 also waits behind T-016 → T-015 → T-014 and the
tasks before it, which SD-004, SD-005, SD-006, SD-008, SD-013 and SD-014 stop; those
are not this entry's ground.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

### SD-016 — "The narrowest supported screen width" is a figure stated nowhere, and the digit grouping SCALE-012 hands to `localization` is not there (2026-09-27)
**Requirement IDs:** SCALE-012 (1, 2, verification, note); read against QUAL-005, the device classes of `spec/140-quality.md`, section `localization` (L10N-001–L10N-008, `spec/160-localization.md`) and A11Y-004
**Class:** omission (items 1 and 2)
**What the gate hit:** T-018 builds the card's ingredient lines and carries SCALE-012
whole. Two things the lines depend on are referred to by the specification and stated
nowhere in it:
1. **The width SCALE-012 (1) is measured at.** "Given an ingredient displayed as
   10 000 000 pieces, when its line is shown on the narrowest supported screen width,
   then every digit is visible and no ellipsis replaces part of the number"; SCALE-012
   (2) is "that same line", and the verification renders "on the narrowest supported
   width". No requirement, constraint, glossary entry or ADR states a supported screen
   width. QUAL-005 fixes the lowest Android version and says nothing about screens;
   the `modest` and `typical` classes of `spec/140-quality.md` name phones by
   version, memory and age, not by width. Readings the text leaves open include the
   smallest screen a device running Android 10 may have (a platform figure, not in
   `spec/`), the narrowest window the application may be given when the screen is
   shared with another application (whether that is supported is itself not stated),
   and a figure the product chooses. They differ on the one condition the criterion is
   about — whether an eight-digit quantity and a name fit on one line or the name must
   wrap — and A11Y-004's 200 per cent font scale, which holds on the card too, narrows
   the line further at every one of them. Whatever width a test uses, gate 2 cannot
   tell whether it is the narrowest supported, so the criterion would be met by the
   choice of width rather than by the card.
2. **How a large number's digits are grouped.** SCALE-012's note: "how the digits of
   a large number are grouped is a localization matter and belongs to section
   `localization`." That section holds L10N-001 to L10N-008 and none of them says how
   digits are grouped in either language, nor which mark separates a whole number from
   its fraction; L10N-008 governs units only and states that it "adds no decision".
   The form in which 10 000 000 reaches a Russian and an English reader — a space, a
   comma, a narrow space, no grouping — is therefore decided by whoever writes the
   line, on the screen the product exists for, and gate 2 has nothing to hold it
   against (TEAM-008). Whether a quantity above 1000 g carries a fractional part at
   all is SD-002 item 1 and is cited, not restated.
**Stopped:** T-018 (SCALE-012). T-018 is also stopped by SD-002 and SD-013. T-019–T-026
and T-028 wait behind T-018 through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

### SD-017 — The fork notice of FLOW-008 is tied to opening the application, which automatic sync does not wait for, and FLOW-005 does not say where an accepted recipe is marked (2026-09-27)
**Requirement IDs:** FLOW-008 (statement, 2), FLOW-005 (statement, 2, 4); read against FLOW-002 (4), FLOW-036 (6), FLOW-029 (the card of an own recipe, cited by FLOW-053) and the binding ADR `docs/architecture/decisions/20260927-server-authoritative-sync-with-outbox.md` (outbox drained "whenever a connection exists")
**Class:** ambiguity (items 1 and 2)
**What the gate hit:** T-028 carries FLOW-008 (2) and FLOW-005 (1), (2), (4) verbatim
(C5), so neither gap can be corrected at the task.
1. **When, and how often, the owner is told of a divergence fork.** FLOW-008's
   statement "shall tell the owner that it did so", unconditionally. Its criterion (2)
   names one trigger: "when the owner opens the application on device B, then they are
   told a fork was created and why". FLOW-002 (4) sends offline changes "without the
   owner asking for it" when a connection returns, which happens as often while the
   application is open as while it is closed; the fork is then created during a
   session the owner has already opened, and the criterion's trigger does not occur
   until the next launch. Nothing says whether the owner is told at that moment, at
   the next opening, or both. Nor does anything bound the telling: read literally,
   every opening of the application on device B after the outcome tells them again,
   and no criterion says whether the notice stays until the owner acknowledges it,
   is shown once, or stops at some other point. An implementation that tells the owner
   the moment the server answers and never again, and one that tells them at every
   launch, both read as conforming to one sentence and failing the other.
2. **Where a fully accepted recipe is marked.** FLOW-005's statement displays the
   state "for each recipe the account owns" and names no surface. Criterion (1) names
   the own-recipes list and criterion (2) the editor, the latter only for a recipe
   that holds unaccepted changes. Criterion (4) — "Given a recipe fully accepted by
   the server, when it is displayed, then it is marked as accepted" — names none. An
   own recipe is also displayed on its card (FLOW-029; FLOW-053 cites "the recipe card
   of an own recipe") and, once published, in the catalogue. Read as the surfaces of
   (1) and (2), the accepted mark is on the list entry and in the editor; read as
   "whenever it is displayed", it is also on the own recipe's card and on every
   catalogue entry of an own published recipe; read narrowest, the editor need carry
   no positive mark at all, because (2) speaks only of unaccepted changes. Which
   surfaces carry the mark is visible to the owner and is decided nowhere.
Whoever builds T-028 would choose both, and gate 2 could not tell a right choice from a
wrong one (TEAM-008). FLOW-005 (1), FLOW-036 (6) and FLOW-002 (4) read determinately on
their own and are not questioned here.
**Stopped:** T-028 (FLOW-008 (2), FLOW-005 (2), (4)). T-028 also waits behind T-027,
T-017, T-026 and T-025, and through them behind the tasks SD-002–SD-016 stop; those are
not this entry's ground.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

### SD-018 — FLOW-008 does not say whether an offline edit to a recipe whose state, but not whose content, changed on the server is "divergent" (2026-09-27)
**Requirement IDs:** FLOW-008 (statement, 1, 3, 4); read against CON-003 (1), ST-003 (statement, 1), ST-009 (statement), FLOW-002 (2, 4), the state table of `spec/60-entity-states.md` and the glossary's "Frozen" (`spec/10-glossary.md`)
**Class:** ambiguity
**What the gate hit:** T-027 builds the server side of FLOW-008 and CON-003 (1): it
decides, for every edit a device sends late, whether it is stored, refused, or stored
as a `private` fork. FLOW-008 gives "divergent" two readings that part on one case.
The statement fires when "the same account edits one recipe on two devices while at
least one of them is offline, so that the local version and the version the server
holds have diverged" — an edit on each side. Criterion 4 glosses the opposite as
"no divergence — device B's version is the one the server already holds", which
makes any difference between the two versions a divergence. The case where they
part: device B edits a recipe offline; on the server nobody edited its content, but
it was submitted and has since become `pending`, `link_shared` or `published`.
Under the gloss, B's version is divergent, criterion 3 applies, and B's edit becomes
a `private` fork. Under the statement, only one device edited, FLOW-008 does not
fire, CON-003 (1) and ST-003 (1) reject the edit — and no requirement says what then
becomes of the change B holds: whether it is kept, discarded, or turned into
something, and what B's owner is told (ST-009 speaks of transitions, and an edit is
not one; FLOW-008's source says "No edit is discarded", but only for the case the
statement covers). The two readings leave different recipes in the account and
different things on device B; each is consistent with CON-003 (1), so gate 2 could
not tell which one was built, and the developer would be making the choice
(TEAM-008). The sync ADR (`docs/architecture/decisions/20260927-server-authoritative-sync-with-outbox.md`)
compares a revision number but does not say whether a state change moves it, and
is not `spec/` in any case. Neither reading is preferred here.
**Stopped:** T-027 (carries FLOW-008 (1, 3, 4) and CON-003 (1)); T-028 (depends on
T-027 and carries FLOW-008 (2), whose "that outcome" is this one).
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

### SD-019 — An account created muted from the write control: AUTH-001 has the action proceed, AUTH-004 has no recipe created, and nothing says what the person is told then or how a muted account attempts writing again (2026-09-27)
**Requirement IDs:** AUTH-001 (statement, 2, 5), ACT-001 (3), AUTH-004 (statement, 2, 4, 5, verification), AUTH-006 (1), AUTH-005 (statement, 1); read against AUTH-004 (3), the permissions table of `spec/40-actors.md` ("Create and fork a recipe": "no (AUTH-004)" for a muted account) and ADR 20260920-account-on-demand
**Class:** inconsistency (items 1 and 2); omission (item 1, what the person is told)
**What the gate hit:** T-026 builds the offer of an account when a person with no
account uses a control that writes a recipe, and carries AUTH-001 (2), ACT-001 (3),
AUTH-004 (4) and AUTH-006 (1). Creation on that trigger yields a muted account
whenever the server answers and no verdict can be obtained (AUTH-001 (5): "AUTH-004
governs what is created"; built by T-006). Two things the person then sees are
decided by the specification in two ways at once:
1. **Whether writing proceeds once a muted account exists, and what the person is
   told at that moment.** AUTH-001 (2) and ACT-001 (3): "the action they asked for
   proceeds once the account exists"; AUTH-004 (2) says that "a muted account is
   still an account". AUTH-004's statement ("shall not write") and (4) ("the editor
   … is not offered and no recipe can be created") say the same action does not
   proceed. For two of the four triggering actions the specification settles the
   collision explicitly: AUTH-004 (5) has AUTH-006 state "at that moment that the
   action cannot proceed and why" when the muted account "was created because the
   person asked to fork or to rate". Writing — one of the four actions AUTH-004's
   verification walks with attestation blocked — is not named there, and nothing
   else says whether the person who asked to write is told at that moment, or is
   returned to where they were with the write control replaced by AUTH-006 (1)'s
   statement, or anything else. The readings give different screens on the task's
   own trigger; under any of them AUTH-001 (2) as written is not met, and gate 2
   could not tell a right outcome from a wrong one (TEAM-008).
2. **Whether a muted account can attempt writing at all.** AUTH-004 (4) and AUTH-006
   (1) take the control away: the editor "is not offered", and "a statement takes
   its place". AUTH-005's statement fires "when a muted account attempts an action
   that AUTH-004 withholds", and its criterion (1) has the person "tap the fork
   control" — the control AUTH-004 (3) makes "absent". Whether the statement that
   replaces the write control is itself something the person can act on (so that
   AUTH-005 obtains a verdict and writing proceeds on a full account), or whether a
   muted account has no path to the attempt AUTH-005 is written for, is not stated.
   T-026 builds that replacement; AUTH-005 is placed in wave 7, and whichever
   surface T-026 ships decides whether AUTH-005 can ever be reached from writing.
The replacement is not stated here. Each requirement is consistent on its own; they
meet only where creation from a withheld action yields a muted account.
**Stopped:** T-026 (AUTH-001 (2), ACT-001 (3), AUTH-004 (4), AUTH-006 (1)). T-023 and
T-028 depend on T-026, and T-024–T-025 wait behind T-023, through the backlog's
dependencies. Item 1 also reaches AUTH-004 (5) (wave 4) and item 2 AUTH-005 (wave 7)
when those waves are sliced.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «И помечай эти пункты как закрытые» (re-checked against spec/ @ 4cc2c18)

### SD-020 — Duplicate of SD-018 (2026-09-27)
**Duplicate of:** SD-018. The same text was appended a second time through a scratchpad-file
collision between concurrent gate-1 calls. SD-018 is the entry of record; this number is
not reused and records nothing of its own.
**Status:** duplicate — see SD-018

### SD-021 — A refused notification permission: "asked for again" cannot be done on Android 10–12 or after a second refusal, its first request is placed nowhere, and whether the timer still sounds is answered two ways (2026-09-27)
**Requirement IDs:** FLOW-013 (5), A11Y-007 (3), EXT-009 (statement, 1); read against FLOW-013 (statement, 1, 2, note), QUAL-005 (statement, 3), CON-005 and L10N-001 (1)
**Class:** not implementable (item 1); omission (item 2); inconsistency (item 3)
**What the gate hit:** T-023 builds the announcement of a timer that ends while the
application is closed or the screen is locked, and carries FLOW-013 (1–5), A11Y-007 (3)
and EXT-009 (1) together, copied verbatim (C5), so none of this can be corrected at the
task. FLOW-013 (5) is the only sentence in `spec/` about the notification permission.
1. **"Asked for again" on the platform the product is bound to.** FLOW-013 (5): "the
   permission is asked for again only when the reader next starts a timer." CON-005
   makes the client native Android and QUAL-005 requires every function of the
   specification on Android 10 (API 29) and every later version. On Android 10–12 there
   is no runtime permission to show notifications: they are on unless the reader turns
   them off in the system's settings, and the application has no request it can make
   that the reader answers. On Android 13 and later the permission is requested at run
   time, and once the reader has refused it twice the platform stops showing the request
   when the application makes it. On those devices, and on every device at API 29–32,
   the request FLOW-013 (5) places at the next timer start either shows the reader
   nothing or has to be something the application draws itself — an explanation that
   sends the reader to the system's settings — which is a screen, with strings under
   L10N-001 (1), that no criterion describes. Whether "asked for" means the platform's
   request, and so holds only where the platform allows it, or any request the product
   makes, is what separates a build that shows nothing from one that shows a screen of
   its own, and gate 2 could not tell which is right.
2. **When the permission is first asked for.** FLOW-013 (5) fixes only when it is asked
   "again". On Android 13 and later notifications are withheld until the permission is
   granted, so some first request is needed for FLOW-013 (1) and (2) to hold at all.
   Nothing says whether it comes on the first start of a timer, on first launch, or
   elsewhere; each is a request the reader sees at a different moment, and a request
   placed anywhere FLOW-013 (5) does not name is behaviour with no requirement behind it
   at gate 2.
3. **Whether a timer ending with notifications refused is heard.** A11Y-007 (3): "Given
   a timer that ends while the screen is off or the application is closed, then it
   announces itself audibly from the device's own facility (FLOW-013, EXT-009)." It
   carries no exception for a refused permission. EXT-009's statement drives "every
   announcement from the device's own clock and its own notification facility", which
   is the facility the reader refused. FLOW-013 (5) says of the refused case only that
   the timer "is still finished and is shown as finished inside the application".
   Read with EXT-009, a refused facility means nothing sounds while the application is
   closed, and A11Y-007 (3) does not hold for that reader. Read with A11Y-007 (3), the
   timer must still sound, which the device can only do through something other than
   its notification facility, which EXT-009's statement does not provide for. The two
   builds differ in the one thing a cook with the oven on would notice.
FLOW-013 (1)–(4), FLOW-016 (3)–(8) and EXT-009 (1) with the permission granted are not
questioned here. SD-014 (a reference to an ingredient holding no quantity) and SD-015
(GUIDE-006's display) also touch the derived name T-023 announces and are cited, not
restated.
**Stopped:** T-023 (FLOW-013 (5), A11Y-007 (3), EXT-009 (1)). T-024, T-025 and T-028 wait
behind it through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «давай закрывать SD-021» (re-checked against spec/ @ ead1a44)

### SD-022 — Duplicate of SD-021 (2026-09-27)
**Duplicate of:** SD-021. The same text was appended again through a scratchpad-file
collision between concurrent gate-1 calls. SD-021 is the entry of record; this number is
not reused and records nothing of its own.
**Status:** duplicate — see SD-021

### SD-023 — Duplicate of SD-021 (2026-09-27)
**Duplicate of:** SD-021. The same text was appended again through a scratchpad-file
collision between concurrent gate-1 calls. SD-021 is the entry of record; this number is
not reused and records nothing of its own.
**Status:** duplicate — see SD-021

### SD-024 — Duplicate of SD-021 (2026-09-27)
**Duplicate of:** SD-021. The same text was appended again through a scratchpad-file
collision between concurrent gate-1 calls. SD-021 is the entry of record; this number is
not reused and records nothing of its own.
**Status:** duplicate — see SD-021

### SD-025 — Duplicate of SD-021 (2026-09-27)
**Duplicate of:** SD-021. The same text was appended again through a scratchpad-file
collision between concurrent gate-1 calls. SD-021 is the entry of record; this number is
not reused and records nothing of its own.
**Status:** duplicate — see SD-021

### SD-026 — The place a reader had scrolled to is restored by FLOW-012's statement and observed by none of its criteria, and whether scale and place survive the operating system ending the application is read two ways (2026-09-27)
**Requirement IDs:** FLOW-012 (statement, 1, 3, verification, source); read against the ADR 20260921-the-cooking-card-is-one-scrolling-screen, the preamble of `30-cooking`, RET-010 (2) and A11Y-004
**Class:** omission (item 1); ambiguity (item 2)
**What the gate hit:** T-025 promises in its title that the card "is where the reader
left it", and carries FLOW-012 (1–3) copied verbatim (C5), so neither item can be
corrected at the task.
1. **The place is restored by the statement and observed by no criterion.** FLOW-012's
   statement returns the reader "at the place in it the reader had scrolled to"; its
   correction of 2026-09-21, the ADR ("What the product restores on return is the
   scroll position") and the preamble of `30-cooking` all name the scroll position as
   what is restored. None of FLOW-012's four criteria, nor any other in `spec/`,
   observes it. The task's titular behaviour therefore reaches the developer through
   the statement alone, and gate 2, walking criteria, can neither find its absence nor
   say what "the place" is when the card is laid out differently on return — text at
   another scale under A11Y-004, a rotated screen, a timer that ended while the reader
   was away.
2. **What survives the operating system ending the application.** The statement covers
   "when the reader returns to the application after leaving it". The criteria
   separate "leaves the application and returns" ((1), (2): recipe, scale, fixed
   ingredient, timers) from "the operating system ended the application ... opens it
   again" ((3): "the same recipe is shown", nothing more), and the source adds that
   only closing "can cost the recipe". Whether the changed serving count, the fixed
   ingredient and the place come back after the process was ended, or only the recipe,
   is read two ways, and the two readings meet different limits on device state:
   RET-010 (2) lists "the recipe last open (FLOW-012)" among what the device holds, and
   the ADR says "nothing about a reader's progress is stored". FLOW-012's own
   verification repeats the walk "with the process ended by the operating system", so
   the case is meant to be tested.
FLOW-014 and A11Y-007 (4) are determinate and are not questioned here. SD-003 item 1
(the combined state of a changed count with a fixed ingredient, FLOW-012 (1)'s Given) is
cited, not restated.
**Stopped:** T-025 (FLOW-012). T-028 waits behind it through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
**Resolved:** 2026-09-27 — «Запусти перепроверку спеки по дефектам, отметь что однозначно разрешено» (re-checked against spec/ @ 08e6223)

### SD-027 — SCALE-001's second criterion shows 6 pieces where the grid and SCALE-015 give 5 (2026-09-27)
**Requirement IDs:** SCALE-001 (2); read against SCALE-015 (statement), the rounding grid (`spec/80-scaling-rules/00-index.md`, `piece`, indivisible: whole pieces) and SCALE-004 (2)
**Class:** incorrect fact
**What the gate hit:** T-001 copies SCALE-001's criteria verbatim (C5), so it cannot be
corrected at the task. SCALE-001 (2) reads: "Given the same recipe with 3 indivisible
pieces, when the reader asks for 6, then it shows 6 pieces." "The same recipe" is the
one of SCALE-001 (1), authored for 4 servings, so the factor is 6 / 4 = 1.5 and the
exact computed value is 3 × 1.5 = 4.5 pieces. SCALE-001's statement displays it
"rounded by the rounding grid"; the grid holds whole pieces for an indivisible `piece`;
SCALE-015 sends a value exactly between two grid values to the larger, so 4.5 is shown
as 5 pieces. SCALE-004 (2) (3 × 0.6 = 1.8 shown as 2) reads the same rule for
indivisible pieces. No reading of the grid found yields 6: 6 pieces is the value at a
factor of 2 (8 servings). An implementation that follows the grid fails SCALE-001 (2)
as written; one that shows 6 breaks SCALE-015 and the grid. Gate 2 can mark this
criterion met under neither. Found at T-001's second gate 1
(docs/product/verdicts/20260927-T-001-gate1-2.md); the first gate stopped at C3 before
this criterion was examined.
**Stopped:** T-001 (SCALE-001). Every wave-1 task except T-003, T-004, T-005, T-006 and
T-027 waits behind T-001 through the backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-027 — A computed serving count between ¾ and one is displayed as ¾ by SCALE-008 and as 1 by SCALE-015 (2026-09-27)
**Requirement IDs:** SCALE-008 (statement, 4), SCALE-015 (statement); read against the serving-count paragraph of the rounding grid (`spec/80-scaling-rules/00-index.md`), the ADR 20260927-the-rounding-grid-completed and the ADR 20260920-scaling-extremes
**Class:** inconsistency
**What the gate hit:** T-002 builds the approximate serving count a fixed ingredient
produces (SCALE-002, SCALE-008, SCALE-015 (4–5)) and so computes counts below one
serving. For a computed count from 0.875 up to, not including, 1 the two statements
give different cards:
- SCALE-008: "While the computed serving count is below one serving, the system shall
  display it as the nearest of the fractions ¼, ⅓, ½, ⅔, ¾". The candidates are the
  five fractions only; 0.9 is displayed as ≈¾. Its criterion (4) — "a computed count of
  exactly one serving or more ... is displayed as a whole number" — ties the whole-number
  form to the computed value, which reads the same way.
- SCALE-015: "every computed serving count as the value of its grid nearest to the exact
  computed value, and where the computed value lies exactly between two grid values, as
  the larger of the two". The section index gives the serving count one set — "whole
  numbers and the fractions ¼, ⅓, ½, ⅔, ¾ ... rounded to the nearest value the set can
  express" — and the scaling-extremes ADR says one set governs with "no second grid".
  1 is in that set, so 0.9 is displayed as ≈1, and 0.875, exactly between ¾ and 1, goes
  up to 1.
Reachable through T-002: in a recipe authored for 4 servings with 500 g of chicken,
fixing 115 g computes 0.92 servings (≈¾ or ≈1), and fixing 109.375 g computes exactly
0.875. Each reading is internally consistent: SCALE-015 is harmonised with SCALE-008 if
"its grid" below one computed serving means the five fractions alone, and SCALE-008 is
harmonised with SCALE-015 if "below one serving" means below one after rounding. Which
is meant is the product decision. No criterion in `spec/` exercises a computed count in
this range, so the developer would decide it and gate 2 could not tell (TEAM-008).
SD-003 item 3 (the tie rule) is not reopened: SCALE-015 answers it; what is open here
is which values the tie is between.
**Stopped:** T-002 (SCALE-008, SCALE-015 (4–5)). T-030 and T-020 wait behind it through
the backlog's dependencies, and T-021–T-025 and T-028 behind T-020.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-028 — "Session" bounds FLOW-034's kept view from both sides and is defined nowhere: whether closing the application, or the operating system ending it, starts a new one is read more than one way (2026-09-27)
**Requirement IDs:** FLOW-034 (5, 6, verification); read against CAT-001 (6), FLOW-033 (its "within the same session" criterion), FLOW-017 ("a later session" against "stays in the application"), FLOW-012 and AUTH-024
**Class:** ambiguity
**What the gate hit:** T-007 carries FLOW-034 (5), under which a chosen view "is still in
force" when the reader "returns within the same session", and FLOW-034 (6), under which
"a session in which the reader has not chosen a view" opens the section on the marked
view. Together they require a chosen view to be kept until a session ends and to be gone
when the next one starts. `spec/` does not say where one session ends:
1. The glossary does not define the word, and neither does FLOW-034. CAT-001 (6) and
   FLOW-033 use the same words without defining them.
2. Elsewhere the specification treats three events as different: leaving the
   application and returning while its process is alive, the operating system ending
   the process while the application is away, and the reader closing the application
   (FLOW-012; AUTH-024). FLOW-017 contrasts "a later session" with "stays in the
   application", which suggests one run of the application. It does not say whether a
   run that the operating system ended and the platform restored is the same run.

A reader who chose the own-recipes view and comes back after either the process was
ended or the application was closed and reopened is shown the marked view under one
reading and the own-recipes view under another. FLOW-034's verification ("open it in a
new session") needs the answer to start its own walk. Each criterion is observable once
the boundary is fixed; the boundary itself is the product decision. Until (6) was carried
the word was harmless, since a view kept forever meets (5) under every reading. It was
recorded as an unknown in T-007's first two gate-1 verdicts. The developer would decide
it, and gate 2 could not tell (TEAM-008).
**Stopped:** T-007 (FLOW-034 (5, 6)). T-008–T-026, T-028 and T-031–T-034 wait behind
T-007 through the backlog's dependencies. CAT-001 (6) and FLOW-033 carry the same words
and are placed in wave 2.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-029 — How the deletion key is handed over on the second step of creation is read three ways: "the controls of AUTH-017" name no control, and AUTH-014 (7) has the account key carry it (2026-09-27)
**Requirement IDs:** AUTH-024 (3), AUTH-017 (1) and its "Constrains AUTH-014" line; read against AUTH-014 (7), AUTH-007 (1, 2), AUTH-019 (3), EXT-006 (1) and the ADR 20260920-deletion-key
**Class:** ambiguity
**What the gate hit:** T-032 builds the second step of creation. AUTH-024 (3) has that
step hold "the account key and the deletion key with the controls and the confirmation
of AUTH-007 and AUTH-017". AUTH-007 states its controls for the account key: displayed
in full, copied in one action (1), saved as a file (2). AUTH-017 states no control at
creation: its only creation criterion (1) says the deletion key "is issued on the same
screen and is carried by the file that AUTH-007 offers". Three passages of `spec/`
then say three different things about what a person who prefers copying to a file is
handed:
1. AUTH-017 (1) read alone: the deletion key is shown on the step and travels only in
   the file. A person who copies the account key into a password manager — the use the
   ADR of the three steps calls expected — leaves creation without the deletion key as
   text, which is the only form EXT-006 (1) accepts, until AUTH-019 (3) reaches it on
   the account's own screen.
2. AUTH-017's "Constrains AUTH-014" line ("issued and carried alongside it as copyable
   text") and the deletion-key ADR ("it carries this value as copyable text"): the
   deletion key is displayed in full with a copy control of its own beside the account
   key's.
3. AUTH-014 (7) ("Given the account key, when it is issued, then it is text the holder
   can copy ... and it carries the deletion key"): the account key's one copy action
   carries the deletion key too, so what reaches the clipboard under AUTH-007 (1) is
   more than the account key.
No criterion says whether the deletion key is displayed in full on the step either;
AUTH-019 (3) masks it only afterwards. Each reading is internally consistent, and the
three give three different second steps. The Team Lead's ADR
20260927-keys-are-hashed-random-secrets fixes the file's contents (both keys as text)
and not what the step displays or copies. The developer would decide it, and gate 2
could not tell: a separate copy control is "the controls of AUTH-017" under reading 2
and behaviour no criterion asks for under reading 1 (TEAM-008). Found at T-032's gate 1
(docs/product/verdicts/20260927-T-032-gate1.md).
**Stopped:** T-032 (AUTH-024 (3), AUTH-017 (1)). T-010 waits behind T-032 through the
backlog's dependencies, T-022 and T-026 behind T-010, and T-023, T-033, T-024, T-025
and T-028 behind those.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-030 — A timer's duration has no stated admissible values, and GUIDE-007's "next action" is not said to include a scroll or a screen-reader move (2026-09-27)
**Requirement IDs:** FLOW-027 (1, 4, 11), GUIDE-007 (4); read against the `Timer` entity's `duration` field (`spec/50-domain-model/00-index.md`), the glossary's "Timer" (`spec/10-glossary/10-recipes-and-scaling.md`), DOM-012, GUIDE-005, A11Y-005 and the ADR `spec/decisions/20260927-steps-timers-and-references.md`
**Class:** omission (item 1); ambiguity (item 2)
**What the gate hit:** T-014 builds attaching a timer inside a step and carries
FLOW-027 (1–7, 9, 11), DOM-013 (1–4) and all four criteria of GUIDE-007.
1. **What the duration field accepts is stated nowhere.** FLOW-027 (1) has the author
   "enter 30 minutes", (4) makes "a duration … the only thing required of them", and
   (11) drops a timer whose "duration the author has not entered". The domain model
   types `duration` as "duration" with no note; the glossary defines a timer as "a
   duration attached inside one step"; DOM-012 bounds the timer's name, not its
   duration; no ADR and no open question addresses it. Three things are open:
   - the unit or granularity the author enters — minutes only, or hours, minutes and
     seconds;
   - whether zero is a duration "entered", and so stored, or the absence FLOW-027 (11)
     drops;
   - whether a duration has a ceiling.
   Each is visible on the editor, and whoever writes the field would decide it; gate 2
   could not tell a right field from a wrong one (TEAM-008). This is the gap SD-013
   recorded for the authored serving count.
2. **Which interaction is "the author's next action".** GUIDE-007 (4): "Given the
   statement, when the author takes their next action in the editor, then the statement
   is gone …; the statement has no control that closes it (GUIDE-005)." Neither
   GUIDE-005, nor the glossary's "Hint", nor the ADR of 2026-09-27 ("lasts until the
   author's next action") says what counts. A build that keeps the statement through a
   scroll and one that removes it on a scroll are each right under one reading. The
   reading also decides whether a person using the screen reader (A11Y-005) can hear the
   statement: if moving the reader's focus onto it is an action, it is gone when reached.
Found at T-014's second gate 1 (docs/product/verdicts/20260927-T-014-gate1-2.md), after
SD-005 was resolved.
**Stopped:** T-014 (FLOW-027 (1, 4, 11), GUIDE-007 (4)). T-015, T-016, T-017, T-034,
T-021 and T-022 wait behind T-014 through the backlog's dependencies, and T-023, T-033,
T-024, T-025 and T-028 behind those. T-022 and T-024 run and restart a timer "from its
full duration"; their own gates decide whether item 1 stops them directly.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open

### SD-031 — Where the author "chooses to edit" a saved recipe is stated nowhere, and FLOW-029 (3) with FLOW-053 (7) reads as forbidding it on the one screen FLOW-029 (2) starts from (2026-09-27)
**Requirement IDs:** FLOW-029 (2, 3, 4); read against FLOW-053 (7), FLOW-036 and FLOW-038 (1)
**Class:** ambiguity
**What the gate hit:** T-018 builds the saved view of FLOW-029 and carries all four of
its criteria. Criterion (2): "Given that view of a recipe that is still editable, when
the author chooses to edit, then the editor opens on the same recipe." Criterion (3):
"Given that view, when the author looks for an edit control on the card itself, then
there is none: the card is read, the editor is written, and they are two screens."
FLOW-053 (7) adds that the card of an own recipe "carries no deletion control, because
the card is read and the list is where a recipe is managed (FLOW-029)". No requirement
names the control or the place through which the author chooses to edit. Two readings
each hold together and give two different screens:
1. The card screen carries a way into the editor outside "the card itself" — in the
   screen's bar, beside the recipe rather than in it. (2) is met from "that view"
   directly; (3) is met because the recipe's content holds no edit control.
2. The card screen carries no way into the editor at all: "the card is read ... they
   are two screens" and FLOW-053 (7)'s "the list is where a recipe is managed" place
   editing on the recipe's entry in the own-recipes view (FLOW-036, FLOW-038 (1)),
   and (2)'s "that view" is only where the author has come from.
An edit action in the card screen's bar is required under reading 1 and is an edit
control on the card under reading 2. The same choice decides where (4)'s "no way into
the editor" is looked for. Whoever builds the card would decide it, and gate 2 could
not tell a right card from a wrong one (TEAM-008). The earlier gate 1 on T-018
(docs/product/verdicts/20260927-T-018-gate1.md) stopped at C3 on other grounds and did
not examine this. Found at T-018's second gate 1
(docs/product/verdicts/20260927-T-018-gate1-2.md).
**Stopped:** T-018 (FLOW-029 (2–4)). T-019, T-015, T-016, T-017, T-020, T-021, T-022,
T-023, T-033, T-034, T-024, T-025, T-026 and T-028 wait behind T-018 through the
backlog's dependencies.
**Replacement:** none written, by PO-006. Route: the user runs `/req-forge`.
**Status:** open
