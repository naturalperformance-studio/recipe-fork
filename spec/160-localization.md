---
slug: L10N
status: closed
updated: 2026-09-27
ids: [L10N-001, L10N-002, L10N-003, L10N-004, L10N-005, L10N-006, L10N-007,
  L10N-008, L10N-009]
---

# Localization

Two languages from the first release, and everything that follows from that:
which of them a reader gets, what a recipe declares about itself, what a text
query looks at and what counts as a match in a language where words decline.

One thing is settled elsewhere and is repeated here only so that it is not
mistaken for an omission: the product is metric (L10N-008).

### L10N-001 — Russian and English from the first release

The system shall present its interface in Russian and in English, and the first
release shall offer no third language.

**Acceptance criteria**
- Given any screen, statement, warning or error in this specification, when it is
  shown, then it exists in both languages and neither falls back to the other.
- Given a release, when its interface strings are enumerated, then none is fixed
  in the code in one language only.
- Given a third language, when it is proposed, then it is a product decision
  recorded as an ADR and not an addition anyone makes quietly, because it also
  obliges L10N-003.

**Verification:** walk every screen in each language and confirm no string
appears in the other.
**Source:** user decision, 2026-09-21.

### L10N-002 — The interface language starts from the device and the reader may change it

The system shall present its interface in Russian when the device is set to
Russian and in English otherwise, shall allow the reader to change that at any
time without an account, and shall keep the choice on the device.

**Acceptance criteria**
- Given a device set to Russian, when the application is first opened, then the
  interface is Russian.
- Given a device set to any other language, when the application is first opened,
  then the interface is English.
- Given the reader changes the language, when any screen is opened afterwards,
  then it is in the chosen language, and the choice survives closing and
  reopening the application.
- Given no account at all, when the language is changed, then it changes, because
  reading needs no account (AUTH-001) and neither does this.
- Given an account signed in on a second device, when the interface opens there,
  then it follows that device and not the first one.

**Verification:** first launch on a Russian device and on an English one; change
the language and reopen; sign one account in on two devices set differently.
**Source:** user decision, 2026-09-21. The device setting is the starting point
rather than the rule because a great many people in the product's own market run
their phone in English and read recipes in Russian; without the switch they would
get an interface they did not choose and could not change. The choice is held on
the device and not on the account because a reader has no account to hold it.

### L10N-003 — Both dictionaries carry both languages, always

The system shall hold every ingredient dictionary entry and every tag in both
languages of L10N-001, and shall not accept an entry or a tag that has a label in
only one of them.

**Acceptance criteria**
- Given a new dictionary entry, when it is added with a label in one language
  only, then it is rejected and does not become available to any author.
- Given any entry the author can pick, when the interface language changes, then
  its label changes with it and the ingredient the author chose does not.
- Given a recipe written in one language, when it is read in the other, then its
  ingredient names and its tags are shown in the reading language, because both
  are dictionary references and not the author's text (DOM-009, CAT-006).
- Given a free-text ingredient (DOM-009), when it is read in either language,
  then it appears exactly as the author typed it and is translated by nothing.
- Given a qualifier (FLOW-023), when it is read, then the same applies: it is the
  author's text.

**Verification:** add an entry in one language and confirm it is refused; read
one recipe in both languages and compare its ingredient list, its tags and its
free text.
**Source:** user decision, 2026-09-21. This is the real price of the second
language and it is recurring rather than one-off: the ingredient dictionary is
maintained by the product owner and grows for as long as the product lives
([ADR — the ingredient dictionary](decisions/20260920-ingredient-dictionary.md)
foresaw exactly this), so every future entry is two entries. Refusing a
half-translated entry is what keeps the catalogue from filling with ingredients
that are invisible to half the readers.

### L10N-004 — A recipe declares the language it is written in

The system shall require the author to declare one language for a recipe from the
languages of L10N-001 before it can be submitted, shall store that declaration on
the recipe, and shall freeze it with the rest of the content (CON-003).

