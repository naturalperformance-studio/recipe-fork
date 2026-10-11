package app.recipefork.core.dictionary

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.EquipmentEntryDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** The generator's refusals: each names the file and the line, and nothing is built. */
class DictionaryBuildTest {
    private val tags = SourceFile("tags.tsv", "@range 100-999\n@range 10-11\n10\tитальянская\tItalian\n")

    private fun ingredients(vararg lines: String, range: String = "@range 1000-2999") =
        SourceFile("produce.tsv", "# produce\n$range\n" + lines.joinToString("\n") + "\n")

    private fun build(
        vararg files: SourceFile,
        tagFile: SourceFile = tags,
        previous: DictionariesDto? = null,
    ): BuildResult = DictionaryBuild.build(files.toList() + tagFile, previous?.let { Baseline("dictionaries.json", it) })

    private fun built(vararg files: SourceFile, tagFile: SourceFile = tags, previous: DictionariesDto? = null) =
        when (val result = build(*files, tagFile = tagFile, previous = previous)) {
            is BuildResult.Built -> result
            is BuildResult.Refused -> fail(result.errors.joinToString("\n"))
        }

    /** Asserts the build is refused with exactly one message, beginning with [where] and holding [what]. */
    private fun refused(result: BuildResult, where: String, what: String) {
        val errors = assertIs<BuildResult.Refused>(result).errors
        assertEquals(1, errors.size, errors.joinToString("\n"))
        assertTrue(errors.single().startsWith("$where: "), errors.single())
        assertTrue(errors.single().contains(what), errors.single())
    }

    @Test
    fun wellFormedSourcesBuildSortedByIdWithTheirVersion() {
        val result = built(
            ingredients("1001\tлук репчатый\tonion\t1000\ttrue\tg=1 pc=100", "1000\tлук\tonion family\t\t\tg=1"),
            SourceFile("animal.tsv", "@range 3000-4999\n3000\tяйцо\tegg\t\tfalse\tg=1 pc=55\t\n"),
        )
        assertEquals(listOf(1000L, 1001L, 3000L), result.dictionaries.ingredients.map { it.id })
        assertEquals(
            IngredientEntryDto(1001, "лук репчатый", "onion", 1000, true, mapOf("g" to 1.0, "pc" to 100.0)),
            result.dictionaries.ingredients[1],
        )
        assertEquals(listOf(TagDto(10, "итальянская", "Italian")), result.dictionaries.tags)
        assertEquals(result.dictionaries.digest(), result.dictionaries.version)
        assertEquals(result.dictionaries, DictionaryBuild.parseSnapshot(result.text))
        assertTrue(result.text.endsWith("}\n"))
    }

    @Test
    fun refusesADuplicateId() {
        refused(build(ingredients("1000\tлук\tonion", "1000\tчеснок\tgarlic")), "produce.tsv:4", "duplicate id 1000")
        refused(
            build(ingredients("1000\tлук\tonion"), SourceFile("produce-fruit.tsv", "@range 1000-2999\n1000\tяблоко\tapple\n")),
            "produce-fruit.tsv:2",
            "duplicate id 1000 (also at produce.tsv:3)",
        )
        refused(
            build(tagFile = SourceFile("tags.tsv", "@range 100-999\n100\tзавтрак\tbreakfast\n100\tужин\tdinner\n")),
            "tags.tsv:3",
            "duplicate id 100",
        )
    }

    @Test
    fun refusesAnIdOutsideItsFilesRange() {
        refused(build(ingredients("3000\tяйцо\tegg")), "produce.tsv:3", "outside the file's range 1000-2999")
        refused(build(ingredients("1\tкурица\tchicken")), "produce.tsv:3", "outside the file's range")
        refused(
            build(tagFile = SourceFile("tags.tsv", "@range 100-999\n1000\tзавтрак\tbreakfast\n")),
            "tags.tsv:2",
            "outside the file's range 100-999",
        )
    }

    @Test
    fun refusesARangeAFileMayNotDeclareAndAFileWithoutARange() {
        refused(build(ingredients("10\tлук\tonion", range = "@range 1-100")), "produce.tsv:2", "is not one a 'produce' file")
        refused(build(SourceFile("produce.tsv", "1000\tлук\tonion\n")), "produce.tsv:1", "declares no id range")
    }

