-- An ingredient entry's divisibility, its comparison factors and its removal
-- (decisions/20260920-divisibility-belongs-to-the-ingredient.md,
-- decisions/20260920-comparison-factors.md, DOM-009, DOM-011, CAT-009).
--
-- Written, like the rest of the dictionaries, only by direct operation on this
-- store (DOM-014), so every rule about them is a constraint here.

-- Whether a `piece` quantity of the entry may hold halves; null while the
-- dictionary does not say, and an author is then asked as for free text.
alter table ingredient_entry add column divisible boolean;

-- A removed entry stays, so that every recipe naming it keeps its name; it is
-- offered to nobody. An entry is never deleted (its id is permanent).
alter table ingredient_entry add column removed boolean not null default false;

-- An entry's comparison factor per unit, on a scale shared by its family. Exact
-- units are given through `g` and `ml` (a kilogram or a litre is compared
-- through its multiple of them); `pc`, `tsp`, `tbsp`, `cup` and `pinch` each on
-- its own. A factor orders and nothing else, and is never shown (DOM-011).
create table ingredient_factor (
    entry_id bigint           not null references ingredient_entry (entry_id) on delete cascade,
    unit     text             not null,
    factor   double precision not null,

    primary key (entry_id, unit),
    constraint ingredient_factor_unit_is_known
        check (unit in ('g', 'ml', 'pc', 'tsp', 'tbsp', 'cup', 'pinch')),
    constraint ingredient_factor_is_above_zero
        check (factor > 0 and factor < 'Infinity'::double precision)
);