**Acceptance criteria**
- Given a new recipe, when the editor opens it, then the declared language is set
  to the author's current interface language and the author may change it.
- Given a recipe with no declared language, when submission is attempted, then it
  is refused because the declaration is part of the submission minimum (DOM-006),
  marked on leaving the part as FLOW-028 marks any missing part, and never
  refused while the author is typing.
- Given a published recipe, when its declared language is read, then it cannot be
  changed, and correcting a wrong one is a fork like any other correction
  (GOAL-002).
- Given a fork, when it is created, then it inherits the parent's declared
  language and the author may change it before submitting, because translating a
  recipe is a legitimate thing for a fork to be.
- Given the declaration, when what it governs is enumerated, then it selects the
  catalogue filter of L10N-005 and the analysis of L10N-007, and it never
  translates, checks or constrains a single word the author wrote.

**Verification:** submit without a declaration; fork a recipe and change the
declaration; attempt to change it after publication.
**Source:** user decision, 2026-09-21. The product checks nothing about what the
author wrote (NGOAL-007), so the declaration is a statement by the author and not
a detection: a recipe declared Russian and written in English is accepted, and the
only thing that suffers is where it is found.

### L10N-005 — Every language is listed until the reader narrows it

The system shall include recipes of every declared language in catalogue
listings, search results and the favourites views, and shall offer a filter by
declared language alongside the other filters.

**Acceptance criteria**
- Given a catalogue with recipes in both languages, when a reader opens it having
  chosen no filter, then recipes of both are listed and the interface language
  changes nothing about which are present.
- Given the language filter set to one language, when a listing is shown, then it
  holds recipes of that declared language alone.
- Given the language filter set and a text query run, when the results are built,
  then the query is analysed in that language alone (L10N-007).
- Given the filter leaves a listing empty, when it is shown, then FLOW-042 names
  what is empty and offers the way out of it.
- Given the favourites views and the own-recipes view, when the filter is used
  there, then it behaves as it does in the catalogue (FLOW-035).

**Verification:** list a catalogue holding both languages with the filter off,
with each value set, and with a value that matches nothing.
**Source:** user decision, 2026-09-21. Defaulting to the interface language was
rejected on what the first months look like: nearly every recipe will be in
Russian, so an English reader would open an empty catalogue and have no reason to
open it twice. A mixed listing costs that reader a title they cannot read, which
the title itself makes obvious — no marker on the card is specified, because the
recipe's own words are the marker.

### L10N-006 — A text query looks at the title and the description

Where a reader runs a text query, the system shall match it against a recipe's
title and its description, and against nothing else.

**Acceptance criteria**
- Given a query matching a word in a recipe's title, when it is run, then that
  recipe is in the results.
- Given a query matching a word in a recipe's description and nowhere else, when
  it is run, then that recipe is in the results.
- Given a query matching only the text of a step, when it is run, then that
  recipe is not in the results.
- Given a query matching only an ingredient's name, when it is run, then that
  recipe is not in the results, because ingredients are reached by the filter of
  CAT-006 and not by words.
- Given a query, when it is run inside the favourites section or the own-recipes
  view, then it searches the same two fields there (FLOW-035).

**Verification:** build four recipes carrying the query word in the title, in the
description, in a step and in an ingredient, and run one query.
**Source:** user decision, 2026-09-21. Step text was weighed and left out on two
counts: it is by far the largest text the product holds — up to fifty steps of two
thousand characters against a title of 120 and a description of 2 000 (DOM-012) —
so an index over it costs more than the rest of the product's text together,
which is money under CON-002; and it is the text least likely to identify a
recipe, since a query would find every recipe that says "stir until smooth".

### L10N-007 — A match is on the word, not on the letters

The system shall match a query word to a word of the searched text when both
reduce to the same form under the analysis of the recipe's declared language,
and shall treat letter case as insignificant.

**Acceptance criteria**
- Given a recipe declared Russian whose title holds `блины`, when the reader
  queries `блинов`, then it matches.
