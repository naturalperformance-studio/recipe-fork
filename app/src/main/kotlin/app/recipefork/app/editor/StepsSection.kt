package app.recipefork.app.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.recipefork.app.R
import app.recipefork.app.card.CardReferences
import app.recipefork.app.card.StepText
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.book.BookName
import app.recipefork.app.media.AnimatedImages
import app.recipefork.app.media.ClipStore
import app.recipefork.app.media.decodeForDisplay
import app.recipefork.app.media.stepMedia
import app.recipefork.app.timer.durationText
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.IngredientSource
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientShares
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepRun
import app.recipefork.core.recipe.StepText
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.recipe.Timer
import app.recipefork.core.timer.TimerName

/** The space between two steps, or two ingredient rows. */
internal val ROW_SPACING = 12.dp

/**
 * The steps, after the ingredients (FLOW-024): each reads as its text, as the card reads it, and opens in a sheet of its
 * fields and actions on a tap ([StepSheet]); steps are appended at the end and deleted. They accept input whatever else
 * the recipe holds or lacks (FLOW-024 (2)), and no text is ever cut short.
 *
 * Which step's sheet is open is kept by the step's key, across the activity being recreated: the sheet follows its step
 * where it moves, and goes when the step is deleted. A step added — by "Add step", or above or below another from its
 * sheet — opens in its sheet with its text focused; closed while it holds nothing (no text, note, timer or picture), it
 * is dropped again, with no Undo, as it was never written.
 */
