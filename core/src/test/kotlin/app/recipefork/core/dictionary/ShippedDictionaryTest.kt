package app.recipefork.core.dictionary

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.NutritionDto
import app.recipefork.core.api.TagDto
import app.recipefork.core.nutrition.CountedRow
import app.recipefork.core.nutrition.LeftOutReason
import app.recipefork.core.nutrition.LeftOutRow
import app.recipefork.core.nutrition.Nutrition
import app.recipefork.core.nutrition.NutritionEntries
import app.recipefork.core.nutrition.r
import app.recipefork.core.nutrition.row
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.scaling.ScaleFactor
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The shipped `dictionaries.json` is what the sources under `dictionary/` build to, byte for byte, so it is never
 * edited by hand: a change goes into the sources and `scripts/dictionary-build.sh` (dictionary/README.md).
 */
class ShippedDictionaryTest {
    /** Unit tests of `:core` run with the module directory as the working directory. */
    private val root = File("..")
    private val sources = File(root, SOURCES)
    private val asset = File(root, ASSET)

    /** Built as scripts/dictionary-build.sh builds, against the snapshot committed in git, never the working-tree file. */
    private fun built(): BuildResult.Built = when (val result = buildFrom(root)) {
        is BuildResult.Built -> result
        is BuildResult.Refused -> fail("the sources are refused:\n" + result.errors.joinToString("\n"))
    }

    @Test
    fun theBaselineIsTheCommittedSnapshot() {
        assertIs<BaselineRead.Found>(gitBaseline(root))
    }

