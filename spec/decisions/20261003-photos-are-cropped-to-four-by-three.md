# Photos are cropped to four by three

**Status:** accepted, changes MEDIA-002

**Context:** MEDIA-002 limited a photo's bytes and pixels and asked that it fill
its area without visible loss; it said nothing about shape. The system camera's
3:4 portrait default gave covers and step photos that did not fit the card. The
user decided at v0.6 that photos are 4:3 landscape, and at the v0.6 demo asked for
a full-screen camera followed by a crop to the frame (built in v0.8, kept when v0.9
went back to the phone's own camera).

**Decision:**
- Every photo — the cover and a step's — is stored as a 4:3 landscape frame,
  whether it was taken or chosen from the gallery.
- After taking or choosing it, the author places the frame; the crop keeps at
  least 1600 source pixels across where the source has them.

**Consequences:** every photo fits every place it is shown, without letterboxing
or an automatic crop the author did not see. A portrait shot loses its top and
bottom by the author's own choice; that is not the "visible loss" MEDIA-002 rules
out.

**Alternatives considered:**
- *Keep the source's shape (the system camera's 3:4 portrait).* Rejected by the
  user after the v0.5 demo, which is what led to the decision of v0.6.
- *An automatic centred crop.* Rejected: the author cannot see what is cut.
