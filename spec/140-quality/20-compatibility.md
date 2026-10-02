---
slug: QUAL
status: closed
updated: 2026-09-27
ids: [QUAL-005, QUAL-011]
---

# Compatibility

### QUAL-005 — The lowest supported Android version

The system shall run on Android 10 and every later version, and shall not be
offered to a device running an earlier one.

**Acceptance criteria**
- Given the released package, when its declared minimum is read, then it is
  Android 10 (API level 29).
- Given a device running Android 9 or earlier, when the product is searched for
  in the distribution channel, then it is not offered as installable.
- Given a device running Android 10, when every function in this specification is
  exercised, then each one works, including the media conversion of MEDIA-002 and
  MEDIA-005 and the attestation request of AUTH-001, and none of them falls back
  to behaviour that differs from what is specified.

**Verification:** run the full functional set on a device at API level 29 and on a
current version.
**Source:** user decision, 2026-09-21. Android 10 and later covered roughly nine
tenths of the measured Android base in April 2026 — API level 30 alone reached
86.9 per cent
([Android version market share, StatCounter](https://gs.statcounter.com/os-version-market-share/android),
checked 2026-09-21). Raising the floor to Android 12 after the MVP, once the
distribution figures for the product's own installed base can be read from the
channel, is OQ-031.

### QUAL-011 — The narrowest supported window is 320 dp wide

The system shall meet every requirement of this specification in a window at
least 320 dp wide, and no requirement binds in a narrower window.

**Acceptance criteria**
- Given a window 320 dp wide with the device text scale at 100 per cent and at
  200 per cent (A11Y-004), when every screen is walked, then each meets its own
  requirements, SCALE-012 and A11Y-004 included.
- Given the application in split-screen or another multi-window mode that gives
  it a window narrower than 320 dp, when it is used, then whatever it shows is
  not a defect against this specification.
- Given a requirement that speaks of the narrowest supported width, when it is
  verified, then it is verified at 320 dp.

**Verification:** walk every screen on an emulator or device configured to a
320 dp wide window, at 100 and 200 per cent text scale.
**Source:** user decision, 2026-09-27 (SD-016 item 1). The figure is the
product's choice, not a platform figure; the platform's own minimum was not
relied on.
