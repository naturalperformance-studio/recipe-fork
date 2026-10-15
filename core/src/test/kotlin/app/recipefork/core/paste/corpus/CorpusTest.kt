package app.recipefork.core.paste.corpus

import app.recipefork.core.paste.IngredientLineReaderTest
import app.recipefork.core.paste.RecipeFromText
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * T-281: the paste parser against real recipes — the owner's own 44 and texts in the common web and messenger shapes —
 * measured against the shipped dictionary, and written out as a report of each text beside the recipe it became.
 */
class CorpusTest {
    private val totals get() = CorpusTotals(measures)

    @Test
    fun theCorpusHoldsTheOwnersRecipesAndTextsOfBothLanguages() {
        assertEquals(44, texts.count { it.set == CorpusText.OWNER })
        assertEquals(1, texts.count { it.isShoppingList })
        assertTrue(texts.count { it.set == "en" } >= 8)
        assertTrue(texts.count { it.set == "ru" } >= 8)
    }

    @Test
    fun theShoppingListGivesNoRecipe() {
        assertNull(measures.single { it.text.isShoppingList }.recipe)
    }

    @Test
    fun everyTextGivesARecipe() {
        assertEquals(emptyList(), measures.filter { !it.text.isShoppingList && it.recipe == null }.map { it.text.name })
        assertTrue(measures.filter { it.recipe != null }.all { it.rows > 0 && it.recipe!!.steps.isNotEmpty() })
    }

    @Test
    fun everyRecipePassesTheRecipeFileCheck() {
        assertEquals(emptyMap(), measures.filter { it.fileProblems.isNotEmpty() }.associate { it.text.name to it.fileProblems })
    }

    @Test
    fun ingredientRowsGetAQuantityAndUnitOrToTaste() {
        val missed = measures.flatMap { m ->
            val rows = m.recipe?.ingredients.orEmpty()
            rows.zip(CorpusMeasures.readRows(rows)).filterNot { it.second }.map { "${m.text.name}: ${it.first}" }
        }
        assertTrue(totals.quantifiedShare >= CorpusMeasures.QUANTIFIED_FLOOR, "${totals.quantifiedShare}\n" + missed.joinToString("\n"))
    }

    @Test
    fun ingredientRowsAreBoundToTheDictionary() {
        assertTrue(totals.boundShare >= CorpusMeasures.BOUND_FLOOR, "${totals.boundShare}")
    }

    @Test
    fun durationsWrittenInStepsBecomeTimers() {
        val missed = measures.flatMap { m ->
            m.recipe?.steps.orEmpty().filter { it.timers.size < WrittenDurations.count(it.text) }.map { "${m.text.name}: ${it.text}" }
        }
        assertTrue(totals.timerShare >= CorpusMeasures.TIMER_FLOOR, "${totals.timerShare}\n" + missed.joinToString("\n"))
    }

    @Test
    fun theSummaryIsPrintedAndTheReportWritten() {
        println(summary(measures))
        val file = File("build/reports/paste-corpus/index.html")
        CorpusReport.write(file, texts, read, shipped)
        val html = file.readText()
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(texts.all { html.contains(it.name.replace("&", "&amp;")) })
        assertLoadsNothing(html)
        println("Report: ${file.absoluteFile.canonicalPath}")
    }

    @Test
    fun theReportRendersWhatAnyReaderGives() {
        val text = CorpusText("en", "one.txt", "Toast\n\n- 2 slices bread\n- 1 tbsp butter\n\n1. Toast the bread for 3 minutes.\n2. Spread with the butter.")
        // A reader of another setting: here, one that puts a reference to the first row into the first step.
        val withReference: (String) -> Recipe? = { pasted ->
            read(pasted)?.let { recipe ->
                recipe.copy(steps = recipe.steps.mapIndexed { i, step -> if (i == 0) step.withReference() else step })
            }
        }
        val html = CorpusReport.html(listOf(text), withReference, shipped)
        assertTrue(html.contains("<span class=\"ref\""), html)
        assertTrue(CorpusReport.html(listOf(text), { null }, shipped).contains("No recipe"))
    }

