package app.recipefork.app.paste

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What went wrong with the last reading: no recipe in the text, or a recipe that could not be written. */
private enum class PasteProblem { NO_RECIPE, FAILED }

/**
 * The paste screen (T-280): one multi-line field holding a recipe's text — the [given] text shared to the application,
 * else the clipboard's text — and "Read", which reads it through [reading] into a new recipe of the owner's and hands
 * it to [onRead]. A text that holds no recipe keeps the screen, saying so under the field, and writes nothing. The
 * field's text survives the screen turning and the process ending; [opening] — the session's count of openings —
 * starts it anew when a text is shared while the screen is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteScreen(
    given: String?,
    opening: Int,
    reading: PasteReading,
    onBack: () -> Unit,
    onRead: (RecipeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var text by rememberSaveable(opening) { mutableStateOf(given ?: clipboardText(context).orEmpty()) }
    var problem by rememberSaveable(opening) { mutableStateOf<PasteProblem?>(null) }
    // Not saved: a reading ends with the process that ran it.
    var busy by remember(opening) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val read: () -> Unit = {
        if (!busy && text.isNotBlank()) {
            busy = true
            problem = null
            val asRead = text
            scope.launch {
                val outcome = try {
                    reading.read(asRead)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    problem = PasteProblem.FAILED
                    null
                } finally {
                    busy = false
                }
                when {
                    outcome != null -> onRead(outcome)
                    problem == null -> problem = PasteProblem.NO_RECIPE
                }
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(PasteTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paste_title), Modifier.semantics { heading() }.testTag(PasteTags.TITLE)) },
                navigationIcon = {
                    val action = stringResource(R.string.paste_back_action)
                    IconButton(
                        onClick = onBack,
                        // Material's icon button lays out at 40 dp; a target is 48 by 48 dp at least (A11Y-003).
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                onClick(label = action) {
                                    onBack()
                                    true
                                }
                            }
                            .testTag(PasteTags.BACK),
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.paste_back))
                    }
                },
                actions = {
                    val action = stringResource(R.string.paste_read_action)
                    val enabled = !busy && text.isNotBlank()
                    TextButton(
                        onClick = read,
                        enabled = enabled,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .semantics {
                                if (enabled) {
                                    onClick(label = action) {
                                        read()
                                        true
                                    }
                                }
                            }
                            .testTag(PasteTags.READ),
                    ) {
                        Text(stringResource(R.string.paste_read))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            // A fixed slot, so that the field does not move as the reading starts and ends.
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (busy) {
                    val described = stringResource(R.string.paste_reading)
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().semantics { contentDescription = described }.testTag(PasteTags.PROGRESS),
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    problem = null
                },
                label = { Text(stringResource(R.string.paste_field)) },
                isError = problem != null,
                supportingText = problem?.let { shown ->
                    {
                        Text(
                            stringResource(if (shown == PasteProblem.NO_RECIPE) R.string.paste_no_recipe else R.string.paste_failed),
                            Modifier.testTag(PasteTags.MESSAGE),
                        )
                    }
                },
                // Multi-line: Enter starts a new line; nothing is read until "Read".
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                    .testTag(PasteTags.FIELD),
            )
        }
    }
}

/** The clipboard's text, or null when it holds none. */
private fun clipboardText(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() }
}

object PasteTags {
    const val SCREEN = "paste-screen"
    const val TITLE = "paste-title"
    const val BACK = "paste-back"
    const val READ = "paste-read"
    const val FIELD = "paste-field"
    const val PROGRESS = "paste-progress"
    const val MESSAGE = "paste-message"
}
