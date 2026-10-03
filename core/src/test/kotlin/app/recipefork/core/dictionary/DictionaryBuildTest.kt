package app.recipefork.core.dictionary

import app.recipefork.core.api.DictionariesDto
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
    ): BuildResult = DictionaryBuild.build(files.toList(), tagFile, previous, "dictionaries.json")

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
        assertEquals(DictionaryBuild.versionOf(result.dictionaries), result.dictionaries.version)
        assertEquals(result.dictionaries, DictionaryBuild.parseSnapshot(result.text))
        assertTrue(result.text.endsWith("}\n"))
    }

    @Test
    fun refusesADuplicateId() {
        refused(build(ingredients("1000\tлук\tonion", "1000\tчеснок\tgarlic")), "produce.tsv:4", "duplicate id 1000")
        refused(
            build(ingredients("1000\tлук\tonion"), SourceFile("fruit.tsv", "@range 1000-2999\n1000\tяблоко\tapple\n")),
            "fruit.tsv:2",
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
        refused(build(ingredients("10\tлук\tonion", range = "@range 1-100")), "produce.tsv:2", "is not one this kind")
        refused(build(SourceFile("produce.tsv", "1000\tлук\tonion\n")), "produce.tsv:1", "declares no id range")
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
        refused(build(ingredients("1000\tлук\tonion\t\t\t\t\textra")), "produce.tsv:3", "8 columns")
        refused(build(ingredients("@rnage 1000-2999")), "produce.tsv:3", "unknown directive")
        refused(build(ingredients(" 1000\tлук\tonion")), "produce.tsv:3", "is not a positive whole number")
        refused(build(ingredients("1000\t лук\tonion")), "produce.tsv:3", "has spaces around it")
    }
}