    @Test
    fun theReportShowsEveryTextAsText() {
        val evil = "</td></tr></table><script>alert(1)</script> <img src=x onerror=alert(1)> </pre> \"quoted\""
        val text = CorpusText(
            "en", "evil.txt",
            "Pie $evil\n\nIngredients\n- 2 cups flour $evil\n- 1 tbsp butter\n\nMethod\n1. Mix $evil for 5 minutes.\n2. Bake.",
        )
        val recipe = assertNotNull(read(text.text))
        assertTrue(evil in recipe.title && recipe.steps[0].text.contains("<script>"), "the parser keeps the words: ${recipe.title}")
        val html = CorpusReport.html(listOf(text), read, shipped)
        assertFalse(Regex("<script|<img|\"quoted\"", RegexOption.IGNORE_CASE).containsMatchIn(html), html)
        assertEquals(1, Regex("</pre>").findAll(html).count(), "only the report's own")
        assertEquals(Regex("<table").findAll(html).count(), Regex("</table>").findAll(html).count())
        assertTrue(html.contains("&lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;") && html.contains("&lt;/pre&gt;") && html.contains("&quot;quoted&quot;"))
        assertLoadsNothing(html)
    }

    @Test
    fun writtenDurationsAreCountedOnTheirOwn() {
        assertEquals(1, WrittenDurations.count("Варить 20 минут."))
        assertEquals(1, WrittenDurations.count("Варить 35–40 минут."))
        assertEquals(1, WrittenDurations.count("Томить 1 ч 30 мин."))
        assertEquals(1, WrittenDurations.count("Cook 45 mins - 1 hr."))
        assertEquals(2, WrittenDurations.count("Bake for 25 min, then rest for 1 hr 15 mins."))
        assertEquals(1, WrittenDurations.count("Оставить на полчаса."))
        assertEquals(0, WrittenDurations.count("Помешивать каждые 5 минут."))
        assertEquals(0, WrittenDurations.count("Добавить 1 ч. л. соли и 30 секунд прогреть."))
        assertEquals(0, WrittenDurations.count("Храните 3 дня."))
        assertEquals(1, WrittenDurations.count("Всыпать специи и прогреть минуту."))
        assertEquals(1, WrittenDurations.count("Снять с огня и держать ещё минуту."))
        assertEquals(1, WrittenDurations.count("Дать побулькать минуту."))
        assertEquals(1, WrittenDurations.count("Взбивать минуту на высокой скорости."))
        assertEquals(1, WrittenDurations.count("Охладить минимум час, лучше два."))
        assertEquals(1, WrittenDurations.count("Томить полтора часа."))
        assertEquals(1, WrittenDurations.count("Оставить на час."))
        assertEquals(2, WrittenDurations.count("Stir for a minute, then rest for an hour."))
        assertEquals(1, WrittenDurations.count("Rest for half an hour."))
        assertEquals(0, WrittenDurations.count("Выпекать на минуту меньше."))
        assertEquals(0, WrittenDurations.count("Поставить тесто на час раньше."))
        assertEquals(0, WrittenDurations.count("Add the herbs an hour later."))
        assertEquals(0, WrittenDurations.count("Помешивать каждую минуту."))
        assertEquals(1, WrittenDurations.count("Жарить блины по минуте с каждой стороны."))
        assertEquals(1, WrittenDurations.count("Жарить около минуты без переворота."))
        assertEquals(1, WrittenDurations.count("Жарить по 1 минуте с каждой стороны."))
        assertEquals(1, WrittenDurations.count("На 5-й минуте убавить огонь, варить 20 минут."))
        assertEquals(0, WrittenDurations.count("Часовой пояс, минутная стрелка, начать."))
    }

