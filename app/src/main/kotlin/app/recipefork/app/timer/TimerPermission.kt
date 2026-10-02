package app.recipefork.app.timer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.recipefork.app.R

/**
 * After each timer the card starts — never before, and never at launch (FLOW-013 (8)) — a look at whether it will be
 * heard. Permitted, nothing is shown (FLOW-013 (11)). Not permitted where the platform has a request, the request is
 * made, and a refusal, or a platform that no longer asks, is explained at once (FLOW-013 (9)); where it has none, or
 * timer notifications alone are off, the explanation is shown straight away (FLOW-013 (10, 13)). The timer has started
 * by then and runs whatever the answer (FLOW-013 (12)).
 */
@Composable
fun TimerNotificationsCheck(timers: RecipeTimers) {
    val access = timers.access
    var explaining by remember(timers) { mutableStateOf<NotificationState?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val state = access.state()
        explaining = if (!granted) NotificationState.OFF else state.takeUnless { it == NotificationState.PERMITTED }
    }
    val starts = timers.starts
    LaunchedEffect(timers, starts) {
        if (starts == 0) return@LaunchedEffect
        when (val state = access.state()) {
            NotificationState.PERMITTED -> Unit
            NotificationState.OFF ->
                if (access.hasRequest) request.launch(Manifest.permission.POST_NOTIFICATIONS) else explaining = state
            NotificationState.TIMERS_OFF -> explaining = state
        }
    }
    explaining?.let { state -> Explanation(state, access, onDone = { explaining = null }) }
}

/** The timer will not be heard while the application is closed or the screen locked; the settings are a tap away. */
@Composable
private fun Explanation(state: NotificationState, access: NotificationAccess, onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.timer_unheard_title)) },
        text = {
            Text(
                stringResource(
                    if (state == NotificationState.TIMERS_OFF) R.string.timer_unheard_timers_off else R.string.timer_unheard_off,
                ),
                modifier = Modifier.testTag(TimerTags.UNHEARD_TEXT),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDone()
                    access.openSettings(context, state)
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag(TimerTags.UNHEARD_SETTINGS),
            ) { Text(stringResource(R.string.timer_unheard_settings)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDone,
                modifier = Modifier.heightIn(min = 48.dp).testTag(TimerTags.UNHEARD_DISMISS),
            ) { Text(stringResource(R.string.timer_unheard_dismiss)) }
        },
        modifier = Modifier.testTag(TimerTags.UNHEARD),
    )
}
