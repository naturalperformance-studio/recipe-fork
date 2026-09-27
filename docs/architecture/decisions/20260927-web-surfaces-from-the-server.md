# The web surfaces are served by the product's server

**Status:** accepted
**Date:** 2026-09-27
**Requirements:** EXT-006, EXT-007, EXT-011, LEG-008, CON-005, QUAL-007, L10N-001

**Context:** A few surfaces exist outside the application: the account deletion page (EXT-006), the terms of use and the privacy statement (LEG-008), the page a private link reaches without the application (EXT-007), and the health endpoint (EXT-011). A private link must open the recipe in the application where it is installed (CON-005).

**Decision:** The same Ktor server renders these as plain HTML in both interface languages, with no JavaScript and no third-party resources. A private link is an HTTPS URL on the product's domain, bound to the application by Android App Links (the server serves the `assetlinks.json`); without the application it lands on a page that leads to the store listing and says nothing about the link. The domain waits on the product's name (OQ-016) and is a configuration value until then.

**Consequences:**
- No second hosting, no second deployment, and nothing on these pages fetched from a service outside EXT-001.
- The deletion page's rate limit shares the mechanism of the key-handling ADR.

**Alternatives considered:**
- *A static site on separate hosting for the pages.* Rejected: the deletion page needs the server anyway, and a second host is a second thing to keep.
- *A custom URL scheme for private links.* Rejected: it opens nothing on a device without the application, so EXT-007's landing page could not exist.
