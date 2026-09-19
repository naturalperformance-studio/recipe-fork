# Native Android is the only client of the MVP

**Status:** accepted

**Context:** The draft left the platform open (its section 11). The ranking in
[ADR 20260920 — cooking over lineage](20260920-cooking-over-lineage.md) makes
the kitchen the decisive environment: a phone on a worktop, screen asleep, timers
expected to fire. A plain web page loses that case — a backgrounded tab is
suspended and its timers do not reliably ring. A PWA survives it; a native
application survives it with the fewest caveats.

The product owner also set a recurring cost ceiling of USD 20 per month
(CON-002), against which the Google Play developer fee is USD 25 once.

**Decision:** The MVP is a native Android application and has no other client
surface. iOS and web are out of MVP scope and are not designed for.

**Consequences:**
- Timers, offline recipe viewing and camera capture are available without
  workarounds.
- Distribution through Google Play makes its User Generated Content policy
  binding (CON-006): in-app reporting of content and authors, in-app blocking of
  authors, terms of use accepted before content is created or uploaded, and a
  content rating questionnaire covering UGC. This reverses the earlier intent to
  keep reporting out of the MVP (OQ-006).
- Private links work only for recipients who have the application installed on
  Android. A recipient on iOS or in a browser cannot open a shared recipe at all.
  "Show it to a friend" is therefore an Android-to-Android capability in the MVP.
  Product owner decision, 2026-09-20.
- Release to production requires a closed test with at least 12 testers opted in
  continuously for 14 days, because the developer account is a personal one
  created after 13 November 2023
  ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en),
  checked 2026-09-20). This is a release condition, not a product requirement,
  and is recorded here only so it is not discovered late.
- Adding iOS or web later is a new client against the same service, not a
  migration.

**Alternatives considered:**
- *PWA.* Rejected by the product owner. It would have avoided the store fee, the
  review cycle, the 12-tester gate and the UGC policy obligations, and would have
  made private links work for any recipient with a browser. Its cost is that
  notifications on iOS require the user to add the app to the home screen first,
  which has to be explained in the interface.
- *Plain web site.* Rejected: unreliable timers and no offline viewing attack
  GOAL-001 directly.
