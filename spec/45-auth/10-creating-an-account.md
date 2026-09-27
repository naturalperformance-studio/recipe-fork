---
slug: AUTH
status: closed
updated: 2026-09-27
ids: [AUTH-001, AUTH-002, AUTH-003, AUTH-004, AUTH-005, AUTH-006, AUTH-018,
  AUTH-022, AUTH-023]
---

# Creating an account

When an account appears, what a device is allowed to create, the nickname it is
given, and what happens when the device cannot be attested at all.

### AUTH-001 — An account appears when an action needs one

When a person without an account performs an action that requires one — writing
a recipe, forking a recipe, rating a recipe, or choosing to carry favourites
beyond this device — the system shall offer to create an account, shall create
a full account only where the device verdict reports a free slot, and otherwise
shall act as AUTH-003 and AUTH-004 state.

**Acceptance criteria**
- Given a person with no account, when they browse, search, open a recipe, cook
  from it, run its timers, keep favourites on the device or report something,
  then no account is created and none is asked for.
- Given a person with no account, when they use a control that needs one, then
  account creation is offered, and the action they asked for proceeds once the
  account exists — for a muted account, as AUTH-004 (5) states.
- Given account creation, when a full account is created, then one slot on the
  device is occupied with the month it was occupied in; a muted account occupies
  none (AUTH-004).
- Given a device with no free slot, when creation is attempted, then no account
  is created and AUTH-003 governs what the person is told.
- Given a verdict that cannot be obtained while the server can be reached, when
  creation is attempted, then AUTH-004 governs what is created.
- Given no network connection at all, when creation is attempted, then nothing is
  created and FLOW-003 states that a connection is needed.
- Given a person who declines the offer, when they return to what they were
  doing, then everything that works without an account still works.

**Verification:** walk every account-less path on a clean install; then trigger
creation from each of the four actions that require it.
**Correction, 2026-09-27:** the statement said an account is created "only where
the device verdict reports a free slot", and criterion 3 had every successful
creation occupy a slot, while AUTH-004 creates a muted account with no verdict
and no slot. Both now speak of a full account; no decision changed.
**Source:** user decision, 2026-09-20. An account exists for the people who
publish and for the reader who wants favourites to outlive the device; a person
who only cooks mints nothing, consumes no nickname and occupies no slot. See
[ADR — an account is created on demand](../decisions/20260920-account-on-demand.md).

### AUTH-018 — The nickname is generated and may be rerolled any number of times before the account exists

When account creation is offered, the system shall show a generated three-word
nickname, shall allow the person to replace it with another generated one any
number of times before the account is created, and shall fix it permanently once
it is.

**Acceptance criteria**
- Given the first step of creation (AUTH-024), when it opens, then a generated
  nickname is already shown and nothing is typed by the person (CON-001).
- Given a shown nickname, when the person asks for another, then a different
  generated nickname replaces it.
- Given a nickname the person passed over, when the account is created, then only
  the accepted nickname is consumed and the others stay available to others.
- Given a created account, when its nickname is looked for in any interface, then
  no control changes it (DOM-001).
- Given two accounts created at the same moment, when their nicknames are
  compared, then they differ.

**Verification:** reroll several times, create the account, and confirm both the
permanence of the accepted name and the availability of the rejected ones.
**Correction, 2026-09-27:** the heading said "rerolled once", which read as a
single reroll against a statement, a criterion and a glossary entry that all say
any number of times (SD-010). The heading now says what the rest already did. The
form of a nickname is AUTH-022 and the words it is drawn from AUTH-023.
**Source:** user decision, 2026-09-20. The nickname is the only public
representation of a user, so the person is given a say in it at the one moment
that costs nothing; after creation it is permanent, because other people's
recipes name it as a parent's author.

### AUTH-002 — A device has three slots and a slot frees after six months

The system shall treat a device as holding three account slots, and shall treat
an occupied slot as free again once six or more calendar months have passed
since the month in which it was occupied.

