---
slug: SERVER
status: closed
updated: 2026-11-01
ids: []
---

# A server — later, not decided

The product ships serverless: everything a person does — writing, forking, scaling,
cooking, the recipe book, recipe files — runs on the device alone
(`70-flows/70-serverless-mode.md`). Whether the product ever has a server again is
not decided (OQ-044). Until it is, this page is the whole of the vision about one.

A server would add, and only a server can add: sending a recipe off the device and
publishing it; accounts; a public catalogue of other people's recipes; ratings;
moderation and a moderator; reports, blocking and publishing bans; importing posts
from social networks through a language model; and watching a running service.

## What holds if a server comes back

These are decisions the owner made while the server was specified. They are not
requirements now; they are where planning a server starts, and each would be
reopened deliberately, not by default.

- **The device stays first.** A recipe reaches a server only when its owner sends
  it; writing, cooking and forking never need an account or a connection
  ([ADR — the device first](decisions/20260928-the-device-first.md)).
- **An account holds no person.** No name, email, phone or avatar (NGOAL-001). It
  is held by a key its holder keeps; the product stores no key and recovers no
  account (NGOAL-003). A separate recovery key lets the holder retire a stolen
  key; a deletion key destroys the account from outside the application
  ([ADR — the recovery key](decisions/20260921-the-recovery-key.md),
  [ADR — the deletion key](decisions/20260920-deletion-key.md)).
- **Publication is permanent.** A recipe visible to anyone but its owner is
  frozen; it changes only by being forked (NGOAL-004, GOAL-002). A published
  recipe is replaced, never withdrawn
  ([ADR](decisions/20260921-a-published-recipe-is-replaced-never-withdrawn.md)).
- **Publishing is a grant.** The author licenses the method — ingredients and
  steps — to everyone, so forking rests on a grant; media and description stay the
  author's ([ADR — a fork carries the method, not the expression](decisions/20260920-fork-carries-method-not-expression.md)).
- **Moderation at first external visibility.** Media is reviewed by a human,
  never by automatic classification (NGOAL-005); a rejection carries a reason; a
  recipe removed as dangerous to health flags every descendant (FLOW-018)
  ([ADR — danger travels down the chain](decisions/20260920-danger-travels-down-the-chain.md)).
- **Two devices that diverge make a fork, never an overwrite.**
- **Ratings** are a binary like or dislike, one per account, never on one's own
  recipe; favouriting is not rating; readers, not the product, sort quality
  (NGOAL-007).
- **Nothing is measured about a person**; the running cost stays minimal (CON-002).
- **Importing a post** from Instagram, TikTok or Pinterest: read by a model with its
  own USD 50 a month, published as the source's recipe with no author and no media.
  An idea, in no release.

## The earlier specification

The full server specification, as written up to 2026-11-01 — accounts and keys,
moderation, ratings, abuse, import, observability, divergence, the moderator's
queue — is in git at commit `aff65dcc` under `spec/`. It is history to consult when
a server is planned, not a draft to restore: it was written for a launch that did
not happen, and much of it is detail the team would decide.