- Given the same recipe, when the reader queries `БЛИНЫ`, then it matches.
- Given a Russian text holding `ёж`, when the reader queries `еж`, then it
  matches, and the same in reverse: `ё` and `е` are the same letter for matching.
- Given a recipe declared English whose title holds `pancakes`, when the reader
  queries `pancake`, then it matches.
- Given a query, when it is run without a language filter, then it is analysed in
  each language of L10N-001 and matched against the recipes declared in that
  language, so a reader who filters nothing searches everything.
- Given two words that reduce to the same form although they mean different
  things, when one is queried, then both match, and this is accepted: the
  analysis is a normalisation and not a dictionary of meanings.

**Verification:** run the five queries above against seeded recipes in both
declared languages.
**Source:** user decision, 2026-09-21, choosing normalisation over matching the
beginning of a word. The cheaper rule was rejected on how people actually type:
a reader searching for pancakes types `рецепт блинов`, and a prefix rule finds
nothing. The cost accepted in exchange is the false match named in the last
criterion — `коса` and `косить` reduce alike — which is a wrong result in a list,
not a wrong recipe.

### L10N-008 — The product is metric and no language changes that

The system shall express every quantity in the units of DOM-002 regardless of
interface language, declared recipe language or device setting.

**Acceptance criteria**
- Given any interface language, when an ingredient is displayed, then its unit is
  one of those DOM-002 defines and no conversion to another system is offered.
- Given a device configured for a non-metric region, when a recipe is read, then
  nothing about its quantities changes.
- Given the household units, when they are shown in English, then they are the
  translated names of the same units and not a different measure.

**Verification:** read one recipe in both languages on a device set to a
non-metric region.
**Source:** DOM-002, which admits grams, kilograms, tonnes, millilitres, litres
and cubic metres alongside pieces and household measures and nothing else. This
requirement adds no decision; it exists so that a reader of this section does not
take the absence of a conversion rule for an oversight. Offering a second system
would mean a second rounding grid for every unit type in `scaling-rules` and a
second set of comparison factors in DOM-011, and it would be a product decision
taken on its own merits, not a translation task.

### L10N-009 — Numbers are written the way the interface language writes them

The system shall display every number with the digit grouping and the decimal
mark of the interface language: in Russian, groups of three digits separated by a
non-breaking space and a comma before the fractional part; in English, groups of
three digits separated by a comma and a point before the fractional part.

**Acceptance criteria**
- Given the interface in Russian, when 10 000 000 pieces are displayed, then the
  number reads `10 000 000` with a space that never breaks the number across two
  lines.
- Given the interface in English, when the same quantity is displayed, then it
  reads `10,000,000`.
- Given an `exact` quantity of 1500 g, when it is displayed, then it reads
  `1,5 кг` in Russian and `1.5 kg` in English (SCALE-013).
- Given the interface in Russian, when a four-digit whole number such as 1234
  servings is displayed, then it reads `1 234`; in English it reads `1,234`.
- Given a recipe declared in one language and the interface in the other, when a
  quantity is displayed, then it follows the interface language, not the
  recipe's.
- Given a vulgar fraction of SCALE-014, when it is displayed, then it carries no
  decimal mark in either language.

**Verification:** display the same seeded recipe at factors producing four-,
seven- and eight-digit quantities and decimal kilograms, once in each interface
language.
**Source:** user decision, 2026-09-27 (SD-016 item 2): the reader sees numbers in
the form their language uses. The forms are those the Unicode CLDR publishes for
`ru` (group separator U+00A0 no-break space, decimal mark comma, grouping from
four digits) and `en` (group separator comma, decimal mark point, grouping from
four digits) ([CLDR number data, cldr-json](https://github.com/unicode-org/cldr-json/tree/main/cldr-json/cldr-numbers-full/main),
checked 2026-09-27). The quantity in a step (SCALE-007) and the serving count
follow the same rule, being numbers displayed like any other.
