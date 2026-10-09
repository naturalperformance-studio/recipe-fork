package app.recipefork.app.editor

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import app.recipefork.app.AppContainer
import app.recipefork.app.R
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipePart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The editor's environment from the composition root: the active book and the device's dictionaries. */
@Composable
fun rememberEditorEnvironment(): EditorEnvironment {
    val context = LocalContext.current.applicationContext
    return remember(context) { editorEnvironmentOf((context as RecipeForkApplication).container) }
}

/** The editor's environment of [container]: the active book and the dictionaries it shares with every screen. */
fun editorEnvironmentOf(container: AppContainer): EditorEnvironment = EditorEnvironment(
    container.activeBook,
    container.dictionaries,
    dragHandleSide = container.dragHandleSide,
    mode = container.mode,
    clipStore = container::clipStore,
    animatedImages = { container.animatedImages },
    animationStore = container::animationStore,
)

/**
 * The editor: one scrolling screen in the order of FLOW-024 — the title with the declared language beside it and the
 * cover under it (FLOW-026), the tags, the serving count, the ingredients, the steps and the description.
 *
 * [onClose] is the bar's back action. What is entered is written as it is typed, so leaving holds nothing back: the
 * system's back, and its predictive gesture, are the navigation host's, which leaves the editor for the destination
 * beneath it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    recipeId: RecipeId,
    recipes: RecipeRepository,
    environment: EditorEnvironment,
    saving: CoroutineScope,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The steps' media work outlives the screen — the activity recreated, or the editor left and opened again —
    // while a clip converts (FLOW-026); the steps' keys are kept with it, so the work reaches the step it was for.
    val mediaWork = rememberStepMediaWork(recipeId)
    // The row keys outlive the activity being recreated, as the state the rows saved under them does.
    val stepKeys = rememberSaveable(recipeId, saver = RowKeys.Saver) { mediaWork.stepKeys ?: RowKeys() }
    remember(mediaWork, stepKeys) { mediaWork.stepKeys = stepKeys }
    val ingredientKeys = rememberSaveable(recipeId, saver = RowKeys.Saver) { RowKeys() }
    val groupKeys = rememberSaveable(recipeId, saver = RowKeys.Saver) { RowKeys() }
    // Not saved: what the editor holds for this session alone — the references offered once (GUIDE-008) among it —
    // starts again after the activity is recreated or the process restored, which is intended, nothing of a hint
    // being kept (GUIDE-005).
    val editor = remember(recipeId) { RecipeEditor(recipes, recipeId, environment.clock, saving, stepKeys, ingredientKeys, groupKeys) }
    // Both reads resume on the main thread whatever the effect's own dispatcher is, so the screen is never
    // touched from a database thread.
    LaunchedEffect(editor) { withContext(Dispatchers.Main.immediate) { editor.load() } }
    var dictionary by remember { mutableStateOf<List<TagEntity>?>(null) }
    LaunchedEffect(environment) { withContext(Dispatchers.Main.immediate) { dictionary = environment.tags.tags() } }
    val recipe by editor.recipe.collectAsState()
    val handleRight by environment.dragHandleSide.right.collectAsState()
    val scrolled = rememberScrollState()
    val scroll = remember(scrolled) { EditorScroll(scrolled) }
    val context = LocalContext.current
    val store = remember(context, environment) { environment.photoStore(context.applicationContext.filesDir) }
    val clips = remember(context, environment) { environment.clipStore(context.applicationContext) }
    val animated = remember(context, environment) { environment.animatedImages(context.applicationContext) }
    val animations = remember(context, environment) { environment.animationStore(context.applicationContext) }
    remember(mediaWork, editor) { mediaWork.show(editor) }
    val activity = LocalActivity.current
    DisposableEffect(mediaWork) {
        onDispose { if (activity?.isChangingConfigurations != true) mediaWork.left() }
    }
    val host = remember { SnackbarHostState() }
    val statementScope = rememberCoroutineScope()
    val statements = remember(host, statementScope) { EditorStatements(host, statementScope) }
    PartsMadeWholeStatement(editor, statements)
    RemovalStatement(editor, statements)
    val focus = LocalFocusManager.current
    val saved = stringResource(R.string.editor_saved)

    Box(modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().testTag(EditorTags.SCREEN)) {
            // The top bar: back, and on its right what a fork takes (FLOW-030) and the checklist, there whatever the
            // recipe holds and whatever its state (GUIDE-003), then "Done" while the recipe can be changed.
            EditorBar(
                done = recipe?.state?.editable == true,
                checklist = {
                    ForkNoteAction()
                    ChecklistControl(environment.mode)
                },
                onBack = onClose,
                onDone = {
                    // Leaving the field is what a field's marks wait for (FLOW-028); then, once every change is in
                    // the book, it says so — unless a removal still offers Undo, which "Saved" never replaces.
                    focus.clearFocus()
                    statementScope.launch {
                        editor.written()
                        if (editor.removal.value == null) statements.say(saved, SnackbarDuration.Short)
                    }
                },
            )
            val shown = recipe ?: return@Column
            Column(
                Modifier
                    .fillMaxSize()
                    // A touch on any field or control is the author's next action, which ends a hint (GUIDE-007);
                    // a scroll is not one.
                    .onEveryTap { editor.noteAction() }
                    .onGloballyPositioned { scroll.viewport = it }
                    // Where the field the author is in stands, so a mark appearing above it does not push it away.
                    .onFocusedBoundsChanged { scroll.focused = it }
                    .verticalScroll(scroll.state)
                    .padding(16.dp)
                    .testTag(EditorTags.FORM),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!shown.state.editable) {
                    Text(
                        stringResource(R.string.editor_frozen),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(EditorTags.FROZEN),
                    )
                }
                CompositionLocalProvider(LocalEditorStatements provides statements, LocalEditorScroll provides scroll) {
                    // The keyboard's Next goes from the title to the serving count, and from there into the rows.
                    val servings = remember { FocusRequester() }
                    val rows = remember { RowFocus() }
                    val headings = remember { RowFocus() }
                    TitleAndLanguage(shown, editor) { servings.requestFocus() }
                    CoverSection(shown, editor, store, recipes.bookName(), saving)
                    Tags(shown, editor, dictionary)
                    ServingCount(shown, editor, servings, rows, headings)
                    CompositionLocalProvider(LocalDragColumnOnRight provides handleRight) {
                        Ingredients(shown, editor, environment.ingredients, scroll, environment.mode, rows, headings)
                        Steps(shown, editor, environment.ingredients, scroll, store, recipes.bookName(), saving, clips, mediaWork, animated, animations)
                    }
                    Description(shown, editor)
                }
            }
        }
        EditorStatementHost(host, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Where the editor's statements show: at the bottom, above the keyboard where it is open, each wrapped to its whole
 * text, read by the screen reader as it comes (the host's live region), gone by itself or swiped away either way.
 */
