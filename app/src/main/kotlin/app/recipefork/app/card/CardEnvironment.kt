package app.recipefork.app.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.recipefork.app.AppContainer
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.data.UsualServings
import app.recipefork.app.dictionary.IngredientEntryEntity

/** The ingredient dictionary as the card reads it: from the device alone, with or without a connection. */
fun interface IngredientEntries {
    suspend fun entries(): List<IngredientEntryEntity>
}

/**
 * What the card needs beyond the recipes of the active book: the serving count the reader usually cooks for, which a
 * recipe opens at (FLOW-017), and the ingredient dictionary its rows are named from.
 */
class CardEnvironment(
    val usualServings: UsualServings = HeldUsualServings(),
    val ingredients: IngredientEntries,
)

/** The card's environment from the composition root: the device's dictionaries, seeded before they are read. */
@Composable
fun rememberCardEnvironment(): CardEnvironment {
    val context = LocalContext.current.applicationContext
    return remember(context) { cardEnvironmentOf((context as RecipeForkApplication).container) }
}

/** The card's environment of [container]: its usual servings and the dictionaries it shares with every screen. */
fun cardEnvironmentOf(container: AppContainer): CardEnvironment =
    CardEnvironment(container.usualServings) { container.dictionaries.ingredients() }
