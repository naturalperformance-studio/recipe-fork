package app.recipefork.app

import android.content.Context
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.data.AndroidConnectivity
import app.recipefork.app.data.AppDatabase
import app.recipefork.app.data.Network
import app.recipefork.app.dictionary.DictionaryRepository
import app.recipefork.app.dictionary.DictionarySync
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.AndroidRingingSound
import app.recipefork.app.timer.AndroidTimerNotices
import app.recipefork.app.timer.DeviceTimerAnnouncements
import app.recipefork.app.timer.RunningTimers
import app.recipefork.app.timer.SystemTimerClock
import app.recipefork.app.timer.TimerAnnouncer
import app.recipefork.app.timer.TimerDatabase
import app.recipefork.app.timer.TimerRinging
import app.recipefork.app.timer.applicationIsOpen
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The composition root: every long-lived object of the application, wired by hand. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.open(context)
    val httpClient = Network.client(Android.create(), BuildConfig.SERVER_URL)
    val connectivity = AndroidConnectivity(context)
    val dictionaries = DictionaryRepository(httpClient, database.dictionaryDao())
    val dictionarySync = DictionarySync(connectivity, dictionaries)

    /** The pool is the only recipe book so far (AUTH-026). */
    val activeBook = ActiveBook(BookName.Pool, BookDatabase.open(context, BookName.Pool))
    val recipes = RecipeRepository(activeBook)

    private val timerNotices = AndroidTimerNotices(context)

    /** Ended timers ringing like an alarm until they are stopped (FLOW-013). */
    val timerRinging = TimerRinging(SystemTimerClock, AndroidRingingSound(context), timerNotices)

    /** Announces timers as they end, with the application open or closed (FLOW-013), and rings them. */
    val timerAnnouncer = TimerAnnouncer(timerNotices, timerRinging, ::applicationIsOpen)

    /** The device's running timers, of every book; held by the moment each ends (FLOW-016). */
    val timers = RunningTimers(
        TimerDatabase.open(context).timers(),
        SystemTimerClock,
        DeviceTimerAnnouncements(context, timerAnnouncer),
    )

    /** Where timers are started and cancelled: it outlives every screen, so each start and cancel is finished. */
    val timerWork = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