@Composable
internal fun EditorStatementHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(host, modifier.imePadding()) { data ->
        val swipe = rememberSwipeToDismissBoxState()
        SwipeToDismissBox(swipe, backgroundContent = {}, onDismiss = { data.dismiss() }) {
            Snackbar(data, modifier = Modifier.testTag(EditorTags.STATEMENT))
        }
    }
}

/**
 * What the editor says at its bottom, above the keyboard where it is open: one statement at a time, each going by
 * itself after a while or swiped away; a new one replaces the one shown. The screen reader reads each as it comes.
 */
internal class EditorStatements(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    private var shown: Job? = null

    fun say(text: String, duration: SnackbarDuration = SnackbarDuration.Long) {
        shown?.cancel()
        shown = scope.launch { host.showSnackbar(text, duration = duration) }
    }

    /**
     * Says [text] with the action [action]: [onAction] once the author takes it, [onGone] once the statement goes
     * without it — by itself, swiped away, replaced by another, or the editor closed. Returns what withdraws it.
     */
    fun offer(text: String, action: String, onAction: () -> Unit, onGone: () -> Unit): Job {
        shown?.cancel()
        return scope.launch {
            val result = try {
                host.showSnackbar(text, actionLabel = action, duration = SnackbarDuration.Long)
            } catch (gone: CancellationException) {
                onGone()
                throw gone
            }
            if (result == SnackbarResult.ActionPerformed) onAction() else onGone()
        }.also { shown = it }
    }
}

/** The statements of the editor the composition is in; outside one, nothing is said. */
internal val LocalEditorStatements = staticCompositionLocalOf<EditorStatements?> { null }

