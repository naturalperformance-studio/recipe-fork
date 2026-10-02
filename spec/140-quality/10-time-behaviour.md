---
slug: QUAL
status: closed
updated: 2026-09-27
ids: [QUAL-001, QUAL-002, QUAL-003, QUAL-004]
---

# Time behaviour

### QUAL-001 — Two reference configurations

Time behaviour shall be measured on the two configurations below, and every
threshold in this section shall name which of them it applies to.

| Profile | Device | Network |
|---|---|---|
| `modest` | an Android phone running the lowest version the product supports (QUAL-005), with no more than 4 GB of memory, of a model first sold at least three years before the measurement | a mobile connection constrained to 1.5 Mbit/s downstream, 0.5 Mbit/s upstream and 300 ms round trip |
| `typical` | a mid-range Android phone of a model first sold within two years of the measurement | a stable connection of at least 10 Mbit/s downstream and no more than 60 ms round trip |

**Acceptance criteria**
- Given a measurement of any threshold in this section, when it is recorded, then
  it names the profile it was taken on and the exact device model and connection
  used.
- Given a threshold that names a profile, when it is verified, then it is
  verified on that profile and a result from the other profile does not satisfy
  it.
- Given a measured operation, when the time is taken, then it starts at the
  user's action and ends when the content asked for is on screen and readable —
  not when a placeholder, a spinner or a partially drawn list is shown.
- Given a measurement, when it is taken, then it is the median of at least ten
  runs on a device that has been running the application for at least a minute,
  so that a first-launch cost is not measured as ordinary behaviour.

**Verification:** review of the release measurement report against this table.
**Source:** user decision, 2026-09-21 — both profiles are measured, because the
cheap phone and the mid-range phone are the common cases and a flagship that
clears them is not in question. The device classes are the product owner's; the
numeric network figures are the spec author's concretisation of "slow mobile
internet" and "stable 4G", written down so that two engineers measure the same
thing, and open to being replaced by figures the product owner prefers.

### QUAL-002 — Response times on `typical`

While the product is measured on the `typical` profile, the system shall complete
each operation below within its threshold.

| Operation | Threshold |
|---|---|
| Open a recipe held in favourites, with no connection | 1 s |
| Recalculate a recipe to a different serving count | 200 ms |
| Show the first screen of catalogue results | 2 s |
| Show the results of a search | 2 s |

**Acceptance criteria**
- Given the `typical` profile, when each operation is run as QUAL-001 requires,
  then its median is at or below the threshold above.
- Given the recalculation threshold, when it is measured, then it is measured on
  a recipe at the caps of DOM-012 — 50 ingredients and 50 steps — and not on a
  small one.
- Given the catalogue and search thresholds, when they are measured, then the
  store holds enough recipes that a result set has to be selected rather than
  returned whole.
- Given any operation above the threshold, when the release is reviewed, then it
  is a defect and not a known limitation.

**Verification:** bench measurement on the `typical` profile, recorded in the
release measurement report.
**Source:** user decision, 2026-09-21. These are the figures the product aims at.

### QUAL-003 — Response times on `modest`

While the product is measured on the `modest` profile, the system shall complete
each operation below within its threshold.

| Operation | Threshold |
|---|---|
| Open a recipe held in favourites, with no connection | 2 s |
| Recalculate a recipe to a different serving count | 500 ms |
| Show the first screen of catalogue results | 4 s |
| Show the results of a search | 4 s |

**Acceptance criteria**
- Given the `modest` profile, when each operation is run as QUAL-001 requires,
  then its median is at or below the threshold above.
- Given the same conditions QUAL-002 sets for the recipe size and the store size,
  when these are measured, then those conditions apply here unchanged.
- Given any operation above the threshold, when the release is reviewed, then it
  is a defect, on the same footing as a failure of QUAL-002.

**Verification:** bench measurement on the `modest` profile, recorded in the
release measurement report.
**Source:** user decision, 2026-09-21. The conservative figures are the binding
ones on the cheap phone and the slow network; the figures of QUAL-002 are what
the product aims at and are binding where the network is not the obstacle.

### QUAL-004 — The aimed-at figures are measured on `modest` too, and recorded

The system shall be measured on the `modest` profile against the thresholds of
QUAL-002 as well, and each result shall be recorded in the release measurement
report.

**Acceptance criteria**
- Given a release, when its measurement report is read, then it states, for each
  of the four operations, whether the `modest` profile met the `typical`
  threshold.
- Given a result that did not meet it, when the release is reviewed, then it is
  recorded and the release is not blocked by it, because QUAL-003 is the
  threshold that binds on this profile.
- Given the report, when it is compared with the previous release's, then the
  four results are comparable, because both were taken as QUAL-001 requires.

**Verification:** the release measurement report carries four rows for the
`modest` profile against the QUAL-002 thresholds.
**Source:** user decision, 2026-09-21, choosing measurement over a gate. The
product owner's instinct was a gate at half the figures; it was not written
because the four operations differ in difficulty by an order of magnitude — the
two local ones clear the strict figures on almost any device, and the two
network ones are bounded by a 300 ms round trip rather than by the code — so
"half of four" would have been satisfied by the same two every time and would
have measured the composition of the list rather than the quality of the build.
