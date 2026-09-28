---
slug: GLOSS
status: closed
updated: 2026-09-29
ids: []
---

# Glossary — People, moderation, ratings, running the product, the device

Part of the [glossary](00-index.md); holds no requirements.

## People and accounts

**Account** — created only when an action requires one — sending a recipe to
the server, rating, or carrying the recipe book beyond the device — and carrying
no personal data
of any kind (ACT-001, AUTH-001, CON-001). Not an identity: a person on two
devices holds one account by holding one key.

**Person with no account** — an actor, not an absence: they browse, search,
cook, write, edit, fork and delete recipes on the device, keep favourites there,
report and block, and the product creates nothing for them; what they make is in
the pool (ACT-001, FLOW-002, FLOW-051).

**Pool** — what is made on a device while no account is active: recipes,
favourites and blocked authors that belong to no account. Shown whenever no
account is active, and offered once to each account that becomes active on the
device (AUTH-026).

**Account list** — the accounts signed in on a device, each holding its own
content apart from the others'; the active one is switched by choosing another
(AUTH-011). An account leaves it only when removed (AUTH-013).

**Account key** — the string that holds an account, offered both to copy and to
save as a file. Whoever holds it holds the account; losing it, with no device
still signed in, loses the account and there is no recovery (NGOAL-003). It is
shown in full exactly once, at creation, and afterwards only masked with a
control that copies it whole (AUTH-007, AUTH-019). Entering it signs in and makes
that account the active one beside any others on the device, deleting nothing
(AUTH-009, AUTH-011, AUTH-012); a key already used on the device opens its account
there without the server (AUTH-027). Its form and cryptography are an engineering choice; the
properties it must have are AUTH-014.

**Deletion key** — a second, smaller secret issued beside the account key,
unique and unguessable. At creation it is shown in full beside the account key
with a copy control of its own, and the key file carries both. It is the only
thing the deletion page accepts, and it can destroy an account but never take it
over (AUTH-017, EXT-006).

**Device slot** — one of three places a device has for creating an account. A
slot is occupied by creating an account and is free again six calendar months
later; signing in with a key consumes none (AUTH-002, AUTH-009). The slots are
held by the attestation service, not by the product, and yield no device
identifier (AUTH-015).

**Device identifier** — anything derived from a device, its hardware or its
operating system, anything taken from a device verdict, and any identifier a third
party issues to a device, such as a delivery token. The product stores none
(AUTH-015, CON-001).

**Sign-in secret** — what a device presents on every request after signing in,
instead of the account key: a random secret the server mints at a sign-in. It is
not a device identifier. The server keeps a record of it from which the secret
cannot be rebuilt, and deletes that record after six calendar months unused, when
the device signs in again with the key it holds (AUTH-025). Not to be confused
with a session, the span of use FLOW-058 defines.

**Device verdict** — the answer the attestation service gives about the device
the application is running on, including which slots are free. Nothing from it
is stored (AUTH-015).

**Muted account** — an account created where the server answered and no device
verdict could be obtained. It carries favourites beyond the device and does on
the device everything anyone does there; it does not send a recipe, submit,
request a private link or rate.
It becomes full when a verdict and a free slot allow (AUTH-004, AUTH-005). With
no connection at all no account is created, muted or otherwise (FLOW-003).

**Nickname** — a random three-word name — two English adjectives and a noun, as
`Brave Copper Otter`, drawn from lists the product owner keeps (AUTH-022,
AUTH-023) — generated at the creation screen, where it may be replaced by another
generated one any number of times. Once the account
exists it is globally unique, permanent, and the only thing about an account that
is ever shown (DOM-001, AUTH-018).

**Deleted account** — an account its holder destroyed. Its published recipes,
the ratings it gave and the reports it filed stay; everything unpublished, its
favourites and its keys are gone, its nickname is never reissued, and its
recipes show that the author is deleted instead of a nickname (RET-001, RET-002,
RET-003). What it held on any device, recipes never sent included, is deleted
there too (RET-001).

**Owner** — the account in whose recipe book a recipe was created, for that
recipe's whole life; a recipe created in the pool has none until the pool is
moved into an account, or until its own sending creates one. Ownership is
otherwise never transferred (ACT-002). Not an actor: a role an account holds
with respect to one recipe.

**Reader** — an account looking at a recipe it does not own. Also a role, not an
actor (`actors`).

**Moderator** — an account holding an assignable role: it approves, rejects and
removes, and it can never alter content (ACT-003, ACT-004). The role reaches its
holder's own work through the moderator's own actions and through nothing else:
they still keep their drafts and still rate no recipe of their own (`actors`,
MOD-013, MOD-014).

**Author's screen** — the screen a nickname leads to, holding that author's
published recipes and the acts aimed at an author rather than at one recipe:
reporting them, blocking them, and, for a moderator, banning them (FLOW-055,
FLOW-056). Not a profile — it carries the nickname and the work and nothing else
(CON-001, NGOAL-002).

**Established account** — an account with at least one published recipe. Only
their reports count towards automatic hiding (ABUSE-003).

