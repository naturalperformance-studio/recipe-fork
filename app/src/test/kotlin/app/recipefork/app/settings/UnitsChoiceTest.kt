package app.recipefork.app.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.cardEnvironmentOf
import app.recipefork.app.data.HeldUnitSystemSetting
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.editorEnvironmentOf
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.recipe.UnitSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * v0.35 T-248, scenario 1: the settings offer "Units" — Metric or US — beneath the theme; the one taken is shown at once
 * and is the setting the card and the editor read.
 */
@RunWith(AndroidJUnit4::class)
class UnitsChoiceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val units = HeldUnitSystemSetting(UnitSystem.METRIC)
    private val card = CardEnvironment(unitSystem = units, ingredients = IngredientEntries { emptyList() })

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun openSettings() {
        session.choose(LibraryView.OWN)
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, card = card) } }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SETTINGS) }
        compose.onNodeWithTag(LibraryTags.SETTINGS).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.UNITS) }
    }

    private fun selected(system: UnitSystem): Boolean =
        compose.onNodeWithTag(SettingsTags.units(system)).fetchSemanticsNode().config[SemanticsProperties.Selected]

    @Test
    fun unitsStandBeneathTheThemeAndTheOneTakenIsShownAtOnce() {
        openSettings()
        assertEquals(listOf("Units", "Metric"), compose.textsOf(SettingsTags.UNITS))
        val tops = listOf(SettingsTags.THEME, SettingsTags.UNITS, SettingsTags.USUAL)
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithTag(SettingsTags.UNITS).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.UNITS_DIALOG) }
        assertEquals(listOf("Metric"), compose.textsOf(SettingsTags.units(UnitSystem.METRIC)))
        assertEquals(listOf("US"), compose.textsOf(SettingsTags.units(UnitSystem.US)))
        assertEquals(listOf(true, false), UnitSystem.entries.map(::selected))
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 3)
        compose.onNodeWithTag(SettingsTags.units(UnitSystem.US)).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(SettingsTags.UNITS_DIALOG) }
        assertEquals("US", compose.textsOf(SettingsTags.value(SettingsTags.UNITS)).single())
        assertEquals(UnitSystem.US, units.system.value)
        // Cancel changes nothing.
        compose.onNodeWithTag(SettingsTags.UNITS).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.UNITS_DIALOG) }
        compose.onNodeWithTag(SettingsTags.UNITS_CANCEL).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(SettingsTags.UNITS_DIALOG) }
        assertEquals(UnitSystem.US, units.system.value)
    }

    @Test
    @Config(qualifiers = "ru")
    fun unitsReadInRussian() {
        openSettings()
        assertEquals(listOf("Единицы", "Метрическая"), compose.textsOf(SettingsTags.UNITS))
        units.set(UnitSystem.US)
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(SettingsTags.value(SettingsTags.UNITS)) == listOf("Американская") }
    }

    // T-249's review: the editor, the card and the settings read the one setting of the composition root.
    @Test
    fun theCardAndTheEditorTakeTheContainersSetting() {
        val container = ApplicationProvider.getApplicationContext<RecipeForkApplication>().container
        assertSame(container.unitSystem, editorEnvironmentOf(container).unitSystem)
        assertSame(container.unitSystem, cardEnvironmentOf(container).unitSystem)
    }
}