@Composable
internal fun Steps(
    recipe: Recipe,
    editor: RecipeEditor,
    source: IngredientSource,
    scroll: EditorScroll,
    store: PhotoStore,
    book: BookName,
    saving: CoroutineScope,
    clips: ClipStore? = null,
    work: StepMediaWork? = null,
    animated: AnimatedImages? = null,
    animations: ClipStore? = null,
) {
    val photos = rememberStepPhotos(store, book, saving, clips, work, animated, animations)
    // What each step's media work tells is said here, so that a step deleted while its clip converts still says it.
    work?.all?.forEach { (stepKey, attaching) -> key(stepKey) { SaysPhotoNotices(attaching) } }
    val editable = recipe.state.editable
    val steps = recipe.steps.sortedBy { it.position }
    val language = numberLanguage(LocalConfiguration.current)
    var dictionary by remember(source) { mutableStateOf<List<IngredientEntryEntity>?>(null) }
    LaunchedEffect(source) { dictionary = runCatching { source.ingredients() }.getOrDefault(emptyList()) }
    // A drop is the same single move as the sheet's (FLOW-025, A11Y-006).
    val drag = rememberRowDrag(scroll, ROW_SPACING) { from, to -> editor.moveStep(from, to) }
    val focus = remember { RowFocus() }
    val motion = rememberRowMotion()
    motion.update(steps.indices.map(editor::stepKey))
    // The key of the step whose sheet is open, and of the step just added, which goes again if closed empty.
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    var fresh by rememberSaveable { mutableStateOf<Long?>(null) }
    val asked = focus.asked
    LaunchedEffect(asked, editable) {
        if (asked != null && editable && asked < (editor.recipe.value?.steps?.size ?: 0)) open = editor.stepKey(asked)
    }
    val shownKey = open
    if (shownKey != null && (editor.stepPosition(shownKey) == null || !editable)) {
        // The step was deleted, or the recipe can no longer be changed: the sheet goes.
        LaunchedEffect(shownKey) { open = null }
    }
    // The sheet of the step [key] closes; a step just added that holds nothing goes again, as it was never written — but
    // not while a photograph or a clip is being [prepared] for it, which it then holds. The author has left the steps'
    // fields, which marks the steps (FLOW-028), as leaving them in the editor does.
    fun closed(key: Long, prepared: Boolean) {
        if (open == key) open = null
        if (fresh == key) {
            fresh = null
            if (!prepared) editor.stepPosition(key)?.let(editor::dropStep)
        }
        editor.leave(RecipePart.Steps)
    }
    // A step added at [position] opens in its sheet, its text focused.
    fun opened(position: Int) {
        val key = editor.stepKey(position)
        fresh = key
        open = key
        focus.ask(position)
    }
    RowsColumn(
        ROW_SPACING,
        Modifier.fillMaxWidth().zIndex(if (drag.dragged != null) 1f else 0f).testTag(EditorTags.STEPS),
    ) {
        Text(
            stringResource(R.string.editor_steps),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        // Under the heading: the steps left with none, or more than the limit (FLOW-028).
        BoundMark(editor, RecipePart.Steps, EditorTags.STEPS_MARK, gap = ROW_SPACING)
        if (!editable && steps.isEmpty()) {
            Text(
                stringResource(R.string.editor_steps_none),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(EditorTags.STEPS_NONE),
            )
        }
        val stating by editor.statement.collectAsState()
        steps.forEachIndexed { position, step ->
            // What the screen holds for a step — a timer being attached, a note being typed — stays with it when steps
            // before it are inserted, moved or deleted.
            motion.LeavingAt(position)
            val key = editor.stepKey(position)
            key(key) {
                val media = recipe.stepMedia(position)
                if (editable) {
                    val held = rememberStepHeld(position, step, editor, photos)
                    StepRow(position, steps.size, step, media, recipe.ingredients, language, editor, drag, photos, held.photo.attaching.working.value || shownKey == key, motion) {
                        // The author has moved on from the ingredients to the steps (FLOW-028 (1)).
                        editor.leave(RecipePart.Ingredients)
                        open = key
                    }
                    // The sheet is a window of its own: it takes no place among the steps.
                    if (shownKey == key) {
                        val host = StepSheetHost(
                            insert = { above ->
                                val at = editor.stepPosition(key)
                                if (at != null) {
                                    val inserted = if (above) at else at + 1
                                    if (editor.insertStep(inserted)) {
                                        val added = editor.stepKey(inserted)
                                        closed(key, held.photo.attaching.working.value)
                                        editor.stepPosition(added)?.let(::opened)
                                    }
                                }
                            },
                            close = { closed(key, held.photo.attaching.working.value) },
                        )
                        StepSheet(position, steps.size, step, media, recipe.ingredients, dictionary, language, editor, stating == position, photos, held, focus, host)
                    }
                } else {
                    ReadOnlyStep(position, step, media, recipe.ingredients, language, photos)
                }
            }
        }
        motion.LeavingAt(steps.size, last = true)
        if (editable) {
            EditorButton(
                text = stringResource(R.string.editor_step_add),
                action = stringResource(R.string.editor_step_add_action),
                onClick = {
                    val added = editor.recipe.value?.steps?.size
                    // The step added opens in its sheet, its text focused, with the keyboard up.
                    if (editor.addStep() && added != null) opened(added)
                    // The author has moved on from the ingredients to the steps (FLOW-028 (1)).
                    editor.leave(RecipePart.Ingredients)
                },
                modifier = Modifier.testTag(EditorTags.STEP_ADD),
                weight = ButtonWeight.TONAL,
            )
        }
    }
}

/**
 * One step as its row reads ([StepLine]): one bounded block per step, so where one step ends and the next begins is
 * plain — dragged, it moves whole, by the column down its left edge (or its right, for a left hand). The rest of the
 * block is one target, at least 48 dp tall, whose tap ([onOpen]) opens the step's sheet; nothing of the step is edited
 * on the row.
 */
@Composable
private fun StepRow(
    position: Int,
    count: Int,
    step: Step,
    media: Media?,
    ingredients: List<Ingredient>,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    drag: RowDrag,
    photos: StepPhotos,
    waiting: Boolean,
    motion: RowMotion,
    onOpen: () -> Unit,
) {
    val edit = stringResource(R.string.editor_step_edit)
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .then(motion.row(editor.stepKey(position)))
            .then(drag.row(position, MaterialTheme.colorScheme.surface, CardDefaults.outlinedShape))
            .testTag(EditorTags.step(position)),
    ) {
        DragColumnBlock(drag, position, count, EditorTags.stepHandle(position), EditorTags.stepHandleIcon(position)) {
            StepLine(
                position, step, media, ingredients, language, editor, photos, waiting,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = edit, role = Role.Button, onClick = onOpen)
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                    .testTag(StepSheetTags.line(position)),
            )
        }
    }
}