    @Test
    fun aFileMayDeclareOnlyTheRangesOfTheFamilyItsNameBeginsWith() {
        // A split file keeps its family's range and cannot claim another's.
        built(SourceFile("dictionary/produce-fruit.tsv", "@range 1000-2999\n1500\tяблоко\tapple\n"))
        built(SourceFile("dictionary/animal-fish.tsv", "@range 3000-4999\n@range 1-5\n3500\tкарп\tcarp\n"))
        built(SourceFile("seasoning-sauces.tsv", "@range 7000-8999\n7000\tкетчуп\tketchup\n"))
        refused(
            build(SourceFile("dictionary/produce-fruit.tsv", "@range 3000-4999\n3500\tяблоко\tapple\n")),
            "dictionary/produce-fruit.tsv:1",
            "range 3000-4999 is not one a 'produce' file may declare (1000-2999)",
        )
        refused(build(SourceFile("pantry.tsv", "@range 1-5\n1\tмука\tflour\n")), "pantry.tsv:1", "is not one a 'pantry' file")
        refused(build(SourceFile("produce.tsv", "@range 1-5\n1\tлук\tonion\n")), "produce.tsv:1", "is not one a 'produce' file")
        refused(
            build(tagFile = SourceFile("tags-cuisine.tsv", "@range 1000-2999\n1000\tзавтрак\tbreakfast\n")),
            "tags-cuisine.tsv:1",
            "is not one a 'tags' file",
        )
        refused(build(SourceFile("misc.tsv", "@range 1000-2999\n1000\tлук\tonion\n")), "misc.tsv:1", "begins with no family")
        // A tags* file holds tags: three columns.
        refused(
            build(tagFile = SourceFile("tags.tsv", "@range 100-999\n100\tзавтрак\tbreakfast\t\ttrue\n")),
            "tags.tsv:2",
            "5 columns, at most 3",
        )
    }

    @Test
    fun refusesAReusedIdWhoseLabelsBothChanged() {
        val previous = DictionariesDto(
            "v",
            listOf(IngredientEntryDto(1000, "лук", "onion")),
            listOf(TagDto(10, "итальянская", "Italian")),
        )
        refused(
            build(ingredients("1000\tчеснок\tgarlic"), previous = previous),
            "produce.tsv:3",
            "id 1000 was shipped as 'лук' / 'onion'",
        )
        refused(
            build(ingredients("1000\tлук\tonion"), tagFile = SourceFile("tags.tsv", "@range 10-11\n10\tгрузинская\tGeorgian\n"), previous = previous),
            "tags.tsv:2",
            "id 10 was shipped as 'итальянская' / 'Italian'",
        )
        // One label corrected, the other kept (or changed only as the search folds it): the same thing.
        built(ingredients("1000\tлук репчатый\tonion"), previous = previous)
        built(ingredients("1000\tлук\tyellow onion"), previous = previous)
        built(ingredients("1000\tЛук\tbulb onion"), previous = previous)
    }

    @Test
    fun refusesAShippedIdMissingFromTheSources() {
        val previous = DictionariesDto(
            "v",
            listOf(IngredientEntryDto(1000, "лук", "onion"), IngredientEntryDto(1001, "чеснок", "garlic")),
            listOf(TagDto(10, "итальянская", "Italian")),
        )
        refused(build(ingredients("1000\tлук\tonion"), previous = previous), "dictionaries.json", "ingredient id 1001")
        refused(
            build(ingredients("1000\tлук\tonion", "1001\tчеснок\tgarlic"), tagFile = SourceFile("tags.tsv", "@range 100-999\n"), previous = previous),
            "dictionaries.json",
            "tag id 10",
        )
        // Marked removed, the entry stays, and the build goes through.
        val result = built(ingredients("1000\tлук\tonion", "1001\tчеснок\tgarlic\t\t\t\tremoved"), previous = previous)
        assertTrue(result.dictionaries.ingredients.single { it.id == 1001L }.removed)
    }

    @Test
    fun refusesABlankLabel() {
        refused(build(ingredients("1000\t\tonion")), "produce.tsv:3", "labelRu is blank")
        refused(build(ingredients("1000\tлук\t  ")), "produce.tsv:3", "labelEn is blank")
        refused(build(ingredients("1000\tлук")), "produce.tsv:3", "labelEn is blank")
        refused(
            build(tagFile = SourceFile("tags.tsv", "@range 100-999\n100\t\tbreakfast\n")),
            "tags.tsv:2",
            "labelRu is blank",
        )
    }

