---
slug: A11Y
status: closed
updated: 2026-10-03
ids: [A11Y-001, A11Y-002, A11Y-010, A11Y-003, A11Y-004, A11Y-005, A11Y-006,
  A11Y-007, A11Y-008, A11Y-009]
---

# Accessibility

A fixed list of commitments, each with a threshold and a way to check it, rather
than a claim of conformance to a standard. The reason is stated once, in
A11Y-001, and the thresholds themselves are taken from the standards anyway.

Everything here is measured on the reference configurations of QUAL-001, on the
device the product actually has to work on.

### A11Y-001 — The commitment is this list, and conformance is not claimed

The product shall satisfy A11Y-002 to A11Y-010, and shall not state or imply
conformance to WCAG 2.2 at any level.

**Acceptance criteria**
- Given the store listing, the terms of use or any screen, when accessibility is
  described, then it describes what the product does and names no conformance
  level.
- Given each requirement in this section, when it is verified, then it is
  verified by the method it states and not by a conformance audit.
- Given a criterion of WCAG 2.2 that this section does not cover, when it is
  raised, then it is a candidate for a new requirement here, not a defect against
  a claim the product never made.

**Verification:** review of every place the product describes itself.
**Source:** user decision, 2026-09-21. Level AA contains criteria that do not
apply to this product at all, and claiming conformance would mean either proving
the whole of it or publishing a claim with exceptions attached — an obligation a
single product owner cannot honestly discharge. A list of thresholds can be
checked on the bench of QUAL-001 by the person who has to check it.

### A11Y-002 — Contrast

The system shall render text at a contrast ratio of at least 4.5:1 against its
background, large text at at least 3:1, and the visible boundary of every
interactive control and every icon that carries meaning at at least 3:1 against
what is adjacent to it.

**Acceptance criteria**
- Given any screen, when its text is measured, then every string is at 4.5:1 or
  better, and text at 18pt, or 14pt bold, and above is at 3:1 or better.
- Given any interactive control, when its boundary is measured against the
  surface behind it, then it is at 3:1 or better.
- Given an icon that carries meaning rather than decoration — the fork marker
  (CAT-004), the derived-quantity mark (CON-004), the safety flag (FLOW-018) —
  when it is measured, then it is at 3:1 or better.
- Given each theme the product ships (A11Y-010), when it is measured, then the
  same ratios hold in it.
- Given a disabled control, when the screen is measured, then the control and its
  text are exempt from these thresholds, as WCAG exempts inactive components; its
  touch target is still A11Y-003's.

**Verification:** measure every distinct text and control style against its
background with a contrast tool, in each theme the product ships.
**Source:** the thresholds are those of WCAG 2.2 success criteria 1.4.3 and
1.4.11 — 4.5:1 for normal text, 3:1 for large text, 3:1 for user interface
components and meaningful graphics
([Understanding SC 1.4.11, W3C](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html),
checked 2026-09-21). The numbers are borrowed; the conformance claim is not
(A11Y-001). Amended 2026-10-03 (sync v0.3–v0.14): disabled controls are exempt, as
in WCAG, and the ratios hold in every shipped theme — req-forge proposal from the
sync of v0.3–v0.14, accepted by the user, 2026-10-03.

### A11Y-010 — A light and a dark theme, chosen by the reader

The system shall present its interface in a light theme and in a dark theme, and
shall offer a setting that chooses light, dark, or as the device is set.

**Acceptance criteria**
- Given the setting at "as the device is set", when the device switches between
  light and dark, then the interface follows it.
- Given the setting at light or at dark, when the device switches, then the
  interface stays in the chosen theme.
- Given either theme, when any screen is shown, then its colours, type and shape
  come from the one theme definition maintained in the product's source, and no
  screen carries colours of its own.
- Given either theme, when it is measured, then A11Y-002 holds in it.

Themes made by users are outside the vision for now.

**Verification:** switch the setting through its three values and the device
between light and dark, and walk the main screens in each theme.
**Source:** req-forge proposal from the sync of v0.3–v0.14, accepted by the user,
2026-10-03 (user decision, 2026-10-03; serverless roadmap F1).

### A11Y-003 — Touch targets

The system shall give every interactive element a touch target of at least 48 by
48 density-independent pixels, and at least 56 by 56 on the cooking card.

An interactive element is anything that answers a tap, a long press, a drag or a
swipe with an action of the product — a link inside text included, and a control
included while it is disabled — and any container that scrolls. Text whose only
answer is focus, selection or copying is not one.

