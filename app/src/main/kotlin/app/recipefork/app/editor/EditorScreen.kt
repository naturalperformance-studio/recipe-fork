package app.recipefork.app.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.IconButton
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
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.dictionary.label
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.card.ingredientLine
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.SnackbarAction
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipePart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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
    animationStore = { context, _ -> container.animationStore(context) },
)

/**
 * The editor: one scrolling screen in the order of FLOW-024 — the title with the declared language beside it and the
 * cover under it (FLOW-026), the tags, the description, the serving count, the equipment, the ingredients and the steps.
 *
 * [onClose] leaves the editor: the bar's back and "Done" call it once every change is in the book. What is entered is
 * written as it is typed, so leaving holds nothing back: the system's back, and its predictive gesture, are the
 * navigation host's, which leaves the editor for the destination beneath it — unless the book refused the last write,
 * when the system's back goes as the bar's does.
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
    val equipmentKeys = rememberSaveable(recipeId, saver = RowKeys.Saver) { RowKeys() }
    // Not saved: what the editor holds for this session alone — the references offered once (GUIDE-008) among it —
    // starts again after the activity is recreated or the process restored, which is intended, nothing of a hint
    // being kept (GUIDE-005).
    val editor = remember(recipeId) { RecipeEditor(recipes, recipeId, environment.clock, saving, stepKeys, ingredientKeys, groupKeys, equipmentKeys) }
    // Both reads resume on the main thread whatever the effect's own dispatcher is, so the screen is never
    // touched from a database thread.
    LaunchedEffect(editor) { withContext(Dispatchers.Main.immediate) { editor.load() } }
    var dictionary by remember { mutableStateOf<List<TagEntity>?>(null) }
    LaunchedEffect(environment) { withContext(Dispatchers.Main.immediate) { dictionary = environment.tags.tags() } }
    // The equipment dictionary the items are named and offered from (DOM-009); null until it is read. One that cannot
    // be read offers nothing, and an item naming an entry then reads as its qualifier alone.
    var equipmentEntries by remember { mutableStateOf<List<EquipmentEntryEntity>?>(null) }
    LaunchedEffect(environment) {
        val read = try {
            environment.equipment.equipment()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        withContext(Dispatchers.Main.immediate) { equipmentEntries = read }
    }
    val equipmentLanguage = numberLanguage(LocalConfiguration.current)
    // An item naming an entry is measured against its limit by its label and qualifier, as it reads.
    LaunchedEffect(editor, equipmentEntries, equipmentLanguage) {
        val byId = equipmentEntries?.associateBy { it.id } ?: return@LaunchedEffect
        editor.knowEquipmentLabels { id -> byId[id]?.label(equipmentLanguage) }
    }
    val recipe by editor.recipe.collectAsState()
    val handleRight by environment.dragHandleSide.right.collectAsState()
    val scrolled = rememberScrollState()
    val scroll = remember(scrolled) { EditorScroll(scrolled) }
    val context = LocalContext.current
    val store = remember(context, environment) { environment.photoStore(context.applicationContext.filesDir) }
    val clips = remember(context, environment) { environment.clipStore(context.applicationContext) }
    val animated = remember(context, environment) { environment.animatedImages(context.applicationContext) }
    val animations = remember(context, environment, animated) { environment.animationStore(context.applicationContext, animated) }
    remember(mediaWork, editor) { mediaWork.show(editor) }
    val activity = LocalActivity.current
    DisposableEffect(mediaWork) {
        onDispose { if (activity?.isChangingConfigurations != true) mediaWork.left() }
    }
    val host = remember { SnackbarHostState() }
    val statementScope = rememberCoroutineScope()
    val statements = remember(host, statementScope) { EditorStatements(host, statementScope) }
    PartsMadeWholeStatement(editor, statements)
    RemovalStatement(editor, statements, equipmentLanguage) { id -> equipmentEntries?.firstOrNull { it.id == id }?.label(equipmentLanguage) }
    val focus = LocalFocusManager.current
    val notSaved = stringResource(R.string.editor_not_saved)
    val retry = stringResource(R.string.editor_retry)
    // One close at a time: a second tap while the first waits for the book closes nothing more.
    var closing by remember { mutableStateOf(false) }
    // The statement that the book refused what is written, while it shows.
    var refusal by remember { mutableStateOf<Job?>(null) }
    // Closing — by "Done", by back, or by Retry: leaving the field is what a field's marks wait for (FLOW-028); once
    // every change is in the book — a refused write tried once more — a removal still offering Undo becomes final, as
    // on leaving, and the editor closes. Where the book still refuses it, the editor stays open and says so, with Retry.
    lateinit var close: () -> Unit
    close = {
        if (!closing) {
            closing = true
            focus.clearFocus()
            statementScope.launch {
                if (editor.written()) {
                    editor.removal.value?.let(editor::endRemoval)
                    onClose()
                } else {
                    closing = false
                    refusal = statements.offer(notSaved, retry, onAction = { close() }, onGone = {})
                }
            }
        }
    }
    // Back closes as "Done" does; a second back while the refusal shows leaves anyway, what the book refused lost.
    val back = { if (refusal?.isActive == true) onClose() else close() }
    // The system's back is the navigation host's, which leaves at once, as nothing is held back — unless the book
    // refused the last write: then it goes as the bar's back.
    val refused by editor.refused.collectAsState()
    BackHandler(enabled = refused) { back() }

    Box(modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().testTag(EditorTags.SCREEN)) {
            // The top bar: back and the recipe's title, and on its right what a fork takes (FLOW-030) and the checklist,
            // there whatever the recipe holds and whatever its state (GUIDE-003), then "Done" while the recipe can be
            // changed.
            EditorBar(
                title = recipe?.title,
                done = recipe?.state?.editable == true,
                checklist = {
                    ForkNoteAction()
                    ChecklistControl(environment.mode)
                },
                onBack = back,
                onDone = close,
            )
            // The form waits for the equipment dictionary as well as the recipe, so an item naming an entry never shows
            // blank before its label is known.
            val equipmentDictionary = equipmentEntries ?: return@Column
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
                    // The steps' Add ends the form: it can scroll clear of a statement shown at the bottom, an Undo
                    // among them.
                    .padding(bottom = STATEMENT_ROOM)
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
                    // The keyboard's Next goes from the title to the serving count, and from there through the
                    // equipment into the ingredient list.
                    val servings = remember { FocusRequester() }
                    val rows = remember { RowFocus() }
                    val headings = remember { RowFocus() }
                    val equipment = remember { RowFocus() }
                    val firstItem = shown.listItems.firstOrNull()
                    val toIngredients = firstItem?.let { { focusOn(it, rows, headings) } }
                    TitleAndLanguage(shown, editor) { servings.requestFocus() }
                    CoverSection(shown, editor, store, recipes.bookName(), saving)
                    Tags(shown, editor, dictionary)
                    // The description stands with the title it tells about, before the serving count; over several
                    // lines it keeps the keyboard's new-line key, so the title's Next passes over it as over the tags.
                    DescriptionField(shown, editor)
                    ServingCount(shown, editor, servings, if (shown.equipment.isNotEmpty()) ({ equipment.ask(0) }) else toIngredients)
                    Equipment(shown, editor, equipment, toIngredients, equipmentDictionary, equipmentLanguage)
                    CompositionLocalProvider(LocalDragColumnOnRight provides handleRight) {
                        Ingredients(shown, editor, environment.ingredients, scroll, environment.mode, rows, headings)
                        // The steps are the form's last part: leaving them for any other part moves the author on from
                        // them (FLOW-028).
                        Box(Modifier.fillMaxWidth().then(leaving { editor.leave(RecipePart.Steps) })) {
                            Steps(shown, editor, environment.ingredients, scroll, store, recipes.bookName(), saving, clips, mediaWork, animated, animations)
                        }
                    }
                }
            }
        }
        EditorStatementHost(host, Modifier.align(Alignment.BottomCenter))
    }
}

/** The room the form leaves at its end for a statement of one or two lines, so that it covers no part of the recipe. */
private val STATEMENT_ROOM = 72.dp

