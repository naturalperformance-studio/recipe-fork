package app.recipefork.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.media.PhotoAttaching

/** Says at the bottom of the editor what [attaching] tells the author — for the cover or a step — once each. */
@Composable
internal fun SaysPhotoNotices(attaching: PhotoAttaching) {
    val statements = LocalEditorStatements.current
    val statement by attaching.statement
    val text = statement?.let { stringResource(it.notice.text) }
    LaunchedEffect(statement) {
        val told = statement ?: return@LaunchedEffect
        statements?.say(text.orEmpty())
        attaching.stated(told)
    }
}

/**
 * That a photograph is being prepared for a place — the cover or a step: a small indicator on its photograph, or where
 * it will show, with no text under it. The screen reader is told as it appears.
 */
@Composable
internal fun PhotoWorking(modifier: Modifier = Modifier) {
    val said = stringResource(R.string.media_working)
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .semantics {
                contentDescription = said
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
    }
}