/** Says once that a row's change of unit made the steps' parts of it whole again. */
@Composable
private fun PartsMadeWholeStatement(editor: RecipeEditor, statements: EditorStatements) {
    val statement by editor.partsMadeWhole.collectAsState()
    val text = statement?.let { stringResource(R.string.editor_parts_made_whole, it.ingredientPosition + 1) }
    // Shown outside the effect, which ends as soon as the statement is marked as stated.
    LaunchedEffect(statement) {
        val shown = statement ?: return@LaunchedEffect
        statements.say(text.orEmpty())
        editor.partsMadeWholeStated(shown)
    }
}

/**
 * Says that a row or a step was deleted, with Undo, while the editor still offers to undo it; the statement goes once
 * the editor stops offering it — the next change made — and the deletion ends for good when it goes without Undo.
 */
@Composable
private fun RemovalStatement(editor: RecipeEditor, statements: EditorStatements) {
    val removal by editor.removal.collectAsState()
    val text = removal?.let {
        val said = when (it.kind) {
            Removal.Kind.STEP -> R.string.editor_step_removed
            Removal.Kind.INGREDIENT -> R.string.editor_ingredient_removed
            Removal.Kind.GROUP -> R.string.editor_group_removed
        }
        stringResource(said, it.position + 1)
    }
    val undo = stringResource(R.string.editor_undo)
    val offered = remember { arrayOfNulls<Job>(1) }
    LaunchedEffect(removal) {
        val shown = removal
        if (shown == null) {
            offered[0]?.cancel()
        } else {
            offered[0] = statements.offer(text.orEmpty(), undo, { editor.undoRemoval(shown) }, { editor.endRemoval(shown) })
        }
    }
}

/**
 * The editor's top app bar, as the book's and the card's: back at its start, announced as showing the recipe; at its
 * end [checklist] and, where [done], "Done", which says the recipe is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorBar(done: Boolean, checklist: @Composable () -> Unit, onBack: () -> Unit, onDone: () -> Unit) {
    TopAppBar(
        title = {},
        modifier = Modifier.testTag(EditorTags.BAR),
        navigationIcon = {
            val label = stringResource(R.string.editor_back)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = stringResource(R.string.editor_back_action), role = Role.Button, onClick = onBack)
                    .testTag(EditorTags.BACK),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = label)
            }
        },
        actions = {
            checklist()
            if (done) {
                EditorButton(
                    text = stringResource(R.string.editor_done),
                    action = stringResource(R.string.editor_done_action),
                    onClick = onDone,
                    modifier = Modifier.testTag(EditorTags.DONE),
                )
            }
        },
        // The screen is already inside the safe drawing area.
        windowInsets = WindowInsets(0),
    )
}

@Composable
private fun TitleAndLanguage(recipe: Recipe, editor: RecipeEditor, onNext: () -> Unit) {
    val editable = recipe.state.editable
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (editable) {
                OutlinedTextField(
                    value = recipe.title,
                    onValueChange = { editor.setTitle(it) },
                    label = { Text(stringResource(R.string.editor_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { onNext() }),
                    modifier = Modifier.weight(1f).then(leaving { editor.leave(RecipePart.Title) }).testTag(EditorTags.TITLE),
                )
            } else {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}.testTag(EditorTags.TITLE)) {
                    Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.labelMedium)
                    Text(recipe.title.ifBlank { stringResource(R.string.common_untitled_recipe) }, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(8.dp))
            // The declared language sits beside the title (FLOW-024): changing it is one action (L10N-004).
            Language(recipe, editor)
        }
        // Under the title and the language, across the width, what either still needs (FLOW-028).
        BoundMark(editor, RecipePart.Title, EditorTags.TITLE_MARK, gap = 4.dp)
        BoundMark(editor, RecipePart.Language, EditorTags.LANGUAGE_MARK, gap = 4.dp)
    }
}

/**
 * Calls [onTap] on every touch that lifts without having moved past the touch slop, before whatever it touched
 * handles it, and consumes nothing: a scroll or a drag is not a tap.
 */
private fun Modifier.onEveryTap(onTap: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var moved = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
            if (!change.pressed) break
        }
        if (!moved) onTap()
    }
}

/** How much an [EditorButton] weighs: a text, or a tonal fill for the sections' own Add. */
internal enum class ButtonWeight { TEXT, TONAL }

/**
 * A button of the editor: Material 3's text or filled tonal button by [weight], its corners the theme's
 * extra-large ones (a pill at a button's height), at least 48 dp high (A11Y-003), its click announced as what
 * it does — [action] — beside the visible [text] (A11Y-005).
 */