**Publishing ban** — a moderator stopping an account from putting anything into
the catalogue: its submissions, and the publication of a recipe it already shares
by a private link. It touches nothing else the account does — revoking and
reissuing that link included — and leaves its published recipes in place
(ACT-005, ACT-006). It is applied and lifted on the author's screen (FLOW-056).

## Moderation and abuse

**Moderation submission** — the record created when a recipe is sent for review,
carrying the trigger that caused it (ABUSE and `moderation`). Approval is final
for that content, which can never change afterwards (MOD-007).

**Trigger** — which act caused a submission: `publication` or `private_link`.
Both make the recipe externally visible, so both go through moderation (MOD-001).

**Report** — one account marking a published or link-shared recipe in exactly
one of two categories: use of someone else's content, or danger to health
(ABUSE-002).

**Counted report** — a report from an established account, one per account per
recipe. Three of them hide a recipe automatically (ABUSE-003, ST-006).

**Claims address** — the one electronic mail address the terms, the privacy
statement and the store listing name, by which someone who is not a user of the
product claims rights over content in it. It needs no account and no installed
application, it is answered within the period MOD-005 states, and it is the only
place anybody's personal data reaches the product owner (ABUSE-007).

**Blocking** — one account hiding another author's recipes from its own
catalogue and search. It does not hide them from anybody else and is not a
report (ABUSE-005).

## Ratings

**Rating** — one account's like or dislike of one published recipe. An account
holds at most one rating per recipe, may change it and may withdraw it by
repeating it (RATE-001, RATE-002). An author never rates their own recipe
(RATE-004).

**Withdrawn rating** — a rating the account took back. It counts for nothing:
not in the two counts, not in either ordering, and the recipe may end up rated
by nobody again (RATE-002, RATE-011).

**Net score** — likes minus dislikes. It is what `highest rated` orders by, and
it is never displayed: the reader is shown the two counts (RATE-007, RATE-010).

## Running the product

**Operational signal** — a total or a share the product measures so that a slow
problem is visible before it becomes an urgent one. Every signal names the
decision that asked for it, and none is about a person (OBS-001, OBS-007).

**Openings** — how many times a published recipe was opened by somebody other
than its owner and the server heard about it. Not readers: the product cannot
tell one reader from another, and an opening without a connection is never
counted. Shown to the recipe's owner alone (ANL-005, ANL-006, ANL-007).

**Additions to favourites** — how many times a published recipe was put into
somebody's favourites, with an account or without one. It counts additions and
never falls when a recipe is removed again, because a reader with no account
cannot report a removal. Shown to the recipe's owner alone (ANL-005, ANL-006).

**Forks made** — how many times people other than its owner forked a published
recipe while the server could be reached, whether or not the fork was ever sent.
Never falls. Shown to the recipe's owner alone; the product owner sees the totals
and how many recipes were forked at least once (ANL-008). Not the fork count of a
catalogue entry, which counts published forks (CAT-005).

**Threshold** — the value at which a signal stops being information and becomes
something to decide. Crossing one marks the signal and sends one alert
(OBS-003, OBS-004).

**Alerting channel** — the one outbound channel that carries a crossed threshold
to the product's operator. It carries a signal's name and value and nothing
else, and it accepts nothing back (OBS-004, OBS-005).

## The device

**Held on the device** — content stored locally so it opens without a
connection: the recipe book of every account on the device and of the pool, both
dictionaries, and every recipe written there, in every state (`flows`, FLOW-001,
FLOW-009, FLOW-038).

**Sent** — said of a recipe its owner sent to the server (FLOW-059); what the
server holds of a recipe is what was last sent. A recipe is never sent, sent, or
sent with changes not sent, and FLOW-005 marks which. Nothing is submitted for
moderation before every change is sent (FLOW-006). Replaces "accepted by the
server" (2026-09-28).

**Recipe book** — the interface name, "My recipe book" («Моя книга рецептов»), of
what this specification calls the favourites section: the recipes a person found
and marked, and the recipes they wrote (FLOW-034).

**Serverless mode** — the state of a release built for after the product owner's
server is switched off: everything that needs the server is gone, everything the
device does alone stays, and recipe files appear. Switched on by a release, never
by the application (FLOW-061, FLOW-062).

**Recipe file** — a recipe exported to a file in serverless mode, carrying its
text, the media the device holds, its author's nickname and its direct parent
(FLOW-063). Imported, it becomes a found recipe — a favourite — whose stated
author is shown as not verified (FLOW-064). Not to be confused with the import of
a post from a social network (`import`).

**Session** — the time from a start of the application that has nothing to
restore until the next such start. Only the reader closing the application or
a restart of the device ends one; the operating system ending the process while
the application is away does not (FLOW-058).

**Favourite** — a recipe the reader marked to take with them, or imported from a
file (FLOW-064); in the interface, a found recipe in "My recipe book". Its text is
always held on the device; whether its media comes too is one setting covering all
favourites, beside an action that clears it (FLOW-009, FLOW-011, FLOW-060).
Without an account a favourite lives in the pool of that device and nowhere else,
and is lost with it; an account carries its favourites to every device it signs
in to, by themselves, without being sent (FLOW-051, FLOW-052).
