---
slug: MEDIA
status: closed
updated: 2026-10-15
ids: [MEDIA-001, MEDIA-002, MEDIA-014, MEDIA-003, MEDIA-004, MEDIA-005, MEDIA-006,
  MEDIA-007, MEDIA-008, MEDIA-009, MEDIA-015, MEDIA-010, MEDIA-011, MEDIA-012,
  MEDIA-013]
---

# Media

Media is optional everywhere: a recipe with none is complete and publishable
(DOM-006). What is here is what the product accepts, what it does to a file
before it leaves the device, and what it costs.

Decided elsewhere and not repeated: media stays on the device until its recipe is
sent, is uploaded with it, and is bound to the account before the recipe is
visible to anyone (FLOW-004, FLOW-059, DOM-005); whether a device keeps the media
of favourites and sent own recipes is one setting, beside an action that clears it
(FLOW-011, FLOW-060); the
cover is attached with the title and a step's item at the step (FLOW-026); a
fork by another account carries no media at all, and an owner's own fork carries
it (FORK-006, FORK-007); no media is classified automatically, and a human
moderator looks at all of it (NGOAL-005).

Every number below comes from one calculation, made 2026-09-20: object storage
without egress charges costs USD 0.015 per GB-month, so half of the ceiling of
CON-002 buys roughly 600 GB. Under the limits in this section a recipe of a cover
and eight steps weighs about 9 MB with photographs and about 49 MB if every step
carries a video, which is 66 000 and 12 000 recipes respectively. See
[ADR — media limits are set by the storage budget](decisions/20260920-media-limits-are-set-by-the-budget.md).

## What a recipe may carry

### MEDIA-001 — One cover photograph and one item per step

The system shall accept for a recipe at most one cover, which shall be a
photograph, and at most one media item per step, which shall be a photograph or
a video.

**Acceptance criteria**
- Given a recipe with no media, when a cover is added, then it is accepted only
  if it is a photograph.
- Given a step with no media, when a photograph or a video is added to it, then
  it is accepted.
- Given a recipe of fifty steps (DOM-012), when each step carries an item, then
  the recipe holds fifty-one media items in total with its cover.
- Given any recipe, when a second cover is offered, then it replaces the first
  rather than being added beside it (MEDIA-009).

**Verification:** attempt each combination in the editor, including a video
offered as a cover.
**Source:** user decision, 2026-09-20. The cover is a photograph because a
catalogue entry shows a cover image (CAT-003), and a video cover would need a
still frame chosen from it — a further decision and a further screen for a
picture whose job is to show the finished dish.

## What happens to a file before it is uploaded

### MEDIA-002 — Every photograph is re-encoded on the device

When a photograph is added, the system shall re-encode the frame the author
placed (MEDIA-014) on the device before upload to at most 1 MB and at most 1600
pixels on its longer edge. A megabyte here is 1,000,000 bytes.

**Acceptance criteria**
- Given a 12 MB photograph from a phone camera, when it is added, then what is
  uploaded is at most 1 MB and at most 1600 pixels on the longer edge.
- Given a photograph already smaller than both limits, when it is added, then it
  is still re-encoded, so that MEDIA-003 applies to every file without exception.
- Given a photograph whose longer edge is under 1600 pixels, when it is
  re-encoded, then it is not enlarged.
- Given the re-encoded file, when it is displayed on the recipe card, then it
  fills the intended area without visible loss on a phone screen; what the
  author's crop (MEDIA-014) left out is the author's choice and not a loss.
- Given a re-encoded file of 1,000,000 bytes, when it is checked, then it is
  within the limit; one of 1,000,001 bytes is not.

**Verification:** add photographs of several sizes and inspect what is uploaded.
**Source:** user decision, 2026-09-20: 1600 pixels covers any phone screen with
room to spare, and halving the limit from the alternative doubles what the
storage budget holds; amended 2026-10-03 (sync v0.3–v0.14): what is re-encoded
is the author's 4:3 crop, a crop is not loss, and 1 MB is 1,000,000 bytes — req-forge
proposal from the sync of v0.3–v0.14, accepted by the user, 2026-10-03.

### MEDIA-014 — Every photograph is a 4:3 landscape frame the author places

When a photograph is taken or chosen — for the cover or for a step, from the
camera or from the gallery — the system shall let the author place a 4:3
landscape frame over it before it is attached, and shall store only that frame.

**Acceptance criteria**
- Given a photograph just taken or chosen, when it is offered, then the author
  places the frame over it, and nothing is attached until the author confirms;
  cancelling attaches nothing.
- Given any stored photograph, cover or step, when its proportions are read, then
  it is 4:3 landscape, whatever the orientation it was shot in.
