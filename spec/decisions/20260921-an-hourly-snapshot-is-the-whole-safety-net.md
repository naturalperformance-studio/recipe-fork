# An hourly snapshot is the whole safety net

**Status:** accepted, qualifies RET-004

**Context:** The product's own store — recipe text, accounts, nicknames,
ratings, moderation records, the ingredient dictionary — lives on the disk of a
single server, because CON-002 buys one. Nothing in the specification had ever
said what happens when that disk fails. The answer, until this decision, was that
the product ends: publication is permanent and a published recipe cannot be
re-entered by anyone (CON-003, ST-001), there is no account recovery by design
(NGOAL-003), and after the store is gone every account key that was ever issued
addresses nothing, held by people who did nothing wrong.

Media is not in the same position. It lives in the object storage service
(EXT-003), which is bought for its durability among other things.

**Decision:** The product's own store is copied to the object storage service at
least once an hour, and the product is restorable from the most recent copy into
an empty environment (QUAL-008). At most one hour of accepted work is lost.
Media is not copied: the object storage service's own durability is what protects
it. A restore is performed and verified at least once a quarter, because a copy
that has never been restored from is not known to be a copy.

**Consequences:**
- At most one hour of authoring, rating and moderation is lost to a total
  failure, instead of everything.
- A restore returns the whole world to the moment the copy began, not only the
  parts that were lost. Accounts deleted in that hour exist again; recipes a
  moderator removed are visible again; private links revoked under ST-005 work
  again; reports that were closed are open again. RET-004 promises that deletion
  is immediate and cannot be undone, and that promise is now qualified: it holds
  for every path the product offers, and a catastrophe restore is not one of
  them. The product owner accepted this rather than the alternative below.
- A media object lost by the object storage service is lost permanently. The
  recipe that carried it is immutable (CON-003), so nobody — not even its
  author — can put the photograph back. The recipe survives without it, which is
  what EXT-004 already assumes when storage cannot be reached. Duplicating media
  into a second copy would double the product's largest line of cost, which is
  the line CON-002 exists to hold.
- The copies cost storage that has to be counted against CON-002. Forty-eight
  hourly copies and thirty daily ones of a store that is tens of megabytes of
  text is a few gigabytes, which at the checked price of USD 0.015 per GB-month
  is cents. The arithmetic is worth writing down only because it is the reason
  the retention is generous.
- The restore drill is work the product owner has to actually do four times a
  year, and it is the part of this decision most likely to be skipped. It is
  written as an acceptance criterion rather than as advice for that reason.

**Alternatives considered:**
- *No copies at all.* Rejected by the product owner. It was the status quo and it
  was coherent — the product promises nobody anything — but it meant a single
  disk failure ended the product and silently voided every key its users were
  told to keep safe.
- *One copy a day.* Rejected by the product owner in favour of an hour. The cost
  difference is nil at this size, and a day of lost authoring is a day of
  people's work.
- *A deletion journal that survives the restore and is replayed after it,* so
  that deletions are never undone. Rejected: it would mean the product
  permanently holds a list of identifiers of deleted accounts, which is exactly
  what RET-001 promises it does not, and it would turn "nothing is left" into
  "a record that you existed is left" for every person who ever deletes an
  account — a permanent cost paid to close a window that only opens after a
  catastrophe.
- *Copies taken on the owner's command rather than on a schedule.* Rejected: the
  window of loss becomes whatever the owner last remembered, and the
  contradiction with RET-004 is not removed by it, only made rarer and less
  predictable.
- *Copying media too.* Rejected against CON-002, as above.
