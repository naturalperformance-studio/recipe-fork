package app.recipefork.app.editor

import androidx.activity.compose.BackHandler
import app.recipefork.app.ui.AppNavHost
import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import app.recipefork.app.data.AppDatabase
import app.recipefork.app.dictionary.DictionaryCatalog
import app.recipefork.app.dictionary.DictionarySeeding
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.SeededVersion
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The device database held in memory for a screen test, whose work the Compose rule waits for — as `TestBook`
 * does for a recipe book: every task handed to Room's executors is counted until it has finished.
 */
class TestDevice(context: Context) {
    private val running = AtomicInteger(0)
    private val threads = Executors.newCachedThreadPool()

    private val counted = Executor { task ->
        running.incrementAndGet()
        try {
            threads.execute {
                try {
                    task.run()
                } finally {
                    running.decrementAndGet()
                }
            }
        } catch (e: RuntimeException) {
            running.decrementAndGet()
            throw e
        }
    }

    val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .setQueryExecutor(counted)
        .setTransactionExecutor(counted)
        .build()

    val idling: IdlingResource = object : IdlingResource {
        override val isIdleNow: Boolean get() = running.get() == 0
        override fun getDiagnosticMessageIfBusy() = "${running.get()} device database tasks queued or running"
    }

    /** The version the held copy was seeded from, as the device's preferences would keep it. */
    val seeded = object : SeededVersion {
        @Volatile private var version: String? = null
        override fun get() = version
        override fun set(version: String) {
            this.version = version
        }
    }

    /** The dictionaries as the editor reads them, seeded from the snapshot this application ships. */
    fun catalog(context: Context) = database.dictionaryDao().let { dao ->
        DictionaryCatalog(dao, DictionarySeeding(dao, seeded) { DictionarySnapshot.read(context) })
    }

    fun close() {
        database.close()
        threads.shutdown()
    }
}

/**
 * The own-recipes view, or — while [opened] holds a recipe — the editor opened on that recipe directly, through the
 * editor screen's own entry point. This is a test fixture for what the editor allows on a seeded recipe, frozen ones
 * included, without going through the recipe's card, which is how the application reaches the editor (FLOW-029 (1)).
 * The editor's back action and the system back close it straight to the book, with no card in between.
 */
@Composable
fun LibraryOrEditor(
    session: LibrarySession,
    recipes: RecipeRepository,
    environment: EditorEnvironment,
    opened: MutableState<RecipeId?>,
) {
    val id = opened.value
    if (id != null) {
        // The system back is the navigation host's in the application; here, with no host, the fixture takes it.
        BackHandler { opened.value = null }
        EditorScreen(id, recipes, environment, session.viewModelScope, onClose = { opened.value = null })
    } else {
        AppNavHost(session, recipes, editor = environment)
    }
}

/** Dictionaries as a server could hold them: three tags and a few entries. */
val SAMPLE_DICTIONARIES = DictionariesDto(
    version = "sample-1",
    ingredients = listOf(IngredientEntryDto(1, "курица", "chicken"), IngredientEntryDto(2, "крылышки", "chicken wings", 1)),
    tags = listOf(
        TagDto(10, "итальянская", "Italian"),
        TagDto(11, "грузинская", "Georgian"),
        TagDto(12, "десерт", "Dessert"),
    ),
)

/** Every label of [dictionaries]' tags, in both languages: the author's content on a screen, not interface text. */
fun tagLabels(dictionaries: DictionariesDto): Set<String> = dictionaries.tags.flatMap { listOf(it.labelRu, it.labelEn) }.toSet()

object Connection {
    fun offline(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(manager).setDefaultNetworkActive(false)
        @Suppress("DEPRECATION")
        shadowOf(manager).setActiveNetworkInfo(null)
    }

    fun isOffline(context: Context): Boolean =
        context.getSystemService(ConnectivityManager::class.java).activeNetworkInfo == null
}

/** How long a wait on a condition may take before it counts as failed: generous, never what passes. */
const val PATIENCE_MS = 60_000L

/** Waits until exactly one node carries [tag]. */
fun ComposeTestRule.awaitTag(tag: String) = waitUntil(PATIENCE_MS) {
    onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size == 1
}

/**
 * Adds the tag [id] in the editor as the author does: types [typed] into the tag search — whose presence says the
 * dictionary is read — and taps the tag once it is offered.
 */
fun ComposeTestRule.addTag(typed: String, id: Long) {
    awaitTag(EditorTags.TAG_SEARCH)
    onNodeWithTag(EditorTags.TAG_SEARCH).performScrollTo().performTextReplacement(typed)
    awaitTag(EditorTags.offered(id))
    onNodeWithTag(EditorTags.offered(id)).performScrollTo().performClick()
}

/**
 * Waits until [text] is the one statement said at the bottom of the editor: a single statement shows, and it reads
 * [text].
 */
fun ComposeTestRule.awaitStatement(text: String) = waitUntil(PATIENCE_MS) {
    val shown = onAllNodes(hasTestTag(EditorTags.STATEMENT), useUnmergedTree = true).fetchSemanticsNodes()
    shown.size == 1 && onAllNodes(
        hasText(text) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size == 1
}

/**
 * Swipes away the statement shown at the bottom of the editor, if one is, as the author would to reach what it covers,
 * once whatever is being prepared has had its say.
 */
fun ComposeTestRule.swipeAwayStatement() {
    waitForIdle()
    if (onAllNodes(hasTestTag(EditorTags.STATEMENT), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) return
    onNodeWithTag(EditorTags.STATEMENT, useUnmergedTree = true).performTouchInput { swipeRight() }
    waitUntil(PATIENCE_MS) { onAllNodes(hasTestTag(EditorTags.STATEMENT), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
}

/** The texts of the node tagged [tag], merged as the screen reader reads them, then what a field holds. */
fun SemanticsNodeInteractionsProvider.textsOf(tag: String): List<String> {
    val config = onNodeWithTag(tag).fetchSemanticsNode().config
    return config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } +
        listOfNotNull(config.getOrElseNullable(SemanticsProperties.EditableText) { null }?.text)
}

/** What the screen reader reads for the node tagged [tag] beyond any text: its content description. */
fun SemanticsNodeInteractionsProvider.descriptionOf(tag: String): List<String> =
    onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }

/** The language the editor's segmented switch shows declared: the one segment selected, or null where neither is. */
fun SemanticsNodeInteractionsProvider.declaredLanguage(): RecipeLanguage? =
    RecipeLanguage.entries.filter { language ->
        onNodeWithTag(EditorTags.language(language)).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected) { false }
    }.also { check(it.size <= 1) { "both languages shown declared" } }.singleOrNull()

/** Taps [language] on the editor's segmented switch, scrolled to first. */
fun SemanticsNodeInteractionsProvider.declareLanguage(language: RecipeLanguage) {
    onNodeWithTag(EditorTags.language(language)).performScrollTo().performClick()
}
