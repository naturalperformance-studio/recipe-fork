# Serverless mode, and recipes that travel as files

**Status:** accepted.

**Context:** the product owner may switch the server off
([the device first](20260928-the-device-first.md)). The application cannot tell a
server closed for good from one that is not answering (FLOW-003), so something
else has to say which. Once the server is gone, the only way a recipe can pass
from one person to another is outside the product — a file posted on a forum.

**Decision:**
- Serverless mode is switched on by a release of the application built with it
  on — the final update. Nothing inside the application switches it on.
- In serverless mode the catalogue, search over it, sending to the server,
  submission, publication, private links, rating, reports, account creation and
  signing in to an account not yet used on the device are gone from every screen.
  What remains is everything the device does alone.
- Export of a recipe to a file and import of a recipe from a file exist in
  serverless mode only. While the server runs, everything that passes from one
  person to another passes through moderation; after it, files are taken at the
  reader's own risk.
- Export does not freeze a recipe: nothing is published any more, and freezing
  exists to protect what other recipes may name on the server.
- A file carries the recipe's text and its media.
- An imported recipe is a found recipe: it is held in the recipe book like one
  found in the catalogue, it is read-only, and it changes only by being forked.
  It carries the nickname its file states as its author, shown as stated in the
  file and not verified.
- A fork of an imported recipe names it as its parent. The file carries the name
  and stated author of its own direct parent, so the lineage of one step survives
  the journey. A parent held in the recipe book can be opened; one that is not is
  shown by name as unavailable.

**Consequences:**
- Moderation is never bypassed while it exists. After the server is gone, nothing
  the product distributes needs moderating, because the product distributes
  nothing.
- The nickname on a file can be typed by anyone. It is labelled as unverified
  every time it is shown, rather than trusted or hidden.
- The final update has to be built and published while the product owner still
  holds the developer account in the store; users who never install it keep a
  server that "does not answer" (FLOW-003) indefinitely.
- FORK-005 hides a parent that was never published. In serverless mode nothing is
  published, so an imported recipe's parent is shown as the file names it.

**Alternatives considered:**
- Export and import while the server runs. Rejected by the product owner: a file
  could carry forbidden content no moderator sees, from a product that
  distributes it.
- An imported recipe as a template — the importer's own editable recipe.
  Rejected for a found recipe, which keeps the fork as the way a recipe changes.
- A cryptographic signature proving the exporter. Rejected: an account's keys are
  secrets the server tests (AUTH-014), not key pairs a device could sign with, and
  a signature would need a key model the product does not have.
- The application detecting the shutdown itself. Rejected: an unanswering server
  and a closed one look the same from the device.
- A field for the address of a server someone else runs, with the server's code
  published at shutdown. Not decided for the first release: OQ-038.
