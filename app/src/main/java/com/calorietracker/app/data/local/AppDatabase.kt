package com.calorietracker.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Room SQLite Database singleton instance.
 */
@Database(entities = [MealEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mealDao(): MealDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calorie_tracker_db"
                )
                .addCallback(DatabaseCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Pre-seed database with historical sample entries on first install
            CoroutineScope(Dispatchers.IO).launch {
                val dao = getInstance(context).mealDao()
                val seedData = listOf(
                    MealEntity(
                        dateIso = "2026-09-07",
                        foodName = "Haldiram Bread, Saoji Gravy, Eggs, Tarri Poha",
                        portionDescription = "Daily food log",
                        calories = 1685,
                        proteinGrams = 110.5f,
                        carbsGrams = 175f,
                        fatGrams = 48f,
                        mealCategory = "Day Log"
                    ),
                    MealEntity(
                        dateIso = "2026-09-08",
                        foodName = "Rotis, Chicken Dry/Curry, Nakpro Whey, Sabudana Khichdi",
                        portionDescription = "Daily food log",
                        calories = 2302,
                        proteinGrams = 154.5f,
                        carbsGrams = 220f,
                        fatGrams = 62f,
                        mealCategory = "Day Log"
                    ),
                    MealEntity(
                        dateIso = "2026-09-09",
                        foodName = "3 Rotis, Dahi Samosa, 200g Chicken, Nakpro Whey",
                        portionDescription = "Daily food log",
                        calories = 1945,
                        proteinGrams = 147.5f,
                        carbsGrams = 195f,
                        fatGrams = 55f,
                        mealCategory = "Day Log"
                    ),
                    MealEntity(
                        dateIso = "2026-09-10",
                        foodName = "3 Rotis, Chicken Curry, 2 scoops Nakpro Whey, Eggs",
                        portionDescription = "Daily food log",
                        calories = 2615,
                        proteinGrams = 192.0f,
                        carbsGrams = 230f,
                        fatGrams = 70f,
                        mealCategory = "Day Log"
                    )
                )
                dao.insertAll(seedData)
            }
        }
    }
}
