# Recipe Fork — specification index

> **NOT READY — 3 questions must be answered before MVP.**
> Sections: 27 closed · 0 blocked · 0 open · 1 n/a.
> Blocking: OQ-016, OQ-029, OQ-030. Last updated 2026-10-02.
> Every section of the first release is written. What is open before it are three
> questions whose answers change no requirement and hold the store listing
> instead (MVP-003). Section `import` is written ahead and is outside the first
> release (MVP-002); its own release waits on OQ-036, classified after MVP.
> Gate 1 of the first backlog found 21 specification defects (SD-001–SD-026, of
> which five are duplicates, in `docs/product/spec-defects.md`). Every one was
> answered by the product owner on 2026-09-27 and written here. The re-check of
> the same day left SD-006, SD-009, SD-019 and SD-021 unresolved; what still stood
> open behind them was answered and written the same day. A second re-check closed
> the first three; its one residue, FLOW-013 (8) asking for a notification
> permission Android 10 to 12 do not have, was answered and written the same day,
> and SD-021 turns resolved only when the product owner says so.
> Gate 1 then found SD-027 (two entries under one number) and SD-028 to SD-032;
> all seven were answered by the product owner on 2026-09-27 and written here, and
> each turns resolved only when the product owner says so.
> Gates 1 and 2 then found SD-033 to SD-035 — what a reader may fix, what an
> author may enter as a quantity, and whether a per-sign-in secret identifies a
> device; all three were answered by the product owner on 2026-09-27 and written
> here (DOM-017, DOM-018, SCALE-018, AUTH-015, AUTH-025), and each turns resolved
> only when the product owner says so.
> On 2026-09-28 the product owner put the device first: anyone writes and forks
> with no account and no server, a recipe reaches the server only when sent, a
> device holds several accounts and a pool, and serverless mode with recipe files
> is built into the first release (FLOW-059 to FLOW-065, AUTH-026, AUTH-027, and
> the sections they touch). The backlog cut before that date needs cutting again.
> Gates 1 and 2 then found SD-036 to SD-041 — two wrong examples of SCALE-008, how
> a typed number is read, the empty pool view, a stale cross-reference in FLOW-008,
> the pool recipe that creates an account, and a sending the server already holds.
> All six were answered by the product owner on 2026-09-29 and written here
> (SCALE-008, DOM-017, DOM-019, FLOW-042, FLOW-008, FLOW-059, FLOW-066, AUTH-026,
> ACT-002), and each turns resolved only when the product owner says so.
> Gate 1 then found SD-042 to SD-045 — whose a pool recipe is when the account
> created for it sends nothing, the mark after a first sending whose answer is lost,
> which check comes first for a repeated sending that already made a fork, and a
> sending of a recipe the server no longer holds; and, following from the second, a
> sending that names no revision for a recipe the server holds. All were answered by the
> product owner on 2026-10-01 and written here (FLOW-059, FLOW-005, FLOW-008,
> FLOW-066, ACT-002, AUTH-004, AUTH-026, ST-010), and each turns resolved only when
> the product owner says so.
> Gate 1 of T-038 then left a question for this specification: an edit sent after
> the device's own unanswered sending was forked by FLOW-008 and not forked by
> FLOW-066 (1). The product owner answered it on 2026-10-01 — the sending is made
> from what the unanswered one stored — and it is written as FLOW-067.
> Gate 2 of T-044 then found "interactive element" in A11Y-003 defined nowhere; the
> product owner defined it on 2026-10-02 — a scrolling container and a disabled
> control are measured, text that only takes focus or selection is not.

The product is a recipe platform built on a fork model: any accessible recipe
can be forked into an independent recipe that names its direct parent. A recipe
is a structured, recalculable object, not prose. Reading, cooking, writing and
forking need no account and no server at all: a recipe lives on the device first
and reaches the server only when its owner sends it. An account is what an author
sends and publishes with; it carries no personal data, and it is held by a key its
holder keeps. The product keeps working when its server is switched off for good
(serverless mode).

Source draft: `/home/personal/Projects/drafts/recipe-app-requirements.md`
(read 2026-09-20; the draft is input, not a spec, and is not maintained here).

## Sections

