---
slug: CON
status: closed
updated: 2026-09-28
ids: [CON-001, CON-002, CON-003, CON-004, CON-005, CON-006]
---

# Hard constraints

Constraints the product owner accepts as non-negotiable. Every one is binding on
all other sections: a requirement that violates a constraint is wrong, not a
trade-off. Behaviour derived from these constraints is specified in the sections
named alongside each one.

### CON-001 — No personal data is collected

The system shall not collect, store or transmit a user's real name, email
address, telephone number or avatar image at any point in its operation.

**Acceptance criteria**
- Given any screen in the application, when a user creates an account or edits
  it, then no input accepts a name, email address, telephone number or image
  representing the user.
- Given the stored data of any account, when its fields are enumerated, then
  none of them holds a value of those four kinds.

**Verification:** data model review against the stored record of a live account.
**Source:** draft sections 1, 2.1, 10; see [NGOAL-001](20-goals.md).

### CON-002 — Recurring infrastructure cost ceiling

Recurring infrastructure cost for the whole product shall not exceed USD 20 per
calendar month.

**Acceptance criteria**
- Given a calendar month of operation, when the provider invoices are summed,
  then the total is at most USD 20.
- Given a proposed feature, when its storage, egress and compute cost at the
  expected load is estimated, then the estimate is stated before the feature is
  accepted into scope.

**Verification:** monthly review of provider invoices.
**Source:** user decision, 2026-09-20 (the draft's "minimal infrastructure cost"
was not measurable and was replaced by this figure).
**Governs:** OQ-003 (media limits), OQ-004 (retention).
**Qualified 2026-09-27:** where the import feature is present, the language
model's cost is not part of this figure and is capped on its own at USD 50 by
IMPORT-020; see
[ADR — the model has its own fifty dollars](decisions/20260927-the-model-has-its-own-fifty-dollars.md).
The first release has no import and this ceiling binds it unchanged.

### CON-003 — Immutability after first external visibility

While a recipe is accessible to any user other than its owner, the system shall
reject every change to that recipe's content. A moderator acting in role is not
such a user — see
[ADR — immutability trigger](decisions/20260920-immutability-trigger.md).

**Acceptance criteria**
- Given a recipe that has become accessible to anyone but its owner, when any
  edit is attempted through any interface, then the edit is rejected and the
  stored content is unchanged.
- Given such a recipe, when the owner opens it, then the interface offers
  forking and does not offer editing.
- Given serverless mode (FLOW-061), when a recipe is exported to a file
  (FLOW-063), then it is not thereby frozen: a file passes outside the product,
  and freezing exists to protect what other recipes may name on the server,
  which no longer exists.

**Verification:** attempted edit on a published recipe and on a recipe shared by
private link, through the application and directly against the service.
**Source:** draft sections 1, 4.2; see [NGOAL-004](20-goals.md).
**Detailed rules:** section [`entity-states`](60-entity-states/00-index.md); ST-003 states
when a recipe freezes and when it becomes editable again.

### CON-004 — Derived values are always marked as derived

Where the system displays a quantity it derived rather than the quantity its
author entered, the system shall mark that quantity as derived and, when the
derivation rounded the value, show the direction of the rounding.

**Acceptance criteria**
- Given a recipe scaled to a serving count other than the author's, when its
  ingredient list is displayed, then every quantity is visibly marked as derived,
  including one whose displayed value equals the authored quantity.
- Given a derived quantity that was rounded, when it is displayed, then the
  interface states whether the displayed value is above or below the exact
  computed value.
- Given a recipe displayed at its authored serving count, when its ingredient
  list is displayed, then no quantity is marked as derived.

**Verification:** manual UI check across the three unit types.
**Source:** draft section 1 ("any automation is explained in the interface"),
section 3.2.
**Detailed rules:** section `scaling-rules`.
**Correction, 2026-09-27:** criterion 1 marked a quantity by its value ("every
quantity that differs from the authored quantity") while the statement marks by
provenance ("a quantity it derived"). By user decision of the same date provenance
governs: at any scale factor other than 1 every quantity is derived (SCALE-004;
[ADR — derived by provenance](decisions/20260927-derived-by-provenance.md)).

### CON-005 — Native Android is the only client surface in the MVP

The product shall be delivered as a native Android application, and the MVP
shall have no other client surface.

**Acceptance criteria**
- Given the MVP release, when its artefacts are enumerated, then the only client
  is an Android application package.
- Given a private link, when it is opened on a device without the application
  installed, then the recipe is not rendered.

**Verification:** release artefact review.
**Source:** user decision, 2026-09-20, resolving OQ-001.
**Consequence:** see [ADR 20260920 — native Android MVP](decisions/20260920-native-android-mvp.md).

### CON-006 — Google Play policy is binding

Where the product is distributed through Google Play, the system shall satisfy
the Google Play User Generated Content policy before release.

The policy obligations this creates are specified as behaviour elsewhere and are
listed here only so that their origin is traceable: in-app reporting of content
and of authors, in-app blocking of authors (section `abuse`), acceptance of
terms of use before a user creates or uploads content, and a content rating
questionnaire answered for user-generated content (section `legal`). The policy
defines user-generated content as "content that users contribute to an app, and
which is visible to or accessible by at least a subset of the app's users"; a
recipe held on the device alone is none of that, so the terms are accepted when an
account is created, before any recipe can be sent (LEG-004).

**Acceptance criteria**
- Given a release candidate, when it is checked against the policy obligations
  listed above, then each obligation has an implemented, reachable counterpart
  in the application.

**Verification:** policy checklist review against the release candidate.
**Source:** [Google Play — User Generated Content](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en),
checked 2026-09-20; the definition quoted above checked 2026-09-28. Re-verify when this section is next touched if that date is
more than six months old.