/**
 * Where the editor's statements show: at the bottom, above the keyboard where it is open, each wrapped to its whole
 * text, read by the screen reader as it comes (the host's live region), gone by itself or swiped away either way.
 */
@Composable
internal fun EditorStatementHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(host, modifier.imePadding()) { data ->
        val swipe = rememberSwipeToDismissBoxState()
        SwipeToDismissBox(swipe, backgroundContent = {}, onDismiss = { data.dismiss() }) {
            val label = data.visuals.actionLabel
            Snackbar(
                modifier = Modifier.testTag(EditorTags.STATEMENT),
                action = label?.let { { SnackbarAction(it, data::performAction) } },
            ) { Text(data.visuals.message) }
        }
    }
}

/**
 * What the editor says at its bottom, above the keyboard where it is open: one statement at a time, each going by
 * itself after a while or swiped away; a new one replaces the one shown. The screen reader reads each as it comes.
 *
 * A sheet standing in front of the editor ([cover]) would hide a statement, so none shows while one does: a statement
 * said then waits until the sheet goes, and one showing as a sheet rises is taken away and shown afresh once it goes —
 * an Undo is never let run out unseen.
 */
internal class EditorStatements(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    private var shown: Job? = null

    /** How many sheets stand in front of the editor now. */
    private var covers by mutableIntStateOf(0)

    /** A sheet now stands in front of the editor, until [uncover]. */
    fun cover() {
        covers++
    }

    fun uncover() {
        covers--
    }

    fun say(text: String, duration: SnackbarDuration = SnackbarDuration.Long) {
        shown?.cancel()
        shown = scope.launch { show(text, null, duration) }
    }

    /**
     * Says [text] with the action [action]: [onAction] once the author takes it, [onGone] once the statement goes
     * without it — by itself, swiped away, replaced by another, or the editor closed. Returns what withdraws it.
     */
    fun offer(text: String, action: String, onAction: () -> Unit, onGone: () -> Unit): Job {
        shown?.cancel()
        return scope.launch {
            val result = try {
                show(text, action, SnackbarDuration.Long)
            } catch (gone: CancellationException) {
                onGone()
                throw gone
            }
            if (result == SnackbarResult.ActionPerformed) onAction() else onGone()
        }.also { shown = it }
    }

    /** Shows the statement once no sheet covers the editor, and again afresh each time one rises while it shows. */
    private suspend fun show(text: String, action: String?, duration: SnackbarDuration): SnackbarResult {
        while (true) {
            snapshotFlow { covers }.first { it == 0 }
            var result: SnackbarResult? = null
            coroutineScope {
                val showing = launch { result = host.showSnackbar(text, actionLabel = action, duration = duration) }
                val covered = launch {
                    snapshotFlow { covers }.first { it > 0 }
                    showing.cancel()
                }
                showing.join()
                covered.cancel()
            }
            result?.let { return it }
        }
    }
}

