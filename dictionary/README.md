# The dictionary sources

The application ships its three fixed dictionaries — ingredients, tags and equipment — in
`app/src/main/assets/dictionaries.json`. That file is **generated** from the sources in this directory and is never
edited by hand: a test (`ShippedDictionaryTest` in `:core`) rebuilds it from these sources and fails unless the
shipped file is byte for byte what they build.

## Building

```
scripts/dictionary-build.sh
```

(or `./gradlew :core:buildDictionary`). It reads every `*.tsv` here, validates them, and writes the asset with its
`version` — the lowercase hex SHA-256 of the compact JSON of the dictionaries with an empty version
(`DictionariesDto.digest()`, the same code the server and the application use). On any problem it prints each one as
`file:line: what is wrong`, exits with 1 and leaves the asset as it was. Commit the sources and the regenerated asset
together.

### The baseline

Ids are permanent, so the sources are checked against the snapshot **already shipped**: the asset as committed in git,
never the file in the working tree (deleting or regenerating that one changes nothing). The commit is the merge base
of `HEAD` and `develop` when a `develop` branch exists — what develop has shipped — else `HEAD`. A task branch may
therefore still renumber or drop ids it added itself; once develop has them, they stay. Messages about the baseline name
it as `app/src/main/assets/dictionaries.json@<commit>`.

When no baseline can be read (not a git checkout, no commit, the asset never committed) the build is refused.
`scripts/dictionary-build.sh --first-build` skips the check, and is only for a repository that never shipped a
dictionary.

### Merging

The asset is generated, so it is never merged by hand. On a conflict in `app/src/main/assets/dictionaries.json`
(`.gitattributes` marks it `merge=binary`: git keeps our side whole, without conflict markers, and still reports the
conflict):

1. resolve the conflicts in the sources under `dictionary/` (each family owns its files and its id range, so these are
   rare);
2. take either side of the asset (`git checkout --ours app/src/main/assets/dictionaries.json`);
3. run `scripts/dictionary-build.sh` on the merged sources — it checks them against the merge base and rewrites the
   asset;
4. run `./gradlew testAll`;
5. `git add` the sources and the asset, and commit.

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
| `equipment.tsv`           | equipment: stovetop cookware, oven and baking, appliances, knives and cutting, tools and utensils, measuring tools, storage, consumables | 10000–10999 |

Every file declares its ranges itself with `@range FIRST-LAST` lines (one per range); the generator refuses an id
outside the file's declared ranges. A file's name decides its family: a family may be split into several files whose
names begin with its prefix (`produce-fruit.tsv`, `animal-fish.tsv`, `tags-cuisine.tsv`), and each such file may
declare only its family's ranges from the table. A `*.tsv` whose name begins with no prefix is refused.

## Format

UTF-8 text, one entry per line, columns separated by a single **tab**. A line starting with `#` is a comment; blank
lines are ignored. Entries may stand in any order; the asset lists them by id.

Ingredient files — `id, labelRu, labelEn, parentId, divisible, factors, removed, synonymsRu, synonymsEn`; empty
trailing columns may be left out:

| Column      | Value                                                                                                 |
|-------------|-------------------------------------------------------------------------------------------------------|
| `id`        | a positive whole number in the file's range; permanent once shipped                                   |
| `labelRu`   | the Russian label, no spaces around it                                                                |
| `labelEn`   | the English label, no spaces around it                                                                |
| `parentId`  | empty for a top-level entry, else the id of a top-level entry (two levels at most)                    |
| `divisible` | empty (not known), `true` (may be used in halves) or `false` (whole pieces only)                      |
| `factors`   | space-separated `unit=number`, units `g ml pc tsp tbsp cup pinch`, each a finite number above zero     |
| `removed`   | empty, or `removed` for an entry no longer offered (it keeps naming the recipes that hold it)          |
| `synonymsRu`| empty, or the other Russian words cooks write for the entry, separated by `;` (`картошка;картоха`)      |
| `synonymsEn`| likewise in English (`confectioners' sugar;icing sugar`)                                               |

