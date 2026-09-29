package app.recipefork.app.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.recipefork.app.R
import app.recipefork.app.book.OwnRecipeRow
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorScreen
import app.recipefork.app.editor.interfaceLanguage
import app.recipefork.app.editor.newRecipe
import app.recipefork.app.editor.rememberEditorEnvironment
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** How many own recipes are read from the device at a time, and asked for again near the end (FLOW-041). */
const val OWN_PAGE = 30

/**
 * "My recipe book": the marked recipes and the recipes written in the active book (FLOW-034), and the editor on
 * an own recipe opened from it.
 */
@Composable
fun LibraryScreen(
    session: LibrarySession,
    recipes: RecipeRepository,
    modifier: Modifier = Modifier,
    editor: EditorEnvironment = rememberEditorEnvironment(),
) {
    val editing by session.editing.collectAsState()
    editing?.let { id ->
        EditorScreen(RecipeId(id), recipes, editor, session.viewModelScope, onClose = session::closeEditor, modifier = modifier)
        return
    }
    val view by session.view.collectAsState()
    Column(modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp).semantics { heading() }.testTag(LibraryTags.TITLE),
        )
        // The switch sits above the list and never scrolls with it (FLOW-034 (1)).
        Row(Modifier.fillMaxWidth().selectableGroup().testTag(LibraryTags.SWITCH)) {
            ViewTab(LibraryView.MARKED, view, R.string.library_view_marked, R.string.library_view_marked_action, session)
            ViewTab(LibraryView.OWN, view, R.string.library_view_own, R.string.library_view_own_action, session)
        }
        HorizontalDivider()
        when (view) {
            LibraryView.MARKED -> MarkedList(recipes)
            LibraryView.OWN -> OwnList(recipes, session, editor)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ViewTab(
    tab: LibraryView,
    current: LibraryView,
    @StringRes label: Int,
    @StringRes action: Int,
    session: LibrarySession,
) {
    val selected = tab == current
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected }
            .clickable(onClickLabel = stringResource(action), role = Role.Tab) { session.choose(tab) }
            .testTag(LibraryTags.tab(tab)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(label),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Box(
            Modifier.fillMaxWidth().height(3.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
        )
    }
}

/** Built from the device alone: nothing here waits on or fails for the network (FLOW-043 (5)). */
@Composable
private fun OwnList(recipes: RecipeRepository, session: LibrarySession, editor: EditorEnvironment) {
    var limit by rememberSaveable { mutableIntStateOf(OWN_PAGE) }
    val rows by remember(limit) { recipes.ownNewest(limit) }.collectAsState(initial = null)
    val listState = rememberLazyListState()
    val loaded = rows
    // Near the end of what is loaded, the next recipes are read from the device (FLOW-041 (5)).
    LaunchedEffect(listState, loaded?.size, limit) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                val size = loaded?.size ?: return@collect
                if (size == limit && last >= size - OWN_PAGE / 3) limit += OWN_PAGE
            }
    }
    if (loaded != null && loaded.isEmpty()) {
        NothingWritten(recipes, session, editor)
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(LibraryTags.OWN_LIST)) {
        items(loaded.orEmpty(), key = { it.recipeId }) { row -> OwnEntry(row) { session.openEditor(row.recipeId) } }
    }
}

/**
 * The own-recipes view of a book in which nothing has been written: it says so, and its one offer writes a new
 * recipe in that book and opens it in the editor (FLOW-042).
 */
@Composable
private fun NothingWritten(recipes: RecipeRepository, session: LibrarySession, editor: EditorEnvironment) {
    val language = interfaceLanguage(LocalConfiguration.current)
    var writing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(16.dp).testTag(LibraryTags.OWN_EMPTY),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.library_own_empty), style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = {
                if (writing) return@Button
                writing = true
                val recipe = newRecipe(editor, language)
                session.viewModelScope.launch {
                    recipes.save(recipe)
                    session.openEditor(recipe.id.value)
                }
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.WRITE),
        ) { Text(stringResource(R.string.library_own_write)) }
    }
}

@Composable
private fun OwnEntry(row: OwnRecipeRow, open: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClickLabel = stringResource(R.string.library_own_open_action), onClick = open)
            .testTag(LibraryTags.entry(row.recipeId))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = row.title.ifBlank { stringResource(R.string.common_untitled_recipe) },
            style = MaterialTheme.typography.titleMedium,
        )
        // The state is named in words, never carried by colour alone (A11Y-008).
        Text(stringResource(stateLabel(row.state)), style = MaterialTheme.typography.labelLarge)
        // The sending mark is shown beside the state and never replaces it (FLOW-036 (6), FLOW-005).
        Text(stringResource(sentLabel(row.sent)), style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

@Composable
private fun MarkedList(recipes: RecipeRepository) {
    val marked by remember { recipes.marked() }.collectAsState(initial = emptyList())
    LazyColumn(Modifier.fillMaxSize().testTag(LibraryTags.MARKED_LIST)) {
        items(marked, key = { it.recipeId }) {
            Text(
                text = it.title.ifBlank { stringResource(R.string.common_untitled_recipe) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().testTag(LibraryTags.entry(it.recipeId))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
        }
    }
}

/** The marker of FLOW-036: `local` and `private` are both a draft. */
@StringRes
fun stateLabel(state: RecipeState): Int = when (state) {
    RecipeState.LOCAL, RecipeState.PRIVATE -> R.string.library_state_draft
    RecipeState.PENDING -> R.string.library_state_pending
    RecipeState.REJECTED -> R.string.library_state_rejected
    RecipeState.LINK_SHARED -> R.string.library_state_link_shared
    RecipeState.PUBLISHED -> R.string.library_state_published
    RecipeState.REPORTED_HIDDEN -> R.string.library_state_hidden
    RecipeState.REMOVED -> R.string.library_state_removed
}

/** The three marks of FLOW-005. */
@StringRes
fun sentLabel(sent: SentStatus): Int = when (sent) {
    SentStatus.NEVER -> R.string.library_sent_never
    SentStatus.SENT -> R.string.library_sent_sent
    SentStatus.SENT_WITH_CHANGES -> R.string.library_sent_with_changes
}

object LibraryTags {
    const val TITLE = "library-title"
    const val SWITCH = "library-switch"
    const val OWN_LIST = "library-own"
    const val MARKED_LIST = "library-marked"
    const val OWN_EMPTY = "library-own-empty"
    const val WRITE = "library-write"
    fun tab(view: LibraryView) = "library-tab-${view.name.lowercase()}"
    fun entry(recipeId: String) = "library-entry-$recipeId"
}