/**
 * A step as the frozen recipe shows it: as an editable step's row reads, with no drag column, and nothing that opens.
 */
@Composable
private fun ReadOnlyStep(position: Int, step: Step, media: Media?, ingredients: List<Ingredient>, language: InterfaceLanguage, photos: StepPhotos) {
    OutlinedCard(Modifier.fillMaxWidth().testTag(EditorTags.step(position))) {
        StepLine(
            position, step, media, ingredients, language, editor = null, photos, waiting = false,
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 12.dp).testTag(StepSheetTags.line(position)),
        )
    }
}

/**
 * What a step reads as in the editor, as the card reads it: its number, then its text with its references at the
 * authored scale (FLOW-032 (5)) — the card's own [StepText] — or, with no text, "New step" muted; the author's note
 * under it, muted and smaller; a small picture of its photograph or clip beside them; and under them each timer as the
 * card's View writes it ("Simmer · 40 min"). Where the [editor] has marked a part of it (FLOW-028), or a part of an
 * ingredient is more than the ingredient holds, the row says so as its fields would.
 */
@Composable
private fun StepLine(
    position: Int,
    step: Step,
    media: Media?,
    ingredients: List<Ingredient>,
    language: InterfaceLanguage,
    editor: RecipeEditor?,
    photos: StepPhotos,
    waiting: Boolean,
    modifier: Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val references = remember(ingredients, language) { CardReferences.authored(ingredients, language) }
    val reading = ReferenceText.authored(ingredients, language)
    val name = stringResource(R.string.editor_step, position + 1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The number, read by the screen reader as the step's name.
            Text(
                stringResource(R.string.editor_step_number, position + 1),
                style = MaterialTheme.typography.bodyLarge,
                color = muted,
                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(name) },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (StepText.render(step, reading).isBlank()) {
                    Text(
                        stringResource(R.string.editor_step_new),
                        style = MaterialTheme.typography.bodyLarge,
                        color = muted,
                        modifier = Modifier.testTag(StepSheetTags.text(position)),
                    )
                } else {
                    StepText(step, references, style = MaterialTheme.typography.bodyLarge, tag = StepSheetTags.text(position))
                }
                step.note?.let {
                    // A frozen recipe's row says whose it is, as nothing else there does: "Author's note: …".
                    val note = if (editor == null) stringResource(R.string.editor_step_note_read_only, it) else it
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.testTag(StepSheetTags.note(position)))
                }
            }
            if (media != null) StepThumbnail(position, media, photos, waiting)
        }
        step.timers.indices.forEach { index -> StepTimerLine(position, step, index, reading) }
        if (editor != null) StepMarks(position, step, ingredients, language, editor)
    }
}

/** The timer at [index] of [step] as the card's View writes it, a clock before it: its name and its duration. */
@Composable
private fun StepTimerLine(position: Int, step: Step, index: Int, reading: app.recipefork.core.recipe.ReferenceQuantity) {
    val timer = step.timers.getOrNull(index) ?: return
    val name = TimerName.of(step, index, reading).orEmpty()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_step_timer), contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.card_view_timer, name, durationText(timer.duration)),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.weight(1f).testTag(StepSheetTags.timer(position, index)),
        )
    }
}

/**
 * What the step's fields would mark, on its row: its text, note and timers' names over their limits once left
 * (FLOW-028), and a part of an ingredient more than the ingredient holds.
 */
@Composable
private fun StepMarks(position: Int, step: Step, ingredients: List<Ingredient>, language: InterfaceLanguage, editor: RecipeEditor) {
    BoundMark(editor, RecipePart.StepText(position), StepSheetTags.textMark(position), gap = 0.dp)
    BoundMark(editor, RecipePart.StepNote(position), StepSheetTags.noteMark(position), gap = 0.dp)
    step.timers.indices.forEach { BoundMark(editor, RecipePart.TimerName(position, it), StepSheetTags.timerNameMark(position, it), gap = 0.dp) }
    val display = StepDisplay.of(step, ingredients, language)
    val oversized = display.references.mapNotNull { shown ->
        val share = shown.reference.share ?: return@mapNotNull null
        val row = ingredients.firstOrNull { it.position == shown.reference.ingredientPosition } ?: return@mapNotNull null
        if (IngredientShares.exceeds(share, row)) display.text.substring(shown.start, shown.end) to ReferenceText.authored(row, language) else null
    }
    if (oversized.isNotEmpty()) {
        Text(
            oversized.map { (part, whole) -> stringResource(R.string.editor_step_part_more_than_whole, part, whole) }.joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(StepSheetTags.partWarning(position)),
        )
    }
}

