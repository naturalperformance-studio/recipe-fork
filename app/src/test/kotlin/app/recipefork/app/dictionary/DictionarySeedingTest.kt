package app.recipefork.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.data.AppDatabase
import app.recipefork.app.data.Network
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.EquipmentEntryDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.NutritionDto
import app.recipefork.core.api.TagDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shipped snapshot seeds the device (decisions/20260928-dictionaries-ship-with-the-application.md): when it
 * holds no dictionaries, or holds an earlier snapshot; never over a copy fetched from the server.
 */
@RunWith(AndroidJUnit4::class)
class DictionarySeedingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.dictionaryDao()

    private val seeded = object : SeededVersion {
        var version: String? = null
        override fun get() = version
        override fun set(version: String) {
            this.version = version
        }
    }

    private var shipped = snapshot("s1", "Italian")
    private val seeding = DictionarySeeding(dao, seeded) { shipped }

    private fun snapshot(version: String, tag: String) = DictionariesDto(
        version = version,
        ingredients = listOf(IngredientEntryDto(1, "курица", "chicken"), IngredientEntryDto(2, "крылышки", "chicken wings", 1)),
        tags = listOf(TagDto(10, "итальянская", tag)),
    )

    private fun refreshFromServer(served: DictionariesDto) = runBlocking {
        val engine = MockEngine {
            respond(Json.encodeToString(DictionariesDto.serializer(), served), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        assertEquals(DictionaryRepository.Refresh.Replaced, DictionaryRepository(Network.client(engine, "http://server.test"), dao).refresh())
    }

    private fun held(): Pair<String?, List<String>> = runBlocking { dao.version() to dao.tags().map { it.labelEn } }

    @After
    fun close() = db.close()

    @Test
    fun aDeviceHoldingNoDictionariesIsSeededFromTheSnapshot() = runBlocking {
        assertNull(dao.version())
        assertTrue(seeding.seedIfNeeded())
        assertEquals("s1" to listOf("Italian"), held())
        assertEquals(listOf(IngredientEntryEntity(1, "курица", "chicken", null), IngredientEntryEntity(2, "крылышки", "chicken wings", 1)), dao.ingredients())
        assertEquals("s1", seeded.version)
        // Seeded once: the same snapshot leaves it as it is.
        assertFalse(seeding.seedIfNeeded())
    }

    @Test
    fun aCopyFromTheServerIsNeverReplacedBySeeding() = runBlocking {
        seeding.seedIfNeeded()
        refreshFromServer(snapshot("server-2", "Italian cuisine"))
        shipped = snapshot("s2", "Italian food")
        assertFalse(seeding.seedIfNeeded())
        assertEquals("server-2" to listOf("Italian cuisine"), held())
    }

    @Test
    fun aCopyFromTheServerOnADeviceNeverSeededStays() = runBlocking {
        refreshFromServer(snapshot("server-1", "Italian cuisine"))
        assertFalse(seeding.seedIfNeeded())
        assertEquals("server-1" to listOf("Italian cuisine"), held())
    }

    @Test
    fun anEarlierSnapshotIsReplacedByTheSnapshotOfANewerApplication() = runBlocking {
        seeding.seedIfNeeded()
        shipped = snapshot("s2", "Italian food")
        assertTrue(seeding.seedIfNeeded())
        assertEquals("s2" to listOf("Italian food"), held())
        assertEquals("s2", seeded.version)
    }

    @Test
    fun theShippedSnapshotIsReadFromTheApplicationsAssets() {
        val read = DictionarySnapshot.read(context)
        assertTrue(read.tags.isNotEmpty())
        assertTrue(read.version.isNotBlank())
    }

    // DOM-011, CAT-009: the divisibility, the factors and the removal of an entry reach the device as the server gives them.
    @Test
    fun anEntryKeepsItsDivisibilityFactorsAndRemovalOnTheDevice() = runBlocking {
        val chicken = IngredientEntryDto(1, "курица", "chicken", divisible = true, factors = mapOf("g" to 1.0, "pc" to 1500.0))
        val wings = IngredientEntryDto(2, "крылышки", "chicken wings", 1, divisible = false, factors = mapOf("g" to 1.0, "pc" to 80.0))
        val goose = IngredientEntryDto(3, "гусь", "goose", removed = true)
        shipped = DictionariesDto("s1", listOf(chicken, wings, goose), emptyList())
        assertTrue(seeding.seedIfNeeded())
        assertEquals(listOf(chicken, wings, goose), dao.ingredients().map { it.toDto() })
        assertEquals(
            IngredientEntryEntity(2, "крылышки", "chicken wings", 1, false, mapOf("g" to 1.0, "pc" to 80.0), false),
            dao.ingredients()[1],
        )
    }

    // The synonyms of an entry are held on the device and read back with it — seeded from the snapshot or refreshed from
    // the server; an entry without synonyms holds none.
    @Test
    fun anEntryKeepsItsSynonymsOnTheDevice() = runBlocking {
        val potato = IngredientEntryDto(1, "картофель", "potato", synonymsRu = listOf("картошка"))
        val sugar = IngredientEntryDto(2, "сахарная пудра", "powdered sugar", synonymsEn = listOf("confectioners' sugar", "icing sugar"))
        val salt = IngredientEntryDto(3, "соль", "salt")
        shipped = DictionariesDto("s1", listOf(potato, sugar, salt), emptyList())
        val catalog = DictionaryCatalog(dao, seeding)
        assertEquals(listOf(potato, sugar, salt), catalog.ingredients().map { it.toDto() })
        assertEquals(listOf("confectioners' sugar", "icing sugar"), catalog.ingredients()[1].synonymsEn)
        assertEquals(emptyList<String>(), catalog.ingredients()[2].synonymsRu)

        val tomato = IngredientEntryDto(4, "помидор", "tomato", synonymsRu = listOf("томат"))
        refreshFromServer(DictionariesDto("server-1", listOf(potato, tomato), emptyList()))
        assertEquals(listOf(listOf("картошка"), emptyList(), emptyList(), listOf("томат")), catalog.ingredients().map { it.synonymsRu })
    }

    // T-287: the nutrition of an entry — per 100 g of the raw product — is held on the device and read back with it, seeded
    // from the snapshot or refreshed from the server; an entry without nutrition holds none.
    @Test
    fun anEntryKeepsItsNutritionOnTheDevice() = runBlocking {
        val egg = IngredientEntryDto(1, "яйцо", "egg", divisible = false, factors = mapOf("g" to 1.0, "pc" to 55.0), nutrition = NutritionDto(143.0, 12.56, 9.51, 0.72))
        val water = IngredientEntryDto(2, "вода", "water", nutrition = NutritionDto(0.0, 0.0, 0.0, 0.0))
        val salt = IngredientEntryDto(3, "соль", "salt")
        shipped = DictionariesDto("s1", listOf(egg, water, salt), emptyList())
        val catalog = DictionaryCatalog(dao, seeding)
        assertEquals(listOf(egg, water, salt), catalog.ingredients().map { it.toDto() })
        assertEquals(NutritionDto(143.0, 12.56, 9.51, 0.72), catalog.ingredients()[0].nutrition)
        assertNull(catalog.ingredients()[2].nutrition)

        val butter = IngredientEntryDto(4, "масло сливочное", "butter", nutrition = NutritionDto(717.0, 0.85, 81.11, 0.06))
        refreshFromServer(DictionariesDto("server-1", listOf(egg.copy(nutrition = null), butter), emptyList()))
        assertEquals(
            listOf(null, NutritionDto(0.0, 0.0, 0.0, 0.0), null, NutritionDto(717.0, 0.85, 81.11, 0.06)),
            catalog.ingredients().map { it.nutrition },
        )
    }

    // The application's own snapshot carries the nutrition of its first entries, and a clean device holds it.
    @Test
    fun theShippedNutritionReachesTheDevice() = runBlocking {
        shipped = DictionarySnapshot.read(context)
        val egg = DictionaryCatalog(dao, seeding).ingredients().single { it.id == 4400L }
        assertEquals(NutritionDto(143.0, 12.56, 9.51, 0.72), egg.nutrition)
    }

    // FLOW-001 (7), DOM-009 (5): replacing the held copy keeps a removed entry, so every recipe naming it keeps its name —
    // whether the new copy carries it marked removed or no longer carries it at all.
    @Test
    fun replacingTheDictionaryKeepsRemovedEntries() = runBlocking {
        seeding.seedIfNeeded()
        val marked = DictionariesDto(
            "server-1",
            listOf(IngredientEntryDto(1, "курица", "chicken", removed = true), IngredientEntryDto(2, "крылышки", "chicken wings", 1)),
            emptyList(),
        )
        refreshFromServer(marked)
        assertEquals(listOf(1L to true, 2L to false), dao.ingredients().map { it.id to it.removed })

        refreshFromServer(DictionariesDto("server-2", listOf(IngredientEntryDto(1, "курица", "chicken", removed = true)), emptyList()))
        assertEquals(
            listOf(IngredientEntryEntity(1, "курица", "chicken", null, removed = true), IngredientEntryEntity(2, "крылышки", "chicken wings", 1, removed = true)),
            dao.ingredients(),
        )
        // An entry back upstream is offered again.
        refreshFromServer(DictionariesDto("server-3", listOf(IngredientEntryDto(2, "крылышки", "chicken wings", 1)), emptyList()))
        assertEquals(listOf(1L to true, 2L to false), dao.ingredients().map { it.id to it.removed })
    }

    // AppDatabase version 3: a device holding an earlier store has it wiped, not migrated, and the dictionaries are
    // seeded again on their next read — even though the snapshot it was seeded from has not changed.
    @Test
    fun aVersionOneStoreIsWipedAndTheDictionariesAreSeededAgain() = wipedAndSeededAgain(1) { v1 ->
        v1.execSQL("create table ingredient_entry (id integer not null primary key, labelRu text not null, labelEn text not null, parentId integer)")
        v1.execSQL("insert into ingredient_entry values (1, 'курица', 'chicken', null)")
    }

    // T-128 gave the entry its synonyms in version 3: a version-2 store — divisibility, factors and removal, no
    // synonyms — is wiped and seeded again, its entries now holding their synonyms.
    @Test
    fun aVersionTwoStoreIsWipedAndTheDictionariesAreSeededAgainWithSynonyms() = wipedAndSeededAgain(2) { v2 ->
        v2.execSQL(
            "create table ingredient_entry (id integer not null primary key, labelRu text not null, labelEn text not null, " +
                "parentId integer, divisible integer, factors text not null, removed integer not null)",
        )
        v2.execSQL("insert into ingredient_entry values (1, 'курица', 'chicken', null, 1, '{\"pc\":1500.0}', 0)")
    }

    // T-201 added the equipment in version 4: a version-3 store — ingredients with synonyms, tags, no equipment — is
    // wiped and seeded again, now holding the equipment.
    @Test
    fun aVersionThreeStoreIsWipedAndTheDictionariesAreSeededAgainWithEquipment() = wipedAndSeededAgain(3) { v3 ->
        v3.execSQL(
            "create table ingredient_entry (id integer not null primary key, labelRu text not null, labelEn text not null, " +
                "parentId integer, divisible integer, factors text not null, removed integer not null, " +
                "synonymsRu text not null, synonymsEn text not null)",
        )
        v3.execSQL("insert into ingredient_entry values (1, 'курица', 'chicken', null, 1, '{\"pc\":1500.0}', 0, '[]', '[]')")
    }

    // T-287 gave the entry its nutrition in version 5: a version-4 store — ingredients with synonyms, tags, equipment, no
    // nutrition — is wiped and seeded again, its entries now holding their nutrition.
    @Test
    fun aVersionFourStoreIsWipedAndTheDictionariesAreSeededAgainWithNutrition() = wipedAndSeededAgain(4) { v4 ->
        v4.execSQL(
            "create table ingredient_entry (id integer not null primary key, labelRu text not null, labelEn text not null, " +
                "parentId integer, divisible integer, factors text not null, removed integer not null, " +
                "synonymsRu text not null, synonymsEn text not null)",
        )
        v4.execSQL("insert into ingredient_entry values (1, 'курица', 'chicken', null, 1, '{\"pc\":1500.0}', 0, '[]', '[]')")
        v4.execSQL(
            "create table equipment_entry (id integer not null primary key, labelRu text not null, labelEn text not null, " +
                "parentId integer, removed integer not null, synonymsRu text not null, synonymsEn text not null)",
        )
        v4.execSQL("insert into equipment_entry values (10000, 'Техника', 'Appliances', null, 0, '[]', '[]')")
    }

    // T-201: the equipment is seeded from the snapshot exactly as the ingredients are — groups, items, synonyms and
    // removal — and an entry removed upstream is held removed, whether marked so or no longer carried; an answer that
    // carries no equipment at all (a server need not serve it) leaves it as it is.
    @Test
    fun theEquipmentIsSeededAndKeptLikeTheIngredients() = runBlocking {
        val group = EquipmentEntryDto(10000, "Посуда для плиты", "Stovetop cookware")
        val pan = EquipmentEntryDto(10001, "Сковорода", "Frying pan", 10000, synonymsRu = listOf("сковородка"), synonymsEn = listOf("skillet"))
        val kazan = EquipmentEntryDto(10002, "Казан", "Kazan", 10000, removed = true)
        shipped = DictionariesDto("s1", emptyList(), emptyList(), listOf(group, pan, kazan))
        val catalog = DictionaryCatalog(dao, seeding)
        assertEquals(listOf(group, pan, kazan), catalog.equipment().map { it.toDto() })
        assertEquals(EquipmentEntryEntity(10001, "Сковорода", "Frying pan", 10000, false, listOf("сковородка"), listOf("skillet")), catalog.equipment()[1])

        refreshFromServer(DictionariesDto("server-1", emptyList(), emptyList(), listOf(group, kazan.copy(removed = false))))
        assertEquals(listOf(10000L to false, 10001L to true, 10002L to false), dao.equipment().map { it.id to it.removed })
        refreshFromServer(DictionariesDto("server-2", emptyList(), emptyList()))
        assertEquals(listOf(10000L to false, 10001L to true, 10002L to false), dao.equipment().map { it.id to it.removed })
    }

    // The application's own snapshot carries the equipment, and a clean device holds it after its first read.
    @Test
    fun theShippedEquipmentReachesTheDevice() = runBlocking {
        val read = DictionarySnapshot.read(context)
        shipped = read
        val held = DictionaryCatalog(dao, seeding).equipment()
        assertEquals(read.equipment, held.map { it.toDto() })
        assertTrue(held.any { it.labelRu == "Сковорода" && it.labelEn == "Frying pan" })
    }

    private fun wipedAndSeededAgain(version: Int, entries: (SQLiteDatabase) -> Unit) = runBlocking {
        val name = "app-database-v$version-test.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            entries(old)
            old.execSQL("create table tag (id integer not null primary key, labelRu text not null, labelEn text not null)")
            old.execSQL("create table dictionary_version (id integer not null primary key, version text not null)")
            old.execSQL("insert into dictionary_version values (0, 's1')")
            old.version = version
        }
        seeded.version = "s1"
        shipped = DictionariesDto(
            "s1",
            listOf(
                IngredientEntryDto(
                    1, "курица", "chicken", divisible = true, factors = mapOf("pc" to 1500.0), synonymsRu = listOf("кура"),
                    nutrition = NutritionDto(144.0, 17.44, 8.1, 0.0),
                ),
            ),
            emptyList(),
            listOf(EquipmentEntryDto(10000, "Техника", "Appliances")),
        )

        val opened = AppDatabase.open(context, name)
        try {
            val dao = opened.dictionaryDao()
            assertNull(dao.version())
            val catalog = DictionaryCatalog(dao, DictionarySeeding(dao, seeded) { shipped })
            assertEquals(
                listOf(
                    IngredientEntryEntity(
                        1, "курица", "chicken", null, true, mapOf("pc" to 1500.0), synonymsRu = listOf("кура"),
                        nutrition = NutritionDto(144.0, 17.44, 8.1, 0.0),
                    ),
                ),
                catalog.ingredients(),
            )
            assertEquals("s1", dao.version())
            assertEquals(listOf(EquipmentEntryEntity(10000, "Техника", "Appliances")), catalog.equipment())
            assertEquals(5, AppDatabase.VERSION)
            assertEquals(AppDatabase.VERSION, opened.openHelper.readableDatabase.version)
        } finally {
            opened.close()
            context.deleteDatabase(name)
        }
    }
}
