package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.ReadingFamily
import app.recipefork.app.ui.WorkingFamily
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.StatedParent
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.fork
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * T-312, v0.43 scenario 2: the card's title area as a page of a cookbook — the title in Literata 28, its words kept
 * whole at a large font; the stated author under it, inside the heading block; the summary in Golos under them; the
 * description in Literata; nothing kept for a cover a recipe does not have. The card's states — loading, not found,
 * not loaded — keep their words with the engraved mark in place of an icon. A version shows once its parent is read
 * as well, so that its provenance line and its title come together and nothing moves.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardTitleAreaTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-29T12:00:00Z")
    private var type = Typography()

    /** How many reads of a recipe are still to fail. */
    private val failing = AtomicInteger(0)

    /** Held shut, a read of the recipe or the parent it names waits on it. */
    @Volatile
    private var gate: Pair<RecipeId, CompletableDeferred<Unit>>? = null

    /** A recipe whose every read fails. */
    @Volatile
    private var broken: RecipeId? = null

    private val recipes = object : RecipeRepository(ActiveBook(BookName.Pool, book.database)) {
        override fun observe(id: RecipeId): Flow<Recipe?> {
            val real = super.observe(id)
            return flow {
                gate?.takeIf { it.first == id }?.second?.await()
                if (id == broken) throw IOException("the parent does not read back")
                if (failing.getAndUpdate { (it - 1).coerceAtLeast(0) } > 0) throw IOException("the row does not read back")
                emitAll(real)
            }
        }
    }

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun save(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private fun recipe(title: String) = Recipe.create(now, null).copy(title = title, servings = 2, steps = listOf(Step(0, "Mix")))

    private var shown by mutableStateOf<RecipeId?>(null)
    private var composed = false

    /** The card of [id], set once and then turned to another recipe. */
    private fun setCard(id: RecipeId) {
        if (!composed) {
            composed = true
            shown = id
            compose.setContent {
                AppTheme {
                    type = MaterialTheme.typography
                    shown?.let { key(it) { CardScreen(it, recipes, environment, onBack = {}, onEdit = {}) } }
                }
            }
        } else {
            compose.runOnIdle { shown = id }
        }
    }

    private fun show(id: RecipeId) {
        setCard(id)
        compose.awaitCard()
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun unmerged(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun bounds(tag: String): Rect = unmerged(tag).boundsInRoot

    private fun dp(px: Float): Float = px / unmerged(CardTags.SCREEN).layoutInfo.density.density

    private fun layout(tag: String): TextLayoutResult = compose.textLayoutsWithin(tag).single()

    // Criterion 1: the title in Literata Medium 28 sp, as the mock sets it; the summary in Golos, bodySmall; the
    // description in Literata.
    @Test
    fun theTitleIsLiterata28TheSummaryGolosAndTheDescriptionLiterata() {
        show(
            save(
                recipe("Shakshuka").copy(
                    description = "Eggs baked in a spiced tomato sauce.",
                    steps = listOf(Step(0, "Fry the onion"), Step(1, "Add the eggs")),
                ),
            ).id,
        )
        val title = layout(CardTags.TITLE).layoutInput.style
        assertEquals(ReadingFamily, title.fontFamily)
        assertEquals(28.sp, title.fontSize)
        assertEquals(FontWeight.Medium, title.fontWeight)
        val summary = layout(CardTags.SUMMARY).layoutInput.style
        assertEquals(WorkingFamily, summary.fontFamily)
        assertEquals(type.bodySmall.fontSize, summary.fontSize)
        assertEquals(ReadingFamily, layout(CardTags.DESCRIPTION).layoutInput.style.fontFamily)
        assertEquals(listOf("Shakshuka"), compose.textsOf(CardTags.TITLE))
        assertTrue(unmerged(CardTags.TITLE).config.contains(SemanticsProperties.Heading))
    }

    // Criterion 1, the rest: without a cover no space is kept for one — the title stands at the content's top.
    @Test
    fun withoutACoverTheTitleOpensTheCard() {
        show(save(recipe("Borscht")).id)
        assertFalse(exists(CardTags.COVER))
        assertFalse(exists(CardTags.COVER_UNAVAILABLE))
        val gap = dp(bounds(CardTags.TITLE).top - bounds(CardTags.CONTENT).top)
        assertTrue("nothing above the title but the content's top margin ($gap dp)", gap >= -0.5f && gap <= 8.5f)
    }

    // Criterion 2: «Рецепт не найден» — the same words and the way back, with the engraved mark over a Literata 22
    // headline; the way back outlined, at the card's target height.
    @Test
    @Config(qualifiers = "ru")
    fun aRecipeNotFoundShowsTheEngravedMarkWithItsWords() {
        show(RecipeId("not-in-this-book"))
        assertEngraved(CardTags.MISSING, "Рецепт не найден")
        assertEquals(listOf("Этого рецепта больше нет в этой книге рецептов."), compose.textsOf(LibraryTags.emptySentence(CardTags.MISSING)))
        assertEquals(listOf("Назад к книге"), compose.textsOf(CardTags.MISSING_BACK))
        compose.assertCardTarget(CardTags.MISSING_BACK)
    }

    // Criterion 2: «Не удалось загрузить» — the same words and «Попробовать ещё раз», with the engraved mark.
    @Test
    @Config(qualifiers = "ru")
    fun aRecipeThatCannotBeReadShowsTheEngravedMarkWithItsWords() {
        val recipe = save(recipe("Борщ"))
        failing.set(1)
        show(recipe.id)
        assertEngraved(CardTags.UNREADABLE, "Не удалось загрузить этот рецепт.")
        assertEquals(listOf("Попробовать ещё раз"), compose.textsOf(CardTags.RETRY))
        compose.assertCardTarget(CardTags.RETRY)
    }

    // Criterion 2: «Рецепт загружается» — the same words, with the engraved mark, once the read takes a while.
    @Test
    @Config(qualifiers = "ru")
    fun aRecipeStillLoadingShowsTheEngravedMarkWithItsWords() {
        val recipe = save(recipe("Борщ"))
        val held = CompletableDeferred<Unit>()
        gate = recipe.id to held
        setCard(recipe.id)
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.LOADING) }
        assertEngraved(CardTags.LOADING, "Рецепт загружается")
        held.complete(Unit)
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.LOADING) }
    }

    /** The state tagged [state] shows the engraved mark, 112 dp, decorative, above its [headline] in Literata 22. */
    private fun assertEngraved(state: String, headline: String) {
        val picture = LibraryTags.emptyPicture(state)
        compose.waitUntil(PATIENCE_MS) { exists(picture) }
        assertEquals(112f, dp(bounds(picture).width), 0.5f)
        assertEquals(112f, dp(bounds(picture).height), 0.5f)
        assertTrue("the mark is decorative", unmerged(picture).config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty())
        assertEquals(listOf(headline), compose.textsOf(LibraryTags.emptyHeadline(state)))
        val style = layout(LibraryTags.emptyHeadline(state)).layoutInput.style
        assertEquals(ReadingFamily, style.fontFamily)
        assertEquals(type.titleLarge.fontSize, style.fontSize)
        assertTrue("the mark above the headline", bounds(picture).bottom <= bounds(LibraryTags.emptyHeadline(state)).top + 0.5f)
    }

    // Criterion 2, the rest: where the state does not fit at a large font on a small phone, it drops its mark, and only
    // its mark — the words and the way out stay, whole on the screen.
    @Test
    @Config(qualifiers = "ru-w320dp-h480dp", fontScale = 2f)
    fun atALargeFontOnASmallPhoneTheStateDropsItsMarkAndKeepsItsWords() {
        show(RecipeId("not-in-this-book"))
        assertFalse("the mark left out", exists(LibraryTags.emptyPicture(CardTags.MISSING)))
        assertEquals(listOf("Рецепт не найден"), compose.textsOf(LibraryTags.emptyHeadline(CardTags.MISSING)))
        assertTrue(exists(LibraryTags.emptySentence(CardTags.MISSING)))
        val whole = compose.onNodeWithTag(CardTags.MISSING_BACK).getUnclippedBoundsInRoot()
        val shownBack = compose.onNodeWithTag(CardTags.MISSING_BACK).getBoundsInRoot()
        val screen = compose.onRoot().getBoundsInRoot()
        assertEquals("the way back shown whole", (whole.bottom - whole.top).value, (shownBack.bottom - shownBack.top).value, 0.5f)
        assertTrue("the way back on the screen", whole.bottom <= screen.bottom + 0.5.dp)
    }

    // Criterion 3: at a font scale of 2 on 360 dp the title keeps whole words — a hyphenated word breaking only after
    // its hyphen — drawn no smaller than at a font scale of 1.
    @Test
    @Config(qualifiers = "ru-w360dp-h780dp", fontScale = 2f)
    fun atTwiceTheFontOn360dpTheTitleKeepsWholeWords() {
        listOf(
            "Картофельно-грибная запеканка по-деревенски со сметаной",
            "Курица с картофелем, лимоном и розмарином, медленно запечённая в духовке",
        ).forEach { title ->
            show(save(recipe(title)).id)
            assertEveryWordShows(title)
        }
    }

    /** Every word of [title] is laid out whole on one line: nothing cut short, nothing past its room. */
    private fun assertEveryWordShows(title: String) {
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.TITLE) == listOf(title) }
        compose.waitForIdle()
        val layout = layout(CardTags.TITLE)
        val laid = layout.layoutInput.text.text
        assertEquals(listOf(title), compose.textsOf(CardTags.TITLE))
        assertEquals("the title, a break place after its hyphens aside", title, laid.replace("​", ""))
        val last = layout.lineCount - 1
        assertTrue("$title wraps at this size", last > 0)
        assertTrue("$title: no line cut short", (0..last).none { layout.isLineEllipsized(it) })
        assertEquals("$title: the last line ends the title", laid.length, layout.getLineEnd(last))
        assertFalse("$title: nothing past its room", layout.hasVisualOverflow)
        assertTrue("$title: no smaller than 28 sp at a font scale of 1", layout.layoutInput.style.fontSize.value * 2f >= 28f - 0.01f)
        val words = title.split(' ').flatMap { word -> Regex("""[^-]*-+|[^-]+""").findAll(word).map { it.value }.toList() }
        var from = 0
        words.forEach { word ->
            val start = laid.indexOf(word, from)
            assertTrue("«$word» is laid out", start >= 0)
            assertEquals("«$word» stays on one line", layout.getLineForOffset(start), layout.getLineForOffset(start + word.length - 1))
            from = start + word.length
        }
    }

    // Criterion 4: an imported recipe's stated author stands with the title inside the heading block — under the title,
    // above the summary, in Literata italics and muted — indented with the title beside the provenance column, and read
    // after the title and before the provenance line.
    @Test
    fun theStatedAuthorStandsWithTheTitleBesideTheColumnAndIsReadBeforeTheLine() {
        val parent = save(recipe("Shakshuka"))
        show(save(parent.fork(now, owner = null).copy(title = "Shakshuka with feta", statedAuthor = "anna")).id)
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.PROVENANCE) }
        compose.waitForIdle()
        assertEquals(listOf("Author: anna (stated in the file, not verified)"), compose.textsOf(CardTags.STATED_AUTHOR))
        val title = bounds(CardTags.TITLE)
        val author = bounds(CardTags.STATED_AUTHOR)
        val summary = bounds(CardTags.SUMMARY)
        assertTrue("under the title", author.top >= title.bottom - 0.5f)
        assertTrue("above the summary", author.bottom <= summary.top + 0.5f)
        assertEquals("indented with the title", title.left, author.left, 0.5f)
        assertTrue("beside the provenance column", author.left >= bounds(ProvenanceTags.COLUMN).right - 0.5f)
        val style = layout(CardTags.STATED_AUTHOR).layoutInput.style
        assertEquals(ReadingFamily, style.fontFamily)
        assertEquals(FontStyle.Italic, style.fontStyle)

        // The order the screen reader takes: within the group the line and the heading make, the author is in the
        // heading's part, which is read first, and in it after the title.
        fun ancestors(node: SemanticsNode) = generateSequence(node) { it.parent }.toList()
        val titlePath = ancestors(unmerged(CardTags.TITLE))
        val authorPath = ancestors(unmerged(CardTags.STATED_AUTHOR))
        val linePath = ancestors(unmerged(CardTags.PROVENANCE))
        val group = titlePath.first { candidate -> linePath.any { it.id == candidate.id } }
        assertEquals(true, group.config.getOrNull(SemanticsProperties.IsTraversalGroup))
        fun partUnder(path: List<SemanticsNode>) = path[path.indexOfFirst { it.id == group.id } - 1]
        assertEquals("the author in the title's part", partUnder(titlePath).id, partUnder(authorPath).id)
        fun index(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f
        assertTrue("the title's part before the line", index(partUnder(titlePath)) < index(partUnder(linePath)))
    }

    // Criterion 4, without a parent: the author stands at the gutter with the title, between it and the summary.
    @Test
    fun withoutAParentTheStatedAuthorStandsUnderTheTitleAtTheGutter() {
        show(save(recipe("Plov").copy(statedAuthor = "Zira")).id)
        val title = bounds(CardTags.TITLE)
        val author = bounds(CardTags.STATED_AUTHOR)
        assertEquals(title.left, author.left, 0.5f)
        assertEquals(6f, dp(author.top - title.bottom), 0.5f)
        assertTrue(author.bottom <= bounds(CardTags.SUMMARY).top + 0.5f)
    }

    // Criterion 5 (fix round 1): a version is shown only once its parent is read too — until then the card's loading
    // state — so that the provenance line and the title come together. A held parent with a long title wraps to several
    // lines at twice the font; the title stands where it first stood as the card shows, and does not move after.
    @Test
    @Config(qualifiers = "ru-w360dp-h780dp", fontScale = 2f)
    fun aHeldParentWithALongTitleAtTwiceTheFontOn360dpMovesNothing() = heldParentWithALongTitle()

    @Test
    @Config(qualifiers = "ru-w320dp-h711dp", fontScale = 2f)
    fun aHeldParentWithALongTitleAtTwiceTheFontOn320dpMovesNothing() = heldParentWithALongTitle()

    @Test
    @Config(qualifiers = "w360dp-h780dp", fontScale = 2f)
    fun aHeldParentWithALongTitleAtTwiceTheFontMovesNothingInEnglish() = heldParentWithALongTitle()

    private fun heldParentWithALongTitle() {
        val parent = save(recipe("Шакшука по-тбилисски с печёными перцами"))
        val version = save(parent.fork(now, owner = null).copy(title = "Моя шакшука с фетой"))
        assertNothingShownUntilTheParentArrives(version, parent.id)
        assertTrue("the held line wraps at this size", compose.textLayoutsWithin(CardTags.PROVENANCE).first().lineCount > 1)
    }

    // Criterion 5, a parent not in the book, with the author its file states: taller than the least place at twice the
    // font, and still nothing moves.
    @Test
    @Config(qualifiers = "ru-w360dp-h780dp", fontScale = 2f)
    fun aParentNotInTheBookAtTwiceTheFontMovesNothing() {
        val parent = RecipeId("gone")
        val version = save(
            recipe("Борщ с фасолью").copy(parent = parent, statedParent = StatedParent("Мой борщ", "Марина из соседнего подъезда")),
        )
        assertNothingShownUntilTheParentArrives(version, parent)
        assertTrue("the line is taller than the least place here", dp(bounds(CardTags.PROVENANCE).height) > CardTarget.value + 0.5f)
    }

    // Criterion 5, the rest: a parent whose read fails does not fail the card — the version shows, its line saying the
    // parent is not in the book, drawn dashed and opening nothing.
    @Test
    fun aParentThatCannotBeReadShowsTheLineAsNotInTheBook() {
        val parent = save(recipe("Shakshuka"))
        broken = parent.id
        show(save(parent.fork(now, owner = null).copy(title = "My shakshuka")).id)
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.PROVENANCE) }
        assertEquals(listOf("My shakshuka"), compose.textsOf(CardTags.TITLE))
        assertEquals(listOf("after a recipe not in this book"), compose.textsOf(CardTags.PROVENANCE))
        assertEquals(null, unmerged(CardTags.PROVENANCE).config.getOrNull(SemanticsActions.OnClick))
        compose.waitUntil(PATIENCE_MS) { unmerged(ProvenanceTags.COLUMN).config.getOrNull(ProvenanceDrawn) != null }
        assertTrue(unmerged(ProvenanceTags.COLUMN).config[ProvenanceDrawn].dashed)
        assertFalse(exists(CardTags.UNREADABLE))
    }

    // Criterion 5, live: the parent renamed, then removed, while the card is open — the line follows.
    @Test
    fun theLineFollowsTheParentWhileTheCardIsOpen() {
        val parent = save(recipe("Shakshuka"))
        show(save(parent.fork(now, owner = null).copy(title = "My shakshuka")).id)
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.PROVENANCE) == listOf("after “Shakshuka”") }
        save(parent.copy(title = "Green shakshuka"))
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.PROVENANCE) == listOf("after “Green shakshuka”") }
        runBlocking { assertTrue(recipes.delete(parent.id)) }
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.PROVENANCE) == listOf("after a recipe not in this book") }
        assertEquals(listOf("My shakshuka"), compose.textsOf(CardTags.TITLE))
    }

    /**
     * While the parent of [version] is held back, the card shows its loading state and no title; once the parent
     * arrives, the title and the line come together, and the title stays where it first stood.
     */
    private fun assertNothingShownUntilTheParentArrives(version: Recipe, parent: RecipeId) {
        val held = CompletableDeferred<Unit>()
        gate = parent to held
        setCard(version.id)
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.LOADING) }
        assertFalse("no title while the parent is asked for", exists(CardTags.TITLE))
        assertFalse("no content while the parent is asked for", exists(CardTags.CONTENT))
        held.complete(Unit)
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.TITLE) }
        assertTrue("the line comes with the title", exists(CardTags.PROVENANCE))
        val first = bounds(CardTags.TITLE).top
        compose.waitUntil(PATIENCE_MS) { unmerged(ProvenanceTags.COLUMN).config.getOrNull(ProvenanceDrawn) != null }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse(exists(CardTags.LOADING))
        assertEquals("the title stands where it first stood", first, bounds(CardTags.TITLE).top, 0.5f)
    }
}
