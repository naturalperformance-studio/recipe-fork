---
slug: LEG
status: closed
updated: 2026-09-27
ids: [LEG-001, LEG-002, LEG-003, LEG-004, LEG-005, LEG-006, LEG-007, LEG-008,
  LEG-009, LEG-010]
---

# Legal

What an author grants by publishing, what they keep, when the terms are accepted
and what the product declares about who it is for.

Two things this section names are not settled, and neither of them is a missing
requirement: the published name has been cleared against nothing (OQ-016), and
what the data protection law of the countries actually distributed in obliges
before release needs a qualified lawyer (OQ-029). Both are release prerequisites
and both sit in MVP-003; what they hold up is the store listing, not the
specification. The section was `blocked` on them until 2026-09-21, when the
product owner decided that a question whose answer changes no requirement here
should not read as work left undone. The readiness banner still names both, for
as long as they are open, because they are classified **define before MVP**.

One requirement underpins the rest and is a consequence rather than a choice:
ST-001 makes publication permanent, so the licence it rests on has to be
irrevocable. An author who could withdraw the permission could make the product's
central promise unlawful in one message.

## What publishing grants

### LEG-001 — Publication grants the product a perpetual licence it cannot lose

When a recipe is published, the system's terms shall obtain from its author a
worldwide, royalty-free, irrevocable and perpetual licence to store, reproduce
and display that recipe and its media within the product, and that licence shall
survive the deletion of the author's account.

**Acceptance criteria**
- Given the published terms, when the grant is read, then it is worldwide,
  royalty-free, irrevocable and unlimited in time, and it is limited to storing,
  reproducing and displaying the work inside the product.
- Given an author who asks for a published recipe to be taken down, when the
  request is handled, then the licence is not the thing that stops them: ST-001
  is, and the claim route is ABUSE-007.
- Given an account deleted under RET-001, when its published recipes are read
  afterwards, then they are still served, because the licence outlived the
  account exactly as RET-002 says the recipes do.
- Given the grant, when its limits are read, then it conveys no right to sell,
  sublicense outside the product, or use the work in advertising, consistent
  with NGOAL-006.
- Given a recipe that was never published, when the terms are read, then no such
  licence is granted over it.

**Verification:** review of the published terms against this requirement, and
against ST-001, RET-002 and ABUSE-007.
**Source:** user decision, 2026-09-21. The irrevocability is not a preference:
without it the product could not keep the promise ST-001 already makes.

### LEG-002 — The author licenses the steps to everyone, so that forking rests on a grant

When a recipe is published, the system's terms shall obtain from its author a
worldwide, royalty-free, irrevocable and perpetual licence, granted to every user
of the product, to use and adapt that recipe's title, ingredient list and step
text within the product.

**Acceptance criteria**
- Given the published terms, when the fork grant is read, then it covers the
  title, the ingredient list and the step text, and names no other part.
- Given a published fork, when the inherited step text is examined, then it rests
  on this grant as well as on the exclusion of methods from copyright, and not on
  that exclusion alone.
- Given the grant, when its reach is read, then it is limited to use within the
  product and conveys no right to publish the text elsewhere.
- Given a recipe that was never published, when the terms are read, then no user
  holds this grant over it, which matches FORK-005 and ST-004: nobody else can
  fork it anyway.
- Given an author who wants their steps not to be forked, when the terms are
  read, then there is no such option, because there is no per-recipe flag that
  forbids forking.

**Verification:** review of the published terms; trace one published fork's
inherited text to the grant.
**Source:** user decision, 2026-09-21, closing a risk the product had already
accepted:
[ADR — a fork carries the method, not the expression](decisions/20260920-fork-carries-method-not-expression.md)
records that step wording is inherited although it is arguably expression, and
calls that the accepted residual risk. A grant from the author removes the need
to be right about where expression begins.

### LEG-003 — Media and description stay the author's

The system's terms shall convey no licence over a recipe's media or its
description to any user other than the product itself, and shall say so
explicitly.

**Acceptance criteria**
- Given the published terms, when the grants are enumerated, then media and
  description appear only in LEG-001's grant to the product and nowhere in
  LEG-002's grant to users.
- Given a fork by another account, when its content is examined, then it carries
  neither, which FORK-002 already requires; the terms and the behaviour say the
  same thing.
- Given a fork by the recipe's own owner, when it carries the media and the
  description (FORK-007), then no grant to another user is involved, because the
  author is the same person.
