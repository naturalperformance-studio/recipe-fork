---
slug: MVP
status: closed
updated: 2026-10-02
ids: [MVP-001, MVP-002, MVP-003]
---

# The MVP boundary

> **Superseded 2026-10-02 by user decision.** The product is now built in increments
> (`docs/increments/`), and the first release is whatever increments have shipped,
> not "every closed section". MVP-001 no longer binds. MVP-002 stays useful as a list
> of things decided against; MVP-003 stays as a checklist for the first public
> release to a store. `/req-forge` rewrites this section when the vision is next synced.

This section takes no decision of its own. It collects decisions already taken
elsewhere into one boundary: what the first release contains, what it
deliberately does not, and what has to be true before it can be released at all.

## What the first release is

### MVP-001 — The first release is this specification, less what the ledger defers

The first release shall implement every requirement in the `closed` sections of
this specification, shall implement nothing listed in MVP-002, and shall not be
released while any question classified **define before MVP** is open.

**Acceptance criteria**
- Given the release candidate, when it is checked against the index, then every
  requirement of every `closed` section has an implemented counterpart.
- Given the ledger, when it is read before release, then no entry classified
  **define before MVP** is unresolved.
- Given the readiness banner in the index, when it is read, then it says the
  spec is ready for handoff and names no blocking question.
- Given anything in MVP-002, when the release candidate is examined, then it is
  absent, and its absence is not a defect.
- Given a requirement nobody implemented, when it is found at release time, then
  it is a gap in the release and not a licence to reclassify the requirement.
- Given serverless mode and the export and import of recipe files (FLOW-061 to
  FLOW-065), when the release candidate is examined, then they are built and
  tested in it, and the release is built with the mode off; a later release
  switches it on (FLOW-061).

**Verification:** a requirement-by-requirement walk of the index against the
release candidate, plus a reading of the ledger.
**Source:** user decision, 2026-09-21. Cutting scope further was offered and
declined: the specification was written throughout against a USD 20 monthly
ceiling and a single moderator, so what is left is the fork mechanic itself.
Serverless mode was added to the first release by user decision, 2026-09-28, so
that switching the server off later needs no new feature, only a release
([ADR — serverless mode and recipe files](decisions/20260928-serverless-mode-and-recipe-files.md)).

### MVP-002 — What the first release deliberately does not contain

The first release shall not contain the following, each of which was decided
against for the MVP rather than left undone.

| Not built | Decided | Where |
|---|---|---|
| A management mode for favourites by the space they occupy, and a size on a card | 2026-09-20 | OQ-012 |
| Holding the last recipe opened when it is not a favourite | 2026-09-20 | OQ-013 |
| A queue of author requests to add an ingredient dictionary entry | 2026-09-20 | OQ-019 |
| A recipe named as an ingredient of another recipe | 2026-09-20 | OQ-024 |
| Rejection reasons as fixed categories | 2026-09-20 | OQ-026 |
| A third interface language | 2026-09-21 | L10N-001 |
| Distribution in the Russian Federation | 2026-09-21 | LEG-010 |
| An imperial or any other non-metric unit system | 2026-09-21 | L10N-008 |
| Voice control of the cooking steps | 2026-09-21 | A11Y-007 |
| A cooking mode with a current step, an advance between steps and a record of what is done | 2026-09-21 | [ADR — the cooking card is one scrolling screen](decisions/20260921-the-cooking-card-is-one-scrolling-screen.md) |
| A withdrawal window on a published recipe | 2026-09-21 | [ADR — replaced, never withdrawn](decisions/20260921-a-published-recipe-is-replaced-never-withdrawn.md) |
| Any monetisation, advertising or paid tier | 2026-09-20 | NGOAL-006 |
| Automated classification of images or video | 2026-09-20 | NGOAL-005 |
| A second client surface of any kind | 2026-09-20 | CON-005 |
| Import of recipes from Instagram, TikTok and Pinterest | 2026-09-27 | [`import`](68-import/00-index.md) — written ahead, the product owner's next feature |
| A field for the address of a server someone else runs, and the server's code published at shutdown | 2026-09-28 | OQ-038 |