| File | Slug | Status | What it is for |
|---|---|---|---|
| [10-glossary/](10-glossary/00-index.md) | GLOSS | closed | Terms that mean something specific here — fork, parent, frozen, private link, unit type, derived, counted report, net score, device slot, unpublished allowance, deletion key, operational signal, openings, session, sign-in secret, sent, unanswered sending, interactive element, pool, recipe book, serverless mode, recipe file and 74 more — each pointing at the requirement that decides it. Holds no requirements and is never a second source. |
| [20-goals.md](20-goals.md) | GOAL | closed | What the product is for, what it refuses to be, and the rule that settles scope disputes. |
| [25-market-context.md](25-market-context.md) | MKT | closed | The market the product launches into, as established by the product owner: what exists, what is hygiene rather than differentiation, and what was not checked; and, looked up on 2026-09-27, the applications that import recipes from social networks — all of which keep what they import private. Holds no requirements. |
| [30-constraints.md](30-constraints.md) | CON | closed | Hard constraints the product owner accepts as non-negotiable: no personal data, minimal infrastructure cost, immutability, visible automation. |
| [40-actors.md](40-actors.md) | ACT | closed | Who acts on the system and what each one may do, including whoever is not signed in; what the moderator role reaches on its holder's own work — their own actions and nothing else, so a moderator still keeps their drafts and still rates nobody's recipe of their own; what a publishing ban stops, which is every route into the catalogue and not only the queue; and the one thing no screen anywhere offers — granting the moderator role, which happens in the store; and the two moments a recipe made with no account gets its owner — the pool moved, or its own sending creating the account. |
| [45-auth/](45-auth/00-index.md) | AUTH | closed | Who needs an account at all and when one appears, the three device slots and the six months that free one, the nickname and its reroll, the muted account created when a device cannot be attested, which sends nothing to the server, several accounts on one device and the pool made with none, and the account's three keys — the account key that signs in, the deletion key that destroys it from outside, and the recovery key issued apart and shown once, the only thing that can retire a stolen account key; the sign-in secret a device presents instead of its key, which is not a device identifier and lapses after six months unused; and creation as three steps — nickname and terms, the two keys, the recovery key. What the server accepts as proof is a set of properties; the cryptography is left to engineering. |
| [50-domain-model/](50-domain-model/00-index.md) | DOM | closed | Entities, their fields and invariants: recipe, ingredient, ingredient dictionary entry, unit type, step, media, account, moderation record; the values an ingredient's quantity may hold and how a typed number is read, with a comma or a point; and that the two dictionaries everything here leans on are maintained outside the application, by hand. |
| [60-entity-states/](60-entity-states/00-index.md) | ST | closed | State machines with transition tables and, for each state, the transitions that must not happen; the state `local` of a recipe never sent, the recipes an owner may destroy outright and those they may not; the server orders every transition, refuses one whose guard has stopped holding, and tells the actor the state the recipe is actually in. |
| [65-forking.md](65-forking.md) | FORK | closed | What a fork inherits, what it never inherits, how provenance is shown, and how an author replaces a published recipe without withdrawing it. |
| [68-import/](68-import/00-index.md) | IMPORT | closed | **Not in the first release.** A post on Instagram, TikTok or Pinterest shared to the application is read by a language model and published as the source's recipe: no author, no media, a mark naming the platform and the model version and leading to the post, and a mark on every descendant that its original was imported. One source becomes one recipe; the next person to share it is led to the existing one. TikTok text comes from its official endpoint and publishes at once; Instagram and Pinterest text is pasted and goes through the queue. The model has its own USD 50 a month. |
| [70-flows/](70-flows/00-index.md) | FLOW | closed | User flows and screens, each with its empty, loading, error and offline behaviour: the device first and sending to the server, including a sending the server already holds, which changes nothing unless it already made a fork, an unanswered sending, which keeps its mark, and a recipe the server no longer holds, which is sent as new, an edit sent after an unanswered sending, which is made from what that sending stored, the recipe book and the device, serverless mode and recipe files, cooking on one scrolling card with no step to advance through, the editor, the catalogue and the favourites section including where a draft is deleted and what the author is told first, the private link, the author's screen a nickname leads to — where an author is reported, blocked and banned — and the moderator's queue. |
| [75-author-guidance.md](75-author-guidance.md) | GUIDE | closed | What the editor tells its author before they can find out the hard way: the statement before an irreversible submission, the checklist of good practice, and the hints that fire on the author's own action. The product teaches here because it inspects nowhere. |
| [80-scaling-rules/](80-scaling-rules/00-index.md) | SCALE | closed | Recalculation and rounding: by servings, from a fixed ingredient, how the two combine and what releasing a fixing restores; the exact values every unit type may display — kilograms to the gram, mixed numbers above one — the nearest-value rule with ties going up, a derived marker on every quantity at any scale but the authored one; and what a reader may fix, in which unit. |
| [90-media.md](90-media.md) | MEDIA | closed | What a recipe may carry, what the device does to a file before it leaves — re-encoding, stripping every metadata field but orientation, silencing and shortening a video — what the author is told about it, the 150 MB an account may hold sent and unpublished, and when a stored file is finally deleted. |
| [100-moderation.md](100-moderation.md) | MOD | closed | What triggers moderation, what is checked automatically and what by a human, what a rejection produces, and what a moderator is told when the submission they just decided had already been withdrawn or its owner had deleted their account; and that the role is not suspended by ownership — a moderator decides their own submission and acts on their own published recipe, because otherwise two states have no exit at a launch with one person in the role. |
| [110-catalogue.md](110-catalogue.md) | CAT | closed | Public catalogue, search, filters, favourites and the search inside favourites; what a catalogue entry shows, including the fork marker and fork count; what `newest` orders by — the moment the catalogue got the recipe — and the one chain that makes all four orderings total, so that endless scrolling neither repeats a recipe nor skips one. |
| [120-ratings.md](120-ratings.md) | RATE | closed | The binary like and dislike over its life: who may give one and on what, the two counts a reader sees, what `highest rated` and `most rated` compute, and why favouriting is not approval. |
| [130-external-interfaces.md](130-external-interfaces.md) | EXT | closed | The closed list of what the product depends on — a device attestation service, object storage, the distribution channel and an outbound alerting channel — what each does when it is absent; the one observer that calls the product and is never called by it, and the silent endpoint it calls; and the two artefacts that cross the boundary in the user's own hands: the account key and a private link. Also the surfaces outside the application: the account deletion page, and the claims address, which the product neither reads nor writes to. |
| [140-quality/](140-quality/00-index.md) | QUAL | closed | Quality attributes, each with a metric, a measurement method and a threshold: the two reference devices and networks every timing is taken on, the response times that bind on each, the lowest Android version and the narrowest window, 320 dp, 95 per cent of the month available, one hour as the most work a catastrophe may cost, ten tries a day at a deletion key, the week in which 95 per cent of submissions are decided — and a table saying where every other ISO 25010 characteristic lives or why nothing is promised of it. |
| [150-retention.md](150-retention.md) | RET | closed | What deleting an account destroys and what survives it, what a deleted author's recipes say instead of a nickname, why nothing is ever deleted for being old, how long server logs live, and what the product deliberately never records about a reader. |
| [160-localization.md](160-localization.md) | L10N | closed | Two languages from the first release and what follows: where the interface language comes from and how it is changed, why both dictionaries must carry both labels, the language a recipe declares about itself and never has checked, a catalogue that lists every language until a reader narrows it, the two fields a text query looks at, matching on the normalised word rather than the letters, and why no locale makes the product non-metric. |
| [170-abuse.md](170-abuse.md) | ABUSE | closed | Flooding, mass publication, and what a single moderator can absorb; reports about an author collapse onto one queue entry with a count, because the product holds nothing that would tell two anonymous reporters apart; and the one address by which somebody who is not a user of this product claims rights over what is in it. |
| [180-analytics.md](180-analytics.md) | ANL | closed | What can be known about the product without tracking anyone: the figures countable from stored state, reach taken from the distribution channel, an anonymous count of starts, and the three numbers a published recipe keeps for its own author — openings, additions to favourites and forks made by others, the last also totalled for the product owner — labelled with what they actually count and where they undercount. |
| [190-legal.md](190-legal.md) | LEG | closed | What publishing grants and what it does not: an irrevocable licence to the product, because permanence cannot rest on a permission that can be withdrawn; a licence to every user over the steps alone, so forking rests on a grant and not only on what copyright excludes; media and description that stay the author's. Terms accepted at account creation and again when they change, where refusing costs authorship and never costs reading. Sixteen years declared and nothing verified. The name is OQ-016 and the law the first release answers to is OQ-029; both are release prerequisites in MVP-003 and neither changes a sentence here. Distribution is limited to a country list the Russian Federation is not on, while access is limited to nobody. |
| [200-accessibility.md](200-accessibility.md) | A11Y | closed | A fixed list of commitments with thresholds instead of a conformance claim: contrast, 48 dp targets and 56 on the cooking card, the device font scale to 200 per cent, the screen reader working on every screen including the editor so that authorship is not closed to anyone, an alternative to every drag, the cooking card built for busy hands, and nothing carried by colour alone. |
| [210-observability.md](210-observability.md) | OBS | closed | The fourteen signals the product watches, each naming the decision that asked for it, the thresholds at which one becomes a decision, the moderator's screen they live on, and the one outbound channel that carries an alert — numbers only, one way. |
| [220-mvp-boundary.md](220-mvp-boundary.md) | MVP | closed | The boundary, taking no decision of its own: the first release is every closed section less the sixteen things decided against, the nine prerequisites that produce a record rather than a requirement, and what the launch actually looks like — an empty catalogue, one author, and that same person holding the moderator role, with the four requirements that shape changes the reading of. |
| — | MIG | n/a | Migration: the product launches from scratch — no existing data, users or predecessor system to migrate from. Moving a user between devices by its key is an authentication requirement, not a migration. |

