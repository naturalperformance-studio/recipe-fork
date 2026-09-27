# Media is stored in object storage and served through the server

**Status:** accepted
**Date:** 2026-09-27
**Requirements:** EXT-003, EXT-004, EXT-001, EXT-010, DOM-005, MEDIA-010, MEDIA-012, MEDIA-013, CON-002

**Context:** EXT-003 limits object storage to storing, serving and deleting an object, and requires that an object of a recipe not visible to a requester is not served to them. Visibility changes at any moment — reports hide a recipe, a moderator removes one. MEDIA-010 caps the unpublished media an account may hold. The storage price the specification rests on is Cloudflare R2's.

**Decision:** The application talks only to the product's server. The server stores media in Cloudflare R2 through the S3 API, using only put, get and delete. Every media request reaches the server, which checks visibility and the requester at that moment and streams the object. Uploads go through the server, which enforces the MEDIA-010 cap before storing.

**Consequences:**
- A hidden or removed recipe's media stops being served at the moment of the transition, not when a signed URL expires.
- The provider can be replaced by any S3-compatible store without changing a requirement (EXT-003).
- All media traffic passes through the one server. Its provider includes a monthly traffic allowance (20 TB on Hetzner Cloud, to be re-checked at deployment), so this costs bandwidth and CPU, not money.
- The server holds the storage credential; the application never does.

**Alternatives considered:**
- *Presigned URLs to the store.* Rejected: a URL issued before a recipe was hidden keeps serving it until it expires, contrary to EXT-003; a presigned upload makes the MEDIA-010 cap harder to hold.
- *Public bucket.* Rejected: unpublished media would be reachable by anyone holding a URL.
