# Media limits are set by the storage budget

**Status:** accepted

**Context:** OQ-003 held every media limit open — image size, video duration and
size, how many items a recipe may carry, how much an account may hold. CON-002
caps recurring infrastructure at USD 20 a month, and FLOW-011 already assumed
that a video weighs about as much as a photograph, since one setting governs
holding both.

The arithmetic was done before the limits were chosen. Object storage without
egress charges costs USD 0.015 per GB-month, so allowing half the ceiling for
storage and operations buys roughly 600 GB (checked 2026-09-20:
[R2 pricing](https://developers.cloudflare.com/r2/pricing)). Against that budget,
a recipe of a cover and eight steps costs about 9 MB as photographs and about
122 MB if every step carries a 30-second video — 66 000 recipes against 4 900.
The lever was never the size of one file; it was how much video a single recipe
may hold.

**Decision:** one cover photograph and at most one item per step (MEDIA-001); a
photograph re-encoded on the device to 1 MB and 1600 pixels (MEDIA-002); a video
of at most 15 seconds converted to at most 6 MB with its audio track removed
(MEDIA-004); duration refused at the door and everything else settled by
conversion (MEDIA-007); the author told what was done (MEDIA-005) and told when
it could not be done (MEDIA-006); every metadata field but orientation discarded
(MEDIA-003); and 150 MB of media per account across recipes that have never been
published (MEDIA-010).

**Consequences:**
- Video stays allowed on every step, and the shorter, silent, smaller clip pays
  for it: the same eight-step recipe now costs 49 MB rather than 122 MB, so the
  budget holds about 12 000 of the heaviest recipes instead of 4 900.
- A recipe of more than roughly two dozen videos cannot be assembled at all,
  because it would exceed the 150 MB allowance before it could be published. This
  is accepted, not solved.
- Photographs lose their EXIF, which closes a personal-data leak nobody had
  named: a dish photographed at home carries the author's coordinates.
- Video loses its sound, which halves what the single moderator must attend to
  and removes background speech from the product entirely.
- An account that never publishes can hold at most 150 MB, so private drafts stop
  being a free way to consume the storage CON-002 pays for.
- The numbers depend on a price checked on one day. If storage is bought
  elsewhere, or the price moves, the limits are what must be recomputed — not the
  budget.

**Alternatives considered:**
- *A per-recipe byte budget the author spends as they like.* Rejected: it is the
  most honest mapping to the money, but it obliges the editor to teach what a
  megabyte is, and the product teaches nothing about its own plumbing.
- *At most one video in a whole recipe.* Rejected by the product owner once the
  clip was shortened and silenced: the five-fold difference it was protecting
  against had already shrunk.
- *30-second video with sound, at 15 MB.* Rejected: it costs two and a half times
  the storage and makes the moderator listen.
- *2 MB photographs at 2048 pixels.* Rejected: it halves the catalogue the budget
  holds and buys nothing visible on a phone.
- *Silent compression with no message.* Rejected against CON-004.
- *Uploading media only at submission instead of when it is added.* Raised by the
  product owner and withdrawn the same day once the cost was laid out: DOM-005
  and draft section 5.2 upload early precisely so that a draft's photographs
  follow the key file to a second device, and FLOW-004, FLOW-005 and FLOW-006 all
  rest on the server holding content before moderation. The allowance of
  MEDIA-010 solves the same cost problem without taking drafts off the second
  device.
- *No allowance at all, relying on OQ-004 to sweep dormant accounts.* Rejected:
  OQ-004 sweeps after a year, while an account with one device can fill storage
  in an evening.
