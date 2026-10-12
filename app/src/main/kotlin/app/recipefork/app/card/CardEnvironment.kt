package app.recipefork.app.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.recipefork.app.AppContainer
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.data.HeldUnitSystemSetting
import app.recipefork.app.data.LastCardTab
import app.recipefork.app.data.UnitSystemSetting
import app.recipefork.app.data.UsualServings
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.kitchen.HeldKitchen
import app.recipefork.app.kitchen.KitchenStore
import app.recipefork.app.shopping.HeldShopping
import app.recipefork.app.shopping.ShoppingStore

/** The ingredient dictionary as the card reads it: from the device alone, with or without a connection. */
fun interface IngredientEntries {
    suspend fun entries(): List<IngredientEntryEntity>
}

/**
 * What the card needs beyond the recipes of the active book: the serving count the reader usually cooks for, which a
 * recipe opens at (FLOW-017), the reader's done marks (FLOW-068), the tab last chosen, which a card opens on, and the
 * ingredient dictionary its rows are named from, the equipment dictionary its items of equipment are, and what the
 * reader's kitchen has ([KitchenStore]), by which an item the kitchen lacks is marked, and the reader's shopping list
 * ([ShoppingStore]), which the card's ⋮ puts the recipe on at the count shown (FLOW-074), and the system of units its
 * amounts are shown in ([UnitSystemSetting], v0.35), and the book's collections, which the card's ⋮ puts the recipe in
 * (v0.36) — none, and no such item, where it is not given.
 */
class CardEnvironment(
    val usualServings: UsualServings = HeldUsualServings(),
    val marks: DoneMarks = DoneMarks.inMemory(),
    val lastTab: LastCardTab = HeldLastCardTab(),
    val equipment: EquipmentSource = EquipmentSource { emptyList() },
    val kitchen: KitchenStore = HeldKitchen(),
    val shopping: ShoppingStore = HeldShopping(),
    val unitSystem: UnitSystemSetting = HeldUnitSystemSetting(),
    val collections: CollectionRepository? = null,
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
 * with every screen, My kitchen, the shopping list, the system of units and the book's collections.
 */
fun cardEnvironmentOf(container: AppContainer): CardEnvironment = CardEnvironment(
    container.usualServings, container.doneMarks, container.lastCardTab, container.dictionaries, container.kitchen, container.shopping,
    container.unitSystem, collections = container.collections,
) {
    container.dictionaries.ingredients()
}