[Open questions ledger](open-questions.md) · [Decisions](decisions/)

## Decisions

| ADR | Status | Subject |
|---|---|---|
| [20260920-cooking-over-lineage.md](decisions/20260920-cooking-over-lineage.md) | accepted | Cooking outranks lineage when the two conflict. |
| [20260920-native-android-mvp.md](decisions/20260920-native-android-mvp.md) | accepted | Native Android is the only client of the MVP; private links are Android-only. |
| [20260920-report-and-block-model.md](decisions/20260920-report-and-block-model.md) | accepted | Three reports hide a recipe; blocking hides an author from catalogue and search only. |
| [20260920-publication-is-permanent.md](decisions/20260920-publication-is-permanent.md) | accepted | Once published, a recipe can never be withdrawn by its owner. |
| [20260920-immutability-trigger.md](decisions/20260920-immutability-trigger.md) | accepted | A recipe freezes at external visibility; withdrawn and rejected recipes stay editable. |
| [20260920-forking-published-only.md](decisions/20260920-forking-published-only.md) | accepted | Only published recipes are forkable by others; owners may fork their own in any state. |
| [20260920-fork-carries-method-not-expression.md](decisions/20260920-fork-carries-method-not-expression.md) | accepted | A fork by another account inherits ingredients and steps, never media or description; no anti-fork flag. |
| [20260920-own-fork-carries-everything.md](decisions/20260920-own-fork-carries-everything.md) | accepted | An owner forking their own recipe carries its media and description too; readers' ratings travel to no fork. |
| [20260920-scaling-extremes.md](decisions/20260920-scaling-extremes.md) | accepted | Serving count floors at ¼ with no ceiling; a warning replaces a downward bound. |
| [20260920-large-quantity-display.md](decisions/20260920-large-quantity-display.md) | accepted | Tonnes and cubic metres extend `exact` only; every unit type stays legible at any magnitude. |
| [20260920-provenance-follows-publication.md](decisions/20260920-provenance-follows-publication.md) | accepted; a fork sent before its parent (FLOW-007) and serverless mode (FLOW-065) added 2026-09-28 | A parent is named only if it is or has been published; a never-published parent is not mentioned. |
| [20260920-danger-travels-down-the-chain.md](decisions/20260920-danger-travels-down-the-chain.md) | accepted | A health-danger removal flags every descendant for review; a rights removal gives no reason and touches no fork. |
| [20260920-lineage-visible-in-discovery.md](decisions/20260920-lineage-visible-in-discovery.md) | accepted | The catalogue marks a fork and states its direct fork count; this narrows GOAL-003. |
| [20260920-ingredient-dictionary.md](decisions/20260920-ingredient-dictionary.md) | accepted | An ingredient names a dictionary entry with an optional qualifier; free text is accepted with a warning and is never filterable. |
| [20260920-comparison-factors.md](decisions/20260920-comparison-factors.md) | accepted | The dictionary gains one level and every entry carries invisible comparison factors; ordering is total. |
| [20260920-quality-sorted-by-readers.md](decisions/20260920-quality-sorted-by-readers.md) | accepted | The product checks nothing about a recipe's craft; readers' ratings sort quality and a moderator never rejects for it. |
| [20260920-divisibility-belongs-to-the-ingredient.md](decisions/20260920-divisibility-belongs-to-the-ingredient.md) | accepted | The dictionary entry decides `piece` divisibility; the author never overrides it, and only free text asks. |
| [20260920-timer-named-by-its-step.md](decisions/20260920-timer-named-by-its-step.md) | accepted | A timer's name derives from its step's text and the author may override it; a step carries one timer. |
| [20260920-the-editor-teaches-without-inspecting.md](decisions/20260920-the-editor-teaches-without-inspecting.md) | accepted | An unsuppressible statement before submission, a read-only checklist, and hints triggered by the author's action — never by reading the recipe. |
| [20260920-two-counts-ordered-by-net-score.md](decisions/20260920-two-counts-ordered-by-net-score.md) | accepted | The reader sees two counts; `highest rated` orders by likes minus dislikes, with a tie chain that makes the order total. |
| [20260920-rating-is-not-queued.md](decisions/20260920-rating-is-not-queued.md) | accepted | Offline the rating controls stay visible and refuse; nothing is queued, and the counts shown are the last synchronised ones. |
| [20260920-account-creation-gated-by-the-device.md](decisions/20260920-account-creation-gated-by-the-device.md) | accepted, narrows ACT-001; muted permissions superseded 2026-09-27 | Creating an account costs one of three device slots that free after six months; without a verdict the account is muted, and no verdict is ever stored. |
| [20260920-replacing-the-account-wipes-the-device.md](decisions/20260920-replacing-the-account-wipes-the-device.md) | superseded by 20260928-several-accounts-on-one-device | One active account per device; signing in with a key wipes what the previous account held, after a statement naming every unsent recipe. |
| [20260920-media-limits-are-set-by-the-budget.md](decisions/20260920-media-limits-are-set-by-the-budget.md) | accepted | Every media limit is derived from what USD 20 a month buys; video stays on every step by being short, small and silent. |
| [20260920-deletion-key.md](decisions/20260920-deletion-key.md) | accepted | Deleting an account from outside the application takes a separate deletion key pasted as text; the account key is never uploaded anywhere. |
| [20260920-account-on-demand.md](decisions/20260920-account-on-demand.md) | accepted, supersedes part of ACT-001; writing and forking without an account superseded by 20260928-the-device-first | Nothing is created until an action needs it: readers hold no account, authors do, and the key is a string shown once in full and masked thereafter. |
| [20260921-nothing-expires.md](decisions/20260921-nothing-expires.md) | accepted | No account, recipe or media item is deleted for being old; the abandoned total is measured instead, and only logs have a period. |
| [20260921-alerting-channel.md](decisions/20260921-alerting-channel.md) | accepted, widens EXT-001 | The dependency list becomes four: one outbound alerting channel, carrying a signal's name and value and nothing else, accepting no command back. |
| [20260921-availability-is-measured-from-outside.md](decisions/20260921-availability-is-measured-from-outside.md) | accepted, widens EXT-001 | A probe outside the product calls a silent health endpoint; the product cannot report its own absence. |
| [20260921-an-hourly-snapshot-is-the-whole-safety-net.md](decisions/20260921-an-hourly-snapshot-is-the-whole-safety-net.md) | accepted, qualifies RET-004 | The store is copied hourly and restored quarterly to prove it; media is not copied, and a restore brings back up to an hour of deletions. |
| [20260921-a-published-recipe-is-replaced-never-withdrawn.md](decisions/20260921-a-published-recipe-is-replaced-never-withdrawn.md) | accepted, confirms permanence | No withdrawal window. An own fork may declare itself a replacement, and the superseded recipe says so and leads to it. |
| [20260921-the-recovery-key.md](decisions/20260921-the-recovery-key.md) | accepted, narrows NGOAL-003 | A third key, issued apart and shown once, is the only thing that can retire a stolen account key; a symmetric replacement would have handed the account to the thief. |
| [20260921-distribution-is-postponed-access-is-not-withheld.md](decisions/20260921-distribution-is-postponed-access-is-not-withheld.md) | accepted | The first release is not offered in the Russian Federation; nobody is blocked by address, and no location is derived from one. |
| [20260921-a-removed-recipe-is-still-its-authors-work.md](decisions/20260921-a-removed-recipe-is-still-its-authors-work.md) | accepted, corrects ST-004 | An owner may fork their own removed recipe; nobody else can reach one to fork it. |
| [20260921-the-moderator-is-not-exempt-from-their-own-content.md](decisions/20260921-the-moderator-is-not-exempt-from-their-own-content.md) | accepted, widens MOD-013 | Every moderator action applies to the moderator's own recipes; the alternative left `reported_hidden` with no exit. |
| [20260921-a-draft-can-be-deleted-nothing-else-can.md](decisions/20260921-a-draft-can-be-deleted-nothing-else-can.md) | accepted, adds ST-010; a recipe never sent is deleted on the device, by 20260928-the-device-first | The owner deletes a recipe in `private` or `rejected`; everything that was ever externally visible is permanent. |
| [20260921-an-account-cannot-be-minted-on-the-device-alone.md](decisions/20260921-an-account-cannot-be-minted-on-the-device-alone.md) | accepted, narrows AUTH-004 | Creating an account needs a connection; a muted account covers only a reachable server and an unreachable attestation service. |
| [20260921-the-author-has-a-screen.md](decisions/20260921-the-author-has-a-screen.md) | accepted, adds FLOW-055 and FLOW-056 | A nickname leads to the author's screen, where they are reported, blocked and banned; reports about one author collapse onto one queue entry with a count. |
| [20260921-a-ban-stops-the-catalogue-not-the-queue.md](decisions/20260921-a-ban-stops-the-catalogue-not-the-queue.md) | accepted, widens ACT-005 | A ban stops every act that would put a recipe in the catalogue, including publication from `link_shared`, which creates no submission. |
| [20260921-the-cooking-card-is-one-scrolling-screen.md](decisions/20260921-the-cooking-card-is-one-scrolling-screen.md) | accepted, corrects FLOW-012 and A11Y-003; narrowed 2026-09-27 | There is no current step and no step advance; what is restored is the scroll position. |
| [20260921-the-claims-channel-is-an-address.md](decisions/20260921-the-claims-channel-is-an-address.md) | accepted, writes ABUSE-007 | The claims channel is one published mail address, answered in the period MOD-005 states; nothing in the product watches it. |
| [20260921-a-week-and-ninety-five-per-cent.md](decisions/20260921-a-week-and-ninety-five-per-cent.md) | accepted, writes QUAL-010 | Seven days, binding as 95 per cent of a calendar month, computed from timestamps the submission record already holds. |
| [20260921-newest-is-when-the-catalogue-got-it.md](decisions/20260921-newest-is-when-the-catalogue-got-it.md) | accepted, completes CAT-001 | `newest` is the instant a recipe entered the catalogue; one tie chain makes every ordering total. |
| [20260921-a-connection-is-not-an-answer.md](decisions/20260921-a-connection-is-not-an-answer.md) | accepted, widens FLOW-003 | A server that does not answer is refused like being offline, and said differently, because the person has a different thing to do. |
| [20260921-a-sign-in-empties-the-device.md](decisions/20260921-a-sign-in-empties-the-device.md) | superseded by 20260928-several-accounts-on-one-device | Signing in deletes the favourites and blocks the device held with no account; creating an account still keeps them. |
| [20260921-nothing-exists-until-it-is-confirmed.md](decisions/20260921-nothing-exists-until-it-is-confirmed.md) | accepted, completes AUTH-007 | An abandoned creation leaves nothing: no nickname consumed, no slot occupied, and the displayed keys match nothing. |
| [20260927-an-import-belongs-to-its-source.md](decisions/20260927-an-import-belongs-to-its-source.md) | accepted, outside the first release | A recipe imported from a social network is published as its source's, parsed by a model, with no author, no media and a mark every descendant keeps. |
| [20260927-where-an-imports-text-comes-from.md](decisions/20260927-where-an-imports-text-comes-from.md) | accepted, widens EXT-001 when built | TikTok text comes from its official endpoint and publishes at once; Instagram and Pinterest text is pasted by the person and goes through the queue. |
| [20260927-the-model-has-its-own-fifty-dollars.md](decisions/20260927-the-model-has-its-own-fifty-dollars.md) | accepted, qualifies CON-002 when built | The language model is capped at USD 50 a month on top of the USD 20; at the cap imports stop until the month ends. |
| [20260927-the-imported-ancestor-is-named.md](decisions/20260927-the-imported-ancestor-is-named.md) | accepted, narrows GOAL-003 when built | Every descendant of an imported recipe names that ancestor as well as its direct parent, and nothing between. |
| [20260927-the-rounding-grid-completed.md](decisions/20260927-the-rounding-grid-completed.md) | accepted, writes SCALE-013–SCALE-015 | Each `exact` rung keeps the precision of the one below; fractions above one are mixed numbers; a computed value goes to the nearest grid value and a tie goes up. |
| [20260927-the-latest-scaling-action-wins.md](decisions/20260927-the-latest-scaling-action-wins.md) | accepted, writes SCALE-016, SCALE-017 | A new serving count releases every fixing; releasing the last fixing restores the count before it; the factor is never shown. |
| [20260927-derived-by-provenance.md](decisions/20260927-derived-by-provenance.md) | accepted, corrects SCALE-004 and CON-004 | At any scale factor other than 1 every quantity is derived, even one that lands on its authored value. |
| [20260927-the-authored-serving-count-is-whole.md](decisions/20260927-the-authored-serving-count-is-whole.md) | accepted, writes DOM-015 | The authored serving count is a whole number of one or more, with no ceiling. |
| [20260927-screen-width-and-numbers.md](decisions/20260927-screen-width-and-numbers.md) | accepted, writes QUAL-011 and L10N-009 | Every requirement holds from a 320 dp window up; numbers are grouped and punctuated as the interface language writes them. |
| [20260927-nickname-words.md](decisions/20260927-nickname-words.md) | accepted, writes AUTH-022 and AUTH-023 | A nickname is two English adjectives and a noun from lists the product owner keeps by hand; combinations are not checked. |
| [20260927-leaving-account-creation.md](decisions/20260927-leaving-account-creation.md) | accepted, completes AUTH-007 and AUTH-020 | Creation has its own cancel and a back that asks; the recovery key stands on its own step, is copied, and is never a file. |
| [20260927-favourites-opens-on-the-marked.md](decisions/20260927-favourites-opens-on-the-marked.md) | accepted, completes FLOW-034; the view with no account superseded by 20260928-the-device-first | Favourites open on the marked recipes; a device with no account has that view alone. |
| [20260927-the-ingredient-field.md](decisions/20260927-the-ingredient-field.md) | accepted, writes FLOW-057 and DOM-016 | Word-beginning match in both languages, leaves before parents, free text always beside the entries, twelve units, and "to taste" chosen and undone in the unit field. |
| [20260927-steps-timers-and-references.md](decisions/20260927-steps-timers-and-references.md) | accepted, corrects FLOW-027, FLOW-031, FLOW-032, GUIDE-006, GUIDE-007 | A second timer is attempted and explained; every ingredient can be referenced; the notification preview renders at the authored count. |
| [20260927-a-muted-account-writes-on-its-device.md](decisions/20260927-a-muted-account-writes-on-its-device.md) | accepted, supersedes part of 20260920-account-creation-gated-by-the-device; the sending when full superseded by 20260928-the-device-first | A muted account writes, edits and forks on its own device; media, submission, private links and rating stay withheld; everything moves to the server when it is full. |
| [20260927-divergence-and-its-notice.md](decisions/20260927-divergence-and-its-notice.md) | accepted, corrects FLOW-005 and FLOW-008; the automatic sync it assumed superseded by 20260928-the-device-first | A change of state alone is divergence; the fork notice comes when the answer does, once; acceptance is marked on the own-recipes entry and in the editor. |
| [20260927-the-card-survives-the-process.md](decisions/20260927-the-card-survives-the-process.md) | accepted, corrects FLOW-012, FLOW-013, A11Y-007 | The recipe, its scale and the line at the top survive the process; the notification permission is asked with the first timer and its refusal is said at every start. |
| [20260927-the-exact-label-and-the-order-of-the-offer.md](decisions/20260927-the-exact-label-and-the-order-of-the-offer.md) | accepted, completes FLOW-057 | "Exactly the label" folds case and `ё` and takes either language; entries whose label begins with the typed text come first, alphabetically. |
| [20260927-a-retry-restores-the-control.md](decisions/20260927-a-retry-restores-the-control.md) | accepted, completes AUTH-005 and AUTH-006 | A retry that makes a muted account full puts the withheld controls back and performs nothing on the person's behalf. |
| [20260927-the-three-steps-of-creation.md](decisions/20260927-the-three-steps-of-creation.md) | accepted, writes AUTH-024 | Nickname and terms, then the two keys, then the recovery key; back goes one step back; leaving the application keeps the place while the process lives. |
| [20260927-a-refusal-is-explained-at-once.md](decisions/20260927-a-refusal-is-explained-at-once.md) | accepted, completes FLOW-013 | A refused platform request at a timer's start is followed at once by the application's explanation; the timer never waits for an answer. |
| [20260927-no-request-where-the-platform-has-none.md](decisions/20260927-no-request-where-the-platform-has-none.md) | accepted, corrects FLOW-013 (8) | The notification request is made only where the platform has one (Android 13 and later); below that, on means nothing is shown and off means the explanation. |
| [20260927-the-serving-count-has-one-set.md](decisions/20260927-the-serving-count-has-one-set.md) | accepted, corrects SCALE-001, SCALE-008 and SCALE-011 | The serving count rounds over one set that holds 1, so 0.875 and up is 1; only a computed count is approximate, a requested one is shown as asked. |
| [20260927-a-session-is-ended-by-the-reader.md](decisions/20260927-a-session-is-ended-by-the-reader.md) | accepted, adds FLOW-058 | A session ends only when the reader closes the application or the device restarts; the system ending the process does not end it. |
| [20260927-the-deletion-key-is-handed-over-beside.md](decisions/20260927-the-deletion-key-is-handed-over-beside.md) | accepted, corrects AUTH-014, AUTH-017 and AUTH-024 | At creation the deletion key is shown in full beside the account key with its own copy control; each control copies one key; one confirmation names both. |
| [20260927-timer-duration-and-the-next-action.md](decisions/20260927-timer-duration-and-the-next-action.md) | accepted, completes FLOW-027 and GUIDE-007 | A timer is hours and minutes up to 99:59, and 0:00 is no timer; a scroll or a screen-reader move does not end a hint. |
| [20260927-the-editor-is-entered-from-the-cards-bar.md](decisions/20260927-the-editor-is-entered-from-the-cards-bar.md) | accepted, corrects FLOW-029 and FLOW-053 | An editable recipe's card carries an edit action in its top bar, outside the recipe; deletion stays on the list. |
| [20260927-stated-quantities-lie-on-the-grid.md](decisions/20260927-stated-quantities-lie-on-the-grid.md) | accepted, completes DOM, corrects SCALE-001 and SCALE-008 | An author's quantity and a reader's fixed amount are values of their unit type's grid; household is fixed in the author's unit only; a quantity published before a divisibility correction stays as written. |
| [20260927-a-sign-in-is-not-a-device-identifier.md](decisions/20260927-a-sign-in-is-not-a-device-identifier.md) | accepted, corrects AUTH-015 | A random secret the server mints at a sign-in is not a device identifier; its record lapses after six months unused and the device signs in again silently. |
| [20260928-the-device-first.md](decisions/20260928-the-device-first.md) | accepted, rewrites FLOW-002–FLOW-007, adds FLOW-059, FLOW-060 | Anyone writes and forks with no account and no server; a recipe reaches the server only when its owner sends it; the book is "My recipe book"; the allowance counts sent, unpublished media. |
| [20260928-several-accounts-on-one-device.md](decisions/20260928-several-accounts-on-one-device.md) | accepted, rewrites AUTH-011–AUTH-013, adds AUTH-026, AUTH-027 | A device holds every account signed in on it, each apart, switched from a list; what is made with no account is a pool offered once to each account. |
| [20260928-serverless-mode-and-recipe-files.md](decisions/20260928-serverless-mode-and-recipe-files.md) | accepted, adds FLOW-061–FLOW-065 | A release switches on serverless mode; recipes then travel as files, imported as found recipes with an unverified author, forks naming their parent. |
| [20260929-a-sending-the-server-already-holds.md](decisions/20260929-a-sending-the-server-already-holds.md) | accepted, narrows FLOW-008, corrects FLOW-059 (8), adds FLOW-066; order of checks completed by 20261001-a-fork-already-made-is-answered-first | A sending the same as the server's content is no divergence and changes nothing; a sending is stored whole or not at all, and a lost answer is settled by sending again. |
| [20260929-the-recipe-that-created-the-account.md](decisions/20260929-the-recipe-that-created-the-account.md) | accepted, widens ACT-002, corrects AUTH-026 (3); reason completed by 20261001-the-accounts-creation-gives-the-owner | A pool recipe whose sending creates an account is that account's whatever the answer to the pool offer. |
| [20260929-a-typed-quantity-is-read-by-its-value.md](decisions/20260929-a-typed-quantity-is-read-by-its-value.md) | accepted, adds DOM-019, completes DOM-017 | A comma and a point are both a decimal mark and no grouping is typed; a value on its grid is held however it is written. |
| [20261001-the-accounts-creation-gives-the-owner.md](decisions/20261001-the-accounts-creation-gives-the-owner.md) | accepted, corrects FLOW-059 (1), ACT-002 (3), adds FLOW-059 (12) | A pool recipe whose sending created an account is that account's even when nothing is sent — the account muted or the sending refused. |
| [20261001-an-unanswered-sending-keeps-its-mark.md](decisions/20261001-an-unanswered-sending-keeps-its-mark.md) | accepted, corrects FLOW-059 (9), adds FLOW-005 (8) | After a refused or unanswered sending a recipe keeps the mark it had; a first sending stays "never sent". |
| [20261001-a-fork-already-made-is-answered-first.md](decisions/20261001-a-fork-already-made-is-answered-first.md) | accepted, corrects FLOW-066, FLOW-008 (7) | A repeated sending that already made a fork is answered with that fork before sameness of content is checked. |
| [20261001-a-recipe-the-server-lost-is-sent-as-new.md](decisions/20261001-a-recipe-the-server-lost-is-sent-as-new.md) | accepted, adds FLOW-059 (13), qualifies ST-010 (2) | A sending of a recipe the server no longer holds creates it again in `private`, with its identity. |
| [20261001-a-sending-with-no-revision-is-made-from-the-first.md](decisions/20261001-a-sending-with-no-revision-is-made-from-the-first.md) | accepted, adds FLOW-059 (14); generalised by 20261001-a-sending-is-made-from-what-the-unanswered-one-stored | A sending that names no revision for a recipe the server holds is taken as made from its first revision. |
| [20261001-a-sending-is-made-from-what-the-unanswered-one-stored.md](decisions/20261001-a-sending-is-made-from-what-the-unanswered-one-stored.md) | accepted, adds FLOW-067 and FLOW-008 (8), narrows FLOW-066 (1) and FLOW-059 (9) | A sending after the device's own unanswered sending is made from what that sending stored — a revision or a fork; an edit in between is not a divergence. |
| [20261002-what-an-interactive-element-is.md](decisions/20261002-what-an-interactive-element-is.md) | accepted, completes A11Y-003 | A scrolling container is measured across its scroll, a disabled control at its enabled size; text that only takes focus or selection is not measured. |