**Acceptance criteria**
- Given a device on which no account has ever been created, when its slots are
  read, then three are free.
- Given a slot occupied in March 2026, when the month is September 2026 or
  later, then that slot is free.
- Given a slot occupied in March 2026, when the month is August 2026, then that
  slot is not free.
- Given three slots occupied in the same month, when six months have passed,
  then all three are free at once.
- Given a slot that frees, when an account is created into it, then the account
  that occupied it before is neither deleted nor affected in any way.

**Verification:** occupy all three slots, move the device clock forward month by
month, and confirm the month each slot frees.
**Source:** user decision, 2026-09-20. Six months rather than never, because a
device is resold and handed on, and because a person who loses their key must be
able to start again on the phone they own. The slot is about the rate at which
one device can mint accounts, not about how many accounts a person may have.

### AUTH-003 — A device with no free slot says when it will have one

If no slot on the device is free, then the system shall state that this device
has created its accounts recently, shall state the month in which the next slot
frees, shall state that favourites kept on this device work without an account
and are lost with it, and shall offer to sign in with an existing account key.

**Acceptance criteria**
- Given all three slots occupied, when creation is attempted, then the message
  names the month the earliest slot frees and offers to sign in instead.
- Given that message, when the person asked for an account in order to carry
  favourites beyond the device, then it also says that favourites on this device
  keep working and that they go with the device (FLOW-051), so the one thing they
  wanted is neither silently withheld nor silently promised.
- Given that message, when it is read, then no account is created, muted or
  otherwise: a slot bounds the rate at which one device mints accounts, and an
  account that consumed no slot would consume a nickname instead (DOM-001,
  OQ-009).
- Given that screen, when the person signs in with a key they hold, then they are
  signed in and no slot is consumed.
- Given that screen, when the person does nothing, then everything that works
  without an account still works.
- Given that screen, when the stated month arrives, then the same action creates
  an account without any further obstacle.

**Verification:** fill the slots, attempt creation, read the message, then sign
in from that screen.
**Source:** user decision, 2026-09-20; the sentence about favourites added
2026-09-21. The month is stated rather than withheld because CON-004 requires
automation to explain itself; a wall with no date reads as a defect. Creating a
muted account here was weighed and rejected: it would give the reader what they
asked for, and it would let one device mint nicknames without any bound, which is
exactly the narrowing the slot gate bought for OQ-009. The reader is told the
truth instead — their favourites already work, and they are tied to this phone.

### AUTH-004 — Without a verdict the account is created muted

If the server can be reached but a device verdict cannot be obtained, then the
system shall create a muted account: one that carries favourites beyond the
device, and whose recipes — written, edited and forked — are held on the device
alone. A muted account shall send no recipe to the server, and shall not attach
media, submit, request a private link or rate.

**Acceptance criteria**
- Given no verdict, when an account is created, then favourites carried by that
  account work exactly as they do for a full account.
- Given no network connection at all, when an account is asked for, then nothing
  is created, not even a muted account, and FLOW-003 states that a connection is
  needed: a muted account is still an account, with a unique nickname (DOM-001)
  and three keys the server issues (AUTH-014).
- Given a muted account, when the recipe card of a published recipe is opened,
  then the fork control is present and the rating controls are absent; a fork it
  makes is held on the device alone.
- Given a muted account, when the editor is opened, then recipes can be written,
  edited and deleted, they are held on the device alone and nothing of them
  reaches the server, and the controls for attaching media, submitting and
  requesting a private link are replaced by the statement of AUTH-006.
- Given a muted account, when it was created because the person asked to write
  or to fork, then that action proceeds on the device at once; when it was
  created because they asked to rate, then AUTH-006 states at that moment that
  the rating cannot proceed and why.
- Given a muted account, when it is created, then no slot on the device is
  occupied.
