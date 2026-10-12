package android.print

/**
 * What a print adapter told its caller, for tests: the platform's callbacks can only be made within their package.
 */
class RecordedLayout : PrintDocumentAdapter.LayoutResultCallback() {
    var info: PrintDocumentInfo? = null
    var changed: Boolean? = null
    var failed = false
    var cancelled = false
    val answered get() = info != null || failed || cancelled

    override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
        this.info = info
        this.changed = changed
    }

    override fun onLayoutFailed(error: CharSequence?) {
        failed = true
    }

    override fun onLayoutCancelled() {
        cancelled = true
    }
}

class RecordedWrite : PrintDocumentAdapter.WriteResultCallback() {
    var written: List<PageRange>? = null
    var failed = false
    var cancelled = false
    val answered get() = written != null || failed || cancelled

    override fun onWriteFinished(pages: Array<out PageRange>) {
        written = pages.toList()
    }

    override fun onWriteFailed(error: CharSequence?) {
        failed = true
    }

    override fun onWriteCancelled() {
        cancelled = true
    }
}