@Composable
internal fun EditorButton(
    text: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weight: ButtonWeight = ButtonWeight.TEXT,
) {
    val shape = MaterialTheme.shapes.extraLarge
    // Drawn 48 dp high, not the 40 dp Material draws a button at, so the target is the button itself (A11Y-003).
    val announced = modifier.heightIn(min = 48.dp).semantics { onClick(label = action, action = null) }
    val label: @Composable RowScope.() -> Unit = { Text(text) }
    when (weight) {
        ButtonWeight.TEXT -> TextButton(onClick, announced, shape = shape, content = label)
        ButtonWeight.TONAL -> FilledTonalButton(onClick, announced, shape = shape, content = label)
    }
}

/**
 * What a row's menu does to its row (FLOW-025): a new row above or below it, or the row one place up or down; for an
 * ingredient row, also a group's heading above it (DOM-022).
 */
enum class RowAction(@StringRes val label: Int) {
    INSERT_ABOVE(R.string.editor_row_insert_above),
    INSERT_BELOW(R.string.editor_row_insert_below),
    MOVE_UP(R.string.editor_row_move_up),
    MOVE_DOWN(R.string.editor_row_move_down),
    GROUP_ABOVE(R.string.editor_row_group_above),
    ;

    /** How a test tag names the action: `insert-above` and so on. */
    internal val tag: String get() = name.lowercase().replace('_', '-')
}

/**
 * The menu of the row at [position] of [count] rows: a 48 dp control named for its row by [name] (A11Y-003, A11Y-005)
 * that opens [actions] — by default the four of FLOW-025, the first row with no "Move up", the last with no "Move down".
 * Each action is one tap, so nothing here is done only by dragging (A11Y-006).
 */
@Composable
internal fun RowMenu(
    position: Int,
    count: Int,
    name: String,
    tag: String,
    actionTag: (RowAction) -> String,
    onAction: (RowAction) -> Unit,
    modifier: Modifier = Modifier,
    actions: List<RowAction> = RowAction.entries.filter {
        it != RowAction.GROUP_ABOVE && (it != RowAction.MOVE_UP || position > 0) && (it != RowAction.MOVE_DOWN || position < count - 1)
    },
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        BlockAction(R.drawable.ic_row_menu, name, tag, onClick = { expanded = !expanded })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions
                .forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(action.label)) },
                        onClick = {
                            expanded = false
                            onAction(action)
                        },
                        modifier = Modifier.testTag(actionTag(action)),
                    )
                }
        }
    }
}

/**
 * What a fork takes and what stays with the author unless the fork is their own (FLOW-030), behind an info action in
 * the top bar, so that it takes no room above the form: the same note for every recipe, forked or not, and never a
 * promise that forking can be prevented (FORK-002). Opening it stores nothing.
 */
@Composable
private fun ForkNoteAction() {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.editor_fork_note)
    EditorBarAction(
        icon = R.drawable.ic_info,
        label = label,
        action = stringResource(R.string.editor_fork_note_action),
        tooltip = label,
        tag = EditorTags.FORK_NOTE,
        tooltipTag = EditorTags.FORK_NOTE_TOOLTIP,
        onClick = { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            modifier = Modifier.testTag(EditorTags.FORK_NOTE_DIALOG),
            title = { Text(label) },
            text = { Text(stringResource(R.string.editor_fork_takes), Modifier.testTag(EditorTags.FORK_TAKES)) },
            confirmButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.testTag(EditorTags.FORK_NOTE_CLOSE)) {
                    Text(stringResource(R.string.editor_fork_note_close))
                }
            },
        )
    }
}

