package app.recipefork.app.paste

import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What went wrong with the last reading: no recipe in the text, or a recipe that could not be written. */
enum class PasteProblem { NO_RECIPE, FAILED }

/**
 * The paste screen's reading (T-280), run in the session's [scope] rather than the screen's: the screen turning does
 * not cancel it, and a recipe once written always has the editor opened on it. Held in memory: a reading ends with the
 * process that ran it.
 */
class PasteRun(private val scope: CoroutineScope) {
    private val running = MutableStateFlow(false)
    private val said = MutableStateFlow<PasteProblem?>(null)

    /** Whether a reading is under way. */
    val busy: StateFlow<Boolean> = running.asStateFlow()

    /** What the screen says under its field about the last reading, until the text changes or the screen opens anew. */
    val problem: StateFlow<PasteProblem?> = said.asStateFlow()

    fun clear() {
        said.value = null
    }

    /**
     * Reads [text] through [reading]: a recipe written is handed to [onRead]; none, or a failure, is said in [problem].
     * Nothing while a reading is under way, or for a blank text.
     */
    fun read(text: String, reading: PasteReading, onRead: (RecipeId) -> Unit) {
        if (running.value || text.isBlank()) return
        running.value = true
        said.value = null
        scope.launch {
            val outcome = try {
                reading.read(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                said.value = PasteProblem.FAILED
                null
            } finally {
                running.value = false
            }
            when {
                outcome != null -> onRead(outcome)
                said.value == null -> said.value = PasteProblem.NO_RECIPE
            }
        }
    }
}