    @Test
    fun refusesADuplicateLabelAsTheSearchFoldsIt() {
        refused(build(ingredients("1000\tЁжевика\tblackberry", "1001\tежевика\tdewberry")), "produce.tsv:4", "labelRu 'ежевика'")
        refused(build(ingredients("1000\tлук-порей\tleek", "1001\tлук порей\tleeks")), "produce.tsv:4", "labelRu")
        refused(build(ingredients("1000\tлук\tOnion", "1001\tлук репчатый\tonion")), "produce.tsv:4", "labelEn 'onion'")
        refused(
            build(ingredients("1000\tлук\tonion"), SourceFile("pantry.tsv", "@range 5000-6999\n5000\tЛУК\tdried onion\n")),
            "pantry.tsv:2",
            "as 'лук' at produce.tsv:3",
        )
        refused(
            build(tagFile = SourceFile("tags.tsv", "@range 100-999\n100\tзавтрак\tbreakfast\n101\tЗавтрак\tbrunch\n")),
            "tags.tsv:3",
            "labelRu",
        )
        // A removed entry gives its label up.
        built(ingredients("1000\tлук\tonion\t\t\t\tremoved", "1001\tлук\tonion"))
    }

    // T-124: the generator folds diacritics as the search does, and takes a label's words in any order as its exact match does.
    @Test
    fun refusesADuplicateLabelWithDiacriticsOrItsWordsInAnotherOrder() {
        refused(
            build(ingredients("1000\tкрем-фреш\tcrème fraîche", "1001\tсливки кислые\tcreme fraiche")),
            "produce.tsv:4",
            "labelEn 'creme fraiche'",
        )
        refused(build(ingredients("1000\tхалапеньо\tjalapeño", "1001\tперец халапеньо\tJalapeno")), "produce.tsv:4", "labelEn")
        refused(build(ingredients("1000\tмасло сливочное\tbutter", "1001\tсливочное масло\tdairy butter")), "produce.tsv:4", "labelRu")
        // Words a search tells apart stay apart: a different word is a different label.
        built(ingredients("1000\tмасло сливочное\tbutter", "1001\tмасло топлёное\tghee"))
    }

    @Test
    fun refusesAParentThatIsMissingRemovedOrNotTopLevel() {
        refused(build(ingredients("1001\tлук репчатый\tonion\t1000")), "produce.tsv:3", "parentId 1000 names no entry")
        refused(
            build(ingredients("1000\tлук\tonions", "1001\tлук репчатый\tonion\t1000", "1002\tлук сладкий\tsweet onion\t1001")),
            "produce.tsv:5",
            "parentId 1001 is not a top-level entry",
        )
        refused(build(ingredients("1000\tлук\tonion\t1000")), "produce.tsv:3", "names itself")
    }

    @Test
    fun refusesARemovedEntryThatIsStillTheParentOfALiveOne() {
        refused(
            build(ingredients("1000\tлук\tonions\t\t\t\tremoved", "1001\tлук репчатый\tonion\t1000")),
            "produce.tsv:3",
            "removed entry 1000 is still the parent of live entry 1001 at produce.tsv:4",
        )
        // Both removed: nothing live hangs below.
        built(ingredients("1000\tлук\tonions\t\t\t\tremoved", "1001\tлук репчатый\tonion\t1000\t\t\tremoved"))
    }

    @Test
    fun refusesAnUnknownFactorUnitOrAFactorThatIsNotAFiniteNumberAboveZero() {
        refused(build(ingredients("1000\tлук\tonion\t\t\tg=1 kg=1000")), "produce.tsv:3", "unknown factor unit 'kg'")
        refused(build(ingredients("1000\tлук\tonion\t\t\tg=0")), "produce.tsv:3", "not a finite number above zero")
        refused(build(ingredients("1000\tлук\tonion\t\t\tg=-1")), "produce.tsv:3", "not a finite number above zero")
        refused(build(ingredients("1000\tлук\tonion\t\t\tg=NaN")), "produce.tsv:3", "not a finite number above zero")
        refused(build(ingredients("1000\tлук\tonion\t\t\tg=Infinity")), "produce.tsv:3", "not a finite number above zero")
        refused(build(ingredients("1000\tлук\tonion\t\t\tg=один")), "produce.tsv:3", "not 'unit=number'")
        refused(build(ingredients("1000\tлук\tonion\t\t\tg=1 g=2")), "produce.tsv:3", "given twice")
    }

    @Test
    fun refusesAPieceFactorWithoutTheDivisibilityFlag() {
        refused(build(ingredients("1000\tлук\tonion\t\t\tg=1 pc=100")), "produce.tsv:3", "needs the divisible flag")
        built(ingredients("1000\tлук\tonion\t\tfalse\tg=1 pc=100"))
    }

