package app.recipefork.app.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.IngredientTags
import app.recipefork.app.R
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import app.recipefork.app.card.CardMode
import app.recipefork.app.card.CardTarget
import app.recipefork.app.card.DoneMark
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.card.EquipmentTags
import app.recipefork.app.card.FakeClipPlayers
import app.recipefork.app.card.LocalClipPlayers
import app.recipefork.app.card.awaitCard
import app.recipefork.app.data.HeldThemeSetting
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.closeIngredient
import app.recipefork.app.editor.openIngredient
import app.recipefork.app.file.FileMessage
import app.recipefork.app.file.FileMessageTags
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.kitchen.HeldKitchen
import app.recipefork.app.kitchen.KitchenEnvironment
import app.recipefork.app.kitchen.KitchenTags
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.library.chooseEntryAction
import app.recipefork.app.library.closeEntryMenu
import app.recipefork.app.library.noticeShown
import app.recipefork.app.library.openEntryMenu
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.crop.CropScreen
import app.recipefork.app.media.crop.CropTags
import app.recipefork.app.media.crop.PhotoToCrop
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.LanguageTags
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerTags
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Scenario 5 of v0.25, the pre-launch test pass: every main screen of the build Play receives (serverless), in each
 * state a stranger meets it in, passes what Play's pre-launch report checks — in English and Russian, in the light and
 * the dark theme, at the usual font scale and at twice it:
 *  - every interactive element is at least 48 by 48 dp, and 56 by 56 dp on the cooking card (A11Y-003, A11Y-007);
 *  - every interactive element announces what it does (A11Y-005 (1));
 *  - no two interactive elements in one window announce the same thing unless each acts on a different named thing;
 *  - every text drawn reaches its contrast against what is drawn behind it (A11Y-002).
 *
 * The screens: the book — the marked and the own view, each empty and with recipes, the own one filtered by "Enough
 * equipment", an entry's menu, a recipe file's
 * notice and failure, and the notice of a deletion with its Undo; the card of a recipe with equipment — an item of it not
 * in My kitchen, so marked on both tabs — ingredient groups, notes,
 * a cover, a step's photo and clip, several timers on a step and one running, and done marks, on its View and its
 * Cooking tab — its current step highlighted — each with its menu open, and on Cooking with every item of equipment and
 * every ingredient marked, both lists folded; the editor on that recipe, its equipment among it, with an ingredient's
 * sheet open, with a row removed and its Undo offered, and on a frozen one with equipment; the settings and each of their
 * dialogs, My kitchen — its groups and, a search finding nothing, its empty result; and the crop.
 *
 * Each theme is a walk of its own, the application started in it as a theme chosen starts it. The screen is tall, so
 * that the lists show every entry and the card and the editor are drawn whole for the contrast check, which reads
 * pixels. The walk goes on past a screen that fails, so that one run names every failure.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreLaunchSweepTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val photos = PhotoStore(context.filesDir)
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
    private val marks = DoneMarks.inMemory()
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private val timers = TestTimers(context)
    private val cardTimers = CardTimers(timers.store, timers.scope, timers.access, timers.ringing) { BookName.Pool }
    private val players = FakeClipPlayers()
    private val editor = EditorEnvironment(active, device.catalog(context)) { now }
    private val theme = HeldThemeSetting()

    /** My kitchen over the shipped equipment dictionary: a frying pan and an oven ticked, so every group's state shows. */
    private val kitchen = KitchenEnvironment(
        HeldKitchen(setOf(FRYING_PAN, OVEN)),
        EquipmentSource { DictionarySnapshot.read(context).equipment.map(EquipmentEntryEntity::of) },
    )

    /** The card reads the device's dictionaries and the same kitchen: the cake's bowl is not in it. */
    private val card = CardEnvironment(
        HeldUsualServings(), marks, equipment = EquipmentSource { device.database.dictionaryDao().equipment() }, kitchen = kitchen.store,
    ) { device.database.dictionaryDao().ingredients() }

    private var crop by mutableStateOf<PhotoToCrop?>(null)

    /** Every failure met on the walk, named by its screen. */
    private val failures = mutableListOf<String>()

    /** How many screen states were checked. */
    private var checked = 0

    @Before
    fun setUp() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::registerIdlingResource)
        // The sample dictionaries with the shipped equipment, so that the card and the editor name the cake's items.
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES.copy(equipment = DictionarySnapshot.read(context).equipment)) }
    }

    @After
    fun close() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        sessions.clear()
        book.close()
        device.close()
        timers.close()
        photos.directory(BookName.Pool).deleteRecursively()
    }

    @Test
    @Config(qualifiers = "en-w360dp-h2000dp")
    fun inEnglishLight() = sweep(dark = false)

    @Test
    @Config(qualifiers = "en-w360dp-h2000dp")
    fun inEnglishDark() = sweep(dark = true)

    @Test
    @Config(qualifiers = "en-w360dp-h2000dp", fontScale = 2f)
    fun inEnglishLightAtTwiceTheFontScale() = sweep(dark = false)

    @Test
    @Config(qualifiers = "en-w360dp-h2000dp", fontScale = 2f)
    fun inEnglishDarkAtTwiceTheFontScale() = sweep(dark = true)

    @Test
    @Config(qualifiers = "ru-w360dp-h2000dp")
    fun inRussianLight() = sweep(dark = false)

    @Test
    @Config(qualifiers = "ru-w360dp-h2000dp")
    fun inRussianDark() = sweep(dark = true)

    @Test
    @Config(qualifiers = "ru-w360dp-h2000dp", fontScale = 2f)
    fun inRussianLightAtTwiceTheFontScale() = sweep(dark = false)

    @Test
    @Config(qualifiers = "ru-w360dp-h2000dp", fontScale = 2f)
    fun inRussianDarkAtTwiceTheFontScale() = sweep(dark = true)

    // The recipes the walk meets.

    /** A photograph held under [mediaId], as the book's media lives on the device. */
    private fun photo(mediaId: String, attachment: MediaAttachment): Media {
        TestPhotos.jpeg(photos.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, 800, 600)
        return Media(mediaId, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
    }

    /** A clip held under [mediaId]; the card's player is a fake that plays it. */
    private fun clip(mediaId: String, step: Int): Media {
        photos.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(0, 0, 0, 24))
        return Media(mediaId, null, MediaKind.VIDEO, MediaAttachment.ToStep(step), MediaVisibility.PRIVATE)
    }

    private fun grams(position: Int, name: String, grams: Int, note: String? = null) =
        Ingredient(position, null, null, name, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(grams.toLong()), null, null, note = note)

    /**
     * Everything a card and an editor show: equipment, groups, notes, a cover, a step's photo and clip, several timers,
     * tags.
     */
    private val plumCake = Recipe.create(now, owner = null).copy(
        title = "Plum cake",
        description = "A sheet cake for late summer.",
        servings = 8,
        tags = setOf(10L, 12L),
        // The oven the kitchen has, free text, and a bowl the kitchen lacks: marked "Not in your kitchen" on the card.
        equipment = listOf(EquipmentItem.entry(OVEN), EquipmentItem.free("Baking tray 30 × 40 cm"), EquipmentItem.entry(BOWL, "large")),
        ingredients = listOf(
            grams(0, "flour", 250),
            grams(1, "butter", 125, note = "cold"),
            grams(2, "plums", 800, note = "halved and stoned"),
            grams(3, "sugar", 60),
            Ingredient(4, null, null, "cinnamon", UnitType.TASTE_UNMEASURED, null, null, null, null),
        ),
        ingredientGroups = listOf(IngredientGroup("Dough", 0), IngredientGroup("Topping", 2)),
        steps = listOf(
            Step(0, "Rub the butter into the flour.", listOf(IngredientReference(1, 8), IngredientReference(0, 24))),
            Step(1, "Chill the dough.", timer = Timer(30.minutes, "Chill")),
            Step(2, "Lay the plums over the dough.", listOf(IngredientReference(2, 8))),
            Step(3, "Sprinkle with sugar and cinnamon, then bake and let it rest.", emptyList(), listOf(Timer(40.minutes, "Bake"), Timer(10.minutes, "Rest"))),
            Step(4, "Cut into squares."),
        ),
    )

    private val soup = Recipe.create(now.minusSeconds(3600), owner = null).copy(
        title = "Tomato soup",
        servings = 2,
        ingredients = listOf(grams(0, "tomatoes", 600)),
        steps = listOf(Step(0, "Simmer the tomatoes.", timer = Timer(20.minutes))),
    )

    /** An own recipe that cannot be changed, with its equipment: the editor shows it frozen. */
    private val published = Recipe.create(now.minusSeconds(7200), owner = null)
        .copy(title = "Apple pie", servings = 6, equipment = listOf("Oven", "Pie dish 24 cm").map(EquipmentItem::free))
        .inState(RecipeState.PUBLISHED)

    private fun found(title: String, minutesAgo: Long): Recipe =
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(
            title = title,
            servings = 4,
            ingredients = listOf(grams(0, "beets", 500)),
            steps = listOf(Step(0, "Boil the beets.", timer = Timer(40.minutes))),
        ).inState(RecipeImport.FOUND_STATE).also { assertTrue(runBlocking { recipes.addFound(it, now.minusSeconds(minutesAgo * 60)) }) }

    // The checks.

    private fun exists(tag: String, unmerged: Boolean = false) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun awaitGone(tag: String) = compose.waitUntil(PATIENCE_MS) { !exists(tag) }

    /** Every check on the screen as it stands, named [screen]; a failure is kept and the walk goes on. */
    private fun checkScreen(screen: String, target: Dp = 48.dp) {
        compose.waitForIdle()
        fun run(check: () -> Unit) = try {
            check()
        } catch (e: AssertionError) {
            failures += "$screen — ${e.message}"
            println("FAILED $screen — ${e.message}")
        }
        run { AccessibilityChecks.assertTouchTargets(compose, min = target) }
        run { AccessibilityChecks.assertInteractiveElementsLabelled(compose, except = AccessibilityChecks.snackbarPane) }
        run { AccessibilityChecks.assertDistinctLabels(compose) }
        run { TextContrastChecks.assertTextContrast(compose) }
        checked++
    }

    // The walk.

    /**
     * The walk through every screen, in the theme [dark] or light as the reader chose it — the application started in
     * it, as a chosen theme starts it.
     */
    private fun sweep(dark: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers, LocalClipPlayers provides players) {
                AppTheme(dark = dark) {
                    val photo = crop
                    if (photo != null) {
                        CropScreen(photo, onDone = {}, onRetake = {}, onCancel = {}, onUnreadable = {})
                    } else {
                        // As MainActivity shows it: on a surface of the theme.
                        Surface(Modifier.fillMaxSize()) {
                            AppNavHost(session, recipes, Mode.Serverless, editor = editor, card = card, themeSetting = theme, kitchen = kitchen)
                        }
                    }
                }
            }
        }
        theEmptyBook()
        val cake = theBookWithRecipes()
        theCard(cake)
        theEditor()
        theSettings()
        theNotices()
        theCrop()
        assertEquals("screens checked", SCREENS, checked)
        if (failures.isNotEmpty()) fail("${failures.size} failures over $checked screens:\n" + failures.joinToString("\n"))
    }

    private fun theEmptyBook() {
        compose.runOnIdle { session.choose(LibraryView.MARKED) }
        await(LibraryTags.MARKED_EMPTY)
        checkScreen("the book, marked, empty")
        compose.runOnIdle { session.choose(LibraryView.OWN) }
        await(LibraryTags.OWN_EMPTY)
        checkScreen("the book, own, empty")
    }

    private fun theBookWithRecipes(): Recipe {
        val cake = plumCake.copy(
            media = listOf(
                photo("cake-cover", MediaAttachment.Cover),
                photo("cake-plums", MediaAttachment.ToStep(2)),
                clip("cake-squares", 4),
            ),
        )
        runBlocking { listOf(cake, soup, published).forEach { recipes.save(it) } }
        found("Borscht", 30)
        found("Pelmeni", 60)
        await(LibraryTags.entry(cake.id.value))
        await(LibraryTags.entry(soup.id.value))
        checkScreen("the book, own, with recipes")
        // Filtered by "Enough equipment": its chip among the filters in force. The cake needs a bowl the kitchen lacks
        // and is hidden; the pie, whose equipment is free text, and the soup, which needs none, stay listed.
        compose.runOnIdle { session.filter(LibraryView.OWN, BookFilters(equipment = true)) }
        await(LibraryTags.EQUIPMENT_FILTER)
        compose.waitUntil(PATIENCE_MS) {
            !exists(LibraryTags.entry(cake.id.value)) && exists(LibraryTags.entry(soup.id.value)) &&
                exists(LibraryTags.entry(published.id.value))
        }
        checkScreen("the book, own, filtered by equipment")
        compose.runOnIdle { session.filter(LibraryView.OWN, BookFilters()) }
        awaitGone(LibraryTags.EQUIPMENT_FILTER)
        await(LibraryTags.entry(cake.id.value))
        compose.openEntryMenu(cake.id.value)
        checkScreen("the book, own, an entry's menu")
        compose.closeEntryMenu(cake.id.value)
        compose.runOnIdle { session.choose(LibraryView.MARKED) }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.MARKED_LIST) && !exists(LibraryTags.MARKED_EMPTY) }
        compose.waitForIdle()
        checkScreen("the book, marked, with recipes")
        return cake
    }

    private fun theCard(cake: Recipe) {
        marks.hold(
            BookName.Pool, cake.id,
            setOf(DoneMark.ofEquipment(0, cake.equipment[0]), DoneMark.of(cake.ingredients[0]), DoneMark.of(cake.steps[0])),
        )
        compose.runOnIdle { session.openCard(cake.id.value, LibraryView.OWN) }
        compose.awaitCard()
        await(CardTags.COVER)
        await(CardTags.stepPhoto(2))
        await(CardTags.stepVideo(4))
        // View, the tab a card first opens on: the cover, the summary, fork; compact lists, the done marks shown.
        compose.onNodeWithTag(CardTags.tab(CardMode.VIEW)).assertIsSelected()
        await(CardTags.SUMMARY)
        // The equipment above the ingredients, one item marked, the bowl not in the kitchen.
        cake.equipment.indices.forEach { await(CardTags.equipment(it)) }
        awaitLacking(listOf(2))
        assertOnlyThePagerScrollsSideways("View")
        checkScreen("the card, View", target = CardTarget)
        compose.onNodeWithTag(CardTags.MENU).performClick()
        await(CardTags.DELETE)
        checkScreen("the card, View, its menu", target = CardTarget)
        compose.onNodeWithTag(CardTags.MENU).performClick()
        awaitGone(CardTags.DELETE)
        // Cooking: one timer running; the step's two others ready; the second step, the first not marked, highlighted.
        chooseTheCardsTab(CardMode.COOKING)
        await(CardTags.stepPhoto(2))
        await(CardTags.stepVideo(4))
        compose.onNodeWithTag(TimerTags.start(1)).performScrollTo().performClick()
        await(TimerTags.cancel(1))
        cake.equipment.indices.forEach { await(CardTags.equipment(it)) }
        awaitLacking(listOf(2))
        compose.onNodeWithTag(CardTags.SCREEN).assertExists()
        assertTrue("the second step is the current one", compose.onNodeWithTag(CardTags.step(1)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.StateDescription) == context.getString(R.string.card_current_step))
        assertOnlyThePagerScrollsSideways("Cooking")
        checkScreen("the card, Cooking", target = CardTarget)
        compose.onNodeWithTag(CardTags.MENU).performClick()
        await(CardTags.CLEAR_MARKS)
        checkScreen("the card, Cooking, its menu", target = CardTarget)
        compose.onNodeWithTag(CardTags.MENU).performClick()
        awaitGone(CardTags.CLEAR_MARKS)
        // Every item of equipment and every ingredient marked: each list folds into its one line.
        (1 until cake.equipment.size).forEach { compose.onNodeWithTag(CardTags.equipment(it)).performScrollTo().performClick() }
        (1 until cake.ingredients.size).forEach { compose.onNodeWithTag(CardTags.ingredient(it)).performScrollTo().performClick() }
        await(EquipmentTags.GATHERED)
        await(IngredientTags.GATHERED)
        checkScreen("the card, Cooking, the equipment and the ingredients all gathered", target = CardTarget)
        chooseTheCardsTab(CardMode.VIEW)
        compose.onNodeWithTag(CardTags.EDIT).performClick()
        await(EditorTags.FORM)
        await(EditorTags.equipmentName(2))
        compose.waitForIdle()
        checkScreen("the editor, a recipe with equipment, groups, notes, several timers and tags")
        // An ingredient's sheet open, its fields over the editor.
        compose.openIngredient(1)
        checkScreen("the editor, an ingredient's sheet open")
        compose.closeIngredient()
        // A row removed: the editor says so with Undo.
        compose.onNodeWithTag(EditorTags.ingredientRemove(4)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.STATEMENT, unmerged = true) }
        checkScreen("the editor, a row removed, with Undo")
        val undo = hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        compose.onNode(undo, useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredient(4)) && !exists(EditorTags.STATEMENT, unmerged = true) }
    }

    /** Waits until exactly the card's items at [indices] say "Not in your kitchen", on the tab in sight. */
    private fun awaitLacking(indices: List<Int>) = compose.waitUntil(PATIENCE_MS) {
        compose.onAllNodes(hasTestTag(CardTags.CONTENT)).fetchSemanticsNodes().size == 1 &&
            (0 until 3).filter { exists(EquipmentTags.lacking(it), unmerged = true) } == indices
    }

    /** Turns the card to its tab [mode] and waits until it has come to rest there. */
    private fun chooseTheCardsTab(mode: CardMode) {
        compose.onNodeWithTag(CardTags.tab(mode)).performClick()
        compose.waitUntil(PATIENCE_MS) {
            compose.onNodeWithTag(CardTags.tab(mode)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true &&
                compose.onAllNodes(hasTestTag(CardTags.CONTENT)).fetchSemanticsNodes().size == 1
        }
        compose.waitForIdle()
    }

    /** Nothing on the card's [tab] scrolls sideways but the pager of its tabs, so that a swipe always turns the tab. */
    private fun assertOnlyThePagerScrollsSideways(tab: String) {
        val sideways = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
        assertEquals("on the card's $tab only the pager scrolls sideways", listOf(CardTags.PAGER), sideways)
    }

    private fun theEditor() {
        compose.runOnIdle { session.openEditor(published.id.value) }
        await(EditorTags.FROZEN)
        await(EditorTags.equipment(1))
        compose.waitForIdle()
        checkScreen("the editor, a frozen recipe with equipment")
        compose.runOnIdle {
            session.closeEditor()
            session.closeCard()
        }
        await(LibraryTags.PAGER)
    }

    private fun theSettings() {
        compose.runOnIdle { session.openSettings() }
        await(SettingsTags.SCREEN)
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.value(SettingsTags.CLEAR_MEDIA), unmerged = true) }
        compose.waitForIdle()
        checkScreen("the settings")
        compose.onNodeWithTag(SettingsTags.LANGUAGE).performClick()
        await(LanguageTags.DIALOG)
        checkScreen("the settings, the language's dialog")
        compose.onNodeWithTag(LanguageTags.CANCEL).performClick()
        awaitGone(LanguageTags.DIALOG)
        compose.onNodeWithTag(SettingsTags.THEME).performClick()
        await(SettingsTags.THEME_DIALOG)
        checkScreen("the settings, the theme's dialog")
        compose.onNodeWithTag(SettingsTags.THEME_CANCEL).performClick()
        awaitGone(SettingsTags.THEME_DIALOG)
        compose.onNodeWithTag(SettingsTags.USUAL).performClick()
        await(SettingsTags.USUAL_DIALOG)
        checkScreen("the settings, the usual servings' dialog")
        compose.onNodeWithTag(SettingsTags.USUAL_SET).performClick()
        awaitGone(SettingsTags.USUAL_DIALOG)
        compose.waitForIdle()
        checkScreen("the settings, the usual servings set")
        // My kitchen: its groups, some partly ticked; a search that finds nothing.
        compose.onNodeWithTag(SettingsTags.KITCHEN).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) && !exists(SettingsTags.SCREEN) }
        compose.waitForIdle()
        checkScreen("my kitchen")
        compose.onNodeWithTag(KitchenTags.SEARCH).performTextInput("zzz")
        await(KitchenTags.EMPTY)
        checkScreen("my kitchen, nothing found")
        compose.onNodeWithTag(KitchenTags.BACK).performClick()
        await(SettingsTags.SCREEN)
        awaitGone(KitchenTags.SCREEN)
        compose.onNodeWithTag(SettingsTags.CLEAR_MEDIA).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.CLEAR_MEDIA_NOTHING) || exists(SettingsTags.CLEAR_MEDIA_STATEMENT) }
        checkScreen("the settings, the saved media's dialog")
        if (exists(SettingsTags.CLEAR_MEDIA_NOTHING)) {
            compose.onNodeWithTag(SettingsTags.CLEAR_MEDIA_OK).performClick()
            awaitGone(SettingsTags.CLEAR_MEDIA_NOTHING)
        } else {
            compose.onNodeWithTag(SettingsTags.CLEAR_MEDIA_DECLINE).performClick()
            awaitGone(SettingsTags.CLEAR_MEDIA_STATEMENT)
        }
        compose.runOnIdle { session.closeSettings() }
        await(LibraryTags.PAGER)
    }

    private fun theNotices() {
        // A recipe deleted from the own view: the book offers Undo while its notice shows.
        compose.runOnIdle { session.choose(LibraryView.OWN) }
        await(LibraryTags.entry(soup.id.value))
        compose.chooseEntryAction(soup.id.value, LibraryTags.delete(soup.id.value))
        compose.waitUntil(PATIENCE_MS) { compose.noticeShown() && !exists(LibraryTags.entry(soup.id.value)) }
        checkScreen("the book, a deletion's notice with Undo")
        val undo = hasAnyAncestor(hasTestTag(LibraryTags.NOTICE)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        compose.onNode(undo, useUnmergedTree = true).performClick()
        await(LibraryTags.entry(soup.id.value))

        // A recipe a file brought: the marked view lists it, and the snackbar offers to open it.
        val imported = found("Imported shchi", 1)
        compose.runOnIdle {
            session.tell(FileMessage(FileMessage.Kind.ADDED, imported.title, imported.id.value))
            session.showImported(imported.id)
        }
        compose.waitUntil(PATIENCE_MS) { compose.noticeShown() && exists(LibraryTags.entry(imported.id.value)) }
        checkScreen("the book, a file's recipe added")

        // A file that could not be imported: said whole in a dialog.
        compose.runOnIdle { session.tell(FileMessage(FileMessage.Kind.DAMAGED)) }
        await(FileMessageTags.DIALOG)
        checkScreen("the book, a file that could not be imported")
        compose.onNodeWithTag(FileMessageTags.OK).performClick()
        awaitGone(FileMessageTags.DIALOG)
    }

    private fun theCrop() {
        val shot = TestPhotos.jpeg(File(context.cacheDir, "sweep-shot.jpg"), 1200, 1600)
        compose.runOnIdle { crop = PhotoToCrop(open = { shot.inputStream() }, orientation = null, retake = true) }
        await(CropTags.FRAME)
        compose.waitForIdle()
        checkScreen("the crop")
        shot.delete()
    }

    private companion object {
        /** The screen states the walk checks. */
        const val SCREENS = 27

        /** Two items of the shipped equipment dictionary, the frying pan and the oven. */
        const val FRYING_PAN = 10001L
        const val OVEN = 10202L
        const val BOWL = 10412L
    }
}
