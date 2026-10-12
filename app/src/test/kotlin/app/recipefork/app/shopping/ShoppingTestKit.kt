package app.recipefork.app.shopping

import app.recipefork.app.book.BookName
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import java.time.Instant

internal const val FLOUR = 101L
internal const val ONION = 102L
internal const val SALT = 103L
internal const val SUGAR = 104L

/** Flour: 0.6 g per ml, 130 g per cup, no pinch. Onion: 150 g a piece, indivisible. Salt: 6 g per tsp. Sugar: 240 g a cup. */
internal val SHOPPING_DICTIONARY = listOf(
    IngredientEntryEntity(FLOUR, "мука", "flour", null, factors = mapOf("g" to 1.0, "ml" to 0.6, "cup" to 130.0)),
    IngredientEntryEntity(ONION, "лук", "onion", null, divisible = false, factors = mapOf("pc" to 150.0, "g" to 1.0)),
    IngredientEntryEntity(SALT, "соль", "salt", null, factors = mapOf("g" to 1.0, "tsp" to 6.0)),
    IngredientEntryEntity(SUGAR, "сахар", "sugar", null, factors = mapOf("g" to 1.0, "tsp" to 5.0, "tbsp" to 15.0, "cup" to 240.0)),
)

internal val POOL: String = BookName.Pool.fileName

internal fun r(n: Long, d: Long = 1) = Rational.of(n, d)

/** A row naming [entry]: [quantity] of [unit], or to taste where [unit] is null. */
internal fun row(entry: Long, quantity: Rational?, unit: QuantityUnit?, qualifier: String? = null) = Ingredient(
    position = 0,
    dictionaryEntry = entry,
    qualifier = qualifier,
    unitType = unit?.type ?: UnitType.TASTE_UNMEASURED,
    unit = unit,
    quantity = quantity,
    divisible = if (unit == QuantityUnit.PIECE) false else null,
)

/** A row in the author's own words. */
internal fun free(text: String, quantity: Rational?, unit: QuantityUnit?) = Ingredient(
    position = 0,
    dictionaryEntry = null,
    freeTextName = text,
    unitType = unit?.type ?: UnitType.TASTE_UNMEASURED,
    unit = unit,
    quantity = quantity,
    divisible = if (unit == QuantityUnit.PIECE) false else null,
)

private val NOW = Instant.parse("2026-10-12T12:00:00Z")

/** A recipe titled [title], written for [servings], holding [rows] in their order. */
internal fun recipe(title: String, servings: Int?, vararg rows: Ingredient, minutesAgo: Long = 0): Recipe =
    Recipe.create(NOW.minusSeconds(minutesAgo * 60), owner = null).copy(
        title = title,
        servings = servings,
        ingredients = rows.mapIndexed { i, row -> row.copy(position = i) },
    )

/** [recipe] listed in the pool at [servings], its authored count where none is given. */
internal fun listed(recipe: Recipe, servings: Rational? = recipe.servings?.let { r(it.toLong()) }) =
    ListedRecipe(POOL, recipe.id, servings)
