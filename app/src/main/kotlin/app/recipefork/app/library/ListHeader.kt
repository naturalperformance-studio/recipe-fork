package app.recipefork.app.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.editor.BlockAction
import app.recipefork.app.editor.interfaceLanguage
import app.recipefork.app.recipe.ListingParams
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Locale

/**
 * What is typed in a view's search field, as the field shows it — the text and where the cursor stands — handed to the
 * session as it changes, so that the query outlives the field (FLOW-035 (4), FLOW-058).
 */
@Stable
class SearchField(initial: String, private val onQuery: (String) -> Unit) {
    var value by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        private set

    fun type(typed: TextFieldValue) {
        val changed = typed.text != value.text
        value = typed
        if (changed) onQuery(typed.text)
    }

    fun clear() = type(TextFieldValue(""))
}

/** The search field of [view], opening on the query the session holds for it. */
@Composable
internal fun rememberSearchField(view: LibraryView, session: LibrarySession): SearchField =
    remember(view, session) { SearchField(session.query(view).value) { session.search(view, it) } }

/** What [view] is listed by, as the session holds it, in the collation of the interface language. */
@Composable
internal fun rememberListingParams(view: LibraryView, session: LibrarySession): Flow<ListingParams> {
    val locale = if (interfaceLanguage(LocalConfiguration.current) == RecipeLanguage.RU) RUSSIAN else Locale.ENGLISH
    return remember(view, session, locale) {
        combine(session.query(view), session.order(view)) { query, order -> ListingParams(query, order, locale) }
    }
}

private val RUSSIAN: Locale = Locale.forLanguageTag(RecipeLanguage.RU.name.lowercase())

/**
 * The header above a view's list: its search field (FLOW-035 (4)) and, at its end, the choice of the view's order
 * (FLOW-035 (3)). It never scrolls with the list; what else narrows the list is to stand under them.
 */
@Composable
internal fun ListHeader(search: SearchField, order: BookOrder, onOrder: (BookOrder) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp).testTag(LibraryTags.HEADER)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search.value,
                onValueChange = search::type,
                singleLine = true,
                label = { Text(stringResource(R.string.library_search)) },
                trailingIcon = if (search.value.text.isEmpty()) null else {
                    {
                        BlockAction(
                            R.drawable.ic_clear_search,
                            stringResource(R.string.library_search_clear),
                            LibraryTags.SEARCH_CLEAR,
                            onClick = search::clear,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.weight(1f).testTag(LibraryTags.SEARCH),
            )
            OrderChoice(order, onOrder)
        }
    }
}

/**
 * The view's order: a button named by the order in force, for the screen reader and in a tooltip, that opens the menu
 * of the three orders, the one in force selected.
 */
@Composable
private fun OrderChoice(order: BookOrder, onOrder: (BookOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BlockAction(
            R.drawable.ic_order,
            stringResource(R.string.library_order, stringResource(orderLabel(order))),
            LibraryTags.ORDER,
            onClick = { expanded = !expanded },
            action = stringResource(R.string.library_order_action),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BookOrder.entries.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(stringResource(orderLabel(choice))) },
                    leadingIcon = { RadioButton(selected = choice == order, onClick = null) },
                    onClick = {
                        expanded = false
                        onOrder(choice)
                    },
                    modifier = Modifier.semantics { selected = choice == order }.testTag(LibraryTags.order(choice)),
                )
            }
        }
    }
}

@StringRes
private fun orderLabel(order: BookOrder): Int = when (order) {
    BookOrder.NEWEST -> R.string.library_order_newest
    BookOrder.OLDEST -> R.string.library_order_oldest
    BookOrder.TITLE -> R.string.library_order_title
}

/** A search of a view that found nothing says so, naming the query, and offers to clear it (FLOW-035 (4)). */
@Composable
internal fun NothingFound(query: String, onClear: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.library_nothing_found, query.trim()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(LibraryTags.NOTHING_FOUND),
        )
        TextButton(
            onClick = onClear,
            modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.NOTHING_FOUND_CLEAR),
        ) { Text(stringResource(R.string.library_search_clear)) }
    }
}
