package app.recipefork.app.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.recipefork.app.AppContainer
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.data.LastCardTab
import app.recipefork.app.data.UsualServings
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.kitchen.HeldKitchen
import app.recipefork.app.kitchen.KitchenStore

/** The ingredient dictionary as the card reads it: from the device alone, with or without a connection. */
fun interface IngredientEntries {
    suspend fun entries(): List<IngredientEntryEntity>
}

/**
 * What the card needs beyond the recipes of the active book: the serving count the reader usually cooks for, which a
 * recipe opens at (FLOW-017), the reader's done marks (FLOW-068), the tab last chosen, which a card opens on, and the
 * ingredient dictionary its rows are named from, the equipment dictionary its items of equipment are, and what the
 * reader's kitchen has ([KitchenStore]), by which an item the kitchen lacks is marked.
 */
class CardEnvironment(
    val usualServings: UsualServings = HeldUsualServings(),
    val marks: DoneMarks = DoneMarks.inMemory(),
    val lastTab: LastCardTab = HeldLastCardTab(),
    val equipment: EquipmentSource = EquipmentSource { emptyList() },
    val kitchen: KitchenStore = HeldKitchen(),
    val ingredients: IngredientEntries,
)

/** The card's environment from the composition root: the device's dictionaries, seeded before they are read. */
@Composable
fun rememberCardEnvironment(): CardEnvironment {
    val context = LocalContext.current.applicationContext
    return remember(context) { cardEnvironmentOf((context as RecipeForkApplication).container) }
}

/**
 * The card's environment of [container]: its usual servings, the device's done marks, its last tab, the dictionaries it shares
 * with every screen and My kitchen.
 */
fun cardEnvironmentOf(container: AppContainer): CardEnvironment =
    CardEnvironment(container.usualServings, container.doneMarks, container.lastCardTab, container.dictionaries, container.kitchen) {
        container.dictionaries.ingredients()
    }
