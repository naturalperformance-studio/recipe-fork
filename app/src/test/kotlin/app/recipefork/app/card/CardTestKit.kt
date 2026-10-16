package app.recipefork.app.card

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import app.recipefork.app.editor.PATIENCE_MS
import org.junit.Assert.assertTrue

/** Turns the open card to its tab [mode] by a tap on the tab, and waits until the card has come to rest there. */
fun ComposeTestRule.chooseCardTab(mode: CardMode) {
    onNodeWithTag(CardTags.tab(mode)).performClick()
    waitUntil(PATIENCE_MS) {
        onNodeWithTag(CardTags.tab(mode)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true &&
            onAllNodes(hasTestTag(CardTags.CONTENT)).fetchSemanticsNodes().size == 1
    }
    waitForIdle()
}

/** Waits until the card is open, then takes its back action to the recipe book. */
fun ComposeTestRule.backThroughTheCard() {
    awaitCard()
    onNodeWithTag(CardTags.BACK).performClick()
    waitForIdle()
}

/** The card's ⋮ items, each that the menu may hold. */
private val CARD_MENU_ITEMS = setOf(CardTags.SHOPPING, CardTags.COLLECTION, CardTags.PRINT, CardTags.SHARE_PDF, CardTags.SOURCE_TEXT, CardTags.CLEAR_MARKS, CardTags.DELETE, CardTags.REMOVE)

/** Whether the card's ⋮ is open: one of its items is shown. */
private fun ComposeTestRule.cardMenuOpen() = CARD_MENU_ITEMS.any { onAllNodes(hasTestTag(it)).fetchSemanticsNodes().isNotEmpty() }

/** What the card's ⋮ holds, as its items' tags: opened, read and closed again, taking nothing from it. */
fun ComposeTestRule.cardMenuItems(): List<String> {
    openTheCardMenu()
    val items = tagsTopToBottom(CARD_MENU_ITEMS)
    closeTheCardMenu()
    return items
}

/** The tags of the nodes tagged one of [tags] that are shown, in the order they stand on screen, top to bottom. */
fun SemanticsNodeInteractionsProvider.tagsTopToBottom(tags: Set<String>): List<String> =
    onAllNodes(SemanticsMatcher("tagged one of $tags") { it.config.getOrNull(SemanticsProperties.TestTag) in tags })
        .fetchSemanticsNodes()
        .sortedBy { it.boundsInRoot.top }
        .map { it.config[SemanticsProperties.TestTag] }

/** Opens the card's ⋮ and waits for its items, without taking one. */
fun ComposeTestRule.openTheCardMenu() {
    onNodeWithTag(CardTags.MENU).performClick()
    waitUntil(PATIENCE_MS) { cardMenuOpen() }
}

/** Closes the card's open ⋮ by its own button, taking nothing from it. */
fun ComposeTestRule.closeTheCardMenu() {
    onNodeWithTag(CardTags.MENU).performClick()
    waitUntil(PATIENCE_MS) { !cardMenuOpen() }
}

/** Takes the card's floating Fork action, scrolling the serving count clear of it first where it stands aside for it. */
fun ComposeTestRule.forkFromTheCard() {
    scrollUntilForkShows()
    onNodeWithTag(CardTags.FORK).performClick()
    waitForIdle()
}

/**
 * Waits for View's floating Fork action; while it stands aside for the serving count under it, scrolls the content on
 * a step at a time, as a reader would, until it shows.
 */
fun ComposeTestRule.scrollUntilForkShows() {
    fun shown() = onAllNodes(hasTestTag(CardTags.FORK)).fetchSemanticsNodes().isNotEmpty()
    waitForIdle()
    var steps = 0
    while (!shown() && steps++ < FORK_SCROLL_STEPS) {
        onNodeWithTag(CardTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, FORK_SCROLL_STEP_PX) }
        waitForIdle()
    }
    waitUntil(PATIENCE_MS) { shown() }
}

private const val FORK_SCROLL_STEPS = 40
private const val FORK_SCROLL_STEP_PX = 40f

