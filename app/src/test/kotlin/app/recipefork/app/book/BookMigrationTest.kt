package app.recipefork.app.book

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/** A book file written by an earlier version of the application opens with every recipe in it. */
@RunWith(AndroidJUnit4::class)
class BookMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("migration-test")

    @After
    fun delete() {
        context.deleteDatabase(book.fileName)
    }

    /** A book file of version 1, exactly as that version created it, holding one recipe with two ingredients. */
    private fun writeVersion1() {
        val file = context.getDatabasePath(book.fileName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_1.forEach(db::execSQL)
            db.execSQL(
                "insert into recipe values ('r1', null, null, 'SENT', null, null, null, 'Soup', 'Hot', 'RU', 4, 'PRIVATE', " +
                    "0, 0, 0, 1000, 2000)",
            )
            db.execSQL("insert into ingredient values ('r1', 0, null, null, 'salt', 'TASTE_UNMEASURED', null, null, null, null)")
            db.execSQL("insert into ingredient values ('r1', 1, 7, null, null, 'PIECE', 'PIECE', '3/2', '60', 1)")
            db.execSQL("insert into recipe_tag values ('r1', 10)")
            db.version = 1
        }
    }

    /**
     * A book file of version 3, its tables exactly as that version created them, holding one recipe whose rows carry a
     * typed quantity and whose step reads a part of an ingredient.
     */
    private fun writeVersion3() {
        val file = context.getDatabasePath(book.fileName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_3.forEach(db::execSQL)
            db.execSQL(
                "insert into recipe values ('r3', null, null, 'NEVER', null, null, null, 'Pie', 'Sweet', 'EN', 6, 'LOCAL', " +
                    "0, 0, 0, 1000, 3000)",
            )
            db.execSQL("insert into ingredient values ('r3', 0, null, null, 'flour', 'EXACT', 'GRAM', '300', null, null, null)")
            db.execSQL("insert into ingredient values ('r3', 1, 7, 'cold', null, 'EXACT', 'GRAM', null, null, null, '0')")
            db.execSQL("insert into step values ('r3', 0, 'Mix  in', null, null)")
            db.execSQL("insert into step_reference values ('r3', 0, 4, 0, '100', 'GRAM')")
            db.execSQL("insert into recipe_tag values ('r3', 4)")
            db.version = 3
        }
    }

    /**
     * A book file of version 4, its tables exactly as that version created them, holding one recipe whose steps carry a
     * timer the author named, a timer named by its step, and none.
     */
    private fun writeVersion4() {
        val file = context.getDatabasePath(book.fileName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_4.forEach(db::execSQL)
            db.execSQL(
                "insert into recipe values ('r4', null, null, 'NEVER', null, null, null, 'Stew', 'Slow', 'EN', 2, 'LOCAL', " +
                    "0, 0, 0, 1000, 4000)",
            )
            db.execSQL("insert into ingredient values ('r4', 0, null, null, 'beef', 'EXACT', 'GRAM', '500', null, null, null, 'diced')")
            db.execSQL("insert into ingredient_group values ('r4', 0, 'Meat', 0)")
            db.execSQL("insert into step values ('r4', 0, 'Brown the ', 90, 'Browning')")
            db.execSQL("insert into step values ('r4', 1, 'Simmer slowly', 5999, null)")
            db.execSQL("insert into step values ('r4', 2, 'Serve', null, null)")
            db.execSQL("insert into step_reference values ('r4', 0, 10, 0, null, null)")
            db.execSQL("insert into recipe_tag values ('r4', 2)")
            db.version = 4
        }
    }

    /**
     * A book file of version 5, its tables exactly as that version created them, holding one recipe with every part a
     * recipe had then — a noted row under a group, a step with two timers and a reference, a cover, a tag — and one
     * found recipe, marked.
     */
    private fun writeVersion5() {
        val file = context.getDatabasePath(book.fileName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_5.forEach(db::execSQL)
            db.execSQL(
                "insert into recipe values ('r5', 'acc-5', 'p5', 'SENT_WITH_CHANGES', null, null, null, 'Pancakes', 'Thin', 'EN', 3, " +
                    "'PRIVATE', 0, 1, 0, 1000, 5000)",
            )
            db.execSQL("insert into ingredient values ('r5', 0, null, null, 'flour', 'EXACT', 'GRAM', '250', null, null, null, 'sifted')")
            db.execSQL("insert into ingredient values ('r5', 1, 3, null, null, 'PIECE', 'PIECE', '2', null, 0, null, null)")
            db.execSQL("insert into ingredient_group values ('r5', 0, 'Batter', 0)")
            db.execSQL("insert into step values ('r5', 0, 'Whisk the  and rest')")
            db.execSQL("insert into step values ('r5', 1, 'Fry')")
            db.execSQL("insert into step_timer values ('r5', 0, 0, 30, 'Rest')")
            db.execSQL("insert into step_timer values ('r5', 0, 1, 2, null)")
            db.execSQL("insert into step_reference values ('r5', 0, 10, 0, null, null)")
            db.execSQL("insert into media values ('m5', 'r5', 0, null, 'PHOTO', null, 'PRIVATE')")
            db.execSQL("insert into recipe_tag values ('r5', 8)")
            db.execSQL(
                "insert into recipe values ('f5', null, null, 'NEVER', 'anna', null, null, 'Found soup', '', null, null, " +
                    "'LINK_SHARED', 0, 0, 0, 1000, 1000)",
            )
            db.execSQL("insert into favourite values ('f5', 'Found soup', 6000)")
            db.version = 5
        }
    }

    /**
     * A book file of version 6, its tables exactly as that version created them, holding one recipe with every part a
     * recipe had then — equipment written as text among it — and one found recipe, marked, with equipment of its own.
     */
    private fun writeVersion6(equipment: List<String> = V6_EQUIPMENT, found: List<String> = listOf("Венчик", "Old copper bowl")) {
        val file = context.getDatabasePath(book.fileName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_6.forEach(db::execSQL)
            db.execSQL(
                "insert into recipe values ('r6', 'acc-6', 'p6', 'SENT', null, null, null, 'Pancakes', 'Thin', 'EN', 3, " +
                    "'PRIVATE', 0, 1, 0, 1000, 6000)",
            )
            db.execSQL("insert into ingredient values ('r6', 0, null, null, 'flour', 'EXACT', 'GRAM', '250', null, null, null, 'sifted')")
            db.execSQL("insert into ingredient values ('r6', 1, 3, null, null, 'PIECE', 'PIECE', '2', null, 0, null, null)")
            db.execSQL("insert into ingredient_group values ('r6', 0, 'Batter', 0)")
            db.execSQL("insert into step values ('r6', 0, 'Whisk the  and rest')")
            db.execSQL("insert into step_timer values ('r6', 0, 0, 30, 'Rest')")
            db.execSQL("insert into step_reference values ('r6', 0, 10, 0, null, null)")
            db.execSQL("insert into media values ('m6', 'r6', 0, null, 'PHOTO', null, 'PRIVATE')")
            db.execSQL("insert into recipe_tag values ('r6', 8)")
            equipment.forEachIndexed { index, name -> db.execSQL("insert into recipe_equipment values ('r6', ?, ?)", arrayOf<Any>(index, name)) }
            db.execSQL(
                "insert into recipe values ('f6', null, null, 'NEVER', 'anna', null, null, 'Found pie', '', null, null, " +
                    "'LINK_SHARED', 0, 0, 0, 1000, 1000)",
            )
            db.execSQL("insert into favourite values ('f6', 'Found pie', 7000)")
            found.forEachIndexed { index, name -> db.execSQL("insert into recipe_equipment values ('f6', ?, ?)", arrayOf<Any>(index, name)) }
            db.version = 6
        }
    }

    /**
     * A book file of version 7, its tables exactly as that version created them, holding an own recipe of two steps —
     * one with a timer, a reference and a photo — and a found recipe, marked, with a step of its own.
     */
    private fun writeVersion7() {
        val file = context.getDatabasePath(book.fileName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_7.forEach(db::execSQL)
            db.execSQL(
                "insert into recipe values ('r7', 'acc-7', null, 'NEVER', null, null, null, 'Pancakes', 'Thin', 'EN', 3, " +
                    "'LOCAL', 0, 0, 0, 1000, 6000)",
            )
            db.execSQL("insert into ingredient values ('r7', 0, null, null, 'flour', 'EXACT', 'GRAM', '250', null, null, null, 'sifted')")
            db.execSQL("insert into step values ('r7', 0, 'Whisk the  and rest')")
            db.execSQL("insert into step values ('r7', 1, 'Fry thin')")
            db.execSQL("insert into step_timer values ('r7', 0, 0, 30, 'Rest')")
            db.execSQL("insert into step_reference values ('r7', 0, 10, 0, null, null)")
            db.execSQL("insert into media values ('m7', 'r7', 0, null, 'PHOTO', 1, 'PRIVATE')")
            db.execSQL("insert into recipe_equipment values ('r7', 0, 10001, '26 cm', null)")
            db.execSQL(
                "insert into recipe values ('f7', null, null, 'NEVER', 'anna', null, null, 'Found pie', '', null, null, " +
                    "'LINK_SHARED', 0, 0, 0, 1000, 1000)",
            )
            db.execSQL("insert into step values ('f7', 0, 'Bake')")
            db.execSQL("insert into favourite values ('f7', 'Found pie', 7000)")
            db.version = 7
        }
    }

    // A version-7 book, written before a step carried a note, opens with every recipe intact — own and found — and no
    // step's note; then a step is given one, and the book reopens with it.
    @Test
    fun aVersion7BookOpensWithEveryRecipeIntactAndNoStepNotes() = runBlocking {
        writeVersion7()
        val expected = Recipe.create(Instant.ofEpochMilli(1000), AccountId("acc-7"), id = RecipeId("r7")).copy(
            title = "Pancakes",
            description = "Thin",
            language = RecipeLanguage.EN,
            servings = 3,
            ingredients = listOf(Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(250), note = "sifted")),
            steps = listOf(
                Step(0, "Whisk the  and rest", listOf(IngredientReference(0, 10)), listOf(Timer(30.minutes, "Rest"))),
                Step(1, "Fry thin"),
            ),
            media = listOf(Media("m7", null, MediaKind.PHOTO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE)),
            equipment = listOf(EquipmentItem.entry(10001, "26 cm")),
            contentChangedAt = Instant.ofEpochMilli(6000),
        )
        val database = BookDatabase.open(context, book) { error("a book of version 7 reads no dictionary") }
        val noted = expected.copy(steps = listOf(expected.steps[0], expected.steps[1].withNote("Бабушка всегда добавляла щепотку сахара")))
        try {
            val repository = RecipeRepository(ActiveBook(book, database))
            assertEquals(expected, repository.get(RecipeId("r7")))
            val found = repository.get(RecipeId("f7"))!!
            assertEquals(listOf(Step(0, "Bake")), found.steps)
            assertTrue(repository.isMarked(RecipeId("f7")))
            assertEquals(listOf("r7"), repository.ownNewest(10).first().map { it.recipeId })
            repository.save(noted)
        } finally {
            database.close()
        }
        val reopened = BookDatabase.open(context, book)
        try {
            assertEquals(noted, RecipeRepository(ActiveBook(book, reopened)).get(RecipeId("r7")))
        } finally {
            reopened.close()
        }
    }

    /** The own recipe of [writeVersion6] as version 7 reads it, with [equipment]. */
    private fun version6Recipe(equipment: List<EquipmentItem>) =
        Recipe.create(Instant.ofEpochMilli(1000), AccountId("acc-6"), RecipeId("p6"), RecipeId("r6")).copy(
            sent = SentStatus.SENT,
            title = "Pancakes",
            description = "Thin",
            tags = setOf(8L),
            language = RecipeLanguage.EN,
            servings = 3,
            ingredients = listOf(
                Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(250), note = "sifted"),
                Ingredient(1, 3, null, null, UnitType.PIECE, QuantityUnit.PIECE, Rational.of(2), divisible = false),
            ),
            ingredientGroups = listOf(IngredientGroup("Batter", 0)),
            steps = listOf(Step(0, "Whisk the  and rest", listOf(IngredientReference(0, 10)), listOf(Timer(30.minutes, "Rest")))),
            media = listOf(Media("m6", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)),
            state = RecipeState.PRIVATE,
            wasPublished = true,
            equipment = equipment,
            contentChangedAt = Instant.ofEpochMilli(6000),
        )

    // Scenario 5 of v0.28: a version-6 book, whose equipment was text, opens with every recipe intact — own and found —
    // each item whose whole text is an entry's label or synonym, in either language and any case, now that entry with
    // no qualifier, against the dictionary the application ships; every other item free text as written. The binding is
    // kept: the book reopens with it, and an entry's item is saved and read back with its qualifier.
    @Test
    fun aVersion6BookOpensWithEveryRecipeIntactAndItsEquipmentNamingEntries() = runBlocking {
        writeVersion6()
        val expected = version6Recipe(
            listOf(
                EquipmentItem.entry(10001), // "Сковорода", the Russian label
                EquipmentItem.entry(10202), // "oven", the English label in another case
                EquipmentItem.entry(10001), // "skillet", an English synonym
                EquipmentItem.entry(10202), // "Духовой шкаф", a Russian synonym
                EquipmentItem.free("Frying pan 26 cm"),
                EquipmentItem.free("Grandma's whisk"),
            ),
        )
        val database = BookDatabase.open(context, book)
        try {
            val repository = RecipeRepository(ActiveBook(book, database))
            assertEquals(expected, repository.get(RecipeId("r6")))
            val found = repository.get(RecipeId("f6"))!!
            assertEquals(listOf(EquipmentItem.entry(10401), EquipmentItem.free("Old copper bowl")), found.equipment)
            assertEquals("Found pie", found.title)
            assertTrue(repository.isMarked(RecipeId("f6")))
            assertEquals(listOf("r6"), repository.ownNewest(10).first().map { it.recipeId })
        } finally {
            database.close()
        }
        val reopened = BookDatabase.open(context, book) { error("a book of version 7 reads no dictionary") }
        try {
            val repository = RecipeRepository(ActiveBook(book, reopened))
            assertEquals(expected, repository.get(RecipeId("r6")))
            val qualified = expected.copy(equipment = listOf(EquipmentItem.entry(10001, "26 cm")) + expected.equipment.drop(1))
            repository.save(qualified)
            assertEquals(qualified, repository.get(RecipeId("r6")))
        } finally {
            reopened.close()
        }
    }

    // An entry removed from the dictionary is found by nothing, and a dictionary that cannot be read finds nothing at
    // all: every item stays free text, and every recipe opens.
    @Test
    fun aVersion6BookWithNoDictionaryToReadOpensWithItsEquipmentAsFreeText() = runBlocking {
        writeVersion6(listOf("Frying pan", "Oven"), found = emptyList())
        val removed = listOf(
            EquipmentEntryEntity(10001, "Сковорода", "Frying pan", 10000, removed = true),
            EquipmentEntryEntity(10202, "Духовка", "Oven", 10200),
        )
        val database = BookDatabase.open(context, book) { removed }
        try {
            val equipment = RecipeRepository(ActiveBook(book, database)).get(RecipeId("r6"))!!.equipment
            assertEquals(listOf(EquipmentItem.free("Frying pan"), EquipmentItem.entry(10202)), equipment)
        } finally {
            database.close()
        }
        context.deleteDatabase(book.fileName)
        writeVersion6(listOf("Frying pan", "Oven"), found = emptyList())
        val unreadable = BookDatabase.open(context, book) { throw IllegalStateException("no snapshot") }
        try {
            val repository = RecipeRepository(ActiveBook(book, unreadable))
            assertEquals(version6Recipe(listOf(EquipmentItem.free("Frying pan"), EquipmentItem.free("Oven"))), repository.get(RecipeId("r6")))
        } finally {
            unreadable.close()
        }
    }

    // A version-6 book listing no equipment never reads the dictionary on opening.
    @Test
    fun aVersion6BookWithNoEquipmentReadsNoDictionary() = runBlocking {
        writeVersion6(equipment = emptyList(), found = emptyList())
        val database = BookDatabase.open(context, book) { error("no equipment, no dictionary") }
        try {
            assertEquals(version6Recipe(emptyList()), RecipeRepository(ActiveBook(book, database)).get(RecipeId("r6")))
        } finally {
            database.close()
        }
    }

    // A version-5 book, written before a recipe listed its equipment, opens with every recipe intact — own and found —
    // each listing none; then a recipe lists some, and the book reopens with it.
    @Test
    fun aVersion5BookOpensWithEveryRecipeIntactAndNoEquipment() = runBlocking {
        writeVersion5()
        val database = BookDatabase.open(context, book)
        val equipped = try {
            val repository = RecipeRepository(ActiveBook(book, database))
            val recipe = repository.get(RecipeId("r5"))
            val expected = Recipe.create(Instant.ofEpochMilli(1000), AccountId("acc-5"), RecipeId("p5"), RecipeId("r5")).copy(
                sent = SentStatus.SENT_WITH_CHANGES,
                title = "Pancakes",
                description = "Thin",
                tags = setOf(8L),
                language = RecipeLanguage.EN,
                servings = 3,
                ingredients = listOf(
                    Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(250), note = "sifted"),
                    Ingredient(1, 3, null, null, UnitType.PIECE, QuantityUnit.PIECE, Rational.of(2), divisible = false),
                ),
                ingredientGroups = listOf(IngredientGroup("Batter", 0)),
                steps = listOf(
                    Step(0, "Whisk the  and rest", listOf(IngredientReference(0, 10)), listOf(Timer(30.minutes, "Rest"), Timer(2.minutes))),
                    Step(1, "Fry"),
                ),
                media = listOf(Media("m5", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)),
                state = RecipeState.PRIVATE,
                wasPublished = true,
                contentChangedAt = Instant.ofEpochMilli(5000),
            )
            assertEquals(expected, recipe)
            assertEquals(emptyList<EquipmentItem>(), recipe!!.equipment)
            val found = repository.get(RecipeId("f5"))!!
            assertEquals("Found soup", found.title)
            assertEquals("anna", found.statedAuthor)
            assertEquals(emptyList<EquipmentItem>(), found.equipment)
            assertTrue(repository.isMarked(RecipeId("f5")))
            assertEquals(listOf("r5"), repository.ownNewest(10).first().map { it.recipeId })

            val equipped = recipe.copy(equipment = listOf("Frying pan 26 cm", "Whisk").map(EquipmentItem::free))
            repository.save(equipped)
            assertEquals(equipped, repository.get(RecipeId("r5")))
            equipped
        } finally {
            database.close()
        }
        val reopened = BookDatabase.open(context, book)
        try {
            val repository = RecipeRepository(ActiveBook(book, reopened))
            assertEquals(equipped, repository.get(RecipeId("r5")))
            assertTrue(repository.delete(RecipeId("r5")))
            reopened.openHelper.readableDatabase.query("select count(*) from recipe_equipment").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        } finally {
            reopened.close()
        }
    }

    // DOM-024: a version-4 book, written when a step held one timer in its own row, opens with every step's timer
    // intact — named or not — as that step's one timer; then a step holds several, and the book reopens with them.
    @Test
    fun aVersion4BookOpensWithEveryOneTimerStepIntact() = runBlocking {
        writeVersion4()
        val database = BookDatabase.open(context, book)
        val recipe = try {
            val repository = RecipeRepository(ActiveBook(book, database))
            val recipe = repository.get(RecipeId("r4"))
            val expected = Recipe.create(Instant.ofEpochMilli(1000), null, id = RecipeId("r4")).copy(
                title = "Stew",
                description = "Slow",
                tags = setOf(2L),
                language = RecipeLanguage.EN,
                servings = 2,
                ingredients = listOf(Ingredient(0, null, null, "beef", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500), note = "diced")),
                ingredientGroups = listOf(IngredientGroup("Meat", 0)),
                steps = listOf(
                    Step(0, "Brown the ", listOf(IngredientReference(0, 10)), listOf(Timer(90.minutes, "Browning"))),
                    Step(1, "Simmer slowly", timers = listOf(Timer((99 * 60 + 59).minutes))),
                    Step(2, "Serve"),
                ),
                contentChangedAt = Instant.ofEpochMilli(4000),
            )
            assertEquals(expected, recipe)

            val several = recipe!!.copy(
                steps = recipe.steps.map { if (it.position == 1) it.copy(timers = it.timers + Timer(5.minutes, "Skim") + Timer(1.minutes)) else it },
            )
            repository.save(several)
            several
        } finally {
            database.close()
        }
        val reopened = BookDatabase.open(context, book)
        try {
            assertEquals(recipe, RecipeRepository(ActiveBook(book, reopened)).get(RecipeId("r4")))
            assertEquals(
                listOf(Timer((99 * 60 + 59).minutes), Timer(5.minutes, "Skim"), Timer(1.minutes)),
                RecipeRepository(ActiveBook(book, reopened)).get(RecipeId("r4"))!!.steps[1].timers,
            )
        } finally {
            reopened.close()
        }
    }

    // DOM-022, DOM-023: a version-3 book, written before groups and notes, opens with every recipe intact — no groups,
    // no notes — and then holds both.
    @Test
    fun aVersion3BookOpensWithEveryRecipeIntactAndNoGroupsOrNotes() = runBlocking {
        writeVersion3()
        val database = BookDatabase.open(context, book)
        try {
            val repository = RecipeRepository(ActiveBook(book, database))
            val recipe = repository.get(RecipeId("r3"))
            val expected = Recipe.create(Instant.ofEpochMilli(1000), null, id = RecipeId("r3")).copy(
                title = "Pie",
                description = "Sweet",
                tags = setOf(4L),
                language = RecipeLanguage.EN,
                servings = 6,
                ingredients = listOf(
                    Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(300)),
                    Ingredient(1, 7, "cold", null, UnitType.EXACT, QuantityUnit.GRAM, null, enteredQuantity = "0"),
                ),
                steps = listOf(Step(0, "Mix  in", listOf(IngredientReference(0, 4, IngredientShare(Rational.of(100), QuantityUnit.GRAM))))),
                contentChangedAt = Instant.ofEpochMilli(3000),
            )
            assertEquals(expected, recipe)
            assertEquals(emptyList<IngredientGroup>(), recipe!!.ingredientGroups)
            assertTrue(recipe.ingredients.all { it.note == null })

            val grouped = recipe.copy(
                ingredients = recipe.ingredients.map { it.copy(note = "sifted") },
                ingredientGroups = listOf(IngredientGroup("Dough", 0)),
            )
            repository.save(grouped)
            assertEquals(grouped, repository.get(RecipeId("r3")))
        } finally {
            database.close()
        }
    }

    @Test
    fun aVersion1BookOpensWithEveryRecipeAndIngredientKept() = runBlocking {
        writeVersion1()
        val database = BookDatabase.open(context, book)
        try {
            val recipe = RecipeRepository(ActiveBook(book, database)).get(RecipeId("r1"))
            val expected = Recipe.create(Instant.ofEpochMilli(1000), null, id = RecipeId("r1")).copy(
                sent = SentStatus.SENT,
                title = "Soup",
                description = "Hot",
                tags = setOf(10L),
                language = RecipeLanguage.RU,
                servings = 4,
                ingredients = listOf(
                    Ingredient(0, null, null, "salt", UnitType.TASTE_UNMEASURED, null, null),
                    Ingredient(1, 7, null, null, UnitType.PIECE, QuantityUnit.PIECE, Rational.of(3, 2), Rational.of(60), true),
                ),
                state = RecipeState.PRIVATE,
                contentChangedAt = Instant.ofEpochMilli(2000),
            )
            assertEquals(expected, recipe)
        } finally {
            database.close()
        }
    }

    // FLOW-024 (6): what the author typed into a quantity field survives the book being closed and reopened.
    @Test
    fun aRowsTypedQuantityIsKeptAfterTheBookIsReopened() = runBlocking {
        writeVersion1()
        val recipe = Recipe.create(Instant.ofEpochMilli(5000), null).copy(
            ingredients = listOf(
                Ingredient(0, null, null, "water", UnitType.EXACT, QuantityUnit.MILLILITRE, null, enteredQuantity = "1 500"),
                Ingredient(1, null, null, null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(15, 2)),
            ),
        )
        BookDatabase.open(context, book).let { database ->
            RecipeRepository(ActiveBook(book, database)).save(recipe)
            database.close()
        }
        val reopened = BookDatabase.open(context, book)
        try {
            assertEquals(recipe, RecipeRepository(ActiveBook(book, reopened)).get(recipe.id))
        } finally {
            reopened.close()
        }
    }

    // A step's part of an ingredient survives the book being closed and reopened, in a book migrated from version 1.
    @Test
    fun aPartOfAnIngredientIsKeptAfterTheBookIsReopened() = runBlocking {
        writeVersion1()
        val recipe = Recipe.create(Instant.ofEpochMilli(5000), null).copy(
            ingredients = listOf(
                Ingredient(0, null, null, "garlic", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(4), divisible = false),
                Ingredient(1, null, null, "flour", UnitType.EXACT, QuantityUnit.KILOGRAM, Rational.of(3, 2)),
            ),
            steps = listOf(
                Step(
                    0, "Marinade:  and ",
                    listOf(
                        IngredientReference(0, 10, IngredientShare(Rational.of(2), QuantityUnit.PIECE)),
                        IngredientReference(1, 15, IngredientShare(Rational.of(1, 2), QuantityUnit.KILOGRAM)),
                    ),
                ),
                Step(1, "Sauce: ", listOf(IngredientReference(0, 7))),
            ),
        )
        BookDatabase.open(context, book).let { database ->
            RecipeRepository(ActiveBook(book, database)).save(recipe)
            database.close()
        }
        val reopened = BookDatabase.open(context, book)
        try {
            assertEquals(recipe, RecipeRepository(ActiveBook(book, reopened)).get(recipe.id))
        } finally {
            reopened.close()
        }
    }

    internal companion object {
        /**
         * What Room generated for the book at version 7: version 6 with each item of equipment named by an entry and a
         * qualifier, or by free text. The identity hash stands in for that version's, which an upgrade does not read.
         */
        val VERSION_7: List<String> get() = VERSION_5.dropLast(1) + listOf(
            "CREATE TABLE IF NOT EXISTS `recipe_equipment` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `dictionary_entry` INTEGER, `qualifier` TEXT, `free_text_name` TEXT, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '00000000000000000000000000000007')",
        )

        /** The equipment of the own recipe of [writeVersion6], as version 6 held it: text. */
        val V6_EQUIPMENT = listOf("Сковорода", "oven", "skillet", "Духовой шкаф", "Frying pan 26 cm", "Grandma's whisk")

        /**
         * What Room generated for the book at version 6: version 5 with a recipe's equipment, each item its text. The
         * identity hash stands in for that version's, which an upgrade does not read.
         */
        val VERSION_6: List<String> get() = VERSION_5.dropLast(1) + listOf(
            "CREATE TABLE IF NOT EXISTS `recipe_equipment` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '00000000000000000000000000000006')",
        )

        /**
         * What Room generated for the book at version 5, its identity hash included: version 4 with a step's timers in
         * their own table and the step's row without them.
         */
        val VERSION_5 = listOf(
            "CREATE TABLE IF NOT EXISTS `recipe` (`recipe_id` TEXT NOT NULL, `owner` TEXT, `parent` TEXT, `sent` TEXT NOT NULL, `stated_author` TEXT, `stated_parent_title` TEXT, `stated_parent_nickname` TEXT, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `language` TEXT, `servings` INTEGER, `state` TEXT NOT NULL, `replaces_parent` INTEGER NOT NULL, `was_published` INTEGER NOT NULL, `safety_flag` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `content_changed_at` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`))",
            "CREATE INDEX IF NOT EXISTS `index_recipe_content_changed_at` ON `recipe` (`content_changed_at`)",
            "CREATE TABLE IF NOT EXISTS `ingredient` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `dictionary_entry` INTEGER, `qualifier` TEXT, `free_text_name` TEXT, `unit_type` TEXT NOT NULL, `unit` TEXT, `quantity` TEXT, `piece_weight` TEXT, `divisible` INTEGER, `entered_quantity` TEXT, `note` TEXT, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step_reference` (`recipe_id` TEXT NOT NULL, `step_position` INTEGER NOT NULL, `text_offset` INTEGER NOT NULL, `ingredient_position` INTEGER NOT NULL, `share` TEXT, `share_unit` TEXT, PRIMARY KEY(`recipe_id`, `step_position`, `text_offset`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `media` (`media_id` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `owner` TEXT, `kind` TEXT NOT NULL, `step_position` INTEGER, `visibility` TEXT NOT NULL, PRIMARY KEY(`media_id`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_media_recipe_id` ON `media` (`recipe_id`)",
            "CREATE TABLE IF NOT EXISTS `recipe_tag` (`recipe_id` TEXT NOT NULL, `tag_id` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`, `tag_id`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `favourite` (`recipe_id` TEXT NOT NULL, `title` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`))",
            "CREATE TABLE IF NOT EXISTS `ingredient_group` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `first_ingredient` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step_timer` (`recipe_id` TEXT NOT NULL, `step_position` INTEGER NOT NULL, `position` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, `label` TEXT, PRIMARY KEY(`recipe_id`, `step_position`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'fc4c3c5324733e23de5e539a166a8b8e')",
        )

        /**
         * What Room generated for the book at version 4: version 3 with an ingredient's note and the ingredient
         * groups, a step's one timer still in its row. The identity hash stands in for that version's.
         */
        val VERSION_4 = listOf(
            "CREATE TABLE IF NOT EXISTS `recipe` (`recipe_id` TEXT NOT NULL, `owner` TEXT, `parent` TEXT, `sent` TEXT NOT NULL, `stated_author` TEXT, `stated_parent_title` TEXT, `stated_parent_nickname` TEXT, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `language` TEXT, `servings` INTEGER, `state` TEXT NOT NULL, `replaces_parent` INTEGER NOT NULL, `was_published` INTEGER NOT NULL, `safety_flag` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `content_changed_at` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`))",
            "CREATE INDEX IF NOT EXISTS `index_recipe_content_changed_at` ON `recipe` (`content_changed_at`)",
            "CREATE TABLE IF NOT EXISTS `ingredient` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `dictionary_entry` INTEGER, `qualifier` TEXT, `free_text_name` TEXT, `unit_type` TEXT NOT NULL, `unit` TEXT, `quantity` TEXT, `piece_weight` TEXT, `divisible` INTEGER, `entered_quantity` TEXT, `note` TEXT, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `timer_minutes` INTEGER, `timer_label` TEXT, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step_reference` (`recipe_id` TEXT NOT NULL, `step_position` INTEGER NOT NULL, `text_offset` INTEGER NOT NULL, `ingredient_position` INTEGER NOT NULL, `share` TEXT, `share_unit` TEXT, PRIMARY KEY(`recipe_id`, `step_position`, `text_offset`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `media` (`media_id` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `owner` TEXT, `kind` TEXT NOT NULL, `step_position` INTEGER, `visibility` TEXT NOT NULL, PRIMARY KEY(`media_id`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_media_recipe_id` ON `media` (`recipe_id`)",
            "CREATE TABLE IF NOT EXISTS `recipe_tag` (`recipe_id` TEXT NOT NULL, `tag_id` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`, `tag_id`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `favourite` (`recipe_id` TEXT NOT NULL, `title` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`))",
            "CREATE TABLE IF NOT EXISTS `ingredient_group` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `first_ingredient` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '00000000000000000000000000000004')",
        )

        /**
         * What Room generated for the book at version 3: version 1 with a typed quantity and a part of a reference. The
         * identity hash stands in for that version's, which an upgrade does not read.
         */
        val VERSION_3 = listOf(
            "CREATE TABLE IF NOT EXISTS `recipe` (`recipe_id` TEXT NOT NULL, `owner` TEXT, `parent` TEXT, `sent` TEXT NOT NULL, `stated_author` TEXT, `stated_parent_title` TEXT, `stated_parent_nickname` TEXT, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `language` TEXT, `servings` INTEGER, `state` TEXT NOT NULL, `replaces_parent` INTEGER NOT NULL, `was_published` INTEGER NOT NULL, `safety_flag` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `content_changed_at` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`))",
            "CREATE INDEX IF NOT EXISTS `index_recipe_content_changed_at` ON `recipe` (`content_changed_at`)",
            "CREATE TABLE IF NOT EXISTS `ingredient` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `dictionary_entry` INTEGER, `qualifier` TEXT, `free_text_name` TEXT, `unit_type` TEXT NOT NULL, `unit` TEXT, `quantity` TEXT, `piece_weight` TEXT, `divisible` INTEGER, `entered_quantity` TEXT, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `timer_minutes` INTEGER, `timer_label` TEXT, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step_reference` (`recipe_id` TEXT NOT NULL, `step_position` INTEGER NOT NULL, `text_offset` INTEGER NOT NULL, `ingredient_position` INTEGER NOT NULL, `share` TEXT, `share_unit` TEXT, PRIMARY KEY(`recipe_id`, `step_position`, `text_offset`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `media` (`media_id` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `owner` TEXT, `kind` TEXT NOT NULL, `step_position` INTEGER, `visibility` TEXT NOT NULL, PRIMARY KEY(`media_id`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_media_recipe_id` ON `media` (`recipe_id`)",
            "CREATE TABLE IF NOT EXISTS `recipe_tag` (`recipe_id` TEXT NOT NULL, `tag_id` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`, `tag_id`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `favourite` (`recipe_id` TEXT NOT NULL, `title` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '00000000000000000000000000000003')",
        )

        /** What Room generated for the book at version 1. */
        val VERSION_1 = listOf(
            "CREATE TABLE IF NOT EXISTS `recipe` (`recipe_id` TEXT NOT NULL, `owner` TEXT, `parent` TEXT, `sent` TEXT NOT NULL, `stated_author` TEXT, `stated_parent_title` TEXT, `stated_parent_nickname` TEXT, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `language` TEXT, `servings` INTEGER, `state` TEXT NOT NULL, `replaces_parent` INTEGER NOT NULL, `was_published` INTEGER NOT NULL, `safety_flag` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `content_changed_at` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`))",
            "CREATE INDEX IF NOT EXISTS `index_recipe_content_changed_at` ON `recipe` (`content_changed_at`)",
            "CREATE TABLE IF NOT EXISTS `ingredient` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `dictionary_entry` INTEGER, `qualifier` TEXT, `free_text_name` TEXT, `unit_type` TEXT NOT NULL, `unit` TEXT, `quantity` TEXT, `piece_weight` TEXT, `divisible` INTEGER, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `timer_minutes` INTEGER, `timer_label` TEXT, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step_reference` (`recipe_id` TEXT NOT NULL, `step_position` INTEGER NOT NULL, `text_offset` INTEGER NOT NULL, `ingredient_position` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`, `step_position`, `text_offset`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `media` (`media_id` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `owner` TEXT, `kind` TEXT NOT NULL, `step_position` INTEGER, `visibility` TEXT NOT NULL, PRIMARY KEY(`media_id`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_media_recipe_id` ON `media` (`recipe_id`)",
            "CREATE TABLE IF NOT EXISTS `recipe_tag` (`recipe_id` TEXT NOT NULL, `tag_id` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`, `tag_id`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `favourite` (`recipe_id` TEXT NOT NULL, `title` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd717bd6e765bd6a3aece06579173a804')",
        )
    }
}
