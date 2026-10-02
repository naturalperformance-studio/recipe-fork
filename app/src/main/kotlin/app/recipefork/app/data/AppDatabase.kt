package app.recipefork.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.recipefork.app.dictionary.DictionaryDao
import app.recipefork.app.dictionary.DictionaryVersionEntity
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.TagEntity

/** The device's local store: its truth until the server has accepted a change. */
@Database(
    entities = [IngredientEntryEntity::class, TagEntity::class, DictionaryVersionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "recipe-fork.db").build()
    }
}
