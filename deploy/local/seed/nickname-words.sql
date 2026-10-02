-- PLACEHOLDER nickname word lists, for the local deployment only.
--
-- These are NOT the product owner's lists. AUTH-023 has the product owner write
-- and review the real adjective and noun lists before the first account is
-- created; until then this placeholder lets a local server offer nicknames of
-- the right form (AUTH-022: two adjectives, then a noun). Never load it into a
-- production store.
--
-- Safe to run more than once. Run with deploy/local/seed/seed.sh after the
-- server has started once (its migrations create the tables).

insert into nickname_adjective (word) values
    ('amber'), ('brave'), ('bright'), ('calm'), ('clever'), ('copper'),
    ('eager'), ('gentle'), ('golden'), ('happy'), ('jolly'), ('keen'),
    ('lively'), ('lucky'), ('merry'), ('nimble'), ('quiet'), ('rapid'),
    ('silver'), ('steady'), ('sunny'), ('swift'), ('tidy'), ('witty')
on conflict (word) do nothing;

insert into nickname_noun (word) values
    ('acorn'), ('badger'), ('beacon'), ('comet'), ('ember'), ('falcon'),
    ('fern'), ('heron'), ('kettle'), ('lantern'), ('maple'), ('meadow'),
    ('orchard'), ('otter'), ('pebble'), ('puffin'), ('quail'), ('river'),
    ('sparrow'), ('teapot'), ('thistle'), ('walnut'), ('willow'), ('wren')
on conflict (word) do nothing;
