# Nothing is deleted for being old

**Status:** accepted

**Context:** OQ-004 asked whether dormant accounts and their unpublished media
should be deleted to contain storage cost, and it had blocked `retention` and
`legal` since 2026-09-20. It was written when an account was free and unbounded:
one was minted at every first launch, and nothing capped what an account could
hold before publishing.

Both premises changed on 2026-09-20. An account is now created only when someone
asks for one and costs a device slot, of which a device has three per six months
(AUTH-001, AUTH-002); and an account may hold at most 150 MB of media across
recipes that were never published (MEDIA-010).

**Decision:** no account, recipe or media item is deleted because time has passed
(RET-006). The total unpublished media of accounts dormant for twelve months is
measured and reported instead (RET-007). Server logs are the one exception, and
they are not user content: they are deleted after fourteen days (RET-008).

**Consequences:**
- A person who returns after two years with their account key finds everything
  where they left it.
- Abandoned accounts accumulate. A thousand of them at the cap would be 150 GB,
  about a quarter of what CON-002 buys. Reaching that by ordinary use would mean
  the product had succeeded, and it would need roughly three hundred devices to
  reach by abuse, which is what the slots of AUTH-002 cost an attacker.
- If the measurement of RET-007 does rise, the answer is a product decision taken
  then, with the numbers in hand, rather than a threshold guessed now.
- The product keeps no way to tell a user that their data is about to go, which
  is what makes any deletion by age arrive unannounced.

**Alternatives considered:**
- *Delete unpublished media after twelve months without a session, keeping the
  account and its text.* The proposal carried in OQ-004 since the draft, and the
  spec author's recommendation. Rejected by the product owner: it takes a
  person's own unfinished work without warning, and the cost it saves is already
  bounded by MEDIA-010.
- *Delete the whole dormant account.* Rejected: it takes an account away for
  inactivity with no notice possible, and leaves that person's published recipes
  reading "account deleted" although they never asked for anything.
- *Keep logs for thirty days.* Rejected in favour of fourteen: the shorter period
  is the better position while OQ-029 is open, and it costs only the ability to
  investigate an attack noticed late.
