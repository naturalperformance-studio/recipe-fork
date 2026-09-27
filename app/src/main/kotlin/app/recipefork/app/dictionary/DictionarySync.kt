package app.recipefork.app.dictionary

import app.recipefork.app.data.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Refreshes the held dictionaries whenever a network connection becomes available (FLOW-001):
 * once when started with a connection, and again each time a connection returns.
 */
class DictionarySync(
    private val connectivity: Connectivity,
    private val repository: DictionaryRepository,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        connectivity.online
            .distinctUntilChanged()
            .filter { it }
            .collect { repository.refresh() }
    }
}