/**
 * Scrolls the card's content until the element tagged [tag] stands clear above the floating fork action, as a reader
 * scrolls a part out from under it before tapping it.
 */
fun ComposeTestRule.scrollClearOfFork(tag: String) {
    onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
    waitForIdle()
    if (onAllNodes(hasTestTag(CardTags.FORK)).fetchSemanticsNodes().isEmpty()) return
    val fork = onNodeWithTag(CardTags.FORK).fetchSemanticsNode().boundsInRoot
    val part = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    if (part.bottom > fork.top && part.right > fork.left) {
        onNodeWithTag(CardTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, part.bottom - fork.top) }
        waitForIdle()
    }
}

/** The element tagged [tag] is laid out at least the card's [CardTarget] in each direction (A11Y-003 (2), A11Y-007). */
fun SemanticsNodeInteractionsProvider.assertCardTarget(tag: String) {
    val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    val density = node.layoutInfo.density.density
    val width = node.size.width / density
    val height = node.size.height / density
    assertTrue("$tag is $width x $height dp, less than $CardTarget", width + 0.5f >= CardTarget.value && height + 0.5f >= CardTarget.value)
}

/** Every text laid out within the element tagged [tag], itself included, as it is laid out now. */
fun SemanticsNodeInteractionsProvider.textLayoutsWithin(tag: String): List<TextLayoutResult> =
    onAllNodes(hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
        .mapNotNull { node ->
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { action ->
                mutableListOf<TextLayoutResult>().also { action(it) }.firstOrNull()
            }
        }

/**
 * Every text within the element tagged [tag] reads whole (A11Y-004): given room, nothing of it cut off, and broken
 * only between words, never inside one; and the element lies within the screen's width.
 */
fun SemanticsNodeInteractionsProvider.assertWhole(tag: String) {
    val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    val screen = node.layoutInfo.coordinates.let { generateSequence(it) { c -> c.parentCoordinates }.last() }.size.width
    assertTrue("$tag lies within the screen: ${node.boundsInRoot} in $screen px",
        node.boundsInRoot.left >= -0.5f && node.boundsInRoot.right <= screen + 0.5f)
    val layouts = textLayoutsWithin(tag)
    assertTrue("$tag shows text", layouts.isNotEmpty())
    layouts.forEach { layout ->
        val text = layout.layoutInput.text.text
        assertTrue("\"$text\" in $tag has room (${layout.size.width} px)", layout.size.width > 0)
        assertTrue("\"$text\" in $tag is not cut off",
            !layout.didOverflowHeight && !layout.multiParagraph.didExceedMaxLines &&
                layout.getLineEnd(layout.lineCount - 1) == text.length &&
                (0 until layout.lineCount).all { layout.getLineRight(it) <= layout.size.width + 0.5f })
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            assertTrue("\"$text\" in $tag breaks only between words, not after \"${text.substring(layout.getLineStart(line), end)}\"",
                text[end - 1].isWhitespace() || text.getOrNull(end)?.isWhitespace() == true)
        }
    }
}

/** Waits until the card is open and shows its content or one of its messages. */
fun ComposeTestRule.awaitCard() = waitUntil(PATIENCE_MS) {
    listOf(CardTags.CONTENT, CardTags.MISSING, CardTags.UNREADABLE).any { onAllNodes(hasTestTag(it)).fetchSemanticsNodes().size == 1 }
}

/**
 * The tag of the ingredient line or step first at the top of the card's scrolling content: the first whose bottom is
 * below the top of the screen. Measured unclipped, so that a part scrolled out of sight is never taken for one in it.
 */
fun ComposeTestRule.firstAtTheTop(): String {
    val top = onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().positionInRoot.y
    val part = Regex("card-(ingredient|step)-\\d+")
    return onAllNodes(SemanticsMatcher("an ingredient line or a step") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.matches(part) == true
    }).fetchSemanticsNodes()
        .filter { it.positionInRoot.y + it.size.height > top + 0.5f }
        .minBy { it.positionInRoot.y }
        .config[SemanticsProperties.TestTag]
}
