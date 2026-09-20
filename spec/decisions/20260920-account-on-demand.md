# An account is created on demand, by the people who need one

**Status:** accepted; supersedes the account-at-first-launch part of ACT-001

**Context:** ACT-001 created an account on first launch, so every installation
minted one. That consumed a nickname permanently (DOM-001), and after the device
gate of the same day it would also have consumed one of three device slots —
including for a person who opens the application once and never returns.

The product owner reworked the entry point on 2026-09-20: nothing is created
until an action needs it, and the account creation screen itself becomes a real
screen rather than an invisible event.

**Decision:** a person uses the product with no account at all — browsing,
searching, opening, cooking, keeping favourites on the device, reporting and
blocking (ACT-001, FLOW-051). An account is offered at the first action that
requires one: writing a recipe, forking one, rating one, or choosing to carry
favourites beyond the device (AUTH-001). The creation screen shows a generated
nickname that can be rerolled before the account exists and is permanent after
(AUTH-018), the account key in full exactly once, copyable and savable as a file,
behind an acknowledgement that creation will not pass without (AUTH-007), and the
deletion key beside it (AUTH-017). Afterwards both keys are reachable in the
account's own screen, masked, and copied whole (AUTH-019). A muted account —
created where no verdict can be obtained — carries favourites and does nothing
else (AUTH-004).

**Consequences:**
- A reader who only cooks mints nothing: no nickname, no slot, no row anywhere.
  This narrows OQ-009 and takes most of the pressure off the slot economics at
  the same time.
- Writing a recipe requires an account, which is where the product asks for one.
  The reason is not ceremony: DOM-005 binds media to an account at upload, and a
  fork must name an owner, so a draft without an owner could hold no photographs
  and could not be forked from.
- Favourites kept without an account are lost with the device, stated on the
  favourites screen rather than discovered afterwards.
- The account key stops being a file and becomes a string that may also be saved
  as a file. Every requirement that spoke of a key file was rewritten on
  2026-09-20; the glossary is the authority.
- The key is shown in full once and afterwards only masked, with a control that
  copies it whole. A device that still holds the account signed in is therefore a
  way back to the key, which is what makes moving to a new phone survivable when
  the saved copy is gone. It also means an unlocked phone hands the account over.
- The one opening the product had against the store's account deletion
  obligation — that the user never asked for an account — is closed by this
  decision. The person now creates it deliberately, on a screen. OQ-030 is
  updated accordingly; the deletion behaviour was written anyway.
- A former author has exactly one route to their published recipes after deleting
  their account: the rights holder channel of ABUSE-007. They arrive there with
  no proof of authorship, because deletion destroyed the account that held it.

**Alternatives considered:**
- *An account at first launch, as before.* Rejected: it mints an account for
  every installation, including those that never act, and the device gate made
  each one cost a slot.
- *Drafts without an account, an account required only at submission.* Rejected:
  media is bound to an account at upload (DOM-005) and uploaded before
  publication so that it follows the account to a second device, so an ownerless
  draft would either lose its photographs or undo that decision.
- *Rating without an account.* Rejected as unimplementable inside the product's
  own constraints: RATE-001 allows one rating per account, and with no account
  the only thing left to count against is a device identifier, which AUTH-015
  forbids storing.
- *Showing the account key only once and never again.* Rejected after being
  adopted and reconsidered the same day: the commonest moment to need the key is
  moving to a new phone while the old one is still in hand, and a key that can
  never be shown again is lost with the note it was written on.
