package app.recipefork.app.library

import android.app.Activity
import android.app.LocaleManager
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.card.CardTags
import app.recipefork.app.data.AppLanguage
import app.recipefork.app.data.LanguageChoice
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Scenario 2 of the increment (L10N-002): the settings offer "Language" — English, Русский, "As on the device";
 * the choice switches the whole application at once, holds across a restart, and "As on the device" follows the
 * device again. Every screen then reads in the chosen language, its numbers (L10N-009) and its dictionary labels
 * (L10N-003) included, because they are read from the application's configuration, not the system's.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w411dp-h2000dp")
class InterfaceLanguageTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private var scenario: ActivityScenario<MainActivity>? = null

    /** A recipe written in Russian, whose ingredients and tag are dictionary references and one quantity is large. */
    private val recipe = Recipe.create(now, null).copy(
        title = "Борщ",
        language = RecipeLanguage.RU,
        servings = 4,
        tags = setOf(10L),
        ingredients = listOf(
            Ingredient(0, 5L, null, null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(1500)),
            Ingredient(1, 1L, null, null, UnitType.PIECE, QuantityUnit.PIECE, Rational.of(10_000_000), divisible = false),
        ),
        steps = listOf(Step(0, "Варить.")),
    )

    @After
    fun close() {
        scenario?.close()
        // The platform's per-app language outlives the test; the next one starts from the device's.
        app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    /** The application relaunched from nothing: the Activity is gone and is started again. */
    private fun restart() {
        scenario?.close()
        launch()
    }

    private fun platform(): LocaleManager = app.getSystemService(LocaleManager::class.java)

    private fun channelNames(): Set<String> =
        app.getSystemService(NotificationManager::class.java).notificationChannels.map { it.name.toString() }.toSet()

    private fun activity(): Activity {
        var current: Activity? = null
        scenario!!.onActivity { current = it }
        return current!!
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun awaitTitle(title: String) = compose.waitUntil(PATIENCE_MS) {
        exists(LibraryTags.TITLE) && compose.textsOf(LibraryTags.TITLE) == listOf(title)
    }

    /** Opens the settings, unless they are open already. */
    private fun openSettings() {
        if (exists(SettingsTags.SCREEN)) return
        await(LibraryTags.SETTINGS)
        compose.onNodeWithTag(LibraryTags.SETTINGS).performClick()
        await(SettingsTags.LANGUAGE)
    }

    private fun openLanguages() {
        openSettings()
        compose.onNodeWithTag(SettingsTags.LANGUAGE).performScrollTo().performClick()
        await(LanguageTags.DIALOG)
    }

    /** What the settings' language item shows as the choice in force. */
    private fun shownChoice(): String = compose.textsOf(SettingsTags.value(SettingsTags.LANGUAGE)).single()

    /** Chooses [choice] in the settings — the screen shows itself again in it — and goes back to the book. */
    private fun choose(choice: LanguageChoice) {
        openLanguages()
        compose.onNodeWithTag(LanguageTags.choice(choice)).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(LanguageTags.DIALOG) }
        leaveSettings()
    }

    /** Back from the settings, which a language taken shows again in place. */
    private fun leaveSettings() {
        await(SettingsTags.BACK)
        compose.onNodeWithTag(SettingsTags.BACK).performClick()
        await(LibraryTags.TITLE)
    }

    private fun selected(choice: LanguageChoice): Boolean =
        compose.onNodeWithTag(LanguageTags.choice(choice)).fetchSemanticsNode().config[SemanticsProperties.Selected]

    /**
     * Every text shown is written in [activity]'s language, but the reader's own [content]; and is that language's own
     * resource, but [content] and the [computed] texts — dictionary labels and quantities, checked by the caller.
     */
    private fun assertTheScreenReadsInItsLanguage(content: Set<String>, computed: Collection<String> = emptySet()) {
        LocalizationChecks.assertScreenTextFromResources(compose, activity(), content + computed)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, activity(), content)
    }

    // The settings offer the item, showing the choice in force; its dialog the three choices, each language named in
    // itself, the one in force marked.
    @Test
    fun theSettingsOfferTheThreeChoices() {
        launch()
        openSettings()
        assertEquals(listOf("Language", "As on the device"), compose.textsOf(SettingsTags.LANGUAGE))
        compose.onNodeWithTag(SettingsTags.LANGUAGE).performScrollTo().performClick()
        await(LanguageTags.DIALOG)
        assertEquals(
            listOf(listOf("English"), listOf("Русский"), listOf("As on the device")),
            LanguageChoice.entries.map { compose.textsOf(LanguageTags.choice(it)) },
        )
        LanguageChoice.entries.forEach { choice ->
            val node = compose.onNodeWithTag(LanguageTags.choice(choice)).fetchSemanticsNode()
            assertEquals(Role.RadioButton, node.config[SemanticsProperties.Role])
        }
        assertTrue("the device's is in force", selected(LanguageChoice.DEVICE))
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 4)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 4)
        // Dismissed, the dialog changes nothing.
        compose.onNodeWithTag(LanguageTags.CANCEL).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(LanguageTags.DIALOG) }
        assertEquals(LanguageChoice.DEVICE, AppLanguage.chosen(app))
    }

    // In Russian the item and the device's choice read in Russian; each language keeps its own name.
    @Test
    @Config(qualifiers = "ru-w411dp-h2000dp")
    fun theChoicesReadInRussian() {
        launch()
        openSettings()
        assertEquals(listOf("Язык", "Как на устройстве"), compose.textsOf(SettingsTags.LANGUAGE))
        compose.onNodeWithTag(SettingsTags.LANGUAGE).performScrollTo().performClick()
        await(LanguageTags.DIALOG)
        assertEquals(
            listOf(listOf("English"), listOf("Русский"), listOf("Как на устройстве")),
            LanguageChoice.entries.map { compose.textsOf(LanguageTags.choice(it)) },
        )
        assertTheScreenReadsInItsLanguage(setOf("English"))
    }

    // Scenario 2: Русский switches the book, a card and the editor at once — words, numbers and dictionary labels —
    // and holds after a restart; "As on the device" gives the device's English back.
    @Test
    fun theChoiceSwitchesTheWholeApplicationAtOnceAndHoldsAfterARestart() {
        runBlocking { app.container.recipes.save(recipe) }
        launch()
        awaitTitle("My recipe book")

        choose(LanguageChoice.RUSSIAN)
        awaitTitle("Моя книга рецептов")
        assertEquals(LanguageChoice.RUSSIAN, AppLanguage.chosen(app))
        // Kept where it outlives the process: the platform's per-app language.
        assertEquals(LocaleList.forLanguageTags("ru"), platform().applicationLocales)
        assertEquals("ru", activity().resources.configuration.locales[0].language)
        assertTheScreenReadsInItsLanguage(setOf("Борщ"))

        // A card: its words, its numbers grouped the Russian way and its dictionary labels in Russian.
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        await(LibraryTags.entry(recipe.id.value))
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).performClick()
        await(CardTags.ingredient(1))
        val cardTexts = (0..1).map { compose.textsOf(CardTags.ingredient(it)) }
        val card = cardTexts.map { it.joinToString(" ") }
        assertTrue(card.toString(), card[0].contains("говядина") && card[0].contains("1,5 кг"))
        assertTrue(card.toString(), card[1].contains("курица") && card[1].contains("10\u00A0000\u00A0000"))
        assertTheScreenReadsInItsLanguage(setOf("Борщ", "Варить.", "1."), cardTexts.flatten())

        // The editor: its words and its tag's label in Russian.
        compose.onNodeWithTag(CardTags.EDIT).performClick()
        await(EditorTags.FORM)
        await(EditorTags.chosen(10))
        compose.onNode(hasText("итальянская") and hasAnyAncestor(hasTestTag(EditorTags.chosen(10))), useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithTag(EditorTags.LANGUAGE).performScrollTo()
        // The editor offers every shipped tag, each by its Russian label.
        val shippedTags = DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText()).tags
        assertTheScreenReadsInItsLanguage(setOf("Борщ", "Варить."), shippedTags.map { it.labelRu })

        // After a restart the application opens in Russian again.
        restart()
        awaitTitle("Моя книга рецептов")
        assertEquals("ru", activity().resources.configuration.locales[0].language)

        // "As on the device" follows the device again: English here, at once and after a restart.
        choose(LanguageChoice.DEVICE)
        awaitTitle("My recipe book")
        assertEquals(LanguageChoice.DEVICE, AppLanguage.chosen(app))
        assertTrue(platform().applicationLocales.isEmpty)
        restart()
        awaitTitle("My recipe book")
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        await(LibraryTags.entry(recipe.id.value))
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).performClick()
        await(CardTags.ingredient(1))
        val englishTexts = (0..1).map { compose.textsOf(CardTags.ingredient(it)) }
        val english = englishTexts.map { it.joinToString(" ") }
        assertTrue(english.toString(), english[0].contains("beef") && english[0].contains("1.5 kg"))
        assertTrue(english.toString(), english[1].contains("chicken") && english[1].contains("10,000,000"))
        assertTheScreenReadsInItsLanguage(setOf("Борщ", "Варить.", "1."), englishTexts.flatten())
    }

    // On a Russian device: English overrides the device's language at once; "As on the device" gives Russian back.
    @Test
    @Config(qualifiers = "ru-w411dp-h2000dp")
    fun asOnTheDeviceFollowsTheDeviceAgain() {
        launch()
        awaitTitle("Моя книга рецептов")
        choose(LanguageChoice.ENGLISH)
        awaitTitle("My recipe book")
        assertEquals(LanguageChoice.ENGLISH, AppLanguage.chosen(app))
        assertTheScreenReadsInItsLanguage(emptySet())
        openSettings()
        assertEquals("English", shownChoice())
        openLanguages()
        assertTrue("the choice in force is marked", selected(LanguageChoice.ENGLISH))
        compose.onNodeWithTag(LanguageTags.choice(LanguageChoice.DEVICE)).performClick()
        // The settings show themselves again in the device's language, the choice in force with them.
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.LANGUAGE) && shownChoice() == "Как на устройстве" }
        leaveSettings()
        awaitTitle("Моя книга рецептов")
        assertEquals("ru", activity().resources.configuration.locales[0].language)
        restart()
        awaitTitle("Моя книга рецептов")
    }

    // A language set behind the application's back — in the system's per-app settings, which deliver a configuration
    // change and do not relaunch the Activity — is followed at once, over an earlier choice made in the application.
    @Test
    fun aChoiceMadeInTheSystemSettingsIsFollowedAtOnce() {
        launch()
        choose(LanguageChoice.RUSSIAN)
        awaitTitle("Моя книга рецептов")
        listOf(LocaleList.forLanguageTags("en") to "My recipe book", LocaleList.forLanguageTags("ru") to "Моя книга рецептов",
            LocaleList.getEmptyLocaleList() to "My recipe book").forEach { (locales, title) ->
            platform().applicationLocales = locales
            scenario!!.onActivity { it.onConfigurationChanged(Configuration(it.resources.configuration)) }
            awaitTitle(title)
        }
    }

    // The timers' channels are renamed in the language taken at once, so the system's settings show them in it.
    @Test
    fun theTimersChannelsFollowTheChoiceAtOnce() {
        launch()
        awaitTitle("My recipe book")
        assertEquals(setOf("Timer ended", "Running timers"), channelNames())
        choose(LanguageChoice.RUSSIAN)
        awaitTitle("Моя книга рецептов")
        assertEquals(setOf("Таймер закончился", "Идущие таймеры"), channelNames())
        choose(LanguageChoice.DEVICE)
        awaitTitle("My recipe book")
        assertEquals(setOf("Timer ended", "Running timers"), channelNames())
    }

    // Upgraded from Android 12, a choice the preferences file kept moves to the platform once; one the platform
    // already holds stands.
    @Test
    fun aChoiceKeptBeforeAndroid13MovesToThePlatformOnce() {
        val preferences = app.getSharedPreferences("reading", Context.MODE_PRIVATE)
        preferences.edit().putString("interface-language", "ru").commit()
        AppLanguage.moveIntoPlatform(app)
        assertEquals(LocaleList.forLanguageTags("ru"), platform().applicationLocales)
        assertFalse(preferences.contains("interface-language"))
        preferences.edit().putString("interface-language", "ru").commit()
        platform().applicationLocales = LocaleList.forLanguageTags("en")
        AppLanguage.moveIntoPlatform(app)
        assertEquals(LocaleList.forLanguageTags("en"), platform().applicationLocales)
        assertFalse(preferences.contains("interface-language"))
    }

    // The system's per-app language settings (Android 13 and later) list the same two languages the interface speaks.
    @Test
    fun theSystemListsTheSameTwoLanguages() {
        val manifest = parse(File("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals("@xml/locales_config", application.getAttribute("android:localeConfig"))
        val config = parse(File("src/main/res/xml/locales_config.xml")).getElementsByTagName("locale")
        val listed = (0 until config.length).map { (config.item(it) as Element).getAttribute("android:name") }.toSet()
        val spoken = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .filter { File(it, "strings.xml").exists() }
            .map { if (it.name == "values") "en" else it.name.removePrefix("values-") }
            .toSet()
        assertEquals(setOf("en", "ru"), spoken)
        assertEquals(spoken, listed)
    }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
}