    @Test
    fun aRowCountsAsReadOnlyWhenItsQuantityIsNoPartOfItsName() {
        /** Whether each of [lines], under an ingredient heading, has its quantity read. */
        fun readOf(vararg lines: String): List<Boolean> {
            val text = "Т\n\nИнгредиенты\n" + lines.joinToString("") { "$it\n" } + "100 г сахара\n\nПриготовление\n1. Смешать всё."
            return CorpusMeasures.readRows(assertNotNull(read(text), text).ingredients).dropLast(1)
        }
        assertEquals(listOf(true), readOf("500 г творога"))
        assertEquals(listOf(true), readOf("500 г творога 9%"), "a percentage says what it is")
        assertEquals(listOf(true), readOf("50 г мацони 1–3%"))
        assertEquals(listOf(true), readOf("Соль — по вкусу"))
        assertEquals(listOf(true), readOf("Зелень — для подачи"))
        assertEquals(listOf(false), readOf("Чеснок — 1 головка"), "a unit the reader does not know")
        assertEquals(listOf(false, false), readOf("Говядина на кости", "800 г"), "a name and its quantity on lines of their own")
        assertEquals(listOf(false, false), readOf("Соль", "по вкусу"))
        assertEquals(listOf(false, false), readOf("Чеснок", "3 зубчика"))
        assertEquals(listOf(false), readOf("куриная грудка, замороженная — 350 г"), "a quantity left in the note")
        assertEquals(listOf(false), readOf("2 x 400g cans chopped tomatoes"), "a quantity left in the qualifier")
        assertEquals(listOf(false, false), readOf("Душистый перец", "4 горошины"), "an eda.ru pair with a unit the list lacks")
        assertEquals(listOf(false), readOf("пучок рукколы"), "a quantity in words left in the qualifier of a to-taste row")
        assertEquals(listOf(false), readOf("juice of half a lime"))
    }

    @Test
    fun countingDurationsIsLinearOnAnyText() {
        val long = "1".repeat(50_000) + " " + "1 - ".repeat(20_000) + "ч".repeat(20_000) + " 5 минут"
        val words = "на ".repeat(20_000) + "минуту ".repeat(20_000) + "a ".repeat(20_000) + "minute"
        val name = "1".repeat(50_000) + " % " + "зубчик".repeat(10_000)
        val row = Ingredient(0, null, qualifier = name, freeTextName = name, unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null, note = name)
        val took = measureTime {
            WrittenDurations.count(long)
            WrittenDurations.count(words)
            CorpusMeasures.readRows(listOf(row, row))
        }
        assertTrue(took.inWholeSeconds < 2, "$took")
    }

    /** Nothing in [html] is loaded from elsewhere: no remote address, script, stylesheet link, image, url() or @import. */
    private fun assertLoadsNothing(html: String) {
        val loads = Regex("""(?:src|href)\s*=\s*"(?:https?:)?//|<script|<link|<img|url\(|@import""", RegexOption.IGNORE_CASE)
        assertEquals(null, loads.find(html)?.value, "nothing loaded from elsewhere")
    }

    private fun Step.withReference() = copy(ingredientReferences = listOf(app.recipefork.core.recipe.IngredientReference(0, 0)))

    companion object {
        val shipped = IngredientLineReaderTest.shipped
        private val at = Instant.parse("2026-10-15T10:00:00Z")
        val read: (String) -> Recipe? = { RecipeFromText.read(it, shipped, at, AccountId("owner")) }
        val texts = CorpusText.load()
        val measures by lazy { CorpusMeasures.measure(texts, read, shipped) }

        /** One line per text, then the corpus figures. */
        fun summary(measures: List<TextMeasure>): String = buildString {
            appendLine(String.format(Locale.ROOT, "%-70s %5s %5s %5s %6s %5s %9s %5s %5s", "text", "rows", "bound", "free", "as-wr", "refs", "timers", "equip", "check"))
            measures.forEach { m ->
                val name = "${m.text.set}/${m.text.name}".take(70)
                if (m.recipe == null) {
                    appendLine(String.format(Locale.ROOT, "%-70s no recipe", name))
                } else {
                    appendLine(
                        String.format(
                            Locale.ROOT, "%-70s %5d %5d %5d %6d %5d %9s %5d %5s", name, m.rows, m.bound, m.free, m.asWritten, m.references,
                            "${m.timers} ${m.timedDurations}/${m.writtenDurations}", m.equipment, if (m.fileProblems.isEmpty()) "pass" else "FAIL",
                        ),
                    )
                }
            }
            val t = CorpusTotals(measures)
            appendLine(
                String.format(
                    Locale.ROOT,
                    "texts %d, recipes %d; rows %d: quantity read %.1f %% (%d), bound %.1f %% (%d), free %d; durations timed %.1f %% (%d/%d), timers %d; references %d; equipment %d; file check %d/%d",
                    t.texts, t.recipes, t.rows, t.quantifiedShare * 100, t.quantified, t.boundShare * 100, t.bound, t.free,
                    t.timerShare * 100, t.timedDurations, t.writtenDurations, t.timers, t.references, t.equipment, t.passingFileCheck, t.recipes,
                ),
            )
        }
    }
}
