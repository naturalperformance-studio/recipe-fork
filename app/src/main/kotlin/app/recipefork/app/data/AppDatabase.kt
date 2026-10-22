package app.recipefork.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.recipefork.app.dictionary.DictionaryDao
import app.recipefork.app.dictionary.FactorsConverter
import app.recipefork.app.dictionary.DictionaryVersionEntity
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.TagEntity

/**
 * The device's local store: its truth until the server has accepted a change.
 *
 * Version 2 gave an ingredient entry its divisibility, factors and removal; version 3 its synonyms; version 4 added the equipment
 * dictionary; version 5 gave an ingredient entry its nutrition. A change of version wipes the store
 * rather than migrating it — what it holds is the dictionaries, which seeding and the next refresh bring back, and
 * the owner's book holds test data only — and the dictionaries are seeded again on their next read.
 */
@Database(
    entities = [IngredientEntryEntity::class, TagEntity::class, EquipmentEntryEntity::class, DictionaryVersionEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = false,
)
@TypeConverters(FactorsConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        const val VERSION = 5
        const val NAME = "recipe-fork.db"

        fun open(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