**Acceptance criteria**
- Given any screen, when each interactive element other than a container that
  scrolls is measured, then its touch target is at least 48 by 48 dp, whatever the
  element looks like; a container that scrolls is measured by criterion 5 alone.
- Given the cooking card, when its controls are measured — the timer controls and
  the serving control — then each is at least 56 by 56 dp. There is no step
  advance among them: the card is one scrolling screen (`flows`, 30-cooking).
- Given two adjacent targets, when they are measured, then they do not overlap
  and neither is smaller than its rule because of the other.
- Given a control smaller than its target by design, when it is touched near its
  edge, then it still responds, because the target is the rule and not the
  drawing.
- Given a container that scrolls, when it is measured across the direction it
  scrolls in, then it is at least 48 dp — a strip that scrolls sideways at least
  48 dp tall, a list that scrolls down at least 48 dp wide — and its length along
  that direction is not measured.
- Given a disabled control, when it is measured, then its touch target is the one
  it has when enabled, and it does not change size when it becomes enabled.
- Given text whose only answer to a touch is focus, selection or copying, when the
  screen is measured, then that text is not measured; a separate control that
  copies it is, and a link inside text is.

**Verification:** measure every interactive element on the bench, with the
cooking card measured separately; include every scrolling container across its
scroll direction and every control in its disabled state.
**Correction, 2026-10-02:** "interactive element" was defined nowhere, and gate 2
of T-044 (`docs/product/verdicts/20261002-T-044-gate2.md`, item b) named three
cases it could not read: a scrolling container, a disabled control, and text
that only takes focus or selection. By user decision of that date the definition
and criteria 5 to 7 are added. The same day criterion 1 still read as measuring a
scrolling container 48 by 48 (SD-047); the user's decision was across the scroll
direction only, so criteria 1 and 5 now say so. See
[ADR — what an interactive element is](decisions/20261002-what-an-interactive-element-is.md).
**Source:** 48 by 48 dp is the Android platform guidance, chosen because it never
falls below about 7 mm on any screen size or density
([Touch target size, Android Accessibility Help](https://support.google.com/accessibility/android/answer/7101858?hl=en),
checked 2026-09-21). The larger figure on the cooking card is a product decision
of 2026-09-21 and is explained in A11Y-007.

### A11Y-004 — Text scale

The system shall follow the device's font scale setting up to 200 per cent, and
shall lose no content and no function at that scale.

**Acceptance criteria**
- Given the device font scale at 200 per cent, when any screen is opened, then
  every string is readable and none is cut off, overlapped or replaced by an
  ellipsis that hides a distinct meaning.
- Given that scale, when any screen is used, then every control remains reachable
  and operable, by scrolling where necessary.
- Given a recipe at that scale, when it is cooked, then the step text, the
  quantities and the timers are all present.
- Given the scale set back, when a screen is reopened, then it returns to its
  ordinary size without a restart.

**Verification:** walk every screen at 100 and at 200 per cent on both profiles
of QUAL-001.
**Source:** user decision, 2026-09-21.

### A11Y-005 — The screen reader works everywhere, including the editor

The system shall be operable end to end with the platform screen reader, on every
screen, including the editor and the moderator's queue.

**Acceptance criteria**
- Given any interactive element, when the screen reader reaches it, then it
  announces what the element does, not what it looks like, and an element with no
  visible label carries one anyway.
- Given any screen, when it is traversed, then the reading order follows the
  order the screen is meant to be used in, which for the editor is the order
  FLOW-024 fixes.
- Given a change the user did not initiate — a timer ending, a submission
  accepted, a quantity recalculated — when it happens, then it is announced.
- Given the editor, when a recipe is written with the screen reader alone, then a
  complete recipe can be authored, submitted and published without sight.
- Given an image, when the reader reaches it, then it is announced as the
  author's photograph or video and is not described, because the product
  inspects no media (NGOAL-005) and would be inventing a description.
- Given the moderator's queue, when it is traversed, then it is operable too, so
  that the role is not closed to a moderator who uses the reader.

**Verification:** author, submit and publish one recipe, then cook another, with
the screen reader on and the screen unseen.
**Source:** spec author's decision on 2026-09-21, delegated by the product owner.
Reading-only access was the cheaper option and is the usual one, and it was
rejected because of what it decides: the product's central mechanic is forking,
which is an act of authorship, so a reader-only commitment would make a group of
people permanent consumers of a product built on everyone being an author. The
marginal cost over reading-only is labels on editor controls, which correctness
needs anyway, and the alternative to dragging that A11Y-006 requires.

### A11Y-006 — Nothing is done only by dragging

The system shall offer every action that can be performed by dragging through an
action that needs no drag.

**Acceptance criteria**
- Given the editor's reordering of steps and ingredients (FLOW-025), when a
  screen reader or a user who cannot drag operates it, then each item can be
  moved up and down by a discrete action.
- Given any gesture in the product that moves or reorders something, when it is
  enumerated, then a non-gesture equivalent exists and reaches the same result.
- Given the moderator's decisions by gesture (FLOW-049), when they are made
  without a gesture, then the same confirmation is asked for.
- Given the device setting for the drag handle's side, when an author sets it to
  the right or to the left, then the drag handles in the editor sit on that side,
  so a left-handed author can drag with the hand they use.

**Verification:** reorder a recipe's steps and ingredients and decide a queue
entry using only discrete actions.
**Source:** user decision, 2026-09-21. Dragging is the one interaction in the
editor that has no accessible form of its own, so it needs an alternative rather
than an improvement; amended 2026-10-03 (sync v0.3–v0.14): the drag handle's side
is a device setting for left-handed use — req-forge proposal from the sync of
v0.3–v0.14, accepted by the user, 2026-10-03.

### A11Y-007 — The cooking card is built for hands that are busy

While a recipe is being cooked, the system shall present its text at a larger
default size than elsewhere, shall give its controls the larger targets of
A11Y-003, and shall keep the timer audible when the screen is off.

**Acceptance criteria**
- Given the cooking card, when it is opened with the device at its default font
  scale, then its step text is larger than body text elsewhere in the product.
- Given the cooking card, when its controls are measured, then A11Y-003's larger
  figure holds.
- Given notifications are permitted, when a timer ends while the screen is off or
  the application is closed, then it announces itself audibly from the device's
  own facility (FLOW-013, EXT-009); with notifications refused FLOW-013 (5)
  governs and the reader has been told so when starting it (FLOW-013 (9),
  (10)).
- Given the card, when it is left untouched, then the screen is kept awake as
  FLOW-014 requires and this requirement adds no second rule about it.
- Given the card at 200 per cent font scale, when it is used, then A11Y-004 holds
  here too and the larger default is not an exception to it.

**Verification:** cook a recipe on the bench with the device at default scale and
at 200 per cent, with the screen allowed to switch off during a timer.
**Correction, 2026-09-27:** criterion 3 carried no exception for a refused
notification permission (SD-021 item 3); by user decision of that date nothing
sounds then, and the reader is told at the timer's start (FLOW-013).
**Source:** user decision, 2026-09-21. The cooking card is used at arm's length,
on a surface, by someone whose hands are wet or covered in flour, and it is the
one screen where that is the normal case rather than the awkward one. Voice
control of the steps was weighed and rejected: it needs either a fifth external
dependency, which EXT-001 closes, or an offline engine weighing tens of
megabytes.

### A11Y-008 — Colour is never the only carrier

The system shall convey no state, difference or warning by colour alone.

**Acceptance criteria**
- Given a derived quantity (CON-004), when it is displayed, then what marks it as
  derived is not colour alone.
- Given a recipe marked as a fork (CAT-004), a recipe carrying the safety flag
  (FLOW-018) and a recipe whose author has published a replacement (FORK-009),
  when each is shown, then each is distinguishable without colour.
- Given an own recipe's state on its entry (FLOW-036), when it is shown, then the
  state is named and not only coloured.
- Given any warning or error, when it is shown, then it carries text.

**Verification:** view every screen rendered without colour and confirm every
distinction above survives.
**Source:** user decision, 2026-09-21. This product marks a great deal — derived,
forked, flagged, replaced, pending — and marking is precisely where colour alone
is the tempting shortcut.

### A11Y-009 — Motion on screen can be stopped and does not start uninvited

The system shall give every step video that plays by itself a control that pauses
it, and shall start no such video by itself while the device's system animations
are turned off.

**Acceptance criteria**
- Given a step video playing on screen (MEDIA-015), when the reader reaches it by
  touch or with the screen reader, then a control pauses it, and the screen reader
  announces that control by what it does.
- Given the system's animations turned off, when a step video comes on screen,
  then it stays still until the reader starts it.

**Verification:** cook a recipe with step videos with the system's animations on
and off, by touch and with the screen reader.
**Source:** req-forge proposal from the sync of v0.3–v0.14, accepted by the user,
2026-10-03. The intent is that of WCAG 2.2 success criterion 2.2.2 — moving content
that starts by itself can be paused — borrowed as a commitment, not as a
conformance claim (A11Y-001).