/**
 * An icon-only action of the editor's top bar, as the card's: 48 dp, named [label] for the screen reader with its click
 * announced as [action], and whose long press shows [tooltip] — the long press the action's own, so that it stays one
 * element for the screen reader. Being only an icon, it keeps its width at any font size.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun EditorBarAction(
    @DrawableRes icon: Int,
    label: String,
    action: String,
    tooltip: String,
    tag: String,
    tooltipTag: String,
    onClick: () -> Unit,
) {
    val state = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip(Modifier.testTag(tooltipTag)) { Text(tooltip) } },
        state = state,
        enableUserInput = false,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = action,
                    onClick = onClick,
                    onLongClickLabel = stringResource(R.string.editor_action_show_name),
                    onLongClick = { scope.launch { state.show() } },
                )
                .testTag(tag),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

object EditorTags {
    /** The top bar's info action that shows what a fork takes (FLOW-030). */
    const val FORK_NOTE = "editor-fork-note"
    const val FORK_NOTE_TOOLTIP = "editor-fork-note-tooltip"
    const val FORK_NOTE_DIALOG = "editor-fork-note-dialog"
    const val FORK_NOTE_CLOSE = "editor-fork-note-close"
    /** The note itself, in the dialog: what a fork takes and what stays with the author (FLOW-030). */
    const val FORK_TAKES = "editor-fork-takes"
    const val SCREEN = "editor"
    const val BAR = "editor-bar"
    const val BACK = "editor-back"
    /** The top bar's "Done": the changes are in the book, and it says "Saved". */
    const val DONE = "editor-done"
    const val FORM = "editor-form"
    const val FROZEN = "editor-frozen"
    const val TITLE = "editor-title"
    /** The marks of FLOW-028: a part left below the minimum or over a limit. */
    const val TITLE_MARK = "editor-title-mark"
    const val LANGUAGE_MARK = "editor-language-mark"
    const val SERVINGS_MARK = "editor-servings-mark"
    const val INGREDIENTS_MARK = "editor-ingredients-mark"
    const val STEPS_MARK = "editor-steps-mark"
    const val DESCRIPTION_MARK = "editor-description-mark"
    fun stepMark(position: Int) = "editor-step-$position-mark"
    fun timerNameMark(position: Int) = "editor-step-$position-timer-name-mark"
    const val LANGUAGE = "editor-language"
    const val LANGUAGE_CLEAR = "editor-language-clear"
    /** One option of the language's segmented switch: `editor-language-ru`, `editor-language-en`. */
    fun language(language: RecipeLanguage) = "editor-language-${language.name.lowercase()}"
    const val TAGS = "editor-tags"
    const val TAG_SEARCH = "editor-tag-search"
    const val OFFERED = "editor-tags-offered"
    const val NO_MATCH = "editor-tag-no-match"
    fun offered(tagId: Long) = "editor-tag-offered-$tagId"
    fun chosen(tagId: Long) = "editor-tag-chosen-$tagId"
    fun remove(tagId: Long) = "editor-tag-remove-$tagId"
    fun removeIcon(tagId: Long) = "editor-tag-remove-icon-$tagId"
    const val SERVINGS = "editor-servings"
    const val SERVINGS_ACCEPTS = "editor-servings-accepts"
    const val INGREDIENTS = "editor-ingredients"
    const val INGREDIENT_ADD = "editor-ingredient-add"
    fun ingredient(position: Int) = "editor-ingredient-$position"
    fun ingredientName(position: Int) = "editor-ingredient-$position-name"
    fun ingredientQuantity(position: Int) = "editor-ingredient-$position-quantity"
    fun ingredientUnit(position: Int) = "editor-ingredient-$position-unit"
    fun ingredientRemove(position: Int) = "editor-ingredient-$position-remove"
    fun ingredientMenu(position: Int) = "editor-ingredient-$position-menu"
    fun ingredientHandle(position: Int) = "editor-ingredient-$position-handle"
    fun ingredientHandleIcon(position: Int) = "editor-ingredient-$position-handle-icon"
    fun ingredientAction(position: Int, action: RowAction) = "editor-ingredient-$position-${action.tag}"
    fun ingredientError(position: Int) = "editor-ingredient-$position-error"
    fun ingredientWarning(position: Int) = "editor-ingredient-$position-warning"
    /** "Add group" under the ingredient list, and the parts of the heading at [index] of the groups (DOM-022). */
    const val GROUP_ADD = "editor-group-add"
    fun group(index: Int) = "editor-group-$index"
    fun groupName(index: Int) = "editor-group-$index-name"
    fun groupMark(index: Int) = "editor-group-$index-mark"
    fun groupRemove(index: Int) = "editor-group-$index-remove"
    fun groupMenu(index: Int) = "editor-group-$index-menu"
    fun groupAction(index: Int, action: RowAction) = "editor-group-$index-${action.tag}"
    fun groupHandle(index: Int) = "editor-group-$index-handle"
    fun groupHandleIcon(index: Int) = "editor-group-$index-handle-icon"
    fun divisibility(position: Int) = "editor-ingredient-$position-divisibility"
    fun divisible(position: Int, divisible: Boolean) = "editor-ingredient-$position-divisible-$divisible"
    fun unitOption(unit: QuantityUnit) = "editor-unit-${unit.name}"
    const val STEPS = "editor-steps"
    const val STEPS_NONE = "editor-steps-none"
    const val STEP_ADD = "editor-step-add"
    fun step(position: Int) = "editor-step-$position"
    fun stepText(position: Int) = "editor-step-$position-text"
    fun stepRemove(position: Int) = "editor-step-$position-remove"
    fun stepMenu(position: Int) = "editor-step-$position-menu"
    fun stepHandle(position: Int) = "editor-step-$position-handle"
    fun stepHandleIcon(position: Int) = "editor-step-$position-handle-icon"
    fun stepAction(position: Int, action: RowAction) = "editor-step-$position-${action.tag}"
    fun stepInsert(position: Int) = "editor-step-$position-insert-ingredient"
    /** The note offering references under the first step as it is begun (GUIDE-008). */
    fun stepReferencesOffered(position: Int) = "editor-step-$position-references-offered"
    fun stepPhoto(position: Int) = "editor-step-$position-photo"
    fun stepPhotoTake(position: Int) = "editor-step-$position-photo-take"
    fun stepPhotoChoose(position: Int) = "editor-step-$position-photo-choose"
    fun stepPhotoRemove(position: Int) = "editor-step-$position-photo-remove"
    fun stepPhotoImage(position: Int) = "editor-step-$position-photo-image"
    fun stepPhotoWorking(position: Int) = "editor-step-$position-photo-working"
    fun stepClipRecord(position: Int) = "editor-step-$position-clip-record"
    fun stepClipChoose(position: Int) = "editor-step-$position-clip-choose"
    fun stepClipImage(position: Int) = "editor-step-$position-clip-image"
    fun stepClipMark(position: Int) = "editor-step-$position-clip-mark"
    /** The round control that plays and pauses a step's clip in place. */
    fun stepClipControl(position: Int) = "editor-step-$position-clip-control"
    fun stepClipConverting(position: Int) = "editor-step-$position-clip-converting"
    fun stepClipCancel(position: Int) = "editor-step-$position-clip-cancel"
    fun stepOffer(position: Int, ingredientPosition: Int) = "editor-step-$position-offer-$ingredientPosition"
    fun stepOfferNone(position: Int) = "editor-step-$position-offer-none"
    fun timerAttach(position: Int) = "editor-step-$position-timer"
    fun timerRemove(position: Int) = "editor-step-$position-timer-remove"
    fun timerStatement(position: Int) = "editor-step-$position-timer-statement"
    fun timerHours(position: Int) = "editor-step-$position-timer-hours"
    fun timerMinutes(position: Int) = "editor-step-$position-timer-minutes"
    fun timerName(position: Int) = "editor-step-$position-timer-name"
    fun timerNameFollows(position: Int) = "editor-step-$position-timer-name-follows"
    /** What a step's unnamed timer will announce itself by, shown once the step is left (GUIDE-006). */
    fun timerNotification(position: Int) = "editor-step-$position-timer-notification"
    fun timerNotificationText(position: Int) = "editor-step-$position-timer-notification-text"
    fun timerNotificationFollows(position: Int) = "editor-step-$position-timer-notification-follows"
    fun timerNotificationName(position: Int) = "editor-step-$position-timer-notification-name"
    fun timerDuration(position: Int) = "editor-step-$position-timer-duration"
    /** The tooltip a block's action tagged [action] shows on a long press. */
    fun tooltip(action: String) = "$action-tooltip"
    const val DESCRIPTION = "editor-description"
    const val DESCRIPTION_TEXT = "editor-description-text"
    /** A statement at the bottom of the editor that goes by itself. */
    const val STATEMENT = "editor-statement"
    fun stepOfferPart(position: Int, ingredientPosition: Int) = "editor-step-$position-part-$ingredientPosition"
    const val PART_DIALOG = "editor-part"
    const val PART_FIELD = "editor-part-field"
    const val PART_PROBLEM = "editor-part-problem"
    const val PART_INSERT = "editor-part-insert"
    const val PART_CANCEL = "editor-part-cancel"
    fun stepPartWarning(position: Int) = "editor-step-$position-part-warning"
}