/**
 * A small picture of the step's photograph or of its clip's first frame, cropped to a square, named for the screen
 * reader as the step's photograph or video; a clip carries the clip's mark so that it is not taken for a photograph.
 */
@Composable
private fun StepThumbnail(position: Int, media: Media, photos: StepPhotos, waiting: Boolean) {
    val file = photos.store.file(photos.book, media.mediaId)
    val clip = media.kind == MediaKind.VIDEO
    val size = with(LocalDensity.current) { THUMBNAIL_SIZE.roundToPx() }
    // Read once the step's work on its picture is over, so that the file is read whole, and not while the step's sheet,
    // which shows the picture itself, covers the row.
    val image by produceState<ImageBitmap?>(null, file, clip, size, waiting) {
        if (!waiting) value = withContext(Dispatchers.IO) { if (clip) firstFrame(file) else decodeForDisplay(file, size) }
    }
    val said = stringResource(if (clip) R.string.media_step_clip_description else R.string.media_step_description, position + 1)
    Box(
        Modifier
            .size(THUMBNAIL_SIZE)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics {
                contentDescription = said
                role = Role.Image
            }
            .testTag(StepSheetTags.thumbnail(position)),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (clip) Icon(painterResource(R.drawable.ic_clip_mark), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}

/** How large a step's picture is on its row. */
private val THUMBNAIL_SIZE = 56.dp

/**
 * The author's own note on the step, beside the method, under the step's text in its sheet: labelled as the author's,
 * with the hint that it stays with them when someone else forks the recipe (FORK-007). What the author types stays in
 * the field as typed ([typedText]) — a space before the next word included — while it reads as the note the step holds,
 * trimmed, blank as none. Over [app.recipefork.core.recipe.RecipeBounds.MAX_STEP_NOTE] characters it is kept in full and
 * marked on leaving the field, as every limit is (FLOW-028).
 */
@Composable
internal fun StepNoteField(position: Int, step: Step, editor: RecipeEditor, typedText: MutableState<String?>) {
    var typed by typedText
    // The step as the editor holds it now — [step] at the latest; the text in the field stands only while it is what the
    // step holds.
    val note = (editor.recipe.value?.steps?.sortedBy { it.position }?.getOrNull(position) ?: step).note
    val shown = typed?.takeIf { Step.noteOf(it) == note } ?: note.orEmpty()
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        OutlinedTextField(
            value = shown,
            onValueChange = {
                typed = it
                editor.setStepNote(position, it)
            },
            label = { Text(stringResource(R.string.editor_step_note)) },
            supportingText = { Text(stringResource(R.string.editor_step_note_hint)) },
            // Over several lines, the keyboard keeps its new-line key.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) typed = null }
                .then(leaving { editor.leave(RecipePart.StepNote(position)) })
                .testTag(StepNoteTags.field(position)),
        )
        BoundMark(editor, RecipePart.StepNote(position), StepNoteTags.mark(position), gap = 4.dp, Modifier.padding(top = 4.dp))
    }
}

/** The test tags of a step's note in its sheet. */
object StepNoteTags {
    fun field(position: Int) = "editor-step-$position-note"
    fun mark(position: Int) = "editor-step-$position-note-mark"
}

/**
 * The step's timers (FLOW-027, DOM-024), in its sheet under its note; the sheet's timer control adds one each time it is
 * chosen, one compact row under another in the order they were added. Choosing it where the step would show more than
 * five creates nothing and says why (GUIDE-007): what it says shows here while [stating].
 */
@Composable
internal fun StepTimers(
    position: Int,
    step: Step,
    rows: List<TimerRows.Row>,
    timers: TimerRows,
    focus: RowFocus,
    ingredients: List<Ingredient>,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    stating: Boolean,
    left: Boolean,
) {
    if (stating) {
        Text(
            stringResource(R.string.editor_timer_limit),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(EditorTags.timerStatement(position)),
        )
    }
    rows.forEachIndexed { place, row ->
        // What the screen holds for a timer — what was typed, a name given — stays with it as others go.
        key(row.key) {
            val index = rows.take(place).count { it.held }
            val timer = if (row.held) step.timers.getOrNull(index) else null
            Column(Modifier.fillMaxWidth().padding(top = if (place > 0) 8.dp else 0.dp)) {
                StepTimer(position, place, row.key, rows.size, index, step, timer, timers, focus, ingredients, language, editor, left)
            }
        }
    }
}

/**
 * One timer of the step, at [place] among the [count] its block shows and at [index] of the step's timers: its duration in hours and whole minutes — the
 * only thing asked — and, once it holds one ([timer]), its own removal and the name it will announce itself by. A timer
 * just added holds no duration yet and is not stored until it does.
 */
@Composable
private fun StepTimer(
    position: Int,
    place: Int,
    key: Long,
    count: Int,
    index: Int,
    step: Step,
    timer: Timer?,
    timers: TimerRows,
    focus: RowFocus,
    ingredients: List<Ingredient>,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    left: Boolean,
) {
    var hours by rememberSaveable { mutableStateOf(timer?.duration?.inWholeHours?.toString().orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(timer?.duration?.let { (it.inWholeMinutes % 60).toString() }.orEmpty()) }
    // The name the author gave, kept while the duration is cleared and given back when it is entered again — by the
    // step's rows, so that it outlives the sheet and the activity being recreated.
    var given by remember { mutableStateOf(timers.name(key, timer?.label)) }
    fun give(name: String?) {
        given = name
        timers.give(key, name)
    }
    val name = remember { FocusRequester() }
    val hoursField = remember { FocusRequester() }
    // In the step's sheet, whose window may not have the focus yet.
    focus.TakenInSheet(place, hoursField)
    // Read as the rows stand when the author types, so the timer edited is this one, wherever the others went.
    fun hold(h: String, m: String) {
        val typedHours = h.toIntOrNull() ?: 0
        val typedMinutes = m.toIntOrNull() ?: 0
        val at = timers.index(place)
        if (timers.held(place)) {
            if (editor.setTimerDuration(position, at, typedHours, typedMinutes) && typedHours == 0 && typedMinutes == 0) {
                give(given)
                timers.hold(place, false)
            }
        } else if (editor.addTimer(position, at, typedHours, typedMinutes, given)) {
            timers.hold(place, true)
        }
    }
    // Several rows are told apart by their place in the block, top to bottom, whether or not each holds a duration —
    // its removal, its name and the notice of its removal alike; a step's only row reads as it always did.
    val number = (place + 1).takeIf { count > 1 }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Two digits each: the field cannot hold more than 99 h 59 min (FLOW-027 (14)), and no seconds are offered.
        OutlinedTextField(
            value = hours,
            onValueChange = { typed ->
                if (typed != hours && typed.length <= 2 && typed.all { it in '0'..'9' }) {
                    hours = typed
                    hold(typed, minutes)
                }
            },
            label = { Text(stringResource(R.string.editor_timer_hours)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f).focusRequester(hoursField).testTag(EditorTags.timerHours(position, place)),
        )
        OutlinedTextField(
            value = minutes,
            onValueChange = { typed ->
                if (typed != minutes && typed.length <= 2 && typed.all { it in '0'..'9' } && (typed.toIntOrNull() ?: 0) <= RecipeEditor.MAX_MINUTES) {
                    minutes = typed
                    hold(hours, typed)
                }
            },
            label = { Text(stringResource(R.string.editor_timer_minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f).testTag(EditorTags.timerMinutes(position, place)),
        )
        // Takes this timer off, with any name the author gave it; the step's other timers stay. Undo puts it back. One
        // that holds no duration yet is no timer: it is only taken off the screen.
        IconButton(
            onClick = {
                when {
                    !timers.held(place) -> timers.remove(place)
                    editor.removeTimer(position, timers.index(place), number) -> timers.remove(place)
                }
            },
            // Laid out at 48 dp, not the 40 dp the button draws by default (A11Y-003).
            modifier = Modifier.align(Alignment.CenterVertically).size(48.dp).testTag(EditorTags.timerRemove(position, place)),
        ) {
            Icon(
                painterResource(R.drawable.ic_remove_timer),
                contentDescription = if (number != null) {
                    stringResource(R.string.editor_timer_remove_numbered_action, position + 1, number)
                } else {
                    stringResource(R.string.editor_timer_remove_action, position + 1)
                },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (timer != null) {
        TimerNameField(position, place, index, number, StepText.runs(step, ReferenceText.authored(ingredients, language)), timer, editor, name) { give(it) }
        BoundMark(editor, RecipePart.TimerName(position, index), EditorTags.timerNameMark(position, place), gap = 0.dp)
        // Under the name, so that it neither moves the name field nor goes from above it once the timer is named.
        if (left) {
            cutNotification(step, index, ingredients, language)?.let { reading ->
                TimerNotification(position, place, number, reading) { name.requestFocus() }
            }
        }
    }
}

/**
 * What the notification of the timer at [index] of [step] will say at the authored count, where that timer is unnamed
 * and the notification is cut, or may be at a reader's larger count (GUIDE-006); null otherwise.
 */
internal fun cutNotification(step: Step, index: Int, ingredients: List<Ingredient>, language: InterfaceLanguage): TimerName.Reading? {
    val timer = step.timers.getOrNull(index) ?: return null
    if (timer.label != null) return null
    return TimerName.reading(StepText.runs(step, ReferenceText.authored(ingredients, language))).takeIf { it.mayBeCut }
}

/**
 * What a step's unnamed timer will announce itself by, shown once the author leaves the step (GUIDE-006): the
 * notification's text as it will read, that quantities in it follow the reader's count where one is shown, and the
 * offer to name the timer, which takes the author to its name field. It refuses nothing and asks no rewrite.
 */
@Composable
private fun TimerNotification(position: Int, place: Int, number: Int?, reading: TimerName.Reading, onName: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            // Appearing above the field the author went on to, it keeps that field where it is.
            .then(keepingTheFocusedFieldInPlace(0.dp))
            .padding(top = 8.dp)
            .testTag(EditorTags.timerNotification(position, place)),
    ) {
        // Read by the screen reader as it appears.
        Text(
            stringResource(R.string.editor_timer_notification, reading.name),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(EditorTags.timerNotificationText(position, place)),
        )
        if (reading.referenceShown) {
            Text(
                stringResource(R.string.editor_timer_notification_follows),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(EditorTags.timerNotificationFollows(position, place)),
            )
        }
        EditorButton(
            text = stringResource(R.string.editor_timer_notification_name),
            action = if (number != null) {
                stringResource(R.string.editor_timer_notification_name_numbered_action, position + 1, number)
            } else {
                stringResource(R.string.editor_timer_notification_name_action, position + 1)
            },
            onClick = onName,
            modifier = Modifier.testTag(EditorTags.timerNotificationName(position, place)),
        )
    }
}

/**
 * The name the timer will announce itself by (FLOW-027 (5–7)): with none given, the one derived from the step, shown
 * filled in and following the step's text; a correction is held as written, whatever the step says afterwards, and
 * however long (DOM-012 is not enforced while typing, only marked on leaving the field — FLOW-028); an emptied field
 * gives the name back to the step.
 */
@Composable
private fun TimerNameField(
    position: Int,
    place: Int,
    index: Int,
    number: Int?,
    step: List<StepRun>,
    timer: Timer,
    editor: RecipeEditor,
    requester: FocusRequester,
    onGiven: (String?) -> Unit,
) {
    // A reference reads here as the editor shows it (FLOW-016 (8), at the authored scale).
    val shown = timer.label ?: TimerName.derived(step)
    // While the author is in the field it shows what they typed, so that emptying it does not refill it at once.
    var focused by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val value = if (focused) typed else shown
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            // A name is one line, its spaces ordinary ones: a derived name holds its references together with no-break
            // spaces, which a name the author gives — seeded from it, or pasted — never keeps.
            val name = text.replace('\n', ' ').replace(StepText.NO_BREAK_SPACE, ' ')
            if (name == value.replace(StepText.NO_BREAK_SPACE, ' ')) return@OutlinedTextField
            typed = name
            onGiven(name.ifEmpty { null })
            editor.setTimerLabel(position, index, name)
        },
        label = { Text(if (number != null) stringResource(R.string.editor_timer_name_numbered, number) else stringResource(R.string.editor_timer_name)) },
        supportingText = if (timer.label == null) {
            { Text(stringResource(R.string.editor_timer_name_follows), modifier = Modifier.testTag(EditorTags.timerNameFollows(position, place))) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused && !focused) typed = shown.replace(StepText.NO_BREAK_SPACE, ' ')
                if (!it.isFocused && focused) editor.leaveStep(editor.stepKey(position), timer = index)
                focused = it.isFocused
            }
            .testTag(EditorTags.timerName(position, place)),
    )
}

/**
 * The timers a step's block shows (DOM-024), each under a key of its own so that what the screen holds for a timer —
 * what was typed, a name given — stays with it: the step's timers, in their order, and among them those the author added
 * that hold no duration yet, each where it was added. The rows that are [Row.held] stand for the step's timers in order.
 */
@Stable
internal class TimerRows private constructor(rows: List<Row>, private var next: Long) {
    /** One timer shown: [held] where the step carries it, not yet where it holds no duration. */
    data class Row(val key: Long, val held: Boolean)

    private val rows = mutableStateListOf<Row>().apply { addAll(rows) }

    /** The rows of a step carrying [held] timers, none added yet. */
    constructor(held: Int) : this(List(held) { Row(it.toLong(), held = true) }, held.toLong())

    /**
     * The rows shown beside a step carrying [held] timers. Where its timers changed other than through these rows — an
     * Undo put one back — they are the step's timers alone, each under a new key; [settle] then makes them the rows.
     */
    fun shown(held: Int): List<Row> = if (heldCount() == held) rows.toList() else List(held) { Row(next + it, held = true) }

    /** Makes the rows [shown] gave for [held] what they are. */
    fun settle(held: Int) {
        if (heldCount() == held) return
        val fresh = shown(held)
        next += held
        rows.clear()
        rows.addAll(fresh)
    }

    /** How many timers are shown that hold no duration yet. */
    val unheld: Int get() = rows.size - heldCount()

    /** Adds a timer that holds no duration yet after the others; returns its place. */
    fun open(): Int {
        rows += Row(next++, held = false)
        return rows.lastIndex
    }

    /** Where the timer at [place] stands among the step's timers — or would, once it holds a duration. */
    fun index(place: Int): Int = rows.take(place).count { it.held }

    fun held(place: Int): Boolean = rows.getOrNull(place)?.held == true

    /** The timer at [place] is now [held] by the step, or no longer. */
    fun hold(place: Int, held: Boolean) {
        rows[place] = rows[place].copy(held = held)
    }

    fun remove(place: Int) {
        rows.removeAt(place)
    }

    private fun heldCount() = rows.count { it.held }

    /** The names the author gave the rows, by key; none given is not here, one cleared is null. */
    private val names = mutableMapOf<Long, String?>()

    /** The name the author gave the row [key], or, where they gave none, [held]: the name its timer holds. */
    fun name(key: Long, held: String?): String? = if (key in names) names[key] else held

    fun give(key: Long, name: String?) {
        names[key] = name
    }

    companion object {
        /**
         * Saved as the next key to give, then each row's key, doubled, plus one where it is held; then the keys of the
         * names given, and the names.
         */
        val Saver: Saver<TimerRows, Any> = listSaver(
            save = {
                listOf(
                    longArrayOf(it.next, *it.rows.map { row -> row.key * 2 + if (row.held) 1 else 0 }.toLongArray()),
                    it.names.keys.toLongArray(),
                    ArrayList(it.names.values),
                )
            },
            restore = { saved ->
                val rows = saved[0] as LongArray
                TimerRows(rows.drop(1).map { Row(it / 2, it % 2 == 1L) }, rows.first()).apply {
                    (saved[1] as LongArray).zip((saved[2] as List<*>).map { it as String? }).forEach { (key, name) -> names[key] = name }
                }
            },
        )
    }
}