- Given a source at least 1600 pixels wide in the frame's direction, when the
  author narrows the frame, then it cannot become narrower than 1600 source
  pixels; given a smaller source, the frame can be no wider than the source allows
  and is not enlarged (MEDIA-002).
- Given a photograph taken sideways or upside down, when it is framed, then it is
  framed the right way up (MEDIA-003).

**Verification:** take and choose photographs of several sizes and orientations
for a cover and a step, frame each, and inspect the stored proportions and width.
**Source:** req-forge proposal from the sync of v0.3–v0.14, accepted by the user,
2026-10-03 (user decision, v0.6: the 4:3 landscape frame; asked by the user at the
v0.6 demo: the crop after the shot, built in v0.8). See [ADR — photos are cropped to 4:3](decisions/20261003-photos-are-cropped-to-four-by-three.md).

### MEDIA-003 — Nothing but the picture is uploaded

When a media file is re-encoded, the system shall discard every metadata field
it carries except the orientation needed to display it the right way up.

**Acceptance criteria**
- Given a photograph carrying GPS coordinates, when it is uploaded, then the
  uploaded file carries no coordinates.
- Given a photograph carrying a camera model, a serial number, a capture time or
  an author field, when it is uploaded, then the uploaded file carries none of
  them.
- Given a photograph taken in portrait orientation, when it is displayed
  anywhere in the product, then it is the right way up.
- Given any uploaded media item, when its bytes are inspected, then nothing in
  them identifies the person, the device or the place (CON-001).

**Verification:** upload a photograph with a full EXIF block and inspect the
stored object field by field.
**Source:** user decision, 2026-09-20. A phone photograph carries the
coordinates of where it was taken, which for a dish is usually the author's
kitchen; publishing it would publish their home address to within a building,
and neither the author nor the product would ever know. This is CON-001 applied
to the one place where personal data enters the product without anyone typing
it.

### MEDIA-004 — A video is short, small and silent

When a video is added, the system shall accept it only if it is at most 15
seconds long, and shall convert it on the device to at most 6 MB and at most
1280 pixels on its longer edge, with its audio track removed.

**Acceptance criteria**
- Given a video of 15 seconds or less, when it is added, then it is converted and
  uploaded with no audio track.
- Given a video longer than 15 seconds, when it is added, then MEDIA-007 refuses
  it before any conversion is attempted.
- Given a converted video, when it is played anywhere in the product, then it
  plays without sound and no control offers sound.
- Given a video recorded at 4K, when it is added, then what is uploaded is at
  most 1280 pixels on its longer edge, in whichever orientation it was shot.
- Given a video already smaller than 1280 pixels on its longer edge, when it is
  converted, then it is not enlarged.
- Given an animated image, when it is added, then it is converted to a video and
  is subject to this requirement like any other.

**Verification:** add videos of 10, 15 and 16 seconds, and an animated image;
inspect each uploaded object for duration, size and audio track.
**Source:** user decision, 2026-09-20. Video here shows a technique — how
something is cut, how one ingredient goes into another — and never a lesson
(FLOW-011). Sound is removed for two reasons beyond weight: the moderator looks
at every media item by hand (NGOAL-005) and sound would make them listen as well
as watch, which doubles the cost of the one resource the product cannot buy more
of; and background speech is exactly the personal data CON-001 keeps out.
The resolution is 1280 rather than 1920 because the 6 MB ceiling over 15 seconds
allows about 3.2 Mbit/s, which serves 720p well and 1080p poorly — at the same
weight the taller picture would look worse, not better — and because the
conversion runs on the author's own phone, where the smaller target finishes
sooner.

### MEDIA-005 — The product says what it did to the file

When the system re-encodes or converts a media file, it shall tell the author
that it did so.

**Acceptance criteria**
- Given a photograph reduced from 12 MB to 1 MB, when it is attached, then the
  author is told it was compressed.
- Given a video converted and stripped of its audio, when it is attached, then
  the author is told both happened.
- Given a file that needed no change beyond re-encoding, when it is attached,
  then the author is still told it was processed and is not left to assume the
  original was uploaded.

**Verification:** attach one file of each kind and read what is shown.
**Source:** CON-004 — automation explains itself. Silent compression was
rejected: an author who does not know their photograph was reduced cannot judge
whether the result is good enough.

### MEDIA-006 — A file that does not fit after conversion is refused

If a file still exceeds its limit after the system has re-encoded or converted
it, then the system shall refuse it, shall state why, and shall leave the recipe
unchanged.

**Acceptance criteria**
- Given a photograph that cannot be brought under 1 MB at 1600 pixels, when it is
  added, then it is refused with a statement and nothing is attached.
