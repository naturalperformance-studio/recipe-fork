---
slug: GLOSS
status: closed
updated: 2026-09-27
ids: []
---

# Glossary — People, moderation, ratings, running the product, the device

Part of the [glossary](00-index.md); holds no requirements.

## People and accounts

**Account** — created only when an action requires one — writing, forking,
rating, or carrying favourites beyond the device — and carrying no personal data
of any kind (ACT-001, AUTH-001, CON-001). Not an identity: a person on two
devices holds one account by holding one key.

**Person with no account** — an actor, not an absence: they browse, search,
cook, keep favourites on the device, report and block, and the product creates
nothing for them (ACT-001, FLOW-051).

**Account key** — the string that holds an account, offered both to copy and to
save as a file. Whoever holds it holds the account; losing it, with no device
still signed in, loses the account and there is no recovery (NGOAL-003). It is
shown in full exactly once, at creation, and afterwards only masked with a
control that copies it whole (AUTH-007, AUTH-019). Entering it signs in and
replaces whatever account was active, wiping what the device held for it
(AUTH-009, AUTH-012). Its form and cryptography are an engineering choice; the
properties it must have are AUTH-014.

**Deletion key** — a second, smaller secret issued beside the account key,
unique and unguessable. It is the only thing the deletion page accepts, and it
can destroy an account but never take it over (AUTH-017, EXT-006).

**Device slot** — one of three places a device has for creating an account. A
slot is occupied by creating an account and is free again six calendar months
later; signing in with a key consumes none (AUTH-002, AUTH-009). The slots are
held by the attestation service, not by the product, and yield no device
identifier (AUTH-015).

**Device verdict** — the answer the attestation service gives about the device
the application is running on, including which slots are free. Nothing from it
is stored (AUTH-015).

**Muted account** — an account created where the server answered and no device
verdict could be obtained. It carries favourites beyond the device, and writes,
edits and forks recipes that stay on the device alone; it attaches no media, does
not submit, request a private link or rate, and sends no recipe to the server
until it is full.
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
RET-003).

**Owner** — the account that created a recipe, for that recipe's whole life.
Ownership is never transferred (ACT-002). Not an actor: a role an account holds
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

**Threshold** — the value at which a signal stops being information and becomes
something to decide. Crossing one marks the signal and sends one alert
(OBS-003, OBS-004).

**Alerting channel** — the one outbound channel that carries a crossed threshold
to the product's operator. It carries a signal's name and value and nothing
else, and it accepts nothing back (OBS-004, OBS-005).

## The device

**Held on the device** — content stored locally so it opens without a
connection: the account's favourites, both dictionaries, and every recipe the
account owns that the server has not accepted (`flows`, FLOW-001, FLOW-009).

**Accepted by the server** — said of a recipe whose every local change, text and
media, has reached the server. Nothing is submitted for moderation before it is
(FLOW-005, FLOW-006).

**Favourite** — a recipe the reader marked to take with them. Its text is always
held on the device; whether its media comes too is one setting covering all
favourites (FLOW-009, FLOW-011). Without an account a favourite lives on that
device and nowhere else, and is lost with it; an account carries its favourites
to every device it signs in to (FLOW-051, FLOW-052).