## C4 context

Actors are settled in [`actors`](40-actors.md), the external systems in
[`external-interfaces`](130-external-interfaces.md). Nothing else crosses the
boundary: no advertising network, no identity provider, no analytics service and
no message delivery service reaching a user's device (EXT-009, EXT-010). The
alerting channel reaches the product's operator and carries numbers only
(OBS-004). One thing on the diagram is not a dependency: the availability probe
calls the product and the product never calls it (EXT-012).

```mermaid
flowchart TB
    holder["Person using the product<br/>with an account key, or without one"]
    moderator["Moderator<br/>the product owner"]
    system["Recipe Fork<br/>Android application and its server"]
    deletion["Account deletion page<br/>the only surface outside the application"]
    attest["Device attestation service<br/>three bits of state per device"]
    storage["Object storage<br/>media, no charge for reads"]
    channel["Distribution channel<br/>the store the application ships through"]
    alerts["Alerting channel<br/>one way, numbers only"]
    probe["Availability probe<br/>outside; calls in, is never called"]

    holder -->|"cooks, writes, forks and reports with no account; sends and rates with one"| system
    moderator -->|"approves, rejects, removes, bans"| system
    holder -->|"pastes a deletion key (EXT-006)"| deletion
    deletion -->|"deletes the account at once"| system
    system -->|"asks for a verdict, occupies a slot (AUTH-001)"| attest
    system -->|"stores, serves and deletes objects (EXT-003)"| storage
    system -->|"is distributed by and obliged by (CON-006, EXT-005)"| channel
    system -->|"sends a crossed threshold (OBS-004)"| alerts
    alerts -->|"read by the moderator; nothing comes back (OBS-005)"| moderator
    probe -->|"asks the silent endpoint whether it answers (EXT-011)"| system
    probe -->|"reports the product down when nothing else can (EXT-012)"| moderator
    holder -.->|"keeps the account key wherever they choose (EXT-008)"| holder
    holder -.->|"in serverless mode, passes recipe files by hand (FLOW-063)"| holder
```

## Retired IDs

No requirement has been retired yet. When one is deleted its ID is recorded
here with the date and reason, and is never reused.

| ID | Retired | Reason |
|---|---|---|