- Given a photograph that would fit only at an encoding quality below the floor
  the product sets, when it is added, then it is refused rather than degraded
  below that floor.
- Given a video that cannot be brought under 6 MB within 15 seconds, when it is
  added, then it is refused with a statement and nothing is attached.
- Given such a refusal, when the author returns to the recipe, then the media the
  recipe already had is untouched.

**Verification:** attempt to add files engineered to resist compression.
**Source:** user decision, 2026-09-20: compressing further without limit would
publish a picture nobody can read, so there is a floor under quality and it is
the author who is told about it; amended 2026-10-03 (sync v0.3–v0.14): the floor
is stated as a rule, its value is the team's — req-forge proposal from the sync of
v0.3–v0.14, accepted by the user, 2026-10-03.

### MEDIA-007 — Duration is checked before anything else

If a video is longer than 15 seconds, then the system shall refuse it before
converting it and shall state the limit. A duration of up to 15.5 seconds as the
file records it counts as 15 seconds, because a camera or an encoder states a
fifteen-second clip a few frames long.

**Acceptance criteria**
- Given a video of 40 seconds, when it is added, then it is refused immediately
  and no conversion runs.
- Given a clip whose file states 15.4 seconds, when it is added, then it is
  accepted; given 15.6 seconds, then it is refused.
- Given that refusal, when it is shown, then it names the 15 second limit.
- Given a video whose duration cannot be read, when it is added, then it is
  refused and MEDIA-008 governs the message.

**Verification:** add a long video and confirm the refusal is immediate.
**Source:** user decision, 2026-09-20: duration is the one property that cannot
be fixed by conversion without deciding for the author which part to keep, so it
is the only thing checked at the door; size and sound the product settles itself.
**Changed 2026-10-15 (sync v0.31–v0.38):** the half second of tolerance, as built
in v0.38 (T-274) for the camera, file import and animations — adopted at that sync.

### MEDIA-008 — A conversion that fails attaches nothing

If the system cannot re-encode or convert a file, then it shall state that the
file could not be processed and shall attach nothing.

**Acceptance criteria**
- Given a corrupt or unsupported file, when it is added, then the failure is
  stated and no media is attached.
- Given that failure, when it happens, then the recipe is unchanged and no
  partial file is uploaded or held on the device.
- Given repeated failures, when they occur, then the editor stays usable and the
  author can continue with the rest of the recipe.

**Verification:** add a file with a valid extension and invalid contents.
**Source:** user decision, 2026-09-20.

### MEDIA-009 — A second item on the same place replaces the first

When a media item is added where one already exists, the system shall replace the
existing item.

**Acceptance criteria**
- Given a step that already has a photograph, when a video is added to it, then
  the video replaces the photograph and the step still holds one item.
- Given a cover that already exists, when another photograph is added as the
  cover, then it replaces the previous one.
- Given a replacement, when it happens, then the replaced item is no longer held
  by that recipe and MEDIA-012 governs when its file is deleted.

**Verification:** replace a step's media and a cover, then inspect what the
recipe holds.
**Source:** user decision, 2026-09-20; MEDIA-001 allows one item per place, so an
addition can only mean a replacement.

## How a clip plays

### MEDIA-015 — A step's clip plays silently in a loop while it is seen

While a step's video is on screen, the system shall play it muted and in a loop,
shall pause it when it leaves the screen, and shall offer a control that pauses
and resumes it.

**Acceptance criteria**
- Given a step with a video, when it scrolls onto the screen, then the video plays
  without sound and starts again when it ends.
- Given that video, when it scrolls off the screen, then it pauses.
- Given a playing video, when the pause control is used, then it stops, and used
  again, it resumes.
- Given the device's system animations turned off, when a step with a video comes
  on screen, then it does not start by itself and waits for the play control
  (A11Y-009).

**Verification:** scroll a recipe with step videos on and off screen, pause and
resume one, then repeat with the system's animations turned off.
**Source:** req-forge proposal from the sync of v0.3–v0.14, accepted by the user,
2026-10-03 (built in v0.13). The size of its box and how it is cropped are the team's.

## What it costs

### MEDIA-010 — An account's unpublished media is capped at 150 MB

The system shall refuse to accept further media for an account once the total
size of the distinct stored files the account holds on the server for recipes
that were sent and have never been published reaches 150 MB.

**Acceptance criteria**
- Given an account whose unpublished media totals 149 MB, when a 2 MB item is
  added, then it is refused and MEDIA-011 states why.
- Given an account at the cap, when one of its recipes is published, then that
  recipe's media stops counting against the cap and further media is accepted.
