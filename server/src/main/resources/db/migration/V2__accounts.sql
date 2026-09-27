-- Accounts, their keys, and the two word lists nicknames are drawn from.
--
-- An account row exists only once creation is confirmed (AUTH-007 (7)): a
-- nickname offered or rerolled, and keys shown before confirmation, leave
-- nothing here.

-- The nickname word lists (AUTH-023). The product owner keeps them by hand and
-- changes them directly in the store, as the dictionaries are changed
-- (DOM-014). A word is held in its canonical form: lower-case Latin letters
-- only. Its capital letter is added when a nickname is displayed (AUTH-022).
create table nickname_adjective (
    word text primary key,

    constraint nickname_adjective_is_one_lower_case_latin_word
        check (word ~ '^[a-z]+$')
);

create table nickname_noun (
    word text primary key,

    constraint nickname_noun_is_one_lower_case_latin_word
        check (word ~ '^[a-z]+$')
);

-- An account.
--
-- The nickname is held as its three words, in canonical form, so two
-- nicknames are compared as the three words and can never differ only in case
-- or spacing (AUTH-022 (3)); the unique key makes it held by one account only
-- (DOM-001). The words are copied, not referenced: removing a word from a list
-- leaves every nickname that holds it unchanged (AUTH-023 (4)).
--
-- The acceptance of the terms of use is a version identifier and the date it
-- was accepted on, and nothing about a person (LEG-004, RET-010). No column
-- holds a name, an email address, a telephone number or an image (CON-001).
create table account (
    account_id        bigint generated always as identity primary key,
    nickname_first    text not null,
    nickname_second   text not null,
    nickname_noun     text not null,
    terms_version     text not null,
    terms_accepted_on date not null,

    constraint account_nickname_words_are_canonical
        check (nickname_first ~ '^[a-z]+$' and nickname_second ~ '^[a-z]+$' and nickname_noun ~ '^[a-z]+$'),
    constraint account_nickname_unique
        unique (nickname_first, nickname_second, nickname_noun),
    constraint account_terms_version_is_an_identifier
        check (terms_version ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$')
);

-- An account's keys: the account key, the deletion key and the recovery key
-- (AUTH-014, AUTH-017, AUTH-020). Only the SHA-256 digest of each key is held
-- (ADR 20260927-keys-are-hashed-random-secrets), so nothing here reconstructs a
-- key. The digest is the primary key across all three kinds, so no key of any
-- kind is held twice.
create table account_key (
    digest     bytea primary key,
    account_id bigint not null references account (account_id) on delete cascade,
    kind       text not null,

    constraint account_key_digest_is_sha256
        check (octet_length(digest) = 32),
    constraint account_key_kind_is_known
        check (kind in ('account', 'deletion', 'recovery')),
    constraint account_key_one_per_kind
        unique (account_id, kind)
);
