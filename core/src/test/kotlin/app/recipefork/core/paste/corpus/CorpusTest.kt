package app.recipefork.core.paste.corpus

import app.recipefork.core.paste.IngredientLineReaderTest
import app.recipefork.core.paste.RecipeFromText
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
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
        val missed = measures.flatMap { m -> m.recipe?.ingredients.orEmpty().filterNot(CorpusMeasures::quantityRead).map { "${m.text.name}: $it" } }
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
        assertTrue(Regex("""(?:src|href)\s*=\s*"(?:https?:)?//""").find(html) == null, "nothing loaded from elsewhere")
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
    }

    @Test
    fun aRowCountsAsReadOnlyWhenItsQuantityIsNoPartOfItsName() {
        fun row(text: String) = assertNotNull(read("Т\n\nИнгредиенты\n- $text\n- 100 г сахара\n\nПриготовление\n1. Смешать всё.")).ingredients.first()
        assertTrue(CorpusMeasures.quantityRead(row("500 г творога")))
        assertTrue(CorpusMeasures.quantityRead(row("Соль — по вкусу")))
        assertTrue(CorpusMeasures.quantityRead(row("Зелень для подачи")))
        assertFalse(CorpusMeasures.quantityRead(row("Чеснок — 1 головка")), "a unit the reader does not know")
        assertFalse(CorpusMeasures.quantityRead(row("800 г")), "a quantity on a line of its own")
        assertFalse(CorpusMeasures.quantityRead(row("по вкусу")))
    }

    @Test
    fun countingDurationsIsLinearOnAnyText() {
        val long = "1".repeat(50_000) + " " + "1 - ".repeat(20_000) + "ч".repeat(20_000) + " 5 минут"
        val took = measureTime { WrittenDurations.count(long) }
        assertTrue(took.inWholeSeconds < 2, "$took")
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
