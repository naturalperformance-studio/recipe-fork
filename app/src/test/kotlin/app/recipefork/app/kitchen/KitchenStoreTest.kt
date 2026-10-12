package app.recipefork.app.kitchen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.data.AppDatabase
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.core.api.DictionariesDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T-204: the ticked set is kept on the device apart from the dictionaries' store — in a preferences file, as the other
 * device choices are — so that a restart, a dictionary refresh or a wipe of the store loses none of it; the composition
 * root exposes it, as it changes, to the card and the book.
 */
@RunWith(AndroidJUnit4::class)
class KitchenStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theTicksStayAcrossRestarts() {
        val store = PreferencesKitchen(context)
        store.hold(listOf(FRYING_PAN, SAUCEPAN, SCALE), has = true)
        store.hold(listOf(SAUCEPAN), has = false)
        assertEquals(setOf(FRYING_PAN, SCALE), store.held.value)
        // The application started anew reads them back.
        assertEquals(setOf(FRYING_PAN, SCALE), PreferencesKitchen(context).held.value)
    }

    @Test
    fun whateverTheFileHoldsThatIsNoSetOfIdsReadsAsNone() {
        context.getSharedPreferences(PreferencesKitchen.FILE, Context.MODE_PRIVATE).edit()
            .putStringSet(PreferencesKitchen.KEY, setOf("10001", "a pan")).commit()
        assertEquals(setOf(FRYING_PAN), PreferencesKitchen(context).held.value)
        context.getSharedPreferences(PreferencesKitchen.FILE, Context.MODE_PRIVATE).edit()
            .putString(PreferencesKitchen.KEY, "10001").commit()
        assertEquals(emptySet<Long>(), PreferencesKitchen(context).held.value)
    }

    // A saved book carries the whole set as text; restored, it replaces the set, kept across restarts.
    @Test
    fun theWholeSetTravelsAsTextAndReplacesTheHeldOne() {
        val text = KitchenJson.encode(setOf(SCALE, FRYING_PAN))
        assertEquals(setOf(SCALE, FRYING_PAN), KitchenJson.decode(text))
        listOf("", "[]", "{\"held\":[\"10001\"]}", "{\"held\":[1.5]}").forEach { assertEquals(null, KitchenJson.decode(it)) }

        val store = PreferencesKitchen(context)
        store.hold(listOf(SAUCEPAN), has = true)
        store.replace(KitchenJson.decode(text)!!)
        assertEquals(setOf(SCALE, FRYING_PAN), store.held.value)
        assertEquals(setOf(SCALE, FRYING_PAN), PreferencesKitchen(context).held.value)
    }

    @Test
    fun theCompositionRootExposesTheHeldSetAsItChanges() {
        val kitchen = (context as RecipeForkApplication).container.kitchen
        assertTrue(kitchen is PreferencesKitchen)
        kitchen.hold(listOf(SCALE), has = true)
        assertEquals(setOf(SCALE), kitchen.held.value)
        assertEquals(setOf(SCALE), PreferencesKitchen(context).held.value)
    }

    // A refresh that removes a ticked entry, and then a wipe of the whole store, leave the set as it was: the removed
    // entry stays in it harmlessly, unlisted and not counted.
    @Test
    fun aDictionaryRefreshOrAStoreWipeNeverLosesIt() = runBlocking {
        val name = "kitchen-store-test.db"
        val store = PreferencesKitchen(context)
        store.hold(listOf(FRYING_PAN, SAUCEPAN), has = true)

        val database = AppDatabase.open(context, name)
        val dao = database.dictionaryDao()
        dao.replace(dictionaries(KITCHEN_DICTIONARY))
        dao.replace(dictionaries(KITCHEN_DICTIONARY.filterNot { it.id == SAUCEPAN }))
        val refreshed = dao.equipment()
        assertTrue(refreshed.single { it.id == SAUCEPAN }.removed)
        assertEquals(setOf(FRYING_PAN, SAUCEPAN), PreferencesKitchen(context).held.value)
        val kitchen = Kitchen(PreferencesKitchen(context).held.value, refreshed)
        assertEquals(1, kitchen.count)
        assertFalse(kitchen.lacks(SAUCEPAN))
        assertFalse(Kitchen.groups(refreshed, app.recipefork.core.scaling.InterfaceLanguage.ENGLISH).any { group -> group.items.any { it.id == SAUCEPAN } })

        database.close()
        assertTrue(context.deleteDatabase(name))
        assertEquals(setOf(FRYING_PAN, SAUCEPAN), PreferencesKitchen(context).held.value)
    }

    private fun dictionaries(equipment: List<EquipmentEntryEntity>) =
        DictionariesDto("test", emptyList(), emptyList(), equipment.map { it.toDto() })
}