    @Test
    fun theShippedAssetIsExactlyWhatTheSourcesBuild() {
        assertEquals(
            asset.readText(Charsets.UTF_8),
            built().text,
            "app/src/main/assets/dictionaries.json differs from the sources; run scripts/dictionary-build.sh",
        )
        assertTrue(asset.readBytes().contentEquals(built().text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun theWordsCooksWriteLeadToTheirEntries() {
        val byLabel = built().dictionaries.ingredients.associateBy { it.labelRu }
        fun synonyms(label: String) = byLabel.getValue(label).let { it.synonymsRu to it.synonymsEn }
        assertEquals(listOf("картошка") to emptyList(), synonyms("картофель"))
        assertEquals(listOf("томат") to emptyList(), synonyms("помидор"))
        assertEquals(listOf("манка") to emptyList(), synonyms("манная крупа"))
        assertEquals(listOf("курдючный жир") to emptyList(), synonyms("курдюк"))
        assertEquals(emptyList<String>() to listOf("confectioners' sugar", "icing sugar"), synonyms("сахарная пудра"))
        assertEquals(listOf("булочка для гамбургера") to listOf("hamburger bun"), synonyms("булочка для бургера"))
        // T-131: the duplicate пармиджано реджано is removed; the word leads to the Parmesan.
        assertEquals(listOf("пармиджано реджано") to listOf("Parmigiano Reggiano"), synonyms("пармезан"))
        assertTrue(built().dictionaries.ingredients.single { it.id == 4792L }.removed)
    }

    // T-287: the format's first data, copied from USDA FoodData Central SR Legacy (cited in the sources, not the asset).
    @Test
    fun theFirstEntriesCarryTheirNutritionFromFoodDataCentral() {
        val dictionaries = built().dictionaries
        val sugar = NutritionDto(387.0, 0.0, 0.0, 99.98)
        val butter = NutritionDto(717.0, 0.85, 81.11, 0.06)
        val flour = NutritionDto(364.0, 10.33, 0.98, 76.31)
        val first = mapOf(
            4400L to NutritionDto(143.0, 12.56, 9.51, 0.72),
            4600L to butter, 4601L to butter, 4602L to butter,
            5001L to flour, 5002L to flour,
            6100L to sugar, 6101L to sugar,
            8800L to NutritionDto(0.0, 0.0, 0.0, 0.0),
        )
        // Later data tasks add entries; these nine keep their values.
        assertEquals(first, dictionaries.ingredients.filter { it.id in first }.associate { it.id to it.nutrition })
        val dairy = File(sources, "animal-dairy.tsv").readText(Charsets.UTF_8)
        assertTrue("fdc:171287" in dairy && "fdc:173430" in dairy && "fdc:173410" in dairy)
        // No source reaches the asset: neither a citation nor the word that marks an entry without values.
        val shipped = asset.readText(Charsets.UTF_8)
        listOf("fdc:", "skurikhin:", "nutritionSource", "\"none\"").forEach { assertTrue(it !in shipped, it) }
    }

    // T-294: children of another form that no source holds are marked as having no values of its own, and do not take
    // their parent's; and the review's data fixes.
    @Test
    fun childrenOfAnotherFormCarryTheMarkAndTheReviewedEntriesTheirSources() {
        val byId = built().dictionaries.ingredients.associateBy { it.id }
        val marked = mapOf(
            1403L to "мини-кукуруза", 1122L to "чеснок чёрный", 3621L to "утиная грудка", 3622L to "утиные ножки",
            3641L to "гусиная грудка", 3642L to "гусиные ножки", 4224L to "кальмар сушёный", 4238L to "мидии в половинках раковин",
            4486L to "двойные сливки", 4491L to "девонширские сливки", 4566L to "лабне", 4567L to "сузьма", 4464L to "молоко 6%",
            3126L to "говяжьи кости", 3127L to "мозговая кость", 3131L to "говяжьи ноги", 5024L to "мука льняная",
            5048L to "мука льняная золотистая", 5028L to "мука конопляная", 5029L to "мука тыквенная", 5030L to "мука кедровая",
            5513L to "лапша ширатаки", 6072L to "сливки соевые", 6073L to "сливки овсяные", 8004L to "соевый соус сладкий",
            8204L to "горчица медовая", 8206L to "горчица баварская", 8302L to "уксус столовый 9%", 8304L to "уксусная эссенция",
            8457L to "маслины вяленые", 8609L to "даши", 8835L to "матча", 4738L to "буррата", 4739L to "страчателла",
        )
        marked.forEach { (id, label) ->
            val entry = byId.getValue(id)
            assertEquals(label, entry.labelRu)
            assertTrue(entry.noNutrition && entry.nutrition == null && byId[entry.parentId]?.nutrition != null, "$entry")
        }
        assertEquals(marked.keys, byId.values.filter { it.noNutrition }.map { it.id }.toSet())
        // Counted from the shipped entries, 100 g of each is left out as no data; the corn beside them counts.
        val rows = (listOf(1400L) + marked.keys).mapIndexed { i, id -> row(id, r(100), QuantityUnit.GRAM).copy(position = i) }
        val dish = assertNotNull(Nutrition.of(rows, ScaleFactor.ONE, NutritionEntries.of(byId.values)))
        assertEquals(marked.keys.indices.map { LeftOutRow(it + 1, LeftOutReason.NO_DATA) }, dish.leftOut)
        assertEquals(listOf(CountedRow(0, java.math.BigInteger.valueOf(90))), dish.counted)
        val fixed = mapOf(
            1816L to NutritionDto(31.0, 3.12, 0.57, 5.1),
            2412L to NutritionDto(317.0, 2.5, 2.5, 80.0),
            4777L to NutritionDto(173.0, 24.35, 7.0, 1.91),
            4741L to NutritionDto(350.0, 6.15, 34.44, 5.52),
            // Re-read from the book: Skurikhin prints 284 kcal for «Шиповник сухой» (9.2.1.13, p. 156-157).
            2414L to NutritionDto(284.0, 3.4, 1.4, 48.3),
        )
        assertEquals(fixed, fixed.keys.associateWith { byId.getValue(it).nutrition })
        // Raw pistachios differ from the mixed nuts by less than the child rule, so the pistachios inherit.
        listOf(2506L, 2507L, 2515L, 2526L).forEach { assertEquals(2500L to null, byId.getValue(it).let { e -> e.parentId to e.nutrition }) }
        val sources = sources.listFiles { f -> f.name.endsWith(".tsv") }!!.joinToString("\n") { it.readText(Charsets.UTF_8) }
        listOf(
            "# сморчки → fdc:168423 Mushrooms, morel, raw", "# черника сушёная → fdc:168158 Blueberries, dried, sweetened",
            "# сыр лёгкий → fdc:173439 Cheese, low fat, cheddar or colby", "# маскарпоне → fdc:173418 Cheese, cream",
            "# свиной стейк → fdc:167849 ", "# эскалоп → fdc:167839 ", "# Skurikhin 3.1.9, p. 52", "# Skurikhin 4.2.1, p. 98",
            "# фисташки → inherits орехи: fdc:170184 Nuts, pistachio nuts, raw",
        ).forEach { assertTrue(it in sources, it) }
    }

    @Test
    fun theVersionIsTheDigestOfTheContent() {
        val dictionaries = built().dictionaries
        assertEquals(dictionaries.digest(), dictionaries.version)
    }

    @Test
    fun theEntriesShippedBeforeTheSourcesKeepTheirIdsLabelsFlagsAndFactors() {
        val dictionaries = built().dictionaries
        // Nutrition is added by the data tasks; ids, labels, flags and factors stay as shipped.
        val legacy = dictionaries.ingredients.filter { it.id <= 5 }.map { it.copy(nutrition = null) }
        assertEquals(
            listOf(
                IngredientEntryDto(1, "курица", "chicken", null, true, mapOf("g" to 1.0, "pc" to 1500.0)),
                IngredientEntryDto(
                    2, "крылышки", "chicken wings", 1, false, mapOf("g" to 1.0, "pc" to 80.0),
                    synonymsRu = listOf("куриные крылья", "куриные крылышки"),
                ),
                IngredientEntryDto(3, "мясо", "meat", factors = mapOf("g" to 1.0)),
                IngredientEntryDto(4, "фарш", "mince", factors = mapOf("g" to 1.0)),
                IngredientEntryDto(5, "говядина", "beef", factors = mapOf("g" to 1.0)),
            ),
            legacy,
        )
        assertEquals(
            listOf(TagDto(10, "итальянская", "Italian"), TagDto(11, "грузинская", "Georgian")),
            dictionaries.tags.filter { it.id <= 11 },
        )
    }

    @Test
    fun eachFamilyFileDeclaresItsRange() {
        val declared = mapOf(
            "produce.tsv" to listOf("@range 1000-2999"),
            "animal.tsv" to listOf("@range 3000-4999", "@range 1-5"),
            "pantry.tsv" to listOf("@range 5000-6999"),
            "seasoning-and-other.tsv" to listOf("@range 7000-8999"),
            "tags.tsv" to listOf("@range 100-999", "@range 10-11"),
            "equipment.tsv" to listOf("@range 10000-10999"),
        )
        declared.forEach { (name, ranges) ->
            val lines = File(sources, name).readLines(Charsets.UTF_8)
            assertEquals(ranges, lines.filter { it.startsWith("@range") }.map { it.trim() }, name)
        }
    }

    @Test
    fun theReadmeDocumentsTheGeneratorTheRangesAndTheContentRules() {
        val readme = File(sources, "README.md").readText(Charsets.UTF_8)
        listOf(
            "scripts/dictionary-build.sh", "1000–2999", "10000–10999", "`equipment.tsv`", "The equipment files", "3000–4999", "5000–6999", "7000–8999", "100–999",
            "**Labels.**", "**Families, two levels.**", "**What a piece is.**", "**Divisibility.**",
            "**Factors, one scale.**", "**Ids.**", "**No brands**",
            "### The baseline", "merge base", "--first-build", "### Merging", "./gradlew testAll",
            "`nutrition`", "`nutritionSource`", "### Nutrition", "USDA FoodData Central", "public domain", "`fdc:<id>`",
            "Skurikhin", "`skurikhin:<the product's name in the table>`", "100 g of the raw product", "**`none`**", "`IngredientEntryDto.noNutrition`",
        ).forEach { assertTrue(it in readme, "README.md lacks $it") }
        assertTrue("$ASSET merge=binary" in File(root, ".gitattributes").readText(), ".gitattributes")
    }

    // T-201: the equipment dictionary ships beside the ingredients and the tags — groups of a home kitchen, their items
    // labelled in both languages in short base forms, two levels, in its own id range.
    @Test
    fun theEquipmentDictionaryCoversAHomeKitchen() {
        val equipment = built().dictionaries.equipment
        val live = equipment.filterNot { it.removed }
        assertTrue(live.size in 150..250, "${live.size} live equipment entries")
        assertTrue(equipment.all { it.id in 10000L..10999L })
        val groups = equipment.filter { it.parentId == null }
        assertEquals(
            listOf(
                "Stovetop cookware", "Oven and baking", "Appliances", "Knives and cutting", "Tools and utensils",
                "Measuring tools", "Storage", "Consumables",
            ),
            groups.map { it.labelEn },
        )
        val groupIds = groups.map { it.id }.toSet()
        // Every item sits in a group; no group is empty.
        equipment.filter { it.parentId != null }.forEach { assertTrue(it.parentId in groupIds, "$it") }
        groupIds.forEach { group -> assertTrue(equipment.any { it.parentId == group }, "group $group is empty") }
        val byRu = live.associateBy { it.labelRu }
        listOf(
            "Сковорода" to "Frying pan", "Духовка" to "Oven", "Блендер" to "Blender", "Кастрюля" to "Saucepan",
            "Форма для выпечки" to "Baking dish",
        ).forEach { (ru, en) -> assertEquals(en, byRu[ru]?.labelEn, ru) }
        assertEquals(listOf("сковородка"), byRu.getValue("Сковорода").synonymsRu)
        // Short base forms: a label of at most five words, no punctuation at its end.
        live.forEach { entry ->
            listOf(entry.labelRu, entry.labelEn).forEach { label ->
                assertTrue(label.split(' ').size <= 5 && label.last().isLetterOrDigit(), "$entry")
            }
        }
    }

    // T-204, from T-201's review: what is used up in cooking stands in a group of its own, which a kitchen never counts;
    // the measuring group's English label says what its Russian one does; two vague synonyms are gone. Ids stay.
    @Test
    fun consumablesStandInAGroupOfTheirOwn() {
        val equipment = built().dictionaries.equipment
        val byId = equipment.associateBy { it.id }
        val consumables = byId.getValue(10700L)
        assertEquals(null, consumables.parentId)
        assertEquals("Расходники" to "Consumables", consumables.labelRu to consumables.labelEn)
        assertEquals(
            setOf("Parchment paper", "Aluminium foil", "Roasting bag", "Cheesecloth", "Kitchen twine", "Paper towel", "Cling film", "Zip-lock bag", "Vacuum bag"),
            equipment.filter { it.parentId == 10700L }.map { it.labelEn }.toSet(),
        )
        listOf(10119L, 10120L, 10121L, 10426L, 10427L, 10431L, 10603L, 10604L, 10605L).forEach {
            assertEquals(10700L, byId.getValue(it).parentId, "$it")
        }
        assertEquals("Мерная посуда" to "Measuring tools", byId.getValue(10500L).let { it.labelRu to it.labelEn })
        assertEquals(listOf("дегидратор"), byId.getValue(10228L).synonymsRu)
        assertEquals(listOf("форма для запекания"), byId.getValue(10102L).synonymsRu)
        assertTrue(equipment.none { "сушилка" in it.synonymsRu || "форма" in it.synonymsRu })
    }
}