- Given any person with or without an account, when they report a recipe or an
  author, then the report is recorded and delivered as `abuse` requires.

**Verification:** attempt each of the four triggering actions with attestation
blocked, walk every control named here, and confirm at the server that no recipe
of the muted account arrived.
**Correction, 2026-09-27:** a muted account created from the write control was
told by AUTH-001 (2) that writing proceeds and by this requirement that it could
not (SD-019 item 1). By user decision of that date a muted account writes, edits
and forks freely on its own device, and nothing of it reaches the server until the
account is full (AUTH-005); what stays withheld is everything that uses the
server's storage or the moderator's queue — media, submission, the private link —
and rating. Media is withheld rather than held locally because every recipe moves
to the server when the account becomes full, and locally held media could exceed
the 150 MB an account may hold unpublished (MEDIA).
**Source:** user decision, 2026-09-20. Refusing to create anything was rejected:
an outage at the attestation service would then block the one thing a reader
might legitimately want — favourites that survive the phone. Creating a full
account was rejected: the gate would be worth nothing, since a sideloaded build
obtains no verdict by construction.

### AUTH-005 — A muted account becomes full when a verdict and a slot allow it

When a muted account attempts an action that AUTH-004 withholds, the system shall
obtain a device verdict, and shall make the account full and occupy a slot if the
verdict reports one free.

**Acceptance criteria**
- Given a muted account created while the attestation service was unreachable,
  when the service can be reached again and the person chooses the retry control
  of the statement that stands where the rating controls would be (AUTH-006),
  then a verdict is obtained, a slot is occupied, the account becomes full, and
  the like and dislike controls take the statement's place; no rating is given
  until the person chooses one.
- Given a muted account on a device with no free slot, when it attempts such an
  action, then it stays muted and AUTH-003 states the month a slot frees.
- Given a muted account whose verdict still cannot be obtained, when it attempts
  such an action, then it stays muted and AUTH-006 states why.
- Given an account that has become full, when it is used afterwards, then no
  further verdict is requested for it.
- Given a muted account holding recipes on its device, when it becomes full, then
  every one of them is sent to the server as FLOW-002 (4) sends offline changes,
  without the person being asked, and FLOW-005 marks each until it is accepted.
- Given a muted account whose recipe is in the editor, when a retry from the
  statement standing where the submission control would be makes the account
  full, then the submission control takes the statement's place and nothing is
  submitted; choosing it then shows GUIDE-001's statement as every submission
  does.
- Given any retry that makes the account full, when it completes, then every
  control AUTH-004 withheld is offered wherever it belongs and no withheld action
  is performed on the person's behalf.

**Verification:** create a muted account with the attestation service unreachable
and the server reachable, restore the service and retry from the statement in
place of the rating controls; repeat from the statement in place of the submission
control; repeat on a device with its slots full.
**Correction, 2026-09-27:** criterion 1 had the person tap the fork control, which
AUTH-004 (3) makes absent for a muted account, so no path reached this
requirement (SD-019 item 2). The attempt is made from the statement that replaces
the control (AUTH-006), by user decision of the same date.
**Corrected again 2026-09-27:** the same day's decision on SD-019 item 1 gave a
muted account the fork control back (AUTH-004 (3)), so criterion 1 retried from a
statement that no longer exists, and the verification created a muted account
offline, which AUTH-004 (2) forbids. Criterion 1 now retries from the statement in
place of the rating controls. By user decision of that date a retry that makes the
account full restores the withheld controls and performs nothing itself: a rating
has no value until the person picks one, and a submission must pass GUIDE-001.
Criteria 6 and 7 are new; the earlier ones keep their numbers. See
[ADR — a retry restores the control](../decisions/20260927-a-retry-restores-the-control.md).
**Source:** user decision, 2026-09-20. The verdict is asked for at the moment it
is needed rather than on every launch, which keeps the application independent of
an external service and keeps requests within the free quota it offers.