- Given an account at the cap, when it deletes an unpublished recipe (ST-010),
  then the freed space is available once MEDIA-012 has deleted the files.
- Given a recipe whose media would take the account past the cap, when it is
  sent (FLOW-059), then nothing of it reaches the server, the media stays on the
  device, and the recipe keeps the mark FLOW-005 gave it.
- Given media on recipes never sent, when the allowance is computed, then it
  counts nothing: the device's own storage is the only bound on what was never
  sent.
- Given a recipe of fifty steps each carrying a video at or near the 6 MB
  ceiling of MEDIA-004, when it is being built, then it cannot be completed
  under this cap.
- Given the same recipe built from five-second clips, which weigh about 2 MB at
  the same quality, when it is being built, then it fits, because the cap counts
  bytes and not items.
- Given an unpublished recipe and the owner's own fork of it holding the same
  photographs (FORK-007), when the allowance is computed, then those photographs
  are counted once: what is counted is the distinct file, not the number of
  recipes holding it.

**Verification:** fill an account to the cap with sent drafts, publish one,
delete one, and send a recipe carrying media while the account is at the cap;
confirm that drafts never sent do not count.
**Source:** user decision, 2026-09-20. ABUSE-001 limits submissions per day and
nothing limits the creation of private recipes, so without this an account could
fill the storage of CON-002 without ever showing anything to anyone. 150 MB is
roughly sixteen photographic recipes in progress at once; the product owner's
reasoning for a figure this tight is that a recipe its author does not intend to
publish can do without photographs. What the cap binds is total weight, not the
number of items: a recipe of many short clips fits where the same recipe built
from full-length ones does not. **Narrowed 2026-09-28** to recipes that were sent,
since nothing else reaches the server (FLOW-059); by user decision of that date.

### MEDIA-011 — What the account is told at the cap

When a sending is refused because the cap of MEDIA-010 is reached, the system
shall state how much of the allowance is used, and shall name the two ways to free
it: publishing a recipe, or deleting a sent one that was never published.

**Acceptance criteria**
- Given the refusal, when it is shown, then it states the amount used and the
  amount allowed.
- Given the refusal, when it is shown, then it names publication and deletion as
  the ways to free space, and offers no way to compress further.
- Given the refusal, when the author frees space, then sending works again
  without reopening the recipe.
- Given the refusal, when it is shown, then it also says that the recipe stays on
  the device as it is, and that adding media to it there needs no allowance.

**Verification:** reach the cap and read the message; free space and retry.
**Source:** CON-004; the product owner rejected compressing harder to fit.

### MEDIA-012 — A file is deleted when no surviving recipe holds it

The system shall delete a stored media file once no recipe that still exists
holds it, and may perform that deletion in batches rather than at the moment the
last holder disappears.

**Acceptance criteria**
- Given a photograph held by one draft, when that draft is deleted (ST-010), then
  the file is deleted.
- Given a photograph held by a draft and by the owner's own fork of it
  (FORK-007), when the draft is deleted, then the file stays, because the fork
  still holds it.
- Given a photograph replaced on a step (MEDIA-009), when nothing else holds the
  replaced file, then it is deleted.
- Given a file whose last holder disappeared, when the deletion has not yet run,
  then the file is served to nobody who could not already see it.
- Given a published recipe, when anything is attempted, then its media is never
  deleted while it exists, because a published recipe cannot be changed or
  withdrawn (ST-001).

**Verification:** delete drafts and replace media in each of the cases above and
inspect the stored objects after the next deletion run.
**Source:** user decision, 2026-09-20: deletion need not be immediate, and a file
is only rubbish when nothing at all holds it.

### MEDIA-013 — A recipe a moderator removed keeps only what something else holds

When a moderator removes a recipe, the system shall treat that recipe as holding
its media no longer, so that each of its files is deleted unless another recipe
that still exists holds it.

**Acceptance criteria**
- Given a removed recipe whose photographs nothing else holds, when the deletion
  has run, then the owner opens the removed recipe and finds it without its
  pictures.
- Given a removed recipe some of whose photographs a private draft of the same
  owner also holds, when the deletion has run, then those photographs are still
  there and the rest are gone.
- Given a removed recipe, when its media is deleted, then the recipe itself stays
  readable to its owner and to a moderator exactly as `entity-states` says.
- Given a fork of a removed recipe made by its owner before the removal, when it
  holds the same media, then that media survives.

**Verification:** remove a recipe whose media is partly shared with a draft and
inspect both afterwards.
**Source:** user decision, 2026-09-20. A removed recipe is not published, and the
storage of CON-002 is not spent on keeping what a moderator took out of the
world; what survives does so because something else is still using it, not
because the removed recipe is being preserved.
