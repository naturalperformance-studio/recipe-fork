package app.recipefork.app.book

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The recipe book whose content is shown. Nothing reads a book that is not active. The pool is the only
 * book there is so far; switching between books belongs to the accounts on the device.
 */
class ActiveBook(name: BookName, database: BookDatabase) {
    data class Open(val name: BookName, val database: BookDatabase)

    private val current = MutableStateFlow(Open(name, database))

    /** The active book, as it changes. */
    val open: StateFlow<Open> = current.asStateFlow()

    val database: BookDatabase get() = current.value.database
}