- Given a reader who copies a photograph out of the product, when the terms are
  read, then nothing in them permits it.

**Verification:** review of the published terms against FORK-002 and FORK-007.
**Source:** user decision, 2026-09-21. Open licensing of the whole recipe,
photographs included, was weighed and rejected: the product earns nothing from
the content (NGOAL-006), so there is nothing to buy with the author's pictures.

## Accepting the terms

### LEG-004 — The terms are accepted before anything is created, and they carry a version

The system shall require an account to accept the terms of use before it can
create, fork, submit or upload anything, shall record which version it accepted,
and shall require no acceptance from anyone who only reads.

**Acceptance criteria**
- Given account creation (AUTH-007), when the account is created, then the terms
  are presented and accepted there — on its first step, before any key is shown
  (AUTH-024) — and the accepted version is recorded with the account.
- Given a person with no account, when they browse, search, cook, favourite or
  report, then they accept nothing, because those need no account (AUTH-001,
  ACT-001).
- Given an account that has not accepted the current version, when it attempts to
  create, fork, submit, publish, rate or upload, then the attempt is refused and
  the terms are presented.
- Given the recorded acceptance, when it is inspected, then it is a version
  identifier and a date and nothing about a person (RET-010).
- Given the terms, when they are opened from inside the application, then they
  are readable in full without leaving it, in the interface language (L10N-002).

**Verification:** create an account and inspect the recorded acceptance; use
every reading action with no account and confirm nothing is asked.
**Source:** user decision, 2026-09-21; CON-006 requires acceptance before a user
creates or uploads content.

### LEG-005 — Changed terms are accepted again, and refusing costs only authorship

When the terms of use change, the system shall ask every account to accept the
new version before its next authoring action, and shall leave reading, cooking
and everything else that needs no account untouched for an account that has not
accepted.

**Acceptance criteria**
- Given a new version of the terms, when an account that accepted an older one
  opens the application, then it is not interrupted until it attempts an
  authoring action.
- Given such an account, when it attempts to create, fork, submit, publish, rate
  or upload, then the new terms are presented and the action proceeds once they
  are accepted.
- Given such an account that declines, when it uses the application afterwards,
  then it reads, cooks, opens its own recipes and keeps its favourites, and
  nothing of its own is destroyed or hidden.
- Given such an account, when it declines, then it is told plainly which actions
  are unavailable until it accepts, and the statement names them.
- Given its published recipes, when the terms change, then they stay published
  under the version in force when they were published, because LEG-001's licence
  was already irrevocable.

**Verification:** publish a new version of the terms and walk one account through
reading, declining and then accepting.
**Source:** user decision, 2026-09-21. Re-acceptance was chosen over accepting
once, so that a change to the terms is something the product can show it obtained
agreement to. The cost named when it was chosen — an author returning after a
long absence meets a screen instead of their recipe — is answered by the third
criterion: the screen stands between them and authoring, never between them and
what they wrote.

## Who the product is for

### LEG-006 — The terms declare an age and the product verifies nothing

The system's terms shall state that an account holder must be at least 16 years
old, and the system shall verify no user's age by any means.

**Acceptance criteria**
- Given the published terms, when the age is read, then it is stated as a
  condition of holding an account.
- Given any screen, when it is used, then nothing asks for a date of birth, an
  age or a document, because CON-001 admits no such field.
- Given a reader with no account, when they read and cook, then no age statement
  applies to them, because they hold nothing and agree to nothing.
- Given the declaration, when it is described anywhere, then it is described as a
  condition and never as a check the product performs.

**Verification:** review of the published terms and of every input in the
application.
**Source:** user decision, 2026-09-21. Sixteen is the cautious number: it matches
the default age of digital consent in the European framework, it sits above the
thirteen that Google Play's Families Policy turns on, and the product performs no
automated classification of images or video at all (NGOAL-005), so the material a
minor could meet here is bounded by nothing but a human moderator's queue.
Verification is impossible by construction, which is why the requirement says so
rather than implying a check.

### LEG-007 — The product declares itself not directed to children

The system shall be declared in the distribution channel as not directed to
children, and its target audience shall be declared as the ages the terms admit.

**Acceptance criteria**
- Given the store listing, when the target audience and content settings are
  completed, then the declared audience excludes children and matches LEG-006.