```
@range 3000-4999
@range 1-5
1	курица	chicken		true	g=1 pc=1500
2	крылышки	chicken wings	1	false	g=1 pc=80
3	мясо	meat			g=1
3100	куриные окорочка	chicken leg quarters	1	false	g=1 pc=300	removed
3001	курдюк	lamb tail fat			g=1		курдючный жир;курдючное сало
```

A synonym is a word an author types for the entry when it is not the label: the ingredient field and the book's search
may find the entry by it, while the entry is still shown under its label. An entry without synonyms is written into
the asset exactly as before they existed, so its digest does not change.

The tags file — `id, labelRu, labelEn`:

```
@range 100-999
@range 10-11
10	итальянская	Italian
```

The equipment files — `id, labelRu, labelEn, parentId, removed, synonymsRu, synonymsEn`; the columns mean what they
mean for an ingredient, and an ingredient's `divisible` and `factors` have no place here. A top-level entry is a group
(«Посуда для плиты» / "Stovetop cookware"), its leaves are the items a kitchen has; every item stands in a group. What is used
up in cooking — parchment, foil, cling film, bags — stands in «Расходники» / "Consumables" (10700), which a kitchen
never counts (the application's My kitchen). The equipment is maintained here alone: the server need not serve it, and a snapshot without it reads as one with none.

```
@range 10000-10999
10000	Посуда для плиты	Stovetop cookware
10001	Сковорода	Frying pan	10000		сковородка	skillet;frypan
```

## What the generator refuses

- a malformed line: an id that is not a positive whole number, a `divisible` other than `true`/`false`/empty, a
  `removed` column other than `removed`/empty, too many columns, an unknown `@` directive, a file without `@range`;
- a duplicate id (ingredients across all ingredient files; tags across all tag files; equipment across all equipment
  files) — each dictionary is checked on its own, so a label or an id may stand in two of them;
- a file whose name begins with no family, a `@range` its family does not own, an id outside its file's ranges;
- an id shipped in the previous snapshot that is missing from the sources — ids are permanent: a dropped ingredient
  or equipment entry stays, marked `removed`; a tag is never dropped;
- a reused id: a shipped id whose labels have **both** changed (as the search folds them) — that is another thing under
  an old id, and recipes holding the id would change meaning; correcting one label is fine;
- no baseline at all, unless `--first-build` is given;
- a blank label (no letter or digit), or a label with spaces around it;
- two live entries (or two tags) whose labels in one language the search cannot tell apart: compared as the search
  folds them — case ignored, `ё` read as `е`, Latin diacritics dropped, words being runs of letters and digits, in any
  order (so `лук-порей` and `лук порей`, `crème fraîche` and `creme fraiche`, `масло сливочное` and `сливочное масло`
  are the same label: the field takes a label typed with its words in another order as an exact match);
- a blank synonym (an empty item between `;`, or one with no letter or digit) or one with spaces around it;
- a synonym that, as the search folds it, is its own entry's label, any live entry's label in the same language, or
  another synonym in the same language (in this entry or another) — a synonym must lead to one entry only; a removed
  entry's synonyms are checked against its own label only;
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
  and the rest 7 000–8 999, tags 100–999, equipment 10 000–10 999; 1–5 and 10–11 stay). Ids are permanent once shipped.
  Equipment groups take the round hundreds (10000, 10100…) and their items the ids after them.
- **Equipment labels.** Sentence case, as a list of equipment shows them: «Сковорода» / "Frying pan", «Форма для
  выпечки» / "Baking dish"; the base nominative form a cook writes, singular unless only a pair or set exists
  («Ступка» / "Mortar and pestle"). Synonyms in lower case, as for the ingredients. No sizes in the label —
  "26 cm" is the recipe line's qualifier, not another entry.
- **Synonyms.** Only words cooks really write for exactly this entry (картошка, манка, icing sugar) — colloquial or
  regional names, not kinds of it (a kind is a leaf) and not a vaguer word that would fit several entries — except a bare word that, on its own, cooks use for this entry (масло → butter, перец → black pepper, укроп → fresh dill).
- **No brands**, no recipes-as-ingredients ("тесто для пиццы" is fine as a bought product; "соус бешамель" as a sauce
  is fine), no duplicates across families (the generator refuses duplicate labels).
