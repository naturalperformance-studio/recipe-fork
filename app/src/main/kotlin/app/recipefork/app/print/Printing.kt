package app.recipefork.app.print

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.recipefork.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What the ⋮'s Print and Share as PDF do with a document (FLOW-075). */
interface Printing {
    /** Hands [document] to the system's print service, the job named by its title. */
    fun print(context: Context, document: PrintDocument)

    /** Writes [document] as a PDF and opens the share sheet on it; false when that could not be done. */
    suspend fun share(context: Context, document: PrintDocument): Boolean
}

/**
 * The platform's printing: [start] starts a print job of a name with its adapter (the [PrintManager]'s by default), and
 * [writer] writes the PDF's pages.
 */
class PlatformPrinting(
    private val writer: PdfWriter = PdfWriter.Platform,
    private val start: (Context, String, PrintDocumentAdapter) -> Unit = { context, name, adapter ->
        context.getSystemService(PrintManager::class.java).print(name, adapter, null)
    },
) : Printing {
    override fun print(context: Context, document: PrintDocument) {
        val fileName = PdfShare.fileName(document.title, context.getString(R.string.common_untitled_recipe))
        // The print service runs a job only from an activity.
        start(context.activity() ?: context, document.title, PagesPrintAdapter(document, fileName, writer))
    }

    override suspend fun share(context: Context, document: PrintDocument): Boolean = PdfShare.share(context, document, writer)

    private tailrec fun Context.activity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
}

/** The printing the screens use; tests provide their own. */
val LocalPrinting = staticCompositionLocalOf<Printing> { PlatformPrinting() }

/** Which of the ⋮'s two items was taken. */
enum class PrintWay { PRINT, SHARE }

/**
 * The ⋮'s Print and Share as PDF of a screen: the document is made when the item is taken — by `make`, null for
 * nothing to print — and printed or shared; a failure says so in [snackbars], replacing what it said before, and changes
 * nothing. A tap while a document is being made or written starts no second one. [onBusy] hears true as one starts and
 * false once it stops, however it stops; [onOver] is called once it has run to its end — handed over, or failed and
 * about to say so — and never when it was cancelled (the screen gone or recreated meanwhile).
 */
@Composable
fun rememberPrintOut(
    snackbars: SnackbarHostState,
    onBusy: (Boolean) -> Unit = {},
    onOver: () -> Unit = {},
): (PrintWay, suspend () -> PrintDocument?) -> Unit {
    val busyNow by rememberUpdatedState(onBusy)
    val overNow by rememberUpdatedState(onOver)
    val context = LocalContext.current
    val printing = LocalPrinting.current
    val scope = rememberCoroutineScope()
    val shareFailed = stringResource(R.string.print_share_failed)
    val printFailed = stringResource(R.string.print_failed)
    val busy = remember { BooleanArray(1) }
    return remember(context, printing, scope, snackbars, shareFailed, printFailed) {
        { way, make ->
            if (!busy[0]) {
                busy[0] = true
                busyNow(true)
                scope.launch {
                    val done = try {
                        val document = make()
                        when {
                            document == null -> true
                            way == PrintWay.PRINT -> {
                                printing.print(context, document)
                                true
                            }
                            else -> printing.share(context, document)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    } finally {
                        busy[0] = false
                        busyNow(false)
                    }
                    // Reached only when it was not cancelled.
                    overNow()
                    if (!done) {
                        snackbars.currentSnackbarData?.dismiss()
                        snackbars.showSnackbar(if (way == PrintWay.PRINT) printFailed else shareFailed)
                    }
                }
            }
        }
    }
}