- Given that declaration, when the channel's Families Policy is checked against
  the product, then it does not apply, because the policy turns on an app
  including children in its target audience.
- Given the content rating questionnaire (CON-006), when it is answered, then it
  is answered for a product carrying user-generated content that is reviewed by a
  human before it becomes visible (MOD-001, MOD-003).
- Given any change to the audience declaration, when it is proposed, then it is a
  product decision recorded as an ADR, because it changes which policies bind.

**Verification:** review of the completed target audience and content settings
against the policy.
**Source:** user decision, 2026-09-21.
[Google Play — manage target audience and app content](https://support.google.com/googleplay/android-developer/answer/9867159?hl=en),
checked 2026-09-21: the Families Policy applies to apps whose target audience
includes children, and an app that is not directed to children is declared as
such in the listing. Re-verify when this section is next touched if that date is
more than six months old.

## The published texts

### LEG-008 — The terms and the privacy statement are reachable from outside the application

The system shall publish its terms of use and its privacy statement at addresses
reachable without the application, and the terms shall name the channel by which
a rights holder makes a claim.

**Acceptance criteria**
- Given the store listing, when its links are followed, then the terms and the
  privacy statement open without the application installed.
- Given the published terms, when they are read, then they name the claims
  address of ABUSE-007, and using it needs no account and no installed
  application.
- Given the privacy statement, when it is read, then it describes what the
  product does not collect (CON-001), what the logs hold and for how long
  (RET-008), the deletion routes (EXT-006, RET-001), and what reaches the claims
  address and what becomes of it — the one place where anybody's personal data
  arrives at the product owner, and the reason the address is part of the review
  OQ-029 waits for.
- Given those pages, when they are read by anyone, then they disclose nothing
  about any account, nickname or recipe, as EXT-006 requires of the deletion
  page.

**Verification:** open both addresses on a device without the application;
check the claims channel from an account-less context.
**Source:** CON-006 and user decision, 2026-09-21. These are the second and third
surfaces outside the application, after the account deletion page (EXT-006).

### LEG-009 — The published name is cleared before the listing is created

The published name of the product shall be cleared for use in the distribution
channel and against trademark registers in the product owner's jurisdiction
before the store listing is created.

**Acceptance criteria**
- Given the release, when the store listing is prepared, then the name has been
  checked against Google Play and against the relevant trademark registers, and
  the result is recorded.
- Given this specification, when it names the product, then it uses the working
  title and does not assert that it is the published name.

**Verification:** the recorded clearance result, dated, before the listing is
created.
**Source:** OQ-016. `Recipe Fork` is a working title; the product owner's market
work found `Forked` already taken in one store and did not check Google Play,
trademarks or name registrations (`market-context`).
**Note:** this requirement is complete as written. What OQ-016 holds up is the
store listing, which MVP-003 already makes a release prerequisite; the answer
will fill in a name, not change a sentence here.

### LEG-010 — Distribution is limited; access is not

The first release shall not be offered through the distribution channel in the
Russian Federation, and the system shall not restrict, degrade or refuse any
function on the basis of a user's location or network address.

**Acceptance criteria**
- Given the store listing, when its countries are set for the first release, then
  the Russian Federation is not among them.
- Given a request reaching the product from any network address, by any route,
  when it is served, then it is served exactly as any other: nothing is blocked,
  nothing is withheld and no function is reduced on account of where it came
  from.
- Given the product, when what it derives from a network address is enumerated,
  then it is nothing: no country, no region and no location is computed, stored
  or acted upon, and the address appears only in the server logs RET-008 keeps
  for fourteen days.
- Given a person who installs the application by any means the channel does not
  control, when they use it, then they are an ordinary user of the product in
  every respect.
- Given a decision to distribute in a country not on the list, when it is taken,
  then it is recorded as an ADR, because it changes which law the product is
  answerable to (OQ-029).

**Verification:** review of the listing's country set; reach the product from an
address in an excluded country and confirm every function behaves identically.
**Source:** user decision, 2026-09-21. Two different things were separated
deliberately. Where the product is *offered* is a choice the product owner makes
in the channel and can revisit. Where it is *reachable from* is not something
this product will police: blocking by address would mean deriving a location from
one, which is the sort of thing CON-001 and RET-010 exist to keep out, and it
would be aimed at people who have done nothing but install an application. See
[ADR — distribution is postponed, access is not withheld](decisions/20260921-distribution-is-postponed-access-is-not-withheld.md).
