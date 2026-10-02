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
import app.recipefork.app.timer.RunningTimers
import app.recipefork.app.timer.SystemTimerClock
import app.recipefork.app.timer.TimerDatabase
import io.ktor.client.engine.android.Android

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

    /** The device's running timers, of every book; held by the moment each ends (FLOW-016). */
    val timers = RunningTimers(TimerDatabase.open(context).timers(), SystemTimerClock)
}