**Acceptance criteria**
- Given the release candidate, when each row is looked for, then it is absent.
- Given a row whose ledger entry is later resolved in favour of building it, when
  that happens, then the row leaves this table and the requirement is written in
  its own section.
- Given this table, when it is compared with the ledger, then every entry
  classified **define after MVP** that was decided against appears here.

**Verification:** walk the table against the release candidate and against the
ledger.
**Source:** collected 2026-09-21 from decisions taken on 2026-09-20 and
2026-09-21. This table is a view, not a second source: where it and the entry it
names disagree, the entry is right.

### MVP-003 — What must be true before the release, beyond the requirements

The release shall not be made until each of the following has been done and
recorded, none of which is behaviour of the product.

| Prerequisite | Promised by |
|---|---|
| The published name cleared against the distribution channel and the trademark registers | LEG-009, OQ-016 |
| A lawyer's review of the logs, the published terms and the privacy statement, under the law of the countries actually distributed in | OQ-029 |
| The distribution countries set, with the Russian Federation excluded | LEG-010 |
| The account deletion obligation confirmed against the channel's own reading | OQ-030 |
| The terms of use and the privacy statement published and linked from the listing | LEG-008 |
| The target audience and content settings completed in the channel | LEG-007 |
| One restore from a copy of the store, performed into an empty environment and checked | QUAL-008 |
| The release measurement report, with both profiles and all four operations | QUAL-002, QUAL-003, QUAL-004 |
| The availability probe running against the health endpoint | EXT-011, EXT-012 |

**Acceptance criteria**
- Given the release, when it is proposed, then every row above has a recorded
  result with a date.
- Given a row whose result is negative, when it is read, then the release waits
  and the specification is changed to match the answer, rather than the answer
  being worked around.
- Given the four ledger rows, when they are resolved, then each is written back
  into the ledger as resolved, with what was decided.

**Verification:** the recorded results, read together before the release is made.
**Source:** assembled 2026-09-21. Three of these are the questions that keep this
specification from being ready; the rest are checks that produce a record rather
than a requirement.

## What the first release actually looks like

This part holds no requirements. It is here because the shape of the launch
changes how several requirements read, and a developer who does not know it will
misread them as broken.

The product owner stated on 2026-09-21 that the product was designed for their
own use and that there is no cohort of authors waiting for it. The first release
therefore opens with an empty catalogue, one author, and that same person holding
the moderator role.

What follows from that, and is expected rather than wrong:

- **Automatic hiding cannot fire.** ABUSE-003 counts only reports from accounts
  that have published something, and at launch nobody else has. Reporting itself
  works from the first day: ABUSE-002 already sends every report to the
  moderator's queue, including reports from people holding no account. Until at
  least three other people have published, the moderator handles every report by
  hand and the threshold of ST-006 simply never arrives.
- **The rating orderings are degenerate.** `highest rated`, `most rated` and
  `most forked` (CAT-001) order a catalogue by one author, so `newest` is not
  merely the default but the only ordering that means anything at first.
- **One operational signal will read at its extreme and must not be treated as
  an alarm.** The share of ratings from accounts that have published nothing
  (OBS-001, promised by RATE-005) will sit near 100 per cent while there is one
  publishing account, and it says nothing about score farming until there are
  several.
- **The device gate and the nickname space are provisioned for a crowd that does
  not exist yet.** Three slots per device (AUTH-002) and the exhaustion question
  (OQ-009) both cost nothing at this size and are built because retrofitting them
  after the catalogue fills is what would be expensive.
- **The moderator's queue holds the owner's own submissions, and that is
  allowed.** MOD-013 was written for exactly this launch: a moderator decides
  their own submission, which goes through the queue and the text filter like
  anyone else's. Forbidding it becomes worth discussing when there is a second
  moderator to hand the work to.
