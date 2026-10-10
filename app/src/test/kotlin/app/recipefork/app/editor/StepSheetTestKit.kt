package app.recipefork.app.editor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.text.TextLayoutResult
import app.recipefork.core.recipe.StepText

/** Whether a step's sheet is open. */
fun ComposeTestRule.stepSheetOpen(): Boolean = onAllNodes(hasTestTag(StepSheetTags.SHEET)).fetchSemanticsNodes().isNotEmpty()

/**
 * Opens the sheet of the step at [position] as the author does, by its row's click — closing by its Done any sheet open
 * first, a step's or an ingredient's — and waits until its text field is there. Nothing is done where it is open.
 */
fun ComposeTestRule.openStep(position: Int) {
    if (onAllNodes(hasTestTag(EditorTags.stepText(position))).fetchSemanticsNodes().isNotEmpty()) return
    closeStep()
    closeIngredient()
    // By its click action, as a screen reader would, so that a statement shown over the row leaves it in reach.
    onNodeWithTag(StepSheetTags.line(position)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    waitUntil(PATIENCE_MS) { onAllNodes(hasTestTag(EditorTags.stepText(position))).fetchSemanticsNodes().size == 1 }
    waitForIdle()
}

/** Closes the open step sheet by its Done, if one is open, and waits until it is gone. */
fun ComposeTestRule.closeStep() {
    if (!stepSheetOpen()) return
    onNodeWithTag(StepSheetTags.DONE).performClick()
    waitUntil(PATIENCE_MS) { !stepSheetOpen() }
    waitForIdle()
}

/**
 * Adds a step by "Add step" as the author does — closing any sheet open first — and waits until the new step's sheet
 * is open on it, at [position].
 */
fun ComposeTestRule.addStepAt(position: Int) {
    closeStep()
    closeIngredient()
    onNodeWithTag(EditorTags.STEP_ADD).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    waitUntil(PATIENCE_MS) { onAllNodes(hasTestTag(EditorTags.stepText(position))).fetchSemanticsNodes().size == 1 }
    waitForIdle()
}

/** Taps the action [action] in the sheet of the step at [position], opening it first. */
fun ComposeTestRule.stepAction(position: Int, action: RowAction) {
    openStep(position)
    onNodeWithTag(EditorTags.stepAction(position, action)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
}

/** Removes the step at [position] from its sheet, opening it first; the sheet closes. */
fun ComposeTestRule.removeStep(position: Int) {
    openStep(position)
    onNodeWithTag(EditorTags.stepRemove(position)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    waitUntil(PATIENCE_MS) { !stepSheetOpen() }
    waitForIdle()
}

/**
 * The text the row of the step at [position] reads: its text with its references, or its placeholder — its no-break
 * spaces, which hold a reference together, read as spaces.
 */
fun SemanticsNodeInteractionsProvider.stepLine(position: Int): String = stepLineAsShown(position).replace(StepText.NO_BREAK_SPACE, ' ')

/** As [stepLine], exactly as shown. */
fun SemanticsNodeInteractionsProvider.stepLineAsShown(position: Int): String =
    onNodeWithTag(StepSheetTags.text(position), useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

/** The text of the node tagged [tag] on a step's row, read apart from the row it is merged into. */
fun SemanticsNodeInteractionsProvider.rowText(tag: String): String =
    onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

/** The colour the text tagged [tag] itself — a step row's text or note — is drawn in. */
fun SemanticsNodeInteractionsProvider.ownTextColour(tag: String): Color {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
    return layouts.single().layoutInput.style.color
}

/** This rule with the sheet of the step at [position] open — opened as the author does, where it is not — to act in. */
fun ComposeTestRule.inStep(position: Int): ComposeTestRule {
    openStep(position)
    return this
}

/**
 * Whether the keyboard is up for a field in the topmost window — a step's sheet, whose window the shadow keyboard does
 * not follow: that window has the focus and its focused view takes text input, which brings the keyboard on a phone.
 */
fun ComposeTestRule.sheetTakesInput(): Boolean {
    var taking = false
    runOnUiThread { taking = windowRoots().last().let { it.hasFocus() && it.findFocus()?.onCheckIsTextEditor() == true } }
    return taking
}

/**
 * Brings the author to where the node tagged [tag] is: into the sheet of the step it belongs to — a step's text, note,
 * timers or picture, which are edited only there — or, for anything else, out of any step's sheet, which leaves the
 * step's fields.
 */
fun ComposeTestRule.reach(tag: String) {
    val step = Regex("editor-step-(\\d+)-(?!line).*").matchEntire(tag)?.groupValues?.get(1)?.toInt()
    if (step != null) openStep(step) else closeStep()
}

/** Opens the sheet of the step the node tagged [tag] belongs to, if it is part of a step's sheet; nothing otherwise. */
fun ComposeTestRule.reachStep(tag: String) {
    Regex("editor-step-(\\d+)-(?!line).*").matchEntire(tag)?.groupValues?.get(1)?.toInt()?.let { openStep(it) }
}

/**
 * Where the node tagged [tag] stands in the open step sheet's content, from its top: as laid out on screen, plus how far
 * the sheet is scrolled — so that the sheet growing or scrolling to keep a focused field in sight moves nothing here.
 */
fun ComposeTestRule.inSheetContent(tag: String): Rect {
    val scroll = onNode(hasAnyAncestor(hasTestTag(StepSheetTags.SHEET)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .fetchSemanticsNode()
    val scrolled = scroll.config[SemanticsProperties.VerticalScrollAxisRange].value()
    val node = onNodeWithTag(tag).fetchSemanticsNode()
    return Rect(node.positionInRoot, node.size.toSize()).translate(0f, scrolled - scroll.positionInRoot.y)
}
