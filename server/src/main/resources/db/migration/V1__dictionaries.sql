-- The ingredient dictionary and the tag dictionary.
--
-- Both are written only by direct operation on this store (DOM-014), so every
-- rule about them is a constraint of the store itself: nothing in the
-- application stands between the operator and these tables.

-- An ingredient entry: at most two levels deep (DOM-010), labelled in both
-- languages (L10N-003).
--
-- Two levels are held declaratively. `is_top_level` is true exactly when the
-- entry has no parent, and a child's foreign key names its parent together
-- with `parent_is_top_level`, which is always true. So a parent must be a
-- top-level entry (no child under an entry that has a parent), and an entry
-- with children cannot be given a parent, because its `is_top_level` would
-- turn false under the key its children still reference.
create table ingredient_entry (
    entry_id            bigint generated always as identity primary key,
    label_ru            text,
    label_en            text,
    parent_id           bigint,
    parent_is_top_level boolean not null default true,
    is_top_level        boolean generated always as (parent_id is null) stored,

    constraint ingredient_entry_label_in_both_languages
        check (coalesce(btrim(label_ru), '') <> '' and coalesce(btrim(label_en), '') <> ''),
    constraint ingredient_entry_parent_is_top_level_is_true
        check (parent_is_top_level),
    constraint ingredient_entry_level_key
        unique (entry_id, is_top_level),
    constraint ingredient_entry_parent_is_top_level
        foreign key (parent_id, parent_is_top_level)
        references ingredient_entry (entry_id, is_top_level)
);

create index ingredient_entry_parent_idx on ingredient_entry (parent_id);

-- A tag, labelled in both languages (L10N-003).
create table tag (
    tag_id   bigint generated always as identity primary key,
    label_ru text,
    label_en text,

    constraint tag_label_in_both_languages
        check (coalesce(btrim(label_ru), '') <> '' and coalesce(btrim(label_en), '') <> '')
);