    @Test
    fun refusesMalformedLines() {
        refused(build(ingredients("лук\tлук\tonion")), "produce.tsv:3", "is not a positive whole number")
        refused(build(ingredients("1000\tлук\tonion\t\tmaybe")), "produce.tsv:3", "divisible 'maybe'")
        refused(build(ingredients("1000\tлук\tonion\t\t\t\tgone")), "produce.tsv:3", "removed column")
        refused(build(ingredients("1000\tлук\tonion\t\t\t\t\t\t\textra")), "produce.tsv:3", "10 columns")
        refused(build(ingredients("@rnage 1000-2999")), "produce.tsv:3", "unknown directive")
        refused(build(ingredients(" 1000\tлук\tonion")), "produce.tsv:3", "is not a positive whole number")
        refused(build(ingredients("1000\t лук\tonion")), "produce.tsv:3", "has spaces around it")
    }

    @Test
    fun synonymsAreReadPerLanguageWrittenIntoTheAssetAndCoveredByTheVersion() {
        val plain = built(ingredients("1000\tкартофель\tpotato\t\ttrue\tg=1 pc=150"))
        val result = built(
            ingredients(
                "1000\tкартофель\tpotato\t\ttrue\tg=1 pc=150\t\tкартошка;картоха",
                "1001\tсахарная пудра\tpowdered sugar\t\t\tg=1\t\t\tconfectioners' sugar;icing sugar",
            ),
        )
        val (potato, sugar) = result.dictionaries.ingredients
        assertEquals(listOf("картошка", "картоха"), potato.synonymsRu)
        assertEquals(emptyList(), potato.synonymsEn)
        assertEquals(emptyList(), sugar.synonymsRu)
        assertEquals(listOf("confectioners' sugar", "icing sugar"), sugar.synonymsEn)
        assertTrue(result.text.contains("\"icing sugar\""), result.text)
        assertEquals(result.dictionaries, DictionaryBuild.parseSnapshot(result.text))
        assertEquals(result.dictionaries.digest(), result.dictionaries.version)
        // The version covers the synonyms: the same entry without them has another digest.
        val potatoAlone = built(ingredients("1000\tкартофель\tpotato\t\ttrue\tg=1 pc=150\t\tкартошка"))
        assertTrue(potatoAlone.dictionaries.version != plain.dictionaries.version)
    }

