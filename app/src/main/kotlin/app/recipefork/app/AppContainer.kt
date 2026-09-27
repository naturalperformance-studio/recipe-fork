package app.recipefork.app

import android.content.Context
import app.recipefork.app.data.AndroidConnectivity
import app.recipefork.app.data.AppDatabase
import app.recipefork.app.data.Network
import app.recipefork.app.dictionary.DictionaryRepository
import app.recipefork.app.dictionary.DictionarySync
import io.ktor.client.engine.android.Android

/** The composition root: every long-lived object of the application, wired by hand. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.open(context)
    val httpClient = Network.client(Android.create(), BuildConfig.SERVER_URL)
    val connectivity = AndroidConnectivity(context)
    val dictionaries = DictionaryRepository(httpClient, database.dictionaryDao())
    val dictionarySync = DictionarySync(connectivity, dictionaries)
}
