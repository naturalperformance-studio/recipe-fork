package app.recipefork.app.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.kitchen.Kitchen
import app.recipefork.app.kitchen.KitchenEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The reader's kitchen as the book filters by it, following what is ticked on My kitchen; null while it is not known
 * yet. Nothing ticked is a kitchen with nothing in it, known at once; otherwise the equipment dictionary is read, the
 * first time it is needed, off the main thread. A dictionary that cannot be read counts nothing: the kitchen lacks
 * nothing, and the filter is not offered.
 */
@Composable
internal fun rememberBookKitchen(environment: KitchenEnvironment): Kitchen? {
    val held by environment.store.held.collectAsState()
    var dictionary by remember(environment) { mutableStateOf<List<EquipmentEntryEntity>?>(null) }
    val needed = held.isNotEmpty()
    LaunchedEffect(environment, needed) {
        if (needed && dictionary == null) dictionary = readEquipment(environment)
    }
    val known = dictionary
    return remember(held, known) {
        when {
            held.isEmpty() -> Kitchen(emptySet(), emptyList())
            known != null -> Kitchen(held, known)
            else -> null
        }
    }
}

private suspend fun readEquipment(environment: KitchenEnvironment): List<EquipmentEntryEntity> = try {
    withContext(Dispatchers.Default) { environment.equipment.equipment() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    emptyList()
}

/** [kitchen] as a flow of its values, which a view's listing follows. */
@Composable
internal fun rememberKitchenFlow(kitchen: Kitchen?): Flow<Kitchen?> {
    val current: State<Kitchen?> = rememberUpdatedState(kitchen)
    return remember { snapshotFlow { current.value } }
}

/**
 * A kitchen emptied of everything that counts switches the filter "Enough equipment" of [view] off: it is not offered
 * then, and would hide nothing.
 */
@Composable
internal fun SwitchOffEquipmentWithoutKitchen(view: LibraryView, session: LibrarySession, kitchen: Kitchen?) {
    val filters by session.filters(view).collectAsState()
    val empty = kitchen?.isEmpty == true
    LaunchedEffect(view, empty, filters.equipment) {
        if (empty && filters.equipment) session.filter(view, session.filters(view).value.copy(equipment = false))
    }
}