    @Test
    fun anEntryWithoutSynonymsEncodesAndDigestsAsBefore() {
        val result = built(ingredients("1000\tкартофель\tpotato\t\ttrue\tg=1 pc=150\t\t\t"))
        assertTrue("synonyms" !in result.text, result.text)
        val before = """{"version":"","ingredients":[{"id":1000,"labelRu":"картофель","labelEn":"potato","divisible":true,""" +
            """"factors":{"g":1.0,"pc":150.0}}],"tags":[{"id":10,"labelRu":"итальянская","labelEn":"Italian"}]}"""
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(before.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(digest, result.dictionaries.version)
        // An asset written before synonyms existed reads with none.
        val old = DictionaryBuild.parseSnapshot(before)
        assertEquals(emptyList(), old.ingredients.single().synonymsRu)
        assertEquals(emptyList(), old.ingredients.single().synonymsEn)
    }

    @Test
    fun refusesABlankSynonymOrOneWithSpacesAroundIt() {
        refused(build(ingredients("1000\tкартофель\tpotato\t\t\t\t\tкартошка;;картоха")), "produce.tsv:3", "synonymsRu holds a blank synonym")
        refused(build(ingredients("1000\tкартофель\tpotato\t\t\t\t\tкартошка;")), "produce.tsv:3", "synonymsRu holds a blank synonym")
        refused(build(ingredients("1000\tкартофель\tpotato\t\t\t\t\t\t - ")), "produce.tsv:3", "synonymsEn holds a blank synonym")
        refused(build(ingredients("1000\tкартофель\tpotato\t\t\t\t\t\tspud; tater")), "produce.tsv:3", "synonymsEn ' tater' has spaces around it")
    }

    @Test
    fun refusesASynonymThatIsItsOwnEntrysLabel() {
        refused(build(ingredients("1000\tкартофель\tpotato\t\t\t\t\tКартофель")), "produce.tsv:3", "synonymsRu 'Картофель' is the entry's own label")
        refused(build(ingredients("1000\tсахарная пудра\tpowdered sugar\t\t\t\t\t\tsugar powdered")), "produce.tsv:3", "own label")
        refused(build(ingredients("1000\tгусь\tgoose\t\t\t\tremoved\t\tGoose")), "produce.tsv:3", "synonymsEn 'Goose' is the entry's own label")
    }

    @Test
    fun refusesASynonymThatIsAnotherLiveEntrysLabel() {
        refused(
            build(ingredients("1000\tпомидор\ttomato\t\t\t\t\tтоматы", "1001\tТоматы\tcanned tomatoes")),
            "produce.tsv:3",
            "synonymsRu 'томаты' is the same, as the search reads it, as the label 'Томаты' at produce.tsv:4",
        )
        refused(
            build(ingredients("1000\tпомидор\ttomato", "1001\tтомат\ttomatoes\t\t\t\t\t\ttomato")),
            "produce.tsv:4",
            "synonymsEn 'tomato' is the same, as the search reads it, as the label 'tomato' at produce.tsv:3",
        )
        // Across files, and a removed entry's label is no longer in the way.
        refused(
            build(ingredients("1000\tпомидор\ttomato\t\t\t\t\tманка"), SourceFile("pantry.tsv", "@range 5000-6999\n5000\tманка\tsemolina\n")),
            "produce.tsv:3",
            "as the label 'манка' at pantry.tsv:2",
        )
        built(ingredients("1000\tпомидор\ttomato\t\t\t\t\tтомат", "1001\tтомат\told tomato\t\t\t\tremoved"))
    }

    @Test
    fun refusesASynonymThatIsAnotherEntrysSynonymInTheSameLanguage() {
        refused(
            build(ingredients("1000\tсахарная пудра\tpowdered sugar\t\t\t\t\t\ticing sugar", "1001\tсахар\tsugar\t\t\t\t\t\tSugar, icing")),
            "produce.tsv:4",
            "synonymsEn 'Sugar, icing' is the same, as the search reads it, as the synonym 'icing sugar' at produce.tsv:3",
        )
        refused(build(ingredients("1000\tкартофель\tpotato\t\t\t\t\tкартошка;Картошка")), "produce.tsv:3", "as the synonym 'картошка'")
        // The same word in the other language is not the same synonym.
        built(ingredients("1000\tкартофель\tpotato\t\t\t\t\tspud", "1001\tбатат\tsweet potato\t\t\t\t\t\tspud"))
    }

    // A removed entry is offered to nobody: its synonyms are exempt from the cross checks — another entry's label or
    // synonym may be the same — and checked against its own label only.
    @Test
    fun aRemovedEntrysSynonymIsExemptFromTheCrossChecks() {
        // The same as a live entry's label.
        built(ingredients("1000\tманная крупа\tsemolina", "1001\tманка старая\told semolina\t\t\t\tremoved\tманная крупа"))
        // The same as a live entry's synonym, whichever stands first.
        built(ingredients("1000\tкартофель\tpotato\t\t\t\t\tкартошка", "1001\tкартофель старый\told potato\t\t\t\tremoved\tкартошка"))
        built(ingredients("1000\tкартофель старый\told potato\t\t\t\tremoved\tкартошка", "1001\tкартофель\tpotato\t\t\t\t\tкартошка"))
        // The same as another removed entry's synonym.
        built(
            ingredients(
                "1000\tгусь\tgoose\t\t\t\tremoved\t\tgander",
                "1001\tгусь домашний\tdomestic goose\t\t\t\tremoved\t\tgander",
            ),
        )
        // Its own label still is not its synonym.
        refused(build(ingredients("1000\tгусь\tgoose\t\t\t\tremoved\tгусь")), "produce.tsv:3", "is the entry's own label")
    }

    // T-201: the third dictionary, equipment, is built from equipment*.tsv with the ingredients' checks, on its own.
    private fun equipment(vararg lines: String, name: String = "equipment.tsv", range: String = "@range 10000-10999") =
        SourceFile(name, "$range\n" + lines.joinToString("\n") + "\n")

    @Test
    fun equipmentBuildsSortedByIdWithGroupsItemsSynonymsAndRemoval() {
        val result = built(
            equipment(
                "10001\tСковорода\tFrying pan\t10000\t\tсковородка\tskillet;frypan",
                "10000\tПосуда для плиты\tStovetop cookware",
                "10002\tКазан\tKazan\t10000\tremoved",
            ),
            SourceFile("equipment-appliances.tsv", "@range 10000-10999\n10200\tДуховка\tOven\n"),
        )
        assertEquals(
            listOf(
                EquipmentEntryDto(10000, "Посуда для плиты", "Stovetop cookware"),
                EquipmentEntryDto(10001, "Сковорода", "Frying pan", 10000, synonymsRu = listOf("сковородка"), synonymsEn = listOf("skillet", "frypan")),
                EquipmentEntryDto(10002, "Казан", "Kazan", 10000, removed = true),
                EquipmentEntryDto(10200, "Духовка", "Oven"),
            ),
            result.dictionaries.equipment,
        )
        assertTrue(result.dictionaries.ingredients.isEmpty())
        assertEquals(result.dictionaries, DictionaryBuild.parseSnapshot(result.text))
        // The version covers the equipment: another label, another version.
        val renamed = built(equipment("10000\tПосуда для плиты\tStovetop cookware", "10001\tСковорода\tSkillet\t10000"))
        val original = built(equipment("10000\tПосуда для плиты\tStovetop cookware", "10001\tСковорода\tFrying pan\t10000"))
        assertEquals(original.dictionaries.digest(), original.dictionaries.version)
        assertTrue(original.dictionaries.version != renamed.dictionaries.version)
    }

    @Test
    fun equipmentHasItsOwnRangeAndColumns() {
        refused(build(equipment("1000\tНож\tKnife")), "equipment.tsv:2", "outside the file's range 10000-10999")
        refused(build(equipment("10000\tНож\tKnife", range = "@range 1000-2999")), "equipment.tsv:1", "is not one a 'equipment' file")
        refused(build(ingredients("10000\tнож\tknife", range = "@range 10000-10999")), "produce.tsv:2", "is not one a 'produce' file")
        // No divisibility nor factors: seven columns at most, the fifth being the removal.
        refused(build(equipment("10000\tНож\tKnife\t\t\t\t\tg=1\t")), "equipment.tsv:2", "9 columns, at most 7")
        refused(build(equipment("10000\tНож\tKnife\t\ttrue")), "equipment.tsv:2", "'true' in the removed column")
    }

    @Test
    fun equipmentIsCheckedLikeTheIngredients() {
        refused(build(equipment("10000\tНож\tKnife", "10000\tДоска\tBoard")), "equipment.tsv:3", "duplicate id 10000")
        refused(build(equipment("10000\t\tKnife")), "equipment.tsv:2", "labelRu is blank")
        refused(build(equipment("10000\tНож\t ")), "equipment.tsv:2", "labelEn is blank")
        refused(build(equipment("10000\tТёрка\tGrater", "10001\tтерка\tBox grater")), "equipment.tsv:3", "labelRu 'терка'")
        refused(
            build(equipment("10000\tТехника\tAppliances", "10001\tБлендер\tBlender\t10000", "10002\tНасадка\tAttachment\t10001")),
            "equipment.tsv:4",
            "entries are two levels at most",
        )
        refused(build(equipment("10001\tБлендер\tBlender\t10000")), "equipment.tsv:2", "parentId 10000 names no entry")
        refused(
            build(equipment("10000\tСковорода\tFrying pan\t\t\tсковородка", "10001\tСковородка\tSmall pan")),
            "equipment.tsv:2",
            "synonymsRu 'сковородка' is the same, as the search reads it, as the label 'Сковородка'",
        )
        // Each dictionary is its own: an equipment label or id may also stand in the ingredients.
        built(ingredients("1000\tнож\tknife"), equipment("10000\tНож\tKnife"))
    }

    @Test
    fun shippedEquipmentIdsArePermanent() {
        val previous = DictionariesDto(
            "v",
            emptyList(),
            listOf(TagDto(10, "итальянская", "Italian")),
            listOf(EquipmentEntryDto(10000, "Нож", "Knife"), EquipmentEntryDto(10001, "Доска", "Board")),
        )
        refused(build(equipment("10000\tНож\tKnife"), previous = previous), "dictionaries.json", "equipment id 10001")
        refused(
            build(equipment("10000\tНож\tKnife", "10001\tТёрка\tGrater"), previous = previous),
            "equipment.tsv:3",
            "id 10001 was shipped as 'Доска' / 'Board'",
        )
        built(equipment("10000\tНож\tKnife", "10001\tДоска\tBoard\t\tremoved"), previous = previous)
        built(equipment("10000\tНож\tKnife", "10001\tРазделочная доска\tBoard"), previous = previous)
    }
}