### AUTH-006 — A muted account is told what it is and what lifts it

While an account is muted, the system shall state that it is limited, shall state
which actions are withheld, and shall state what would lift the limit.

**Acceptance criteria**
- Given a muted account, when a withheld control would have been shown, then a
  statement takes its place rather than the control failing silently.
- Given that statement, when it is shown, then it carries a retry control; when
  the person chooses it, AUTH-005 is attempted for the action the withheld
  control stood for, and if the account becomes full the control returns in the
  statement's place without being acted on (AUTH-005 (7)).
- Given a retry that leaves the account muted, when it completes, then the
  statement remains and says why, by the no verdict or the no free slot case
  below.
- Given that statement, when it is read, then it says the device could not be
  checked and that a connection and an installation from the store lift the
  limit.
- Given a muted account on a device with no free slot, when the statement is
  shown, then it names the month a slot frees instead (AUTH-003).
- Given a full account, when any screen is shown, then no such statement appears
  anywhere.
- Given a muted account, when the statement is shown in the editor or in the
  own-recipes view, then it also says that its recipes are held on this device
  only, and are lost with the device or when another key signs in (AUTH-012).
- Given a muted account, when its own account screen is opened, then the same
  statement stands there, with the retry control, for as long as it is muted.

**Verification:** read every statement a muted account can reach, in both the no
verdict and the no free slot case.
**Source:** CON-004 — automation that changes what a user may do explains itself.
**Added 2026-09-27:** the retry control on the statement, which is the only path
by which a muted account reaches AUTH-005 (SD-019 item 2; user decision).

### AUTH-022 — A nickname is three English words: two adjectives and a noun

The system shall generate every nickname as an adjective, a second adjective and
a noun, in that order, each an English word in Latin letters beginning with a
capital letter, separated by single spaces, and shall display it in that form in
every interface language.

**Acceptance criteria**
- Given the creation screen, when a nickname is generated, then it has the form
  `Brave Copper Otter`: two adjectives, then a noun, each capitalised, separated
  by one space.
- Given an interface in Russian, when a nickname is displayed anywhere, then it is
  displayed in Latin letters exactly as generated and is neither translated nor
  transliterated.
- Given any two nicknames, when they are compared for uniqueness (DOM-001), then
  they are compared as the three words, so two nicknames never differ only in
  letter case or spacing.

**Verification:** generate a large sample and check form and case; display one
nickname in both interface languages.
**Source:** user decision, 2026-09-27 (SD-001). English words avoid the agreement
of gender and case that a Russian adjective owes its noun, which three
independent word lists cannot give; one form for everyone keeps a nickname the
same string on every screen. The size of the lists is OQ-009.

### AUTH-023 — The nickname words come from lists the product owner keeps by hand

The system shall draw every nickname word from one of two lists, adjectives and
nouns, that the product owner maintains outside the application, and shall admit
to a list only a word that is not profane or insulting and does not name a real
person, a brand, a nationality or ethnicity, a religion, a disease, a
sexuality, or a feature of a person's body or appearance.

**Acceptance criteria**
- Given the two lists, when every word is reviewed against the criteria above,
  then none fails any of them.
- Given any screen of the application, when it is enumerated, then none edits a
  word list; a list changes in the store, as the dictionaries do (DOM-014).
- Given a combination of three admitted words, when it is generated, then it is
  not checked against anything further: no list of forbidden combinations exists.
- Given a word removed from a list, when an account already holds a nickname
  containing it, then that nickname is unchanged (DOM-001) and the word is no
  longer drawn for new ones.

**Verification:** review of both lists against the criteria before the first
account is created and at every change; enumerate every screen.
**Source:** user decision, 2026-09-27 (SD-001). The lists are written before the
first account exists, because the first nickname issued is permanent.
**Cost, stated:** three harmless words can still combine into something that reads
badly, and nothing catches it; a stop list of combinations was rejected as a list
kept for ever against a rare case.