/**
 * Stands for a sheet in front of the editor while it is composed: the editor's statements wait until it goes
 * ([EditorStatements.cover]).
 */
@Composable
internal fun CoversStatements() {
    val statements = LocalEditorStatements.current ?: return
    DisposableEffect(statements) {
        statements.cover()
        onDispose { statements.uncover() }
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
private fun RemovalStatement(editor: RecipeEditor, statements: EditorStatements, language: InterfaceLanguage, equipmentLabel: (Long) -> String?) {
    val removal by editor.removal.collectAsState()
    val text = removal?.let { removalText(it, editor.knownEntries, language, equipmentLabel) }
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
 * What the statement of [removal] says: it names what went, as the recipe held it just before — an ingredient by its
 * name as its line reads it in [language] ([entries] naming a dictionary row), a heading by its words, an item of
 * equipment by its name ([equipmentLabel] giving an entry's label) — or says what kind of thing it was where it had no
 * name; a step, which has none, by its number, and a timer by its step's.
 */
@Composable
private fun removalText(
    removal: Removal,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    equipmentLabel: (Long) -> String?,
): String {
    val before = removal.before
    fun String?.named(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    return when (removal.kind) {
        Removal.Kind.STEP -> stringResource(R.string.editor_step_removed, removal.position + 1)
        Removal.Kind.TIMER -> removal.number?.let { stringResource(R.string.editor_timer_removed_numbered, removal.position + 1, it) }
            ?: stringResource(R.string.editor_timer_removed, removal.position + 1)
        Removal.Kind.INGREDIENT -> before.ingredients.firstOrNull { it.position == removal.position }
            ?.let { ingredientLine(it, entries, language).name.named() }
            ?.let { stringResource(R.string.editor_ingredient_removed, it) }
            ?: stringResource(R.string.editor_ingredient_removed_unnamed)
        Removal.Kind.GROUP -> before.ingredientGroups.getOrNull(removal.position)?.name.named()
            ?.let { stringResource(R.string.editor_group_removed, it) }
            ?: stringResource(R.string.editor_group_removed_unnamed)
        Removal.Kind.EQUIPMENT -> before.equipment.getOrNull(removal.position)
            ?.let { item -> item.shown(item.dictionaryEntry?.let(equipmentLabel)).named() }
            ?.let { stringResource(R.string.editor_equipment_removed, it) }
            ?: stringResource(R.string.editor_equipment_removed_unnamed)
    }
}

/**
 * The editor's top app bar, as the book's and the card's: back at its start, announced as showing the recipe, then the
 * recipe's [title] on one line, cut short where it is too long, or "New recipe" while it has none (nothing while the
 * recipe is read); at its end [checklist] and, where [done], "Done", which saves the recipe and closes the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorBar(title: String?, done: Boolean, checklist: @Composable () -> Unit, onBack: () -> Unit, onDone: () -> Unit) {
    TopAppBar(
        title = {
            if (title != null) {
                Text(
                    title.ifBlank { stringResource(R.string.editor_bar_new_recipe) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(EditorTags.BAR_TITLE),
                )
            }
        },
        modifier = Modifier.testTag(EditorTags.BAR),
        navigationIcon = {
            val action = stringResource(R.string.editor_back_action)
            // Laid out at 48 dp, not the 40 dp the button draws by default (A11Y-003); its click announced as what it
            // does (A11Y-005).
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).semantics { onClick(label = action, action = null) }.testTag(EditorTags.BACK),
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.editor_back))
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
        val label = stringResource(R.string.editor_title)
        val measurer = rememberTextMeasurer()
        val resting = MaterialTheme.typography.bodyLarge
        val density = LocalDensity.current
        // What the title needs across at least, in the field's text style: its label on one line as it rests in the
        // empty field, and room for about ten characters of the title — so that "Plum cake" is not "Plum c".
        val need = remember(label, resting, density) {
            with(density) {
                listOf(label, TITLE_SAMPLE).maxOf { measurer.measure(it, resting, softWrap = false, maxLines = 1).size.width }.toDp() + LABEL_ROOM
            }
        }
        TitleBesideLanguage(
            need = need,
            title = {
                if (editable) {
                    OutlinedTextField(
                        value = recipe.title,
                        onValueChange = { editor.setTitle(it) },
                        label = { Text(label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { onNext() }),
                        modifier = Modifier.fillMaxWidth().then(leaving { editor.leave(RecipePart.Title) }).testTag(EditorTags.TITLE),
                    )
                } else {
                    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.TITLE)) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        Text(recipe.title.ifBlank { stringResource(R.string.common_untitled_recipe) }, style = MaterialTheme.typography.titleLarge)
                    }
                }
            },
            // The declared language sits beside the title (FLOW-024): changing it is one action (L10N-004).
            language = { Language(recipe, editor) },
        )
        // Under the title and the language, across the width, what either still needs (FLOW-028).
        BoundMark(editor, RecipePart.Title, EditorTags.TITLE_MARK, gap = 4.dp)
        BoundMark(editor, RecipePart.Language, EditorTags.LANGUAGE_MARK, gap = 4.dp)
    }
}

/** About ten characters of a title, as wide as ten digits in the field's style: what a title needs to be read. */
private const val TITLE_SAMPLE = "0000000000"

/**
 * The [title] with the [language] beside it, 8 dp apart, both from the top — where the title keeps at least half the
 * width and what it [need]s; else the language goes under the title, at its start, 4 dp below it, and the title takes
 * the whole width.
 */
@Composable
private fun TitleBesideLanguage(need: Dp, title: @Composable () -> Unit, language: @Composable () -> Unit) {
    Layout(contents = listOf(title, language), modifier = Modifier.fillMaxWidth().testTag(EditorTags.TITLE_ROW)) { (titles, languages), constraints ->
        val width = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val shown = languages.single().measure(Constraints(maxWidth = width))
        val room = width - shown.width - gap
        if (room >= need.roundToPx() && room >= width / 2) {
            val field = titles.single().measure(Constraints.fixedWidth(room))
            layout(width, maxOf(field.height, shown.height)) {
                field.place(0, 0)
                shown.place(room + gap, 0)
            }
        } else {
            val field = titles.single().measure(Constraints.fixedWidth(width))
            val below = field.height + 4.dp.roundToPx()
            layout(width, below + shown.height) {
                field.place(0, 0)
                shown.place(0, below)
            }
        }
    }
}

/**
 * The description, under the title and the tags, before the serving count: one optional field over as many lines as it
 * takes, marked on leaving it when over its limit (FLOW-028); read-only where the recipe is frozen.
 */
@Composable
private fun DescriptionField(recipe: Recipe, editor: RecipeEditor) {
    if (!recipe.state.editable) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.DESCRIPTION)) {
            Text(stringResource(R.string.editor_description), style = MaterialTheme.typography.labelMedium)
            Text(
                recipe.description.ifEmpty { stringResource(R.string.editor_description_none) },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(EditorTags.DESCRIPTION_TEXT),
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = recipe.description,
            onValueChange = { editor.setDescription(it) },
            label = { Text(stringResource(R.string.editor_description_optional)) },
            minLines = 3,
            // Over several lines, the keyboard keeps its new-line key.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .then(leaving { editor.leave(RecipePart.Description) })
                .testTag(EditorTags.DESCRIPTION),
        )
        BoundMark(editor, RecipePart.Description, EditorTags.DESCRIPTION_MARK, gap = 4.dp)
    }
}

/**
 * Calls [onTap] on every touch that lifts without having moved past the touch slop, before whatever it touched
 * handles it, and consumes nothing: a scroll or a drag is not a tap.
 */
internal fun Modifier.onEveryTap(onTap: () -> Unit): Modifier = pointerInput(Unit) {
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
    /** The recipe's title in the top bar, or "New recipe" while it has none. */
    const val BAR_TITLE = "editor-bar-title"
    /** The top bar's "Done": once the changes are in the book, the editor closes. */
    const val DONE = "editor-done"
    const val FORM = "editor-form"
    const val FROZEN = "editor-frozen"
    const val TITLE = "editor-title"
    /** The title with the language beside it, or under it where the line is too narrow for both. */
    const val TITLE_ROW = "editor-title-row"
    /** The marks of FLOW-028: a part left below the minimum or over a limit. */
    const val TITLE_MARK = "editor-title-mark"
    const val LANGUAGE_MARK = "editor-language-mark"
    const val SERVINGS_MARK = "editor-servings-mark"
    const val INGREDIENTS_MARK = "editor-ingredients-mark"
    const val STEPS_MARK = "editor-steps-mark"
    const val DESCRIPTION_MARK = "editor-description-mark"
    fun stepMark(position: Int) = "editor-step-$position-mark"
    fun timerNameMark(position: Int, index: Int = 0) = "${timer(position, index)}-name-mark"
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
    /** The equipment, its "Add", and the statement an "Add" past the limit makes (GUIDE-007). */
    const val EQUIPMENT = "editor-equipment"
    const val EQUIPMENT_ADD = "editor-equipment-add"
    const val EQUIPMENT_STATEMENT = "editor-equipment-statement"
    /** The item at [index] of the equipment, and its parts. */
    fun equipment(index: Int) = "editor-equipment-$index"
    fun equipmentName(index: Int) = "editor-equipment-$index-name"
    fun equipmentMark(index: Int) = "editor-equipment-$index-mark"
    fun equipmentRemove(index: Int) = "editor-equipment-$index-remove"
    fun equipmentMenu(index: Int) = "editor-equipment-$index-menu"
    fun equipmentAction(index: Int, action: RowAction) = "editor-equipment-$index-${action.tag}"
    const val INGREDIENTS = "editor-ingredients"
    const val INGREDIENT_ADD = "editor-ingredient-add"
    fun ingredient(position: Int) = "editor-ingredient-$position"
    fun ingredientName(position: Int) = "editor-ingredient-$position-name"
    fun ingredientQuantity(position: Int) = "editor-ingredient-$position-quantity"
    fun ingredientUnit(position: Int) = "editor-ingredient-$position-unit"
    /** The removal among the actions of the row's sheet. */
    fun ingredientRemove(position: Int) = "editor-ingredient-$position-remove"
    fun ingredientHandle(position: Int) = "editor-ingredient-$position-handle"
    fun ingredientHandleIcon(position: Int) = "editor-ingredient-$position-handle-icon"
    /** An action of the row's sheet: moving it, putting a row or a heading beside it. */
    fun ingredientAction(position: Int, action: RowAction) = "editor-ingredient-$position-${action.tag}"
    fun ingredientError(position: Int) = "editor-ingredient-$position-error"
    fun ingredientWarning(position: Int) = "editor-ingredient-$position-warning"
    /** "Add group" under the ingredient list, and the parts of the heading at [index] of the groups (DOM-022). */
    const val GROUP_ADD = "editor-group-add"
    fun group(index: Int) = "editor-group-$index"
    /** The heading's name field, in its sheet. */
    fun groupName(index: Int) = "editor-group-$index-name"
    fun groupMark(index: Int) = "editor-group-$index-mark"
    fun groupRemove(index: Int) = "editor-group-$index-remove"
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
    /** The control that adds a timer to the step at [position], and the statement it makes on a sixth (GUIDE-007). */
    fun timerAttach(position: Int) = "editor-step-$position-timer"
    fun timerStatement(position: Int) = "editor-step-$position-timer-statement"
    /**
     * The parts of the timer at [index] of those the step at [position] shows (DOM-024). The first timer's tags read as
     * they did when a step carried one: `editor-step-0-timer-hours`; the second's `editor-step-0-timer-1-hours`.
     */
    fun timer(position: Int, index: Int) = "editor-step-$position-timer${timerPlace(index)}"
    private fun timerPlace(index: Int) = if (index == 0) "" else "-$index"
    fun timerRemove(position: Int, index: Int = 0) = "${timer(position, index)}-remove"
    fun timerHours(position: Int, index: Int = 0) = "${timer(position, index)}-hours"
    fun timerMinutes(position: Int, index: Int = 0) = "${timer(position, index)}-minutes"
    fun timerName(position: Int, index: Int = 0) = "${timer(position, index)}-name"
    fun timerNameFollows(position: Int, index: Int = 0) = "${timer(position, index)}-name-follows"
    /** What a step's unnamed timer will announce itself by, shown once the step is left (GUIDE-006). */
    fun timerNotification(position: Int, index: Int = 0) = "${timer(position, index)}-notification"
    fun timerNotificationText(position: Int, index: Int = 0) = "${timer(position, index)}-notification-text"
    fun timerNotificationFollows(position: Int, index: Int = 0) = "${timer(position, index)}-notification-follows"
    fun timerNotificationName(position: Int, index: Int = 0) = "${timer(position, index)}-notification-name"
    fun timerDuration(position: Int, index: Int = 0) = "${timer(position, index)}-duration"
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
