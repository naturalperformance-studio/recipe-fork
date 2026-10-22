package app.recipefork.app.collections

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.app.book.CollectionRow
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppColors
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.appColorScheme
import app.recipefork.app.ui.appColors
import app.recipefork.app.ui.WorkingFamily
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs

/**
 * The book's collections as narrow spines (v0.42, T-303): a sideways lazy row above the recipes, each spine at least
 * 48 dp high with a 3 dp stripe at its start, its name in Golos (titleSmall) on one line — a long one ending with an
 * ellipsis — and its count under it; tapping one opens the collection; at twice the font scale a spine grows and
 * nothing in it is cut. With the spines, at least four recipe rows still fit the first screen of a 360 × 780 dp phone.
 * A spine stands on the theme's raised paper, lighter than the page, in the light theme and the dark one (T-308).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h780dp")
class CollectionSpinesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private var ticks = 0L
    private val collections = CollectionRepository(active) { now.plusSeconds(ticks++) }

    private val soup = TagEntity(10, "суп", "Soup")
    private val quick = TagEntity(11, "быстро", "Quick")
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = listOf(soup, quick)
        },
    ) { now }

    private lateinit var type: Typography

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun own(title: String, vararg tags: TagEntity, minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null)
            .copy(title = title, tags = tags.mapTo(HashSet()) { it.id })
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun collection(name: String, vararg members: Recipe): CollectionId = runBlocking {
        collections.create(name)!!.also { id -> members.forEach { collections.addTo(it.id, listOf(id)) } }
    }

    private lateinit var scheme: ColorScheme
    private lateinit var extra: AppColors

    private fun show(mode: Mode = Mode.Serverless, dark: Boolean = false) {
        session.choose(LibraryView.OWN)
        compose.setContent {
            AppTheme(dark = dark) {
                type = MaterialTheme.typography
                scheme = MaterialTheme.colorScheme
                extra = MaterialTheme.appColors
                AppNavHost(session, recipes, mode, editor = editor, collections = collections)
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun dp(value: Float) = with(compose.density) { value.dp.toPx() }

    private fun layoutOf(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    private fun spine(id: CollectionId) = CollectionScreenTags.card(id.value)

    /** The spine of [id] holds its stripe, name and count whole, the stripe running its height inside its padding. */
    private fun assertWholeInside(id: CollectionId) {
        val spine = bounds(spine(id))
        val stripe = bounds(CollectionScreenTags.spineStripe(id.value))
        val name = bounds(CollectionScreenTags.cardName(id.value))
        val count = bounds(CollectionScreenTags.cardCount(id.value))
        listOf("stripe" to stripe, "name" to name, "count" to count).forEach { (what, part) ->
            assertTrue("the $what ($part) is cut by the spine ($spine)", part.top >= spine.top - 0.5f && part.bottom <= spine.bottom + 0.5f)
            assertTrue("the $what ($part) spills sideways ($spine)", part.left >= spine.left - 0.5f && part.right <= spine.right + 0.5f)
        }
        assertTrue("the count under the name", count.top >= name.bottom - 0.5f)
        assertTrue("the stripe at the start", stripe.right <= name.left && stripe.left - spine.left <= dp(12f))
        assertEquals("the stripe 3 dp wide", dp(3f), stripe.width, 0.5f)
        assertEquals("the stripe runs the spine's height", spine.height - dp(12f), stripe.height, 1f)
        assertTrue("the spine at least 48 dp high (${spine.height})", spine.height >= dp(48f) - 0.5f)
        // Each line drawn whole within its text's height (a long name is ended by its ellipsis, not cut).
        listOf(CollectionScreenTags.cardName(id.value), CollectionScreenTags.cardCount(id.value)).forEach { tag ->
            val layout = layoutOf(tag)
            assertTrue(
                "$tag cut below: ${layout.getLineBottom(layout.lineCount - 1)} > ${layout.size}",
                layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 0.5f,
            )
            val drawn = bounds(tag).width
            (0 until layout.lineCount).forEach { line ->
                assertTrue("$tag cut at the side: ${layout.getLineRight(line)} > $drawn", layout.getLineRight(line) <= drawn + 0.5f)
            }
        }
    }

    @Test
    fun theCollectionsAreALazyRowOfSpinesAboveTheRecipes() {
        val bread = own("Bread")
        val breakfasts = collection("Breakfasts", bread, own("Eggs"), own("Porridge"))
        val quickOnes = collection("Quick on weekdays", bread)
        show()
        await(spine(breakfasts))
        await(spine(quickOnes))
        // A lazy row: it scrolls sideways to an item, which a plain scrolled row cannot.
        val row = compose.onNodeWithTag(CollectionScreenTags.ROW).fetchSemanticsNode().config
        assertNotNull("a lazy row", row.getOrNull(SemanticsActions.ScrollToIndex))
        // Side by side, the last made first, above the recipes.
        assertTrue(bounds(spine(quickOnes)).right <= bounds(spine(breakfasts)).left)
        assertEquals(bounds(spine(quickOnes)).top, bounds(spine(breakfasts)).top, 0.5f)
        await(LibraryTags.entry(bread.id.value))
        assertTrue(bounds(CollectionScreenTags.ROW).bottom <= bounds(LibraryTags.entry(bread.id.value)).top)

        assertWholeInside(breakfasts)
        val name = layoutOf(CollectionScreenTags.cardName(breakfasts.value))
        assertEquals("Breakfasts", name.layoutInput.text.text)
        assertEquals("the name in Golos", WorkingFamily, name.layoutInput.style.fontFamily)
        assertEquals("the name as titleSmall", type.titleSmall.fontSize, name.layoutInput.style.fontSize)
        assertEquals("the name as titleSmall", type.titleSmall.fontWeight, name.layoutInput.style.fontWeight)
        assertEquals("3 recipes", layoutOf(CollectionScreenTags.cardCount(breakfasts.value)).layoutInput.text.text)
        assertEquals("1 recipe", layoutOf(CollectionScreenTags.cardCount(quickOnes.value)).layoutInput.text.text)
        AccessibilityChecks.assertTouchTargets(compose)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, except = AccessibilityChecks.snackbarPane)
    }

    @Test
    fun tappingASpineOpensItsCollection() {
        val bread = own("Bread")
        val menu = collection("Menu", bread)
        show()
        await(spine(menu))
        val click = compose.onNodeWithTag(spine(menu)).fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertEquals("Open the collection", click.label)
        compose.onNodeWithTag(spine(menu)).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CollectionScreenTags.SCREEN) && exists(LibraryTags.entry(bread.id.value)) }
    }

    // The shelf keeps its sideways place: opening a collection from its far end and coming back shows that spine again.
    @Test
    fun theShelfKeepsItsPlaceWhenACollectionIsOpenedAndLeft() {
        val bread = own("Bread")
        val shelf = (0 until 8).map { collection("Collection number $it", bread) }
        show()
        await(LibraryTags.entry(bread.id.value))
        await(CollectionScreenTags.ROW)
        // The last made stands first: the oldest is at the far end.
        val far = shelf.first()
        compose.onNodeWithTag(CollectionScreenTags.ROW).performScrollToIndex(shelf.lastIndex)
        compose.waitUntil(PATIENCE_MS) { exists(spine(far)) }
        assertInSight(far)
        compose.onNodeWithTag(spine(far)).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CollectionScreenTags.SCREEN) }
        compose.onNodeWithTag(CollectionScreenTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CollectionScreenTags.SCREEN) && exists(CollectionScreenTags.ROW) }
        compose.waitForIdle()
        assertTrue("the far spine is still there", exists(spine(far)))
        assertInSight(far)
        assertFalse("the shelf is not back at its start", exists(spine(shelf.last())))
    }

    private fun assertInSight(id: CollectionId) {
        val row = bounds(CollectionScreenTags.ROW)
        val shown = bounds(spine(id))
        assertTrue("$shown in sight within $row", shown.width > 0f && shown.left >= row.left - 0.5f && shown.right <= row.right + 0.5f)
    }

    // The shelf shows no heading, but the screen reader can move to it by its heading.
    @Test
    @Config(qualifiers = "ru-w360dp-h780dp")
    fun theShelfIsAHeadingForTheScreenReaderOnly() {
        val menu = collection("Меню", own("Суп"))
        show()
        await(spine(menu))
        val row = compose.onNodeWithTag(CollectionScreenTags.ROW).fetchSemanticsNode().config
        assertTrue("a heading", SemanticsProperties.Heading in row)
        assertEquals(listOf("Коллекции"), row[SemanticsProperties.ContentDescription])
        assertTrue("no heading drawn", compose.onAllNodes(hasText("Коллекции"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    // Collections read after the recipes do not stand out of sight above the first recipe at the top of the book.
    @Test
    fun theFirstCollectionStandsInSightAboveTheRecipesAtTheTopOfTheBook() {
        val bread = own("Bread")
        show()
        await(LibraryTags.entry(bread.id.value))
        val list = bounds(LibraryTags.OWN_LIST)
        assertEquals("the book at its top", list.top + dp(12f), bounds(LibraryTags.entry(bread.id.value)).top, 1f)
        val menu = collection("Menu", bread)
        compose.waitUntil(PATIENCE_MS) { exists(spine(menu)) }
        compose.waitForIdle()
        val row = bounds(CollectionScreenTags.ROW)
        assertTrue("the spines in sight ($row in $list)", row.height > 0f && row.top >= list.top - 0.5f)
        assertTrue("above the recipe", row.bottom <= bounds(LibraryTags.entry(bread.id.value)).top)
    }

    private fun assertLongNameEllipsized(name: String) {
        val long = collection(name, own("Bread"))
        val short = collection("Salads")
        show()
        await(spine(long))
        val layout = layoutOf(CollectionScreenTags.cardName(long.value))
        assertEquals("$name on one line", 1, layout.lineCount)
        assertTrue("$name ends with an ellipsis", layout.isLineEllipsized(0))
        assertTrue("the spine stays narrow (${bounds(spine(long)).width})", bounds(spine(long)).width <= dp(240f) + 0.5f)
        assertWholeInside(long)
        // Spines are as high as each other whatever a name's length.
        await(spine(short))
        assertEquals(bounds(spine(short)).height, bounds(spine(long)).height, 0.5f)
    }

    @Test
    fun aLongNameEndsWithAnEllipsis() = assertLongNameEllipsized("A long New Year's Eve dinner menu for the family")

    @Test
    @Config(qualifiers = "ru-w360dp-h780dp")
    fun inRussianALongNameEndsWithAnEllipsis() = assertLongNameEllipsized("Новогодний ужин для всей большой семьи и гостей")

    // The redesign's own example stands whole on its spine.
    @Test
    @Config(qualifiers = "ru-w360dp-h780dp")
    fun inRussianTheRedesignsNameStandsWhole() {
        val id = collection("Завтраки с Васильевского", own("Сырники"), own("Шакшука"), own("Каша"))
        show()
        await(spine(id))
        val layout = layoutOf(CollectionScreenTags.cardName(id.value))
        assertFalse("the name is cut", layout.isLineEllipsized(0))
        assertEquals("3 рецепта", layoutOf(CollectionScreenTags.cardCount(id.value)).layoutInput.text.text)
        assertWholeInside(id)
    }

    private fun assertGrowsAtTwiceTheFontScale() {
        val id = collection("A long New Year's Eve dinner menu for the family", own("Bread"), own("Soup"))
        show()
        await(spine(id))
        val spine = bounds(spine(id))
        val name = bounds(CollectionScreenTags.cardName(id.value))
        val count = bounds(CollectionScreenTags.cardCount(id.value))
        assertTrue("the spine grows past 48 dp (${spine.height})", spine.height > dp(48f) + 0.5f)
        assertTrue("the spine holds both lines", spine.height >= name.height + count.height + dp(12f) - 1f)
        assertTrue(layoutOf(CollectionScreenTags.cardName(id.value)).isLineEllipsized(0))
        assertWholeInside(id)
        AccessibilityChecks.assertTouchTargets(compose)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleASpineGrowsAndNothingIsCut() = assertGrowsAtTwiceTheFontScale()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleASpineGrowsAndNothingIsCut() = assertGrowsAtTwiceTheFontScale()

    // --- Raised paper (T-308) -------------------------------------------------------------------------------------

    private fun pixelAt(x: Float, y: Float): Color =
        Color(compose.onRoot().captureToImage().asAndroidBitmap().getPixel(x.toInt(), y.toInt()))

    private fun assertColour(message: String, expected: Color, shown: Color) {
        val close = listOf(shown.red - expected.red, shown.green - expected.green, shown.blue - expected.blue).all { abs(it) < 0.02f }
        assertTrue("$message: $shown, not $expected", close)
    }

    /** Where a spine's own paper shows: inside its end padding, clear of its border, its words and its corners. */
    private fun paperOf(id: CollectionId): Color = bounds(spine(id)).let { pixelAt(it.right - dp(6f), it.center.y) }

    private fun assertOnRaisedPaper(dark: Boolean) {
        val menu = collection("Menu", own("Bread"))
        show(dark = dark)
        await(spine(menu))
        compose.waitForIdle()
        val file = appColors(context.resources, dark).raisedPaper
        assertEquals("the theme's raised paper is the file's", file, extra.raisedPaper)
        val paper = paperOf(menu)
        assertColour("the spine on raised paper", file, paper)
        // The page beside the first spine, in the shelf's start padding.
        val page = bounds(spine(menu)).let { pixelAt(bounds(CollectionScreenTags.ROW).left + dp(8f), it.center.y) }
        assertColour("the page beside the spine", appColorScheme(context.resources, dark).surface, page)
        assertTrue("the spine ($paper) lighter than the page ($page)", paper.luminance() > page.luminance())
        assertColour("the spine's name in ink", scheme.onSurface, layoutOf(CollectionScreenTags.cardName(menu.value)).layoutInput.style.color)
    }

    @Test
    fun inTheLightThemeASpineStandsOnRaisedPaperLighterThanThePage() = assertOnRaisedPaper(dark = false)

    @Test
    fun inTheDarkThemeASpineStandsOnRaisedPaperLighterThanThePage() = assertOnRaisedPaper(dark = true)

    // The spine reads its paper through the theme: a raised paper changed in the file's resources is what it draws.
    @Test
    fun aChangedRaisedPaperInTheFileReachesTheSpine() {
        val changed = Color(0xFF00796B)
        val base = compose.activity
        @Suppress("DEPRECATION")
        val edited = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
            override fun getColor(id: Int, theme: Theme?): Int =
                if (id == R.color.theme_dark_raisedPaper) changed.toArgb() else super.getColor(id, theme)
        }
        val wrapped = object : ContextWrapper(base) {
            override fun getResources(): Resources = edited
        }
        val row = CollectionRow("menu", "Menu", CollectionOrigin.OWN, null, now, 2)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides wrapped) {
                AppTheme(dark = true) { CollectionsRow(listOf(row), onOpen = {}) }
            }
        }
        compose.waitForIdle()
        assertColour("the spine on the changed paper", changed, bounds(CollectionScreenTags.card("menu")).let { pixelAt(it.right - dp(6f), it.center.y) })
    }

    // --- The first screen ------------------------------------------------------------------------------------------

    /** How many of [all] stand whole on the first screen of the own recipes' list. */
    private fun wholeOnTheFirstScreen(all: List<Recipe>): Int {
        val shown = bounds(LibraryTags.OWN_LIST)
        val window = compose.onRoot().fetchSemanticsNode().size.height
        return all.count { recipe ->
            val tag = LibraryTags.entry(recipe.id.value)
            exists(tag) && bounds(tag).let { it.top >= shown.top - 0.5f && it.bottom <= minOf(shown.bottom, window.toFloat()) + 0.5f }
        }
    }

    private fun assertFourFitBelowTheSpines() {
        val all = (0 until 8).map { own("Soup number $it", soup, quick, minutesAgo = it.toLong()) }
        val shelf = listOf(
            collection("Breakfasts", all[0], all[1], all[2]),
            collection("Quick on weekdays", all[3]),
            collection("For a holiday", all[4], all[5]),
        )
        show(Mode.Serverless)
        await(LibraryTags.entryTags(all.first().id.value))
        shelf.forEach { await(spine(it)) }
        // Below the title, the tabs, the search, the filter row and the spines.
        val first = bounds(LibraryTags.entry(all.first().id.value))
        listOf(LibraryTags.TITLE, LibraryTags.tab(LibraryView.OWN), LibraryTags.SEARCH, LibraryTags.FILTERS, CollectionScreenTags.ROW).forEach {
            assertTrue("$it shows", exists(it))
            assertTrue("$it above the rows", bounds(it).bottom <= first.top)
        }
        val whole = wholeOnTheFirstScreen(all)
        assertTrue("at least four rows fit the first screen below the spines ($whole)", whole >= 4)
    }

    @Test
    fun withSpinesAtLeastFourRowsFitTheFirstScreenOfA360By780Phone() = assertFourFitBelowTheSpines()

    @Test
    @Config(qualifiers = "ru-w360dp-h780dp")
    fun inRussianWithSpinesAtLeastFourRowsFitTheFirstScreenOfA360By780Phone() = assertFourFitBelowTheSpines()
}
