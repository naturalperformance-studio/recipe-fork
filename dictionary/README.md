# The dictionary sources

The application ships its two fixed dictionaries — ingredients and tags — in
`app/src/main/assets/dictionaries.json`. That file is **generated** from the sources in this directory and is never
edited by hand: a test (`ShippedDictionaryTest` in `:core`) rebuilds it from these sources and fails unless the
shipped file is byte for byte what they build.

## Building

```
scripts/dictionary-build.sh
```

(or `./gradlew :core:buildDictionary`). It reads every `*.tsv` here, validates them against the previous snapshot
(the asset as it is now), and writes the asset with its `version` — the lowercase hex SHA-256 of the compact JSON of
the dictionaries with an empty version, encoded by the application's own DTOs. On any problem it prints each one as
`file:line: what is wrong`, exits with 1 and leaves the asset as it was. Commit the sources and the regenerated asset
together.

`scripts/dictionary-snapshot.sh` still pulls the dictionaries from a server, should there ever be one again; what it
writes is then no longer what these sources build, and the test says so.

## Files and id ranges

| File                      | Holds                                                                                              | Ids         |
|---------------------------|----------------------------------------------------------------------------------------------------|-------------|
| `produce.tsv`             | vegetables, mushrooms, fresh herbs, fruit, berries, nuts and seeds                                 | 1000–2999   |
| `animal.tsv`              | meat, poultry, offal, fish, seafood, eggs, dairy and cheese                                        | 3000–4999, and the legacy 1–5 |
| `pantry.tsv`              | flour, grains, pasta, bread, legumes, sugar and sweeteners, baking, oils and fats                  | 5000–6999   |
| `seasoning-and-other.tsv` | spices, dried herbs, salt, sauces and condiments, vinegars, canned and preserved goods, stocks, drinks and alcohol used in cooking, other | 7000–8999 |
| `tags.tsv`                | tags: cuisines, meal and course, diet, method and appliance, occasion and season, characteristics  | 100–999, and the legacy 10–11 |

Every file declares its ranges itself with `@range FIRST-LAST` lines (one per range); the generator accepts only the
ranges in this table and refuses an id outside the file's declared ranges. Any other `*.tsv` here is read as an
ingredient file too, so a family may be split into several files, each declaring its family's range.

## Format

UTF-8 text, one entry per line, columns separated by a single **tab**. A line starting with `#` is a comment; blank
lines are ignored. Entries may stand in any order; the asset lists them by id.

Ingredient files — `id, labelRu, labelEn, parentId, divisible, factors, removed`; empty trailing columns may be left
out:

| Column      | Value                                                                                                 |
|-------------|-------------------------------------------------------------------------------------------------------|
| `id`        | a positive whole number in the file's range; permanent once shipped                                   |
| `labelRu`   | the Russian label, no spaces around it                                                                |
| `labelEn`   | the English label, no spaces around it                                                                |
| `parentId`  | empty for a top-level entry, else the id of a top-level entry (two levels at most)                    |
| `divisible` | empty (not known), `true` (may be used in halves) or `false` (whole pieces only)                      |
| `factors`   | space-separated `unit=number`, units `g ml pc tsp tbsp cup pinch`, each a finite number above zero     |
| `removed`   | empty, or `removed` for an entry no longer offered (it keeps naming the recipes that hold it)          |

```
@range 3000-4999
@range 1-5
1	курица	chicken		true	g=1 pc=1500
2	крылышки	chicken wings	1	false	g=1 pc=80
3	мясо	meat			g=1
3100	куриные окорочка	chicken leg quarters	1	false	g=1 pc=300	removed
```

The tags file — `id, labelRu, labelEn`:

```
@range 100-999
@range 10-11
10	итальянская	Italian
```

## What the generator refuses

- a malformed line: an id that is not a positive whole number, a `divisible` other than `true`/`false`/empty, a
  `removed` column other than `removed`/empty, too many columns, an unknown `@` directive, a file without `@range`;
- a duplicate id (ingredients across all ingredient files; tags within the tags file);
- an id outside its file's ranges;
- an id shipped in the previous snapshot that is missing from the sources — ids are permanent: a dropped ingredient
  stays, marked `removed`; a tag is never dropped;
- a blank label (no letter or digit), or a label with spaces around it;
- two live entries (or two tags) whose labels in one language the search cannot tell apart: compared as the search
  folds them — case ignored, `ё` read as `е`, words being runs of letters and digits (so `лук-порей` and `лук порей`
  are the same label);
- a `parentId` that names no entry, names the entry itself, or names an entry that has a parent;
- a removed entry that is still the parent of a live entry;
- an unknown factor unit, a factor that is not a finite number above zero, a unit given twice;
- a `pc` factor without the `divisible` flag.

## Content rules

- **Labels.** Short base nominative forms an author would type: Russian as cooks write it in a list ("лук репчатый",
  "мука пшеничная", "яйцо куриное" or simply "яйцо" with leaves); English likewise. Russian is the primary language of
  the product; English labels are natural, not transliterations.
- **Families, two levels.** A generic top-level entry with its kinds as leaves (мука → мука пшеничная, мука ржаная…;
  сыр → пармезан, моцарелла…). A leaf is never a parent. An entry that is not a kind of anything is top-level with no
  leaves.
- **What a piece is.** A `pc` factor is the weight of the thing cooks count under that label (one egg, one onion, one
  clove). Where cooks count different things under one word, make separate entries (чеснок — a head; зубчик чеснока —
  a clove).
- **Divisibility.** `divisible: false` for what cannot be split in a recipe (an egg, a clove, a bay leaf); `true` for
  what can be used in halves (an onion, a lemon, a pepper); every entry with a `pc` factor has the flag.
- **Factors, one scale.** Grams per unit for every entry: g = 1; ml = grams per millilitre (density); pc = grams per
  piece; tsp, tbsp, cup (a cup is 250 ml) and pinch = grams each, level measure. Give only the factors a cook would
  plausibly use for that ingredient; never guess wildly — use common culinary references.
- **Ids.** Each family source owns a range (produce 1 000–2 999, animal 3 000–4 999, pantry 5 000–6 999, seasoning
  and the rest 7 000–8 999, tags 100–999; 1–5 and 10–11 stay). Ids are permanent once shipped.
- **No brands**, no recipes-as-ingredients ("тесто для пиццы" is fine as a bought product; "соус бешамель" as a sauce
  is fine), no duplicates across families (the generator refuses duplicate labels).
