# Where an import's text comes from
**Status:** accepted, 2026-09-27. Widens EXT-001 when the import feature is built.

**Context:** The model needs the post's text. The three platforms differ in what
they allow, checked 2026-09-27:
- Instagram's oEmbed needs an application token and forbids "consuming,
  manipulating, extracting, or persisting the metadata and content"
  ([Meta — Instagram oEmbed](https://developers.facebook.com/docs/instagram-platform/oembed/)).
- TikTok's oEmbed is public, needs no token and returns the caption as `title`
  ([TikTok — Embed videos](https://developers.tiktok.com/doc/embed-videos/)).
  TikTok's developer terms were read and name no storage limit
  ([TikTok Developer Terms](https://www.tiktok.com/legal/page/global/tik-tok-developer-terms-of-service/en));
  its user terms forbid automated collection and reproducing content without
  consent ([TikTok Terms of Service](https://www.tiktok.com/legal/page/row/terms-of-service/en)).
  Whether republishing a recipe derived from an oEmbed caption is allowed is not
  settled by either text.
- Pinterest gives a pin's description only through API v5 behind OAuth and an
  access review ([Pinterest API v5](https://developers.pinterest.com/docs/api/v5/introduction/)).

**Decision:**
- A TikTok import takes its text from TikTok's official oEmbed endpoint. The text
  is known to come from the source, so the resulting recipe is published without
  waiting for a moderator.
- An Instagram or a Pinterest import takes its text from the person importing,
  who shares the link and pastes the caption. The result goes through the
  moderator's queue, where the moderator compares the text with the post before
  it is published.

**Consequences:**
- Buys: no platform's page is scraped and nothing is downloaded; the feature
  works for Instagram and Pinterest without an agreement with either.
- Costs: EXT-001's closed list gains TikTok's oEmbed endpoint and a language
  model service.
- Costs: pasted text could put any words under any blogger's link; the queue is
  what stops that, so every Instagram and Pinterest import adds work to the one
  moderator (ABUSE, MOD-005).
- Costs: a TikTok import reaches the catalogue with nobody having read it.
- Costs: the legality of republishing an oEmbed caption as a recipe is a question
  for the same review as OQ-029, not something settled here.

**Alternatives considered:**
- *The server fetches every post itself.* Rejected: it breaks the terms of all
  three platforms, Instagram's in so many words, and an address blocked by any of
  them stops the feature on a day the product does not choose.
- *Pasted text for all three, with moderation.* Rejected: it throws away the one
  platform whose text can be taken from the source, and puts TikTok imports into
  a queue that has one person in it.
